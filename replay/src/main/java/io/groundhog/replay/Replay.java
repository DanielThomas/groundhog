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

import io.groundhog.base.Services;
import io.groundhog.logging.Slf4jModule;

import com.google.inject.Guice;
import com.google.inject.Injector;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public final class Replay {
  public static void main(String[] args) throws Exception {
    Injector injector = Guice.createInjector(new Slf4jModule(), new ReplayModule());
    final ReplayClient client = injector.getInstance(ReplayClient.class);
    Services.addShutdownHook(client);
    client.startAsync();
    client.awaitTerminated();
  }
}
