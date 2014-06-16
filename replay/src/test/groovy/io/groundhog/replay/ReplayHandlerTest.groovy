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

package io.groundhog.replay

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPipeline
import io.netty.channel.ChannelPromise
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification

/**
 * Tests for {@link ReplayHandler}.
 */
class ReplayHandlerTest extends Specification {
  def listener = Mock(ReplayResultListener)
  def handler = new ReplayHandler(Mock(ChannelPipeline), Mock(UserAgentHandler), listener, false, 5000)

  def 'a response with a new instance of an equal response status is successful'() {
    given:
    def request = Mock(ReplayHttpRequest)
    def expectedResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    request.expectedResponse >> expectedResponse
    def response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, new HttpResponseStatus(200, "OK", true)) // Create a new instance to ensure equals, not identity comparison is used

    when:
    def context = Mock(ChannelHandlerContext)
    handler.write(context, request, Mock(ChannelPromise))
    handler.channelRead(context, response)
    handler.channelRead(context, Mock(ReplayLastHttpContent))

    then:
    //noinspection GroovyAssignabilityCheck
    1 * listener.success( _, _, _, _, _, _, _)
  }

  def 'a response with a different status to expected causes a failure notification'() {
    given:
    def request = Mock(ReplayHttpRequest)
    def expectedResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.ACCEPTED)
    request.expectedResponse >> expectedResponse
    def response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)

    when:
    def context = Mock(ChannelHandlerContext)
    handler.write(context, request, Mock(ChannelPromise))
    handler.channelRead(context, response)
    handler.channelRead(context, Mock(ReplayLastHttpContent))

    then:
    //noinspection GroovyAssignabilityCheck
    1 * listener.failure(_, _, _, _, _, _, _, _)
  }

  def 'a response with a Blackboard Learn error header causes a failure notification'() {
    given:
    def request = Mock(ReplayHttpRequest)
    def expectedResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.ACCEPTED)
    request.expectedResponse >> expectedResponse
    def response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    response.headers().add('X-Blackboard-errorid', '123456')

    when:
    def context = Mock(ChannelHandlerContext)
    handler.write(context, request, Mock(ChannelPromise))
    handler.channelRead(context, response)
    handler.channelRead(context, Mock(ReplayLastHttpContent))

    then:
    //noinspection GroovyAssignabilityCheck
    1 * listener.failure(_, _, _, _, _, _, _, _)
  }
}
