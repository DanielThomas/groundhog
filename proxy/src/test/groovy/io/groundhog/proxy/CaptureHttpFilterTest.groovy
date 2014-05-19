package io.groundhog.proxy

import io.groundhog.capture.CaptureController
import io.groundhog.capture.CaptureHttpDecoder
import io.netty.handler.codec.http.*
import spock.lang.Specification

/**
 * Tests for {@link CaptureHttpFilter}.
 */
class CaptureHttpFilterTest extends Specification {
  def captureFilter = new CaptureHttpFilter(Mock(CaptureHttpDecoder), Mock(CaptureController), 'http', 'localhost', 8080)

  def 'uri rewriting handles domain without a path or query'() {
    def uri = 'http://foo.com'
    def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)

    when:
    captureFilter.rewriteUri(request)

    then:
    request.uri == 'http://localhost:8080'
  }

  def 'uri rewriting handles query without a path'() {
    def uri = 'http://foo.com?q=bar'
    def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)

    when:
    captureFilter.rewriteUri(request)

    then:
    request.uri == 'http://localhost:8080?q=bar'
  }

  def 'uri rewriting handles path without a leading slash'() {
    def uri = 'index.html'
    def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)

    when:
    captureFilter.rewriteUri(request)

    then:
    request.uri == 'http://localhost:8080/index.html'
  }

  def 'uri rewriting handles pipes in paths'() {
    def uri = 'http://ad.crwdcntrl.net/4/pe=y|c=244|var=CN.ad.lotame.tags|out=json'
    def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)

    when:
    captureFilter.rewriteUri(request)

    then:
    request.uri == 'http://localhost:8080/4/pe=y|c=244|var=CN.ad.lotame.tags|out=json'
  }

  def 'uri writing substitutes hostname from header'() {
    def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/path')
    request.headers().add(HttpHeaders.Names.HOST, "localhost")

    when:
    captureFilter.rewriteUri(request)

    then:
    request.uri == 'http://localhost:8080/path'
  }

  def 'defensive copy of request is made, preventing proxy from modifying recorded request'() {
    def decoder = Mock(CaptureHttpDecoder)
    def captureFilter = new CaptureHttpFilter(decoder, Mock(CaptureController), 'http', 'localhost', 8080)
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
