/*
 * Copyright 2013-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.groundhog.base;

import com.google.common.util.concurrent.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static utility methods pertaining to {@link Service} instances.
 *
 * @author Danny Thomas
 * @author Michael Olague
 * @since 1.0
 */
public final class Services {
  private static final Logger LOG = LoggerFactory.getLogger(Services.class);

  public static void addShutdownHook(final Service service) {
    Thread shutdownThread = (new Thread(new Runnable() {
      public void run() {
        if (service.isRunning()) {
          LOG.info("Forced shutdown requested for service {}", service);
          service.stopAsync();
          service.awaitTerminated();
        }
      }
    }));
    shutdownThread.setName(service.getClass().getSimpleName() + "-shutdown");
    Runtime.getRuntime().addShutdownHook(shutdownThread);
  }
}
