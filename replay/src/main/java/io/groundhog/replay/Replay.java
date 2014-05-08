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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public class Replay {
  private static final Logger LOG = LoggerFactory.getLogger(Replay.class);

  public static void main(String[] args) throws Exception {
    final ReplayClient client = new ReplayClient(new File("out/recording.har"), HostAndPort.fromParts("localhost", 8080),
        false, new LoggingResultListener());

    // TODO move this logic to a core class
    Thread shutdownThread = (new Thread(new Runnable() {
      public void run() {
        if (client.isRunning()) {
          LOG.info("Forced shutdown requested");
          client.stopAsync();
          client.awaitTerminated();
        }
      }
    }));
    shutdownThread.setName(Replay.class.getSimpleName() + "-shutdown");
    Runtime.getRuntime().addShutdownHook(shutdownThread);

    client.startAsync();
    client.awaitTerminated();
  }
}
