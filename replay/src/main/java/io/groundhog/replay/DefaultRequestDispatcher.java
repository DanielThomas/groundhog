/*
 * Copyright 2013-2014 the original author or authors.
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

import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.Service;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public class DefaultRequestDispatcher extends AbstractExecutionThreadService implements RequestDispatcher {
  private static final int SKEW_THRESHOLD_MILLIS = 100;
  private static final int CHANNEL_WAIT_DURATION = 5000;

  private final Bootstrap bootstrap;
  private final ChannelGroup channelGroup;
  private final DelayQueue<DelayedUserAgentRequest> queue;
  private final HostAndPort hostAndPort;
  private final ReplayResultListener resultListener;

  private Logger log = LoggerFactory.getLogger(RequestDispatcher.class);

  @Inject
  DefaultRequestDispatcher(Bootstrap bootstrap, @Named("target") HostAndPort hostAndPort, ReplayResultListener resultListener) {
    this.bootstrap = checkNotNull(bootstrap);
    this.hostAndPort = checkNotNull(hostAndPort);
    this.resultListener = checkNotNull(resultListener);

    channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    queue = new DelayQueue<>();
  }

  public Service clearQueue() {
    queue.clear();
    return stopAsync();
  }

  public void queue(DelayedUserAgentRequest request) throws InterruptedException {
    checkNotNull(request);
    checkState(isRunning(), "This dispatcher is not running");
    queue.put(request);
  }

  @Override
  protected void startUp() throws Exception {
    log.info("Request dispatcher starting up");
  }

  @Override
  protected void run() throws Exception {
    long startTime = System.nanoTime();
    log.info("Running request replay, with skew threshold of {}ms", SKEW_THRESHOLD_MILLIS);
    while (isRunning() || !queue.isEmpty()) {
      DelayedUserAgentRequest delayedRequest = queue.poll(SKEW_THRESHOLD_MILLIS, TimeUnit.MILLISECONDS);
      if (null != delayedRequest) {
        long actualTime = System.nanoTime() - startTime;
        checkSkew(TimeUnit.NANOSECONDS.toMillis(actualTime), delayedRequest.getExpectedTime());
        ChannelFuture future = bootstrap.connect(hostAndPort.getHostText(), hostAndPort.getPort());
        UserAgentRequest request = delayedRequest.getRequest();
        future.addListener(new UserAgentChannelWriter(request, resultListener, log));
        channelGroup.add(future.channel());
      }
    }
  }

  private void checkSkew(long actualTime, long expectedTime) {
    long skew = actualTime - expectedTime;
    if (Math.abs(skew) > SKEW_THRESHOLD_MILLIS) {
      String message = skew > 0 ? "Dispatcher is behind recorded time by {}ms" : "Dispatcher is ahead of recorded time by {}ms";
      log.warn(message, skew);
    }
  }

  @Override
  protected void shutDown() throws Exception {
    log.info("Request dispatcher shutting down");
    while (!channelGroup.isEmpty()) {
      log.info("Waiting for in flight channels to complete...");
      Thread.sleep(CHANNEL_WAIT_DURATION);
    }
    checkState(queue.isEmpty(), "The request queue should have been drained before shutdown");
  }
}
