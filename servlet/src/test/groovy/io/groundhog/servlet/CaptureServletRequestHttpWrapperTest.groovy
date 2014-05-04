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

import com.google.common.base.Charsets
import io.groundhog.capture.HttpCaptureDecoder
import spock.lang.Specification

import javax.servlet.http.HttpServletRequest

/**
 * Tests for {@link CaptureServletRequestHttpWrapper}.
 */
class CaptureServletRequestHttpWrapperTest extends Specification {
  def 'ISO-8859-1 is default character set'() {
    def request = Mock(HttpServletRequest)
    def decoder = Mock(HttpCaptureDecoder)
    def wrapper = new CaptureServletRequestHttpWrapper(request, decoder)

    expect:
    wrapper.getCharset() == Charsets.ISO_8859_1
  }

  def 'UTF-8 charset is supported'() {
    def request = Mock(HttpServletRequest)
    request.getCharacterEncoding() >> 'UTF-8'
    def decoder = Mock(HttpCaptureDecoder)
    def wrapper = new CaptureServletRequestHttpWrapper(request, decoder)

    expect:
    wrapper.getCharset() == Charsets.UTF_8
  }
}
