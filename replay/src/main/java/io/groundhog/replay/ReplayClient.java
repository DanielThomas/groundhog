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
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
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
  /**
   * The maximum delay for a request before the service pauses reading requests, to prevent excessive number of objects
   * in the dispatcher queue.
   */
  private static final int DELAY_LIMIT_MS = 5000;

  private final EventLoopGroup group;
  private final RequestReader requestReader;
  private final RequestDispatcher dispatcher;

  private Logger log = LoggerFactory.getLogger(ReplayClient.class);

  @Inject
  ReplayClient(Bootstrap bootstrap, File recordingFile, RequestDispatcher dispatcher, final ReplayHandlerFactory replayHandlerFactory, @Named("connectionTimeout") final int connectionTimeout) {
    checkNotNull(recordingFile);

    this.dispatcher = checkNotNull(dispatcher);
    checkNotNull(connectionTimeout);

    group = new NioEventLoopGroup();
    bootstrap.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
        ChannelConfig chConfig = ch.config();
        chConfig.setConnectTimeoutMillis(connectionTimeout);
        replayHandlerFactory.create(ch.pipeline());
      }
    });

    File uploadLocation = new File(recordingFile.getParentFile(), "uploads");
    try {
      requestReader = new DefaultRequestReader(recordingFile, uploadLocation);
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }

  @Override
  protected void run() throws Exception {
    UserAgentRequest firstRequest = requestReader.readRequest();
    long firstRequestTime = firstRequest.getStartedDateTime();
    DelayedUserAgentRequest delayedFirstRequest = new DelayedUserAgentRequest(firstRequest, firstRequestTime, System.nanoTime(), firstRequestTime);
    log.trace("Queuing first request {}", delayedFirstRequest);
    dispatcher.queue(delayedFirstRequest);

    /*
     * The overhead of getting initial connections can cause initial requests to bunch up, and cause out of order
     * requests which affects session cookie management. We use the first request to warm up the dispatcher, and delay
     * further requests by the skew tolerance limit for the dispatcher, to prevent warnings being logged.
     *
     * If in future we support multiple hosts, we might need handle warm up differently. That said, this should only
     * affect requests captured from load generation tools with very low latency, and no wait times. I'd never expect
     * to see problems with this with normal wait times.
     */
    long timeStartedNanos = System.nanoTime();
    firstRequestTime = firstRequestTime - RequestDispatcher.SKEW_THRESHOLD_MILLIS;

    while (isRunning()) {
      if (dispatcher.isRunning()) {
        UserAgentRequest request = requestReader.readRequest();
        long startedDateTime = request.getStartedDateTime();
        DelayedUserAgentRequest delayedRequest = new DelayedUserAgentRequest(request, startedDateTime, timeStartedNanos, firstRequestTime);
        log.trace("Queuing {}", delayedRequest);
        dispatcher.queue(delayedRequest);

        if (requestReader.isLastRequest(request)) {
          log.info("Last request read, performing graceful shutdown of dispatcher");
          dispatcher.stopAsync();
          dispatcher.awaitTerminated();
          break;
        }

        long delayMillis = delayedRequest.getDelay(TimeUnit.MILLISECONDS);
        if (DELAY_LIMIT_MS < delayMillis) {
          log.info("Reached read-ahead limit of {}ms (current request delay {}ms). Sleeping for {}ms", DELAY_LIMIT_MS, delayMillis, DELAY_LIMIT_MS);
          Thread.sleep(DELAY_LIMIT_MS);
        }
      }
    }
  }

  @Override
  protected void startUp() throws Exception {
    log.info("Starting request dispatcher");
    dispatcher.startAsync();
    dispatcher.awaitRunning();
  }

  @Override
  protected void triggerShutdown() {
    log.info("Forced shutdown requested, clearing dispatcher queue and shutting down dispatcher");
    dispatcher.clearQueue();
    dispatcher.stopAsync();
    dispatcher.awaitTerminated();
  }

  @Override
  protected void shutDown() throws Exception {
    group.shutdownGracefully();
  }
}
