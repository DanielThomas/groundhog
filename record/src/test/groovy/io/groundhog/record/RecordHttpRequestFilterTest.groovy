package io.groundhog.record

import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by dthomas on 31/10/2013.
 */
class RecordHttpRequestFilterTest extends Specification {

  @Shared def file = Mock(File, constructorArgs: [""])
  @Shared def filter = new RecordHttpRequestFilter(Mock(RequestWriter, constructorArgs: [file, false, false, false]), file)

  def 'uri rewriting handles pipes in paths'() {
    when:
    def uri = 'http://ad.crwdcntrl.net/4/pe=y|c=244|var=CN.ad.lotame.tags|out=json'
    def file = Mock(File, constructorArgs: [""])
    def filter = new RecordHttpRequestFilter(Mock(RequestWriter, constructorArgs: [file, false, false, false]), file)
    def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)
    filter.rewriteUri(request)

    then:
    request.uri == uri
  }

  def 'uri writing substitutes hostname from header'() {
    when:
    def uri = '/path'
    def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)
    request.headers().add(HttpHeaders.Names.HOST, "localhost")
    filter.rewriteUri(request)

    then:
    request.uri == 'http://localhost:8080/path'
  }

}
