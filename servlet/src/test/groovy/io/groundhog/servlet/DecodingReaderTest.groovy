/*
 * Copyright 2010 the original author or authors.
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

import com.google.common.base.Charsets
import io.groundhog.capture.HttpCaptureDecoder
import org.apache.coyote.InputBuffer
import spock.lang.Specification

import javax.servlet.ServletInputStream

/**
 * Tests for {@link DecodingReader}.
 */
class DecodingReaderTest extends Specification {
  def 'placeholder'() {
    def reader = Mock(Reader)
    def captureDecoder = Mock(HttpCaptureDecoder)
    def decoder = DecodingReader.wrap(reader, Charsets.UTF_8, captureDecoder)
    decoder.setFailFast(true)
  }
}
