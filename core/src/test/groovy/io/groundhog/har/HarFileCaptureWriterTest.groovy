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

package io.groundhog.har

import com.google.common.net.HostAndPort
import io.groundhog.capture.DefaultCaptureRequest
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import spock.lang.Specification

/**
 * Tests for {@link HarFileCaptureWriter}.
 *
 * @author Danny Thomas
 * @since 1.0
 */
class HarFileCaptureWriterTest extends Specification {
  def 'URL has correct external form'() {
    given:
    def request = Mock(HttpRequest)
    request.getUri() >> "/"
    def headers = new DefaultHttpHeaders()
    headers.add(HttpHeaders.Names.HOST, "localhost")
    request.headers() >> headers

    def response = Mock(HttpResponse)
    def captureRequest = new DefaultCaptureRequest(System.currentTimeMillis(), request, response)

    expect:
    HarFileCaptureWriter.getUrl(captureRequest) == "http://localhost/"
  }

  def 'URL contains port when not default'() {
    given:
    def request = Mock(HttpRequest)
    request.getUri() >> "/"
    def headers = new DefaultHttpHeaders()
    headers.add(HttpHeaders.Names.HOST, "localhost:8080")
    request.headers() >> headers

    def response = Mock(HttpResponse)
    def captureRequest = new DefaultCaptureRequest(System.currentTimeMillis(), request, response)

    expect:
    HarFileCaptureWriter.getUrl(captureRequest) == "http://localhost:8080/"
  }

  def 'URL has correct external form, when host scheme included in URI'() {
    given:
    def request = Mock(HttpRequest)
    request.getUri() >> "http://localhost/"
    def response = Mock(HttpResponse)
    def captureRequest = new DefaultCaptureRequest(System.currentTimeMillis(), request, response)

    expect:
    HarFileCaptureWriter.getUrl(captureRequest) == "http://localhost/"
  }
}
