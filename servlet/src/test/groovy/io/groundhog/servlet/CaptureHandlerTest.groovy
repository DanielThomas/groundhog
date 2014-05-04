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

import io.groundhog.capture.CaptureWriter
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import spock.lang.Specification

/**
 * Tests for {@link CaptureHandler}.
 */
class CaptureHandlerTest extends Specification {
  def 'Writer is invoked with correct request details'() {
    def writer = Mock(CaptureWriter.class)
    def handler = new CaptureHandler(writer)
    def request = Mock(Request)
    request.getProtocol() >> 'HTTP/1.1'
    request.getMethod() >> 'GET'
    request.getRequestURI() >> '/'
    request.getHeaderNames() >> Collections.emptyEnumeration()
    def response = Mock(Response)
    def captured

    when:
    handler.handle("", request, request, response )

    then:
    1 * writer.writeAsync(_) >> { captured = it }
    //noinspection GroovyVariableNotAssigned
    def capturedRequest = captured.get(0).getRequest()
    capturedRequest.getProtocolVersion() == HttpVersion.HTTP_1_1
    capturedRequest.getMethod() == HttpMethod.GET
    capturedRequest.getUri() == '/'
  }
}
