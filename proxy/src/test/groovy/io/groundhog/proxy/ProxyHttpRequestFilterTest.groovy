package io.groundhog.proxy

import io.netty.handler.codec.http.*
import spock.lang.Specification

/**
 * Tests for {@link ProxyHttpRequestFilter}.
 */
class ProxyHttpRequestFilterTest extends Specification {
  def file = Mock(File, constructorArgs: [""])
  def writer = Mock(RequestWriter, constructorArgs: [file, false, false, false])
  def filter = new ProxyHttpRequestFilter(writer, file)

  def 'uri rewriting handles pipes in paths'() {
    def uri = 'http://ad.crwdcntrl.net/4/pe=y|c=244|var=CN.ad.lotame.tags|out=json'
    def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)

    when:
    filter.rewriteUri(request)

    then:
    request.uri == uri
  }

  def 'uri writing substitutes hostname from header'() {
    def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/path')
    request.headers().add(HttpHeaders.Names.HOST, "localhost")

    when:
    filter.rewriteUri(request)

    then:
    request.uri == 'http://localhost:8080/path'
  }

  def 'defensive copy of request is made, preventing proxy from modifying recorded request'() {
    def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, 'http://localhost/')
    def header = HttpHeaders.Names.CONNECTION
    request.headers().add(header, HttpHeaders.Values.KEEP_ALIVE)
    def response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)

    when:
    filter.requestPre(request)
    request.headers().remove(header)
    filter.responsePre(response)

    then:
    1 * writer.queue({ it.request != request && it.request.headers().contains(header) } as ProxyRequest)
  }
}
