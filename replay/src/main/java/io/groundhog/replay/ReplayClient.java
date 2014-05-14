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

import com.google.common.base.Throwables;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public final class ReplayClient extends AbstractExecutionThreadService {
  private static final Logger LOG = LoggerFactory.getLogger(ReplayClient.class);

  // The maximum delay for a request before the service pauses reading requests, to prevent excessive number of objects in the dispatcher queue
  private static final int DELAY_LIMIT_MS = 10000;

  private final EventLoopGroup group;
  private final RequestReader requestReader;
  private final RequestDispatcher dispatcher;

  @Inject
  public ReplayClient(File recordingFile, @Named("target") HostAndPort hostAndPort, @Named("usessl") final boolean useSSL, final ReplayResultListener resultListener) {
    checkNotNull(recordingFile);
    checkNotNull(resultListener);

    group = new NioEventLoopGroup();

    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
        new ReplayHandler(ch.pipeline(), resultListener, useSSL);
      }
    });

    File uploadLocation = new File(recordingFile.getParentFile(), "uploads");
    try {
      requestReader = new DefaultRequestReader(recordingFile, uploadLocation);
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
    dispatcher = new DefaultRequestDispatcher(bootstrap, hostAndPort, resultListener);
  }

  @Override
  protected void run() throws Exception {
    UserAgentRequest firstRequest = requestReader.readRequest();
    long firstRequestTime = firstRequest.getStartedDateTime();
    long timeStartedNanos = System.nanoTime();
    LOG.debug("Starting replay, first request time (millis) {}, time started (nanos) {}", firstRequestTime, timeStartedNanos);
    dispatcher.queue(new DelayedUserAgentRequest(firstRequest, firstRequestTime, timeStartedNanos, firstRequestTime));

    while (isRunning()) {
      UserAgentRequest userAgentRequest = requestReader.readRequest();
      long startedDateTime = userAgentRequest.getStartedDateTime();
      DelayedUserAgentRequest delayedRequest = new DelayedUserAgentRequest(userAgentRequest, startedDateTime, timeStartedNanos, firstRequestTime);
      long delayMillis = delayedRequest.getDelay(TimeUnit.MILLISECONDS);
      if (DELAY_LIMIT_MS < delayMillis) {
        long sleepMillis = delayMillis - DELAY_LIMIT_MS;
        LOG.info("Reached delay limit of {}ms, request delay {}ms, sleeping for {}ms", DELAY_LIMIT_MS, delayMillis, sleepMillis);
        Thread.sleep(sleepMillis);
      }
      dispatcher.queue(delayedRequest);
      if (requestReader.isLastRequest(userAgentRequest)) {
        LOG.info("Performing graceful shutdown of dispatcher");
        dispatcher.stopAsync();
        dispatcher.awaitTerminated();
        break;
      }
    }
  }

  @Override
  protected void startUp() throws Exception {
    LOG.info("Starting dispatcher");
    dispatcher.startAsync();
    dispatcher.awaitRunning();
  }

  @Override
  protected void triggerShutdown() {
    LOG.info("Forced shutdown requested, clearing dispatcher queue and shutting down dispatcher");
    dispatcher.clearQueue();
    dispatcher.stopAsync();
    dispatcher.awaitTerminated();
  }

  @Override
  protected void shutDown() throws Exception {
    group.shutdownGracefully();
  }
}
