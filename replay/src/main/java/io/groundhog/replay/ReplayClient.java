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
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Danny Thomas
 * @since 0.1
 */
public final class ReplayClient extends AbstractExecutionThreadService {
  private static final Logger LOG = LoggerFactory.getLogger(ReplayClient.class);

  private final EventLoopGroup group;
  private final EventLoopGroup blockingGroup;
  private final RequestDispatcher dispatcher;
  private final RequestReader reader;

  public ReplayClient(File recordingFile, final ResultListener resultListener) {
    checkNotNull(recordingFile);
    checkNotNull(resultListener);

    group = new NioEventLoopGroup();
    blockingGroup = new NioEventLoopGroup();

    File uploadLocation = new File(recordingFile.getParentFile(), "uploads");

    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
        new ReplayHandler(ch.pipeline(), blockingGroup, resultListener);
      }
    });

    dispatcher = new RequestDispatcher(bootstrap, "localhost", 8080);
    reader = new RequestReader(recordingFile, dispatcher, uploadLocation);
  }

  @Override
  protected void startUp() throws Exception {
    LOG.info("Starting dispatcher");
    dispatcher.startAsync();
    dispatcher.awaitRunning();

    LOG.info("Starting reader");
    reader.startAsync();
  }

  @Override
  protected void triggerShutdown() {
    if (reader.isRunning()) {
      LOG.info("Shutting down reader");
      reader.stopAsync();
      reader.awaitTerminated();
    }

    LOG.info("Clearing dispatcher queue");
    dispatcher.clearQueue();
  }

  @Override
  protected void run() throws Exception {
    reader.awaitTerminated();
    if (dispatcher.isRunning()) {
      LOG.info("Shutting down dispatcher");
      dispatcher.stopAsync();
      dispatcher.awaitTerminated();
    }
    stopAsync();
  }

  @Override
  protected void shutDown() throws Exception {
    group.shutdownGracefully();
    blockingGroup.shutdownGracefully();
  }

}