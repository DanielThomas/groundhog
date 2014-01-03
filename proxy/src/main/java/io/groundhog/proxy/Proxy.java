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

package io.groundhog.proxy;

import java.io.FileNotFoundException;

/**
 * @author Danny Thomas
 * @since 0.1
 */
public final class Proxy {
  public static void main(String[] args) throws FileNotFoundException {
    final ProxyServer server = new ProxyServer();

    Thread shutdownThread = (new Thread(new Runnable() {
      public void run() {
        if (server.isRunning()) {
          server.stopAsync();
          server.awaitTerminated();
        }
      }
    }));
    shutdownThread.setName(Proxy.class.getSimpleName() + "-shutdown");
    Runtime.getRuntime().addShutdownHook(shutdownThread);

    server.startAsync();
    server.awaitTerminated();
  }
}
