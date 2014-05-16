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

package io.groundhog.proxy

import com.google.common.base.Charsets
import com.google.common.io.Files
import com.google.common.net.MediaType
import io.groundhog.capture.CaptureController
import io.groundhog.capture.CaptureRequest
import io.groundhog.capture.CaptureWriter
import io.groundhog.capture.DefaultCaptureController
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import spock.lang.Shared
import spock.lang.Specification

import javax.servlet.ServletException
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Tests for {@link ProxyServer} that mock the {@link CaptureWriter} to verify interactions.
 */
class ProxyServerMockCaptureIntegTest extends Specification {
  static final String LOCALHOST = 'localhost'
  static final String BASE_PATH = '/test'

  @Shared
  int proxyPort
  @Shared
  File tempDir

  @Shared
  CaptureFilterSource filterSource
  @Shared
  ProxyServer proxy

  @Shared
  Server server
  @Shared
  HttpClient client
  @Shared
  HttpServlet servlet

  def setupSpec() {
    proxyPort = getRandomPort()
    def serverPort = getRandomPort()

    tempDir = Files.createTempDir()
    def writer = Mock(CaptureWriter)
    def controller = new DefaultCaptureController(writer)
    filterSource = new CaptureFilterSource(writer, controller, tempDir, 'http', LOCALHOST, serverPort)
    proxy = new ProxyServer(writer, filterSource, LOCALHOST, proxyPort)

    server = new Server(serverPort);
    def context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS)
    context.setContextPath('/')
    server.setHandler(context)

    servlet = new ProxyTestHttpServlet()
    context.addServlet(new ServletHolder(servlet), BASE_PATH + '/*')

    proxy.startAsync()
    proxy.awaitRunning()

    server.start()

    client = new HttpClient()
    client.start()
  }

  def cleanupSpec() {
    proxy.stopAsync()
    proxy.awaitTerminated()
    server.stop()
    client.stop()
    tempDir.deleteDir()
  }

  int getRandomPort() {
    new Random().nextInt(64541) + 1024
  }

  URI getURI() {
    getURI(BASE_PATH)
  }

  URI getURI(String path) {
    new URI('http', null, LOCALHOST, proxyPort, path, null, null)
  }

  CaptureWriter mockWriter() {
    def writer = Mock(CaptureWriter)
    filterSource.setCaptureWriter(writer)
    writer
  }

  def 'control request reach the controller'() {
    def controller = Mock(CaptureController)
    filterSource.setCaptureController(controller)

    given:
    controller.isControlRequest(_) >> true
    controller.handleControlRequest(_) >> {
      new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.ACCEPTED)
    }

    when:
    def response = client.GET(getURI())

    then:
    response.status == HttpResponseStatus.ACCEPTED.code()
  }

  def 'non-existent path returns page not found, ensuring that 200 responses elsewhere can be trusted'() {
    when:
    def response = client.GET(getURI('/'))

    then:
    response.status == HttpResponseStatus.NOT_FOUND.code()
  }

  def 'simple GET request has response matching endpoint'() {
    when:
    def response = client.GET(getURI())

    then:
    response.status == HttpResponseStatus.OK.code()
    response.contentAsString == 'GETRESP'
  }

  def 'simple POST request has response matching endpoint'() {
    when:
    def response = client.POST(getURI()).send()

    then:
    response.status == HttpResponseStatus.OK.code()
    response.contentAsString == 'POSTRESP'
  }

  def 'simple GET request is captured'() {
    CaptureRequest captured = null
    def writer = mockWriter()

    when:
    client.GET(getURI())

    then:
    1 * writer.writeAsync({ captured = it } as CaptureRequest)
    def capturedRequest = captured.request
    capturedRequest.method == HttpMethod.GET
    capturedRequest.uri == BASE_PATH
    capturedRequest.protocolVersion == HttpVersion.HTTP_1_1
  }

  def 'simple POST request is captured'() {
    CaptureRequest captured = null
    def writer = mockWriter()

    when:
    client.POST(getURI()).send()

    then:
    1 * writer.writeAsync({ captured = it } as CaptureRequest)
    def capturedRequest = captured.request
    capturedRequest.method == HttpMethod.POST
    capturedRequest.uri == BASE_PATH
    capturedRequest.protocolVersion == HttpVersion.HTTP_1_1
  }

  def 'headers are captured'() {
    CaptureRequest captured = null
    def writer = mockWriter()

    when:
    client.newRequest(getURI()).header('header1', 'value1').header('header2', 'value2').send()

    then:
    1 * writer.writeAsync({ captured = it } as CaptureRequest)
    def headers = captured.request.headers()
    headers.get('header1') == 'value1'
    headers.get('header2') == 'value2'
  }

  def 'duplicate headers with different values are captured'() {
    CaptureRequest captured = null
    def writer = mockWriter()

    when:
    client.newRequest(getURI()).header('header', 'value1').header('header', 'value2').send()

    then:
    1 * writer.writeAsync({ captured = it } as CaptureRequest)
    def headers = captured.request.headers()
    headers.getAll('header') == ['value1', 'value2']
  }

  def 'captured response matches endpoint response'() {
    CaptureRequest captured = null
    def writer = mockWriter()

    when:
    client.newRequest(getURI()).header('header', 'value1').header('header', 'value2').send()

    then:
    1 * writer.writeAsync({ captured = it } as CaptureRequest)
    def response = captured.response
    response.status == HttpResponseStatus.OK
  }

  private static class ProxyTestHttpServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      resp.getWriter().write('GETRESP')
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      resp.getWriter().write('POSTRESP')
    }
  }
}
