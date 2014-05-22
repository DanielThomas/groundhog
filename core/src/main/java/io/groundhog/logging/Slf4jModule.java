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

package io.groundhog.logging;

import com.google.inject.AbstractModule;

import static com.google.inject.matcher.Matchers.any;

/**
 * Module to install which enables automatic injection of slf4j loggers into Guice-managed objects (by field injection
 * only).
 * <p/>
 * From https://github.com/dhanji/sitebricks/tree/master/slf4j/src/main/java/com/google/sitebricks/slf4j/.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class Slf4jModule extends AbstractModule {
  @Override
  protected void configure() {
    bindListener(any(), new Slf4jInjectionTypeListener());
  }
}
