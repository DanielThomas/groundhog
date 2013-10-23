/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   whttp://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.groundhog.replay;

import io.groundhog.base.HttpArchive;

import com.google.common.base.*;
import com.google.common.collect.FluentIterable;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import static com.google.common.base.Preconditions.*;

/**
 * @author Danny Thomas
 * @since 0.1
 */
public class ReplayRequest implements ChannelFutureListener {
  private static final Logger LOG = LoggerFactory.getLogger(ReplayRequest.class);

  private static final String CONTAINER_SESSION_COOKIE_NAME = "JSESSIONID";

  private static final String APPLICATION_SESSION_COOKIE_NAME = "session_id";

  private static final Predicate<Cookie> IS_APPLICATION_SESSION_COOKIE = new Predicate<Cookie>() {
    @Override
    public boolean apply(Cookie cookie) {
      return APPLICATION_SESSION_COOKIE_NAME.equals(cookie.getName());
    }
  };

  private static final Predicate<Cookie> IS_CONTAINER_SESSION_COOKIE = new Predicate<Cookie>() {
    @Override
    public boolean apply(Cookie cookie) {
      return CONTAINER_SESSION_COOKIE_NAME.equals(cookie.getName());
    }
  };

  private static final Function<String, Cookie> HEADER_TO_COOKIE = new Function<String, Cookie>() {
    @Override
    public Cookie apply(String header) {
      Iterator<Cookie> iterator = CookieDecoder.decode(header).iterator();
      Cookie cookie = iterator.next();
      checkState(!iterator.hasNext(), "One cookie should be decoded");
      return cookie;
    }
  };

  private final HttpVersion httpVersion;
  private final HttpMethod method;
  private final String uri;
  private final HttpArchive.PostData postData;
  private final HttpHeaders headers;
  private final Set<Cookie> cookies;
  private final File uploadLocation;
  private final long startedDateTime;
  private final Optional<HttpResponse> expectedResponse;

  public ReplayRequest(HttpVersion httpVersion, HttpMethod method, String uri, HttpArchive.PostData postData, HttpHeaders headers,
                       Set<Cookie> cookies, File uploadLocation, long startedDateTime) {
    this.httpVersion = checkNotNull(httpVersion);
    this.method = checkNotNull(method);
    this.uri = checkNotNull(uri);
    this.postData = postData;
    this.headers = checkNotNull(headers);
    this.cookies = checkNotNull(cookies);
    this.uploadLocation = checkNotNull(uploadLocation);
    this.startedDateTime = startedDateTime;
    this.expectedResponse = Optional.absent();
  }

  public ReplayRequest(ReplayRequest request, HttpResponse expectedResponse) {
    this.httpVersion = request.httpVersion;
    this.method = request.method;
    this.uri = request.uri;
    this.postData = request.postData;
    this.headers = request.headers;
    this.cookies = request.cookies;
    this.uploadLocation = request.uploadLocation;
    this.startedDateTime = request.startedDateTime;
    this.expectedResponse = Optional.of(expectedResponse);
  }

  @Override
  public String toString() {
    Objects.ToStringHelper helper = Objects.toStringHelper(this);
    helper.add("uri", uri);
    return helper.toString();
  }

  @Override
  public void operationComplete(ChannelFuture future) throws Exception {
    HttpRequest request = createRequest(httpVersion, method, uri);
    setHeaders(request);

    Optional<HashCode> userAgent = getUserAgent();
    setCookies(request, userAgent);

    HttpPostRequestEncoder encoder = null;
    if (null != postData) {
      String mimeType = postData.getMimeType();
      if (mimeType.startsWith("text/plain")) {
        request = createTextPlainRequest(request, userAgent);
      } else {
        encoder = new HttpPostRequestEncoder(request, mimeType.startsWith(HttpHeaders.Values.MULTIPART_FORM_DATA));
        request = preparePostRequest(request, userAgent, encoder);
      }
    }

    boolean blocking = shouldRequestBlock();
    HttpResponseStatus expectedStatus = expectedResponse.get().getStatus();
    if (request instanceof FullHttpRequest) {
      request = new ReplayFullHttpRequest((FullHttpRequest) request, expectedStatus, userAgent, blocking);
    } else {
      request = new ReplayHttpRequest(request, expectedStatus, userAgent, blocking);
    }

    Channel channel = future.channel();
    channel.write(request);
    if (null != encoder && encoder.isChunked()) {
      channel.writeAndFlush(encoder);
    } else {
      channel.flush();
    }
  }

  /**
   * Indicates if a request should block others from the same user agent. Deals with timing issues caused by Set-Cookie
   * responses being processed just after new requests are dispatched. That shouldn't be a problem for normal workloads,
   * it's only been observed in JMeter recordings.
   */
  private boolean shouldRequestBlock() {
    return getSetSessionCookie(expectedResponse.get()).isPresent();
  }

  private HttpRequest preparePostRequest(HttpRequest request, Optional<HashCode> userAgent, HttpPostRequestEncoder encoder) throws HttpPostRequestEncoder.ErrorDataEncoderException {
    List<HttpArchive.Param> params = getPostParamsWithOverrides(postData.getParams(), userAgent);
    for (HttpArchive.Param param : params) {
      if (param.getFileName().isEmpty()) {
        encoder.addBodyAttribute(param.getName(), param.getValue());
      } else {
        File sourceFile = new File(uploadLocation, String.format("%s/%s", startedDateTime, param.getFileName()));
        encoder.addBodyFileUpload(param.getName(), sourceFile, param.getContentType(), false);
      }
    }
    return params.isEmpty() ? request : encoder.finalizeRequest();
  }

  private HttpRequest createTextPlainRequest(HttpRequest request, Optional<HashCode> userAgent) {
    checkArgument(!postData.getText().isEmpty(), "Text data expected for text/plain");
    String text = updateDwrSession(request, postData.getText(), userAgent);
    request = new DefaultFullHttpRequest(request.getProtocolVersion(), request.getMethod(), request.getUri(),
        Unpooled.copiedBuffer(text, Charsets.UTF_8));
    setHeaders(request);
    return request;
  }

  // TODO make the user agent a concrete class, rather than just using a hash of the session cookie
  private Optional<HashCode> getUserAgent() {
    Optional<Cookie> sessionCookie = getSessionCookie();
    Optional<Cookie> setSessionCookie = getSetSessionCookie(expectedResponse.get());

    Optional<HashCode> userAgent = Optional.absent();
    if (sessionCookie.isPresent()) {
      HashCode currentHash = getCookieValueHash(sessionCookie.get());
      if (setSessionCookie.isPresent()) {
        HashCode newHash = getCookieValueHash(setSessionCookie.get());
        if (!currentHash.equals(newHash)) {
          LOG.debug("Detected user agent cookie hash change in replay data: {} -> {}", currentHash, newHash);
          UserAgentStorage.addSynonym(currentHash, newHash);
          currentHash = newHash;
        }
      }
      userAgent = Optional.of(currentHash);
    } else if (setSessionCookie.isPresent()) {
      HashCode newUserAgent = getCookieValueHash(setSessionCookie.get());
      LOG.debug("Detected new user agent {}", newUserAgent);
      userAgent = Optional.of(newUserAgent);
    }

    LOG.debug("Generated {} for session {}, set session {}", userAgent, sessionCookie, setSessionCookie);
    return userAgent;
  }

  private Optional<Cookie> getSessionCookie() {
    return FluentIterable.from(cookies).filter(IS_APPLICATION_SESSION_COOKIE).first();
  }

  private Optional<Cookie> getSetSessionCookie(HttpResponse response) {
    List<String> headers = response.headers().getAll(HttpHeaders.Names.SET_COOKIE);
    return FluentIterable.from(headers).transform(HEADER_TO_COOKIE).filter(IS_APPLICATION_SESSION_COOKIE).last();
  }

  private HashCode getCookieValueHash(Cookie cookie) {
    return Hashing.goodFastHash(64).hashString(cookie.getValue(), Charsets.UTF_8);
  }

  private HttpRequest createRequest(HttpVersion httpVersion, HttpMethod method, String uri) {
    checkNotNull(httpVersion);
    checkNotNull(method);
    checkNotNull(uri);
    return new DefaultHttpRequest(httpVersion, method, uri);
  }

  public void setCookies(HttpRequest request, Optional<HashCode> userAgent) {
    if (userAgent.isPresent()) {
      Set<Cookie> cookies = UserAgentStorage.getCookiesForUri(userAgent.get(), request.getUri());
      String encodedCookies = ClientCookieEncoder.encode(cookies);
      if (!encodedCookies.isEmpty()) {
        request.headers().add(HttpHeaders.Names.COOKIE, encodedCookies);
      }
    }
  }

  private List<HttpArchive.Param> getPostParamsWithOverrides(List<HttpArchive.Param> params, Optional<HashCode> userAgent) {
    if (userAgent.isPresent()) {
      ListIterator<HttpArchive.Param> it = params.listIterator();
      while (it.hasNext()) {
        HttpArchive.Param param = it.next();
        String name = param.getName();
        Optional<HttpArchive.Param> override = UserAgentStorage.getOverrideParam(userAgent.get(), name);
        if (override.isPresent()) {
          HttpArchive.Param overrideParam = override.get();
          LOG.info("Overriding {} with {}", param, overrideParam);
          it.set(overrideParam);
        }
      }
    }

    return params;
  }

  private void setHeaders(HttpRequest request) {
    HttpHeaders headers = request.headers();
    headers.add(this.headers);

    headers.remove(HttpHeaders.Names.CONNECTION);
    headers.remove(HttpHeaders.Names.COOKIE);
    headers.remove(HttpHeaders.Names.HOST);
    headers.remove(HttpHeaders.Names.VIA);

    headers.add(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
    headers.add(HttpHeaders.Names.HOST, "localhost");
  }

  /**
   * Quick and dirty handling of sessions for DWR. We use static sessions, so it's not a problem for Learn.
   * However, dynamic sessions would need to be handled in UserAgentHandler, scraping the js resources per-session for the
   * origScriptSessionId and sessionCookieName.
   */
  private String updateDwrSession(HttpRequest request, String text, Optional<HashCode> userAgent) {
    if (request.getUri().endsWith(".dwr")) {
      if (userAgent.isPresent()) {
        Set<Cookie> cookies = UserAgentStorage.getCookiesForUri(userAgent.get(), request.getUri());
        Optional<Cookie> cookie = FluentIterable.from(cookies).filter(IS_CONTAINER_SESSION_COOKIE).first();
        if (cookie.isPresent()) {
          return text.replaceFirst("httpSessionId=.*", "httpSessionId=" + cookie.get().getValue());
        } else {
          LOG.warn("Could not update DWR request content. No container session cookie found for session {}", userAgent.get());
        }
      } else {
        LOG.info("Unable to update DWR request content for an anonymous session");
      }
    }

    return text;
  }

}
