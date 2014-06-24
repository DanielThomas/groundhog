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
import com.google.common.net.HostAndPort
import io.groundhog.base.URIScheme
import io.groundhog.capture.*
import io.groundhog.har.HttpArchive
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.BasicHttpClientConnectionManager
import org.apache.http.util.EntityUtils
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.client.util.StringContentProvider
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import spock.lang.Ignore
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
  CaptureFilterSourceFactory filterSourceFactory
  @Shared
  ProxyServer proxy

  @Shared
  HostAndPort listen
  @Shared
  HostAndPort target

  @Shared
  Server server
  @Shared
  HttpServlet servlet

  @Shared
  HttpClient client
  @Shared
  CloseableHttpClient hcClient

  @Shared
  String baseUri

  def setupSpec() {
    proxyPort = getRandomPort()
    def serverPort = getRandomPort()

    tempDir = Files.createTempDir()
    def writer = Mock(CaptureWriter)
    def controller = new DefaultCaptureController(writer)
    listen = HostAndPort.fromParts(LOCALHOST, proxyPort)
    target = HostAndPort.fromParts(LOCALHOST, serverPort)
    def scheme = URIScheme.HTTP
    filterSource = new CaptureFilterSource(scheme, writer, controller)
    filterSourceFactory = Mock(CaptureFilterSourceFactory)
    filterSourceFactory.create(_) >> filterSource
    proxy = new ProxyServer(writer, filterSourceFactory, listen, target)
    baseUri = new URL(scheme.name(), 'localhost', proxyPort, BASE_PATH).toExternalForm()

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

    // Use a basic connection manager so we catch connection leaks caused by tests that don't consume the response
    def connectionManager = new BasicHttpClientConnectionManager()
    hcClient = HttpClients.createMinimal(connectionManager)
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
    new URI(URIScheme.HTTP.scheme, null, LOCALHOST, proxyPort, path, null, null)
  }

  URI getURI(String path, String query) {
    new URI(URIScheme.HTTP.scheme, null, LOCALHOST, proxyPort, path, query, null)
  }

  CaptureWriter mockWriter() {
    def writer = Mock(CaptureWriter)
    def decoder = new DefaultCaptureHttpDecoder(writer)
    filterSource.setCaptureDecoder(decoder)
    writer
  }

  def 'starting a proxy an already listening port causes a RuntimeException to be thrown'() {
    def newProxy = new ProxyServer(Mock(CaptureWriter), filterSourceFactory, listen, target)

    when:
    newProxy.startAsync()
    newProxy.awaitRunning()

    then:
    thrown(RuntimeException)
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
    capturedRequest.uri == baseUri
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
    capturedRequest.uri == baseUri
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

  def 'query string is captured as part of URI for GET request'() {
    CaptureRequest captured = null
    def writer = mockWriter()

    when:
    client.GET(getURI(BASE_PATH, 'key1=value1&key2=value2'))

    then:
    1 * writer.writeAsync({ captured = it } as CaptureRequest)
    def capturedRequest = captured.request
    capturedRequest.uri == baseUri + '?key1=value1&key2=value2'
  }

  def 'query string is captured as part of URI for POST request'() {
    CaptureRequest captured = null
    def writer = mockWriter()

    when:
    client.POST(getURI(BASE_PATH, 'key1=value1&key2=value2')).send()

    then:
    1 * writer.writeAsync({ captured = it } as CaptureRequest)
    def capturedRequest = captured.request
    capturedRequest.uri == baseUri + '?key1=value1&key2=value2'
  }

  def 'query strings are url encoded when captured'() {
    CaptureRequest captured = null
    def writer = mockWriter()

    when:
    client.newRequest(getURI()).param('field1', 'value1').param('field2', '☺').send()

    then:
    1 * writer.writeAsync({ captured = it } as CaptureRequest)
    captured.request.uri == baseUri + '?field1=value1&field2=%E2%98%BA'
  }

  def 'multipart text fields are captured'() {
    CaptureRequest captured = null
    def writer = mockWriter()

    given:
    HttpPost httpPost = new HttpPost(getURI())
    def builder = MultipartEntityBuilder.create()
    builder.addTextBody('field1', 'value1')
    builder.addTextBody('field2', 'value2')
    httpPost.setEntity(builder.build())

    when:
    def response = hcClient.execute(httpPost)

    then:
    1 * writer.writeAsync({ captured = it } as CaptureRequest)
    captured.params == [new HttpArchive.Param('field1', 'value1'), new HttpArchive.Param('field2', 'value2')]

    cleanup:
    EntityUtils.consumeQuietly(response.getEntity())
  }

  def 'mixed multipart text and binary fields are captured'() {
    CaptureRequest captured = null
    def writer = mockWriter()

    given:
    HttpPost httpPost = new HttpPost(getURI())
    def builder = MultipartEntityBuilder.create()
    builder.addTextBody('field1', 'value1')
    builder.addBinaryBody('filefield', new byte[1024], ContentType.APPLICATION_OCTET_STREAM, 'filename.bin')
    builder.addTextBody('field2', 'value2')
    httpPost.setEntity(builder.build())

    when:
    def response = hcClient.execute(httpPost)

    then:
    1 * writer.writeAsync({ captured = it } as CaptureRequest)
    captured.params == [new HttpArchive.Param('field1', 'value1'),
                        new HttpArchive.Param('filefield', 'filename.bin',
                            ContentType.APPLICATION_OCTET_STREAM.mimeType), new HttpArchive.Param('field2', 'value2')]

    cleanup:
    EntityUtils.consumeQuietly(response.getEntity())
  }

  def 'form url encoded fields are captured'() {
    CaptureRequest captured = null
    def writer = mockWriter()

    when:
    def contentProvider = new StringContentProvider("field1=value1&field2=value2")
    client.POST(getURI(BASE_PATH)).content(contentProvider, ContentType.APPLICATION_FORM_URLENCODED.mimeType).send()

    then:
    1 * writer.writeAsync({ captured = it } as CaptureRequest)
    captured.params == [new HttpArchive.Param('field1', 'value1'), new HttpArchive.Param('field2', 'value2')]
  }

  @Ignore // Ignore for now, it's not clear whether this is required for URL encoded fields
  def 'escaped form url encoded fields are escaped when captured'() {
    CaptureRequest captured = null
    def writer = mockWriter()

    when:
    def contentProvider = new StringContentProvider("field1=value1%25&field2=value2%25")
    client.POST(getURI(BASE_PATH)).content(contentProvider, ContentType.APPLICATION_FORM_URLENCODED.mimeType).send()

    then:
    1 * writer.writeAsync({ captured = it } as CaptureRequest)
    captured.params == [new HttpArchive.Param('field1', 'value1%25'), new HttpArchive.Param('field2', 'value2%25')]
  }

  @Ignore // Ignore for now, it's not clear whether this is required for URL encoded fields
  def 'UTF-8 escaped form url encoded fields are escaped when captured'() {
    CaptureRequest captured = null
    def writer = mockWriter()

    when:
    def contentProvider = new StringContentProvider('field1=%E2%98%BA') // value == ☺
    client.POST(getURI(BASE_PATH)).content(contentProvider, ContentType.APPLICATION_FORM_URLENCODED.mimeType).send()

    then:
    1 * writer.writeAsync({ captured = it } as CaptureRequest)
    captured.params == [new HttpArchive.Param('field1', '%E2%98%BA')]
  }

  def 'plain text POST requests are captured'() {
    CaptureRequest captured = null
    def writer = mockWriter()
    def expected = 'Some request string\nwith newlines\tand other control characters, and UTF-8 for good measure ☺'

    when:
    def contentProvider = new StringContentProvider(expected)
    client.POST(getURI(BASE_PATH)).content(contentProvider, ContentType.TEXT_PLAIN.mimeType).send()

    then:
    1 * writer.writeAsync({ captured = it } as CaptureRequest)
    captured.content == expected
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
