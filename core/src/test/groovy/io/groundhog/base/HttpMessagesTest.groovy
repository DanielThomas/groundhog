package io.groundhog.base;

import io.netty.handler.codec.http.*
import spock.lang.Specification

/**
 * Tests for {@link HttpMessages}.
 *
 * @author Danny Thomas
 * @since 1.0
 */
class HttpMessagesTest extends Specification {
  def 'returns host with port'() {
    when:
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, 'http://localhost:80/uri')

    then:
    HttpMessages.identifyHostAndPort(request).toString() == 'localhost:80'
  }

  def 'returns host, from url with path'() {
    when:
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, 'http://localhost/uri')

    then:
    HttpMessages.identifyHostAndPort(request).toString() == 'localhost'
  }

  def 'returns host from plain url'() {
    when:
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, 'http://localhost');

    then:
    HttpMessages.identifyHostAndPort(request).toString() == 'localhost'
  }

  def 'returns host from path with request host header set'() {
    when:
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/')
    request.headers().set(HttpHeaders.Names.HOST, 'localhost');

    then:
    HttpMessages.identifyHostAndPort(request).toString() == 'localhost'
  }

  def 'throws IAE if path with no request host header set'() {
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/')

    when:
    HttpMessages.identifyHostAndPort(request)

    then:
    thrown(IllegalArgumentException)
  }
}
