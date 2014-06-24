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

package io.groundhog.capture

import io.groundhog.base.URIScheme
import io.netty.handler.codec.http.*
import spock.lang.Ignore
import spock.lang.Specification

/**
 * Tests for {@link DefaultCaptureHttpDecoder}.
 */
class DefaultCaptureHttpDecoderTest extends Specification {
  def 'defensive copy of request is made, preventing proxy from modifying recorded request'() {
    def writer = Mock(CaptureWriter)
    def decoder = new DefaultCaptureHttpDecoder(writer)
    def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, 'http://localhost/')
    def header = HttpHeaders.Names.CONNECTION
    request.headers().add(header, HttpHeaders.Values.KEEP_ALIVE)
    def response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)

    CaptureRequest captureRequest = null;

    when:
    decoder.request(request, URIScheme.HTTP)
    decoder.request(DefaultLastHttpContent.EMPTY_LAST_CONTENT, URIScheme.HTTP)
    decoder.response(response)
    decoder.response(DefaultLastHttpContent.EMPTY_LAST_CONTENT)
    request.headers().remove(header)

    then:
    1 * writer.writeAsync({ captureRequest = it } as CaptureRequest)
    captureRequest.request.headers().get(HttpHeaders.Names.CONNECTION) == HttpHeaders.Values.KEEP_ALIVE
  }
}
