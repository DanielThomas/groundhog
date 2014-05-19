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

package io.groundhog.proxy;

import io.groundhog.base.Services;

import java.io.FileNotFoundException;

import com.google.inject.Guice;
import com.google.inject.Injector;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public final class Proxy {
  public static void main(String[] args) throws FileNotFoundException {
    Injector injector = Guice.createInjector(new ProxyModule());
    final ProxyServer server = injector.getInstance(ProxyServer.class);
    Services.addShutdownHook(server);
    server.startAsync();
    server.awaitTerminated();
  }
}
