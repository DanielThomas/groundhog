package io.groundhog.base;

import io.netty.handler.codec.http.*;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Static utility methods pertaining to {@link io.netty.handler.codec.http.HttpRequest} instances.
 *
 * @author Danny Thomas
 * @since 0.1
 */
public class HttpRequestsTest {

  @Test
  public void identityHostAndPort() {
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://localhost:80/uri");
    assertThat(HttpRequests.identifyHostAndPort(request).toString(), equalTo("localhost:80"));
  }

  @Test
  public void identityHostAndPort_NoPort() {
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://localhost/uri");
    assertThat(HttpRequests.identifyHostAndPort(request).toString(), equalTo("localhost"));
  }

  @Test
  public void identityHostAndPort_NoPath() {
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://localhost");
    assertThat(HttpRequests.identifyHostAndPort(request).toString(), equalTo("localhost"));
  }

  @Test
  public void identityHostAndPort_NoHost() {
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
    request.headers().set(HttpHeaders.Names.HOST, "localhost");
    assertThat(HttpRequests.identifyHostAndPort(request).toString(), equalTo("localhost"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void identityHostAndPort_NoHost_NoHostsHeader_ThrowsIAE() {
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
    assertThat(HttpRequests.identifyHostAndPort(request).toString(), equalTo("localhost"));
  }

}
