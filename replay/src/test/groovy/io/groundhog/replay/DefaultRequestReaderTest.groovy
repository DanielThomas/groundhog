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

package io.groundhog.replay

import spock.lang.Specification

/**
 * Tests for {@link DefaultRequestReader}.
 *
 * @author Danny Thomas
 * @since 1.0
 */
class DefaultRequestReaderTest extends Specification {
  def 'reader reads expected number of entries'() {
    def count = 0;
    def reader = getReader();
    when:
    while (true) {
      def request = reader.readRequest();
      count++
      if (reader.isLastRequest(request)) {
        break
      }
    }

    then:
    count == 21
  }

  def 'attempt to read more than the available entries throws IOException'() {
    def reader = getReader();

    when:
    for (int i = 0; i < 22; i++) {
      reader.readRequest();
    }

    then:
    thrown(IOException)
  }

  def getReader() {
    def url = getClass().getClassLoader().getResource('github.com.har')
    new DefaultRequestReader(new File(url.getFile()), new File('/tmp'))
  }
}
