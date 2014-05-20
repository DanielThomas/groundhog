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

import io.netty.handler.codec.http.*
import spock.lang.Ignore
import spock.lang.Specification

/**
 * Tests for {@link DefaultCaptureHttpDecoder}.
 */
class DefaultCaptureHttpDecoderTest extends Specification {
  @Ignore // copied from proxy tests, as this copy is now made here. Needs to be rewritten
  def 'defensive copy of request is made, preventing proxy from modifying recorded request'() {
    def decoder = Mock(CaptureHttpDecoder)
    //def captureFilter = new CaptureHttpFilter(decoder, Mock(CaptureController), 'http', 'localhost', 8080)
    def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, 'http://localhost/')
    def header = HttpHeaders.Names.CONNECTION
    request.headers().add(header, HttpHeaders.Values.KEEP_ALIVE)
    def response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)

    HttpObject decodedObject = null;

    when:
    captureFilter.requestPre(request)
    request.headers().remove(header)
    captureFilter.responsePre(response)
    captureFilter.responsePost(DefaultLastHttpContent.EMPTY_LAST_CONTENT)

    then:
    1 * decoder.request({ decodedObject = it } as HttpObject)
    decodedObject instanceof DefaultHttpRequest
    def capturedRequest = (HttpRequest) decodedObject
    !capturedRequest.is(request)
    capturedRequest.headers().get(HttpHeaders.Names.CONNECTION) == HttpHeaders.Values.KEEP_ALIVE
  }
}
