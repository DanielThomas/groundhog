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

import com.google.common.io.Files
import io.groundhog.capture.CaptureRequest
import io.groundhog.capture.CaptureWriter
import io.netty.handler.codec.http.HttpMethod
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
    filterSource = new CaptureFilterSource(writer, tempDir, 'http', LOCALHOST, serverPort)
    proxy = new ProxyServer(writer, filterSource, LOCALHOST, proxyPort)

    server = new Server(serverPort);
    def context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS)
    context.setContextPath('/')
    server.setHandler(context)

    servlet = new ProxyTestHttpServlet()
    context.addServlet(new ServletHolder(servlet), '/*')

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

  URI getURI(String path) {
    new URI('http', null, LOCALHOST, proxyPort, path, null, null)
  }

  def 'Simple GET request is written'() {
    CaptureRequest captured = null

    given:
    def writer = Mock(CaptureWriter)
    filterSource.setCaptureWriter(writer)

    when:
    def response = client.GET(getURI(BASE_PATH))

    then:
    response.status == 200
    response.contentAsString == 'GETRESP'

    then:
    1 * writer.writeAsync({ captured = it } as CaptureRequest)
    def capturedRequest = captured.request
    capturedRequest.method == HttpMethod.GET
    capturedRequest.uri == BASE_PATH
    capturedRequest.protocolVersion == HttpVersion.HTTP_1_1
  }

  def 'Simple POST request is written'() {
    CaptureRequest captured = null

    given:
    def writer = Mock(CaptureWriter)
    filterSource.setCaptureWriter(writer)

    when:
    def response = client.POST(getURI(BASE_PATH)).send()

    then:
    response.status == 200
    response.contentAsString == 'POSTRESP'

    then:
    1 * writer.writeAsync({ captured = it } as CaptureRequest)
    def capturedRequest = captured.request
    capturedRequest.method == HttpMethod.POST
    capturedRequest.uri == BASE_PATH
    capturedRequest.protocolVersion == HttpVersion.HTTP_1_1
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
