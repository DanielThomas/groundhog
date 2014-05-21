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

package io.groundhog.replay;

import io.groundhog.har.HttpArchive;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.FluentIterable;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.net.MediaType;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import static com.google.common.base.Preconditions.*;

/**
 * A {@link ChannelFutureListener} that writes a {@link UserAgentRequest} to the channel future,
 * and notifies a {@link ReplayResultListener} of failures, if any.
 *
 * @author Danny Thomas
 * @since 1.0
 */
public class UserAgentChannelWriter implements ChannelFutureListener {
  private static final Logger LOG = LoggerFactory.getLogger(UserAgentRequest.class);

  private static final String CONTAINER_SESSION_COOKIE_NAME = "JSESSIONID";
  private static final String APPLICATION_SESSION_COOKIE_NAME = "session_id";

  private static final Predicate<Cookie> IS_APPLICATION_SESSION_COOKIE = new Predicate<Cookie>() {
    @Override
    public boolean apply(@Nullable Cookie cookie) {
      return null != cookie && APPLICATION_SESSION_COOKIE_NAME.equals(cookie.getName());
    }
  };

  private static final Predicate<Cookie> IS_CONTAINER_SESSION_COOKIE = new Predicate<Cookie>() {
    @Override
    public boolean apply(@Nullable Cookie cookie) {
      return null != cookie && CONTAINER_SESSION_COOKIE_NAME.equals(cookie.getName());
    }
  };

  public static final Function<String, Cookie> HEADER_TO_COOKIE = new Function<String, Cookie>() {
    @Override
    public Cookie apply(@Nullable String header) {
      Iterator<Cookie> iterator = CookieDecoder.decode(header).iterator();
      Cookie cookie = iterator.next();
      checkState(!iterator.hasNext(), "One cookie should be decoded");
      return cookie;
    }
  };

  private static final LoadingCache<HashCode, UserAgent> UA;
  private static final UserAgent NON_PERSISTENT_UA;

  static {
    CacheLoader<HashCode, UserAgent> loader = new CacheLoader<HashCode, UserAgent>() {
      @Override
      public UserAgent load(HashCode key) throws Exception {
        return new DefaultUserAgent(key);
      }
    };
    UA = CacheBuilder.newBuilder().build(loader);
    NON_PERSISTENT_UA = new DefaultUserAgent();
  }

  private final UserAgentRequest uaRequest;
  private final ReplayResultListener resultListener;

  public UserAgentChannelWriter(UserAgentRequest uaRequest, ReplayResultListener resultListener) {
    this.uaRequest = checkNotNull(uaRequest);
    this.resultListener = checkNotNull(resultListener);
  }

  @Override
  public void operationComplete(ChannelFuture future) throws Exception {
    checkNotNull(future);
    HttpRequest request = copyRequest(uaRequest);
    UserAgent userAgent = getUserAgent();
    HttpPostRequestEncoder encoder = null;
    Optional<HttpArchive.PostData> postData = uaRequest.getPostData();
    if (postData.isPresent()) {
      String mimeType = postData.get().getMimeType();
      if (MediaType.parse(mimeType).is(MediaType.ANY_TEXT_TYPE)) {
        request = createTextPlainRequest(request, userAgent);
      } else {
        encoder = new HttpPostRequestEncoder(request, mimeType.startsWith(HttpHeaders.Values.MULTIPART_FORM_DATA));
        request = preparePostRequest(request, userAgent, encoder);
      }
    }

    HttpResponse expectedResponse = uaRequest.getExpectedResponse().get();
    boolean blocking = getSetSessionCookie(expectedResponse).isPresent();
    if (request instanceof FullHttpRequest) {
      request = new ReplayFullHttpRequest((FullHttpRequest) request, expectedResponse, userAgent, blocking);
    } else {
      request = new ReplayHttpRequest(request, expectedResponse, userAgent, blocking);
    }

    ChannelWriteFailureListener failureListener = new ChannelWriteFailureListener(request);
    Channel channel = future.channel();
    channel.write(request).addListener(failureListener);
    if (null != encoder && encoder.isChunked()) {
      channel.writeAndFlush(encoder).addListener(failureListener);
    } else {
      channel.flush();
    }
  }

  private HttpRequest preparePostRequest(HttpRequest request, UserAgent userAgent, HttpPostRequestEncoder encoder) throws HttpPostRequestEncoder.ErrorDataEncoderException {
    Optional<HttpArchive.PostData> postData = uaRequest.getPostData();
    List<HttpArchive.Param> params = getPostParamsWithOverrides(postData.get().getParams(), userAgent);
    for (HttpArchive.Param param : params) {
      if (param.getFileName().isEmpty()) {
        encoder.addBodyAttribute(param.getName(), param.getValue());
      } else {
        File sourceFile = new File(uaRequest.getUploadLocation(), String.format("%s/%s", uaRequest.getStartedDateTime(),
            param.getFileName()));
        encoder.addBodyFileUpload(param.getName(), sourceFile, param.getContentType(), false);
      }
    }
    return params.isEmpty() ? request : encoder.finalizeRequest();
  }

  private HttpRequest createTextPlainRequest(HttpRequest request, UserAgent userAgent) {
    HttpArchive.PostData postData = uaRequest.getPostData().get();
    checkArgument(!postData.getText().isEmpty(), "Text data expected for text/plain");
    String text = updateDwrSession(request, postData.getText(), userAgent);
    request = new DefaultFullHttpRequest(request.getProtocolVersion(), request.getMethod(), request.getUri(),
        Unpooled.copiedBuffer(text, Charsets.UTF_8));
    setHeaders(request);
    return request;
  }

  private UserAgent getUserAgent() {
    Optional<Cookie> sessionCookie = getSessionCookie();
    Optional<Cookie> setSessionCookie = getSetSessionCookie(uaRequest.getExpectedResponse().get());

    Optional<HashCode> cacheKey = Optional.absent();
    if (sessionCookie.isPresent()) {
      HashCode currentHash = getCookieValueHash(sessionCookie.get());
      if (setSessionCookie.isPresent()) {
        HashCode newHash = getCookieValueHash(setSessionCookie.get());
        if (!currentHash.equals(newHash)) {
          LOG.info("Detected user agent cookie hash change in replay data: {} -> {}", currentHash, newHash);
          UA.put(newHash, UA.getUnchecked(currentHash));
          UA.invalidate(currentHash);
          currentHash = newHash;
        }
      }
      cacheKey = Optional.of(currentHash);
    } else if (setSessionCookie.isPresent()) {
      HashCode newUserAgent = getCookieValueHash(setSessionCookie.get());
      LOG.info("Detected new user agent {}", newUserAgent);
      cacheKey = Optional.of(newUserAgent);
    }
    return cacheKey.isPresent() ? UA.getUnchecked(cacheKey.get()) : NON_PERSISTENT_UA;
  }

  private Optional<Cookie> getSessionCookie() {
    return FluentIterable.from(uaRequest.getCookies()).filter(IS_APPLICATION_SESSION_COOKIE).first();
  }

  private Optional<Cookie> getSetSessionCookie(HttpResponse response) {
    List<String> headers = response.headers().getAll(HttpHeaders.Names.SET_COOKIE);
    return FluentIterable.from(headers).transform(HEADER_TO_COOKIE).filter(IS_APPLICATION_SESSION_COOKIE).last();
  }

  private HashCode getCookieValueHash(Cookie cookie) {
    return Hashing.goodFastHash(64).hashString(cookie.getValue(), Charsets.UTF_8);
  }

  @VisibleForTesting
  HttpRequest copyRequest(HttpRequest request) {
    DefaultHttpRequest copiedRequest = new DefaultHttpRequest(request.getProtocolVersion(), request.getMethod(), request.getUri(), false);
    setHeaders(copiedRequest);
    return copiedRequest;
  }

  private List<HttpArchive.Param> getPostParamsWithOverrides(List<HttpArchive.Param> params, UserAgent userAgent) {
    ListIterator<HttpArchive.Param> it = params.listIterator();
    while (it.hasNext()) {
      HttpArchive.Param param = it.next();
      String name = param.getName();
      Optional<HttpArchive.Param> override = userAgent.getOverrideParam(name);
      if (override.isPresent()) {
        HttpArchive.Param overrideParam = override.get();
        LOG.debug("Overriding {} with {}", param, overrideParam);
        it.set(overrideParam);
      }
    }
    return params;
  }

  private void setHeaders(HttpRequest request) {
    HttpHeaders headers = request.headers();
    headers.add(uaRequest.headers());

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
  private String updateDwrSession(HttpRequest request, String text, UserAgent userAgent) {
    if (request.getUri().endsWith(".dwr")) {
      if (userAgent.isPersistent()) {
        Set<Cookie> cookies = userAgent.getCookiesForUri(request.getUri());
        Optional<Cookie> cookie = FluentIterable.from(cookies).filter(IS_CONTAINER_SESSION_COOKIE).first();
        if (cookie.isPresent()) {
          return text.replaceFirst("httpSessionId=.*", "httpSessionId=" + cookie.get().getValue());
        } else {
          LOG.warn("Could not update DWR request content. No container session cookie found for session {}", userAgent);
        }
      } else {
        LOG.debug("Unable to update DWR request content for an non-persistent user agent");
      }
    }
    return text;
  }

  private final class ChannelWriteFailureListener implements ChannelFutureListener {
    private final HttpRequest request;

    public ChannelWriteFailureListener(HttpRequest request) {
      this.request = checkNotNull(request);
    }

    @Override
    public void operationComplete(ChannelFuture future) throws Exception {
      if (!future.isSuccess()) {
        resultListener.failure(request, Optional.fromNullable(future.cause()));
      }
    }
  }
}
