/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   whttp://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.groundhog.replay;

import com.google.common.util.concurrent.AbstractExecutionThreadService;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * @author Danny Thomas
 * @since 0.1
 */
public class RequestDispatcher extends AbstractExecutionThreadService {
  private static final Logger LOG = LoggerFactory.getLogger(RequestDispatcher.class);

  private static final int QUEUE_TIMEOUT = 1000;
  private static final int QUEUE_LENGTH = 1000;
  private static final int SKEW_THRESHOLD = 100;
  private static final int CHANNEL_WAIT_DURATION = 5000;

  private final Bootstrap bootstrap;
  private final ChannelGroup channelGroup;
  private final DelayedRequestQueue queue;
  private final String hostname;
  private final int port;

  public RequestDispatcher(Bootstrap bootstrap, String hostname, int port) {
    this.bootstrap = checkNotNull(bootstrap);
    this.hostname = checkNotNull(hostname);
    this.port = checkNotNull(port);

    channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    queue = new DelayedRequestQueue(QUEUE_LENGTH);
  }

  public void queue(DelayedReplayRequest request) throws InterruptedException {
    checkNotNull(request);
    checkState(isRunning(), "The dispatcher is not running");
    boolean queued = false;
    while (!queued) {
      queued = queue.put(request, QUEUE_TIMEOUT);
      if (!isRunning()) {
        break;
      }
    }
  }

  @Override
  protected void startUp() throws Exception {
    LOG.info("Dispatcher starting up");
  }

  @Override
  protected void run() throws Exception {
    long startTime = System.nanoTime();
    LOG.info("Running request replay");
    while (isRunning() || !queue.isEmpty()) {
      DelayedReplayRequest delayedRequest = queue.poll(QUEUE_TIMEOUT);
      if (null != delayedRequest) {
        long actualTime = System.nanoTime() - startTime;
        checkSkew(TimeUnit.NANOSECONDS.toMillis(actualTime), delayedRequest.getExpectedTime());

        LOG.debug("Adding request to channel {}", delayedRequest);
        ChannelFuture future = bootstrap.connect(hostname, port);
        UserAgentRequest request = delayedRequest.getRequest();
        future.addListener(request);
        channelGroup.add(future.channel());
      }
    }
  }

  private void checkSkew(long actualTime, long expectedTime) {
    long skew = actualTime - expectedTime;
    if (Math.abs(skew) > SKEW_THRESHOLD) {
      String message = skew > 0 ? "Dispatcher is behind recorded time by {}ms" : "Dispatcher is ahead of recorded time by {}ms";
      LOG.warn(message, skew);
    }
  }

  @Override
  protected void shutDown() throws Exception {
    LOG.info("Dispatcher shutting down");

    while (!channelGroup.isEmpty()) {
      LOG.info("Waiting for in flight channels to complete...");
      Thread.sleep(CHANNEL_WAIT_DURATION);
    }

    checkState(queue.isEmpty(), "The request queue should have been drained before shutdown");
  }

  /**
   * A composed {@link java.util.concurrent.DelayQueue} that supports blocking on capacity.
   */
  private static final class DelayedRequestQueue {
    private final DelayQueue<DelayedReplayRequest> queue = new DelayQueue<>();
    private final Semaphore available;

    public DelayedRequestQueue(int capacity) {
      available = new Semaphore(capacity, true);
    }

    public boolean put(DelayedReplayRequest e, long timeout) throws InterruptedException {
      if (available.tryAcquire(timeout, TimeUnit.MILLISECONDS)) {
        queue.put(e);
        return true;
      } else {
        return false;
      }
    }

    public DelayedReplayRequest poll(long timeout) throws InterruptedException {
      DelayedReplayRequest request = queue.poll(timeout, TimeUnit.MILLISECONDS);
      available.release();
      return request;
    }

    public boolean isEmpty() {
      return queue.isEmpty();
    }
  }

}
