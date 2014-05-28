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

import org.slf4j.LoggerFactory
import spock.lang.Specification

/**
 * Test to ensure that the {@link io.groundhog.logging.AssertAppender} is correctly configured for this project.
 */
class AssertAppenderIntegTest extends Specification {
  def 'logging an error results in a AssertionError'() {
    def logger = LoggerFactory.getLogger(AssertAppenderIntegTest)

    when:
    logger.error('An error')

    then:
    thrown(AssertionError)
  }
}
