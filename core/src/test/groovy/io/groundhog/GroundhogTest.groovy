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

package io.groundhog

import spock.lang.Specification

/**
 * Tests for {@link Groundhog}.
 */
class GroundhogTest extends Specification {
  def 'version returns valid version, or Unknown when not available from jar manifest'() {
    expect:
    Groundhog.getVersion() =~ /[0-9]{1,2}\.[0-9]{1,2}(-SNAPSHOT)?/ || Groundhog.getVersion() == "Unknown"
  }
}
