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

package io.groundhog.base

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.LoggingEvent
import com.google.common.base.VerifyException
import spock.lang.Specification

/**
 * Tests for {@link AssertAppender}.
 */
class AssertAppenderTest extends Specification {
  def 'appending an error level event results in a AssertionError'() {
    given:
    def appender = new AssertAppender()
    def event = new LoggingEvent()
    event.setLevel(Level.ERROR)

    when:
    appender.append(event)

    then:
    thrown(AssertionError)
  }

  def 'appending an warning level event does not result in a AssertionError'() {
    given:
    def appender = new AssertAppender()
    def event = new LoggingEvent()
    event.setLevel(Level.WARN)

    when:
    appender.append(event)

    then:
    notThrown(AssertionError)
  }
}
