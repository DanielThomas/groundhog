/*
 * Copyright 2010 the original author or authors.
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

import com.google.common.base.Optional;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.LastHttpContent;
import org.jsoup.nodes.Document;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A {@link LastHttpContent} that allows a parsed JSoup {@link Document} to be attached.
 *
 * @author Danny Thomas
 * @since 0.1
 */
public class ReplayLastHttpContent implements LastHttpContent {
  private final LastHttpContent content;
  private final Optional<Document> document;

  public ReplayLastHttpContent(LastHttpContent content, Optional<Document> document) {
    this.content = checkNotNull(content);
    this.document = checkNotNull(document);
  }

  public Optional<Document> getDocument() {
    return document;
  }

  @Override
  public HttpHeaders trailingHeaders() {
    return content.trailingHeaders();
  }

  @Override
  public ByteBuf content() {
    return content.content();
  }

  @Override
  public LastHttpContent copy() {
    return new ReplayLastHttpContent(content.copy(), document);
  }

  @Override
  public HttpContent duplicate() {
    return new ReplayLastHttpContent((LastHttpContent) content.duplicate(), document);
  }

  @Override
  public LastHttpContent retain(int increment) {
    content.retain(increment);
    return this;
  }

  @Override
  public boolean release() {
    return content.release();
  }

  @Override
  public boolean release(int decrement) {
    return content.release(decrement);
  }

  @Override
  public int refCnt() {
    return content.refCnt();
  }

  @Override
  public LastHttpContent retain() {
    content.retain();
    return this;
  }

  @Override
  public DecoderResult getDecoderResult() {
    return content.getDecoderResult();
  }

  @Override
  public void setDecoderResult(DecoderResult result) {
    content.setDecoderResult(result);
  }
}
