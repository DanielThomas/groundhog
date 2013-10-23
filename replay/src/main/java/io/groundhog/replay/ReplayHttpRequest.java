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

import com.google.common.base.Optional;
import com.google.common.hash.HashCode;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.*;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * @author Danny Thomas
 * @since 0.1
 */
public class ReplayHttpRequest implements HttpRequest {
  protected final HttpRequest request;
  private final HttpResponseStatus expectedStatus;
  private final Optional<HashCode> userAgent;
  private final boolean blocking;

  public ReplayHttpRequest(HttpRequest request, HttpResponseStatus expectedStatus, Optional<HashCode> userAgent,
                           boolean blocking) {
    this.request = checkNotNull(request);
    this.expectedStatus = checkNotNull(expectedStatus);
    this.userAgent = checkNotNull(userAgent);
    this.blocking = blocking;
    if (blocking) {
      checkState(userAgent.isPresent(), "A user agent must be present for a blocking request");
    }
  }

  @Override
  public String toString() {
    return getClass().getName() + request.toString();
  }

  @Override
  public HttpMethod getMethod() {
    return request.getMethod();
  }

  @Override
  public HttpRequest setMethod(HttpMethod method) {
    request.setMethod(method);
    return this;
  }

  @Override
  public String getUri() {
    return request.getUri();
  }

  @Override
  public HttpRequest setUri(String uri) {
    request.setUri(uri);
    return this;
  }

  @Override
  public HttpVersion getProtocolVersion() {
    return request.getProtocolVersion();
  }

  @Override
  public HttpRequest setProtocolVersion(HttpVersion version) {
    request.setProtocolVersion(version);
    return this;
  }

  @Override
  public HttpHeaders headers() {
    return request.headers();
  }

  @Override
  public DecoderResult getDecoderResult() {
    return request.getDecoderResult();
  }

  @Override
  public void setDecoderResult(DecoderResult result) {
    request.setDecoderResult(result);
  }

  public HttpResponseStatus getExpectedStatus() {
    return expectedStatus;
  }

  public Optional<HashCode> getUserAgent() {
    return userAgent;
  }

  public boolean isBlocking() {
    return blocking;
  }

}
