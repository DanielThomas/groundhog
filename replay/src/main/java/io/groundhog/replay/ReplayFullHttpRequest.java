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

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.*;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public class ReplayFullHttpRequest extends ReplayHttpRequest implements FullHttpRequest {
  public ReplayFullHttpRequest(FullHttpRequest request, HttpResponse expectedResponse, UserAgent userAgent, boolean blocking) {
    super(request, expectedResponse, userAgent, blocking);
  }

  @Override
  public FullHttpRequest setProtocolVersion(HttpVersion version) {
    super.setProtocolVersion(version);
    return this;
  }

  @Override
  public FullHttpRequest setMethod(HttpMethod method) {
    super.setMethod(method);
    return this;
  }

  @Override
  public FullHttpRequest setUri(String uri) {
    super.setUri(uri);
    return this;
  }

  @Override
  public FullHttpRequest copy() {
    throw new UnsupportedOperationException();
  }

  @Override
  public FullHttpRequest duplicate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public FullHttpRequest retain(int increment) {
    fullRequest().retain(increment);
    return this;
  }

  @Override
  public FullHttpRequest retain() {
    fullRequest().retain();
    return this;
  }

  @Override
  public ByteBuf content() {
    return fullRequest().content();
  }

  @Override
  public HttpHeaders trailingHeaders() {
    return fullRequest().trailingHeaders();
  }

  @Override
  public int refCnt() {
    return fullRequest().refCnt();
  }

  @Override
  public boolean release() {
    return fullRequest().release();
  }

  @Override
  public boolean release(int decrement) {
    return fullRequest().release(decrement);
  }

  private FullHttpRequest fullRequest() {
    return (FullHttpRequest) request;
  }
}
