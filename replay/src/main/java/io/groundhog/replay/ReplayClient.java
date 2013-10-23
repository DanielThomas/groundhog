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

import com.google.common.io.Files;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;

/**
 * @author Danny Thomas
 * @since 0.1
 */
public final class ReplayClient {
  private static final Logger LOG = LoggerFactory.getLogger(ReplayClient.class);

  public void run() throws Exception {
    File recordingFile = new File("out/recording.har");
    String resultsFilename = String.format("%s-results-%s.csv", Files.getNameWithoutExtension(recordingFile.getName()),
        System.currentTimeMillis());
    File uploadLocation = new File(recordingFile.getParentFile(), "uploads");
    File resultsFile = new File(recordingFile.getParentFile(), resultsFilename);
    final Writer resultsWriter = new FileWriter(resultsFile);
    resultsWriter.write("timeStamp,elapsed,label,responseCode,responseMessage,threadName,dataType,success,bytes,grpThreads,allThreads,URL,Latency,Hostname");

    EventLoopGroup group = new NioEventLoopGroup();
    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
        new ReplayHandler(ch.pipeline(), resultsWriter);
      }
    });

    RequestDispatcher dispatcher = new RequestDispatcher(bootstrap, "localhost", 8080);
    RequestReader reader = new RequestReader(recordingFile, dispatcher, uploadLocation);
    try {
      reader.startAsync();
      reader.awaitTerminated();
      LOG.info("Replay completed!");
    } finally {
      resultsWriter.close();
      group.shutdownGracefully();
    }
  }

}