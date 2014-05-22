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

package io.groundhog.servlet

import spock.lang.Specification

import javax.servlet.ServletContext

/**
 * Tests for {@link ServletModule}.
 */
class ServletModuleTest extends Specification {
  def 'configuring module with an unsupported server throws an IllegalStateException'() {
    def context = Mock(ServletContext)
    context.serverInfo >> "SomeServer/1.0"

    when:
    new ServletModule(context).configure()

    then:
    thrown(IllegalArgumentException)
  }
}
