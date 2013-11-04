package io.groundhog.base;

import io.netty.handler.codec.http.*
import spock.lang.Specification

/**
 * Tests for {@link HttpRequests}.
 *
 * @author Danny Thomas
 * @since 0.1
 */
class HttpRequestsTest extends Specification {
  def 'returns host with port'() {
    when:
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, 'http://localhost:80/uri')

    then:
    HttpRequests.identifyHostAndPort(request).toString() == 'localhost:80'
  }

  def 'returns host, from url with path'() {
    when:
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, 'http://localhost/uri')

    then:
    HttpRequests.identifyHostAndPort(request).toString() == 'localhost'
  }

  def 'returns host from plain url'() {
    when:
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, 'http://localhost');

    then:
    HttpRequests.identifyHostAndPort(request).toString() == 'localhost'
  }

  def 'returns host from path with request host header set'() {
    when:
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/')
    request.headers().set(HttpHeaders.Names.HOST, 'localhost');

    then:
    HttpRequests.identifyHostAndPort(request).toString() == 'localhost'
  }

  def 'throws IAE if path with no request host header set'() {
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/')

    when:
    HttpRequests.identifyHostAndPort(request)

    then:
    thrown(IllegalArgumentException)
  }
}
