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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.hash.HashCode;
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
public final class DefaultRequestDispatcher extends AbstractExecutionThreadService implements RequestDispatcher {
  private static final int CHANNEL_WAIT_DURATION = 5000;

  private final Bootstrap bootstrap;
  private final ChannelGroup channelGroup;
  private final DelayQueue<DelayedUserAgentRequest> queue;
  private final HostAndPort hostAndPort;
  private final LoadingCache<HashCode, UserAgent> userAgentCache;
  private final UserAgentChannelWriterFactory channelWriterFactory;

  private Logger log = LoggerFactory.getLogger(RequestDispatcher.class);

  @Inject
  DefaultRequestDispatcher(Bootstrap bootstrap, @Named("target") HostAndPort hostAndPort,
                           UserAgentChannelWriterFactory channelWriterFactory,
                           final UserAgentFactory userAgentFactory) {
    this.bootstrap = checkNotNull(bootstrap);
    this.hostAndPort = checkNotNull(hostAndPort);
    this.channelWriterFactory = checkNotNull(channelWriterFactory);

    checkNotNull(userAgentFactory);
    CacheLoader<HashCode, UserAgent> loader = new CacheLoader<HashCode, UserAgent>() {
      @Override
      public UserAgent load(HashCode key) throws Exception {
        return userAgentFactory.create(key);
      }
    };
    userAgentCache = CacheBuilder.newBuilder().build(loader);

    channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    queue = new DelayQueue<>();
  }

  @Override
  public void clearQueue() {
    queue.clear();
    channelGroup.disconnect();
  }

  @Override
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
        future.addListener(channelWriterFactory.create(request, userAgentCache));
        channelGroup.add(future.channel());
      }
    }
  }

  private void checkSkew(long actualTime, long expectedTime) {
    long skew = actualTime - expectedTime;
    if (Math.abs(skew) > SKEW_THRESHOLD_MILLIS) {
      String message = skew > 0 ? "Dispatcher is behind simulated time by {}ms" : "Dispatcher is ahead of simulated time by {}ms";
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
