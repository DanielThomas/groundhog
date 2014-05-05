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

package io.groundhog.servlet;

import io.groundhog.capture.HttpCaptureDecoder;

import com.google.common.annotations.VisibleForTesting;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A {@link Reader} that decodes read bytes using a {@link HttpCaptureDecoder}.
 *
 * @author Danny Thomas
 * @since 1.0
 */
public final class DecodingReader extends FilterReader implements FilterWrapper<Reader> {
  private static final Logger LOG = LoggerFactory.getLogger(DecodingReader.class);

  private final HttpCaptureDecoder captureDecoder;
  private final Charset charset;

  private byte[] singleByte = new byte[1];
  private boolean failFast;

  public DecodingReader(Reader in, Charset charset, HttpCaptureDecoder captureDecoder) {
    super(in);
    this.charset = checkNotNull(charset);
    this.captureDecoder = checkNotNull(captureDecoder);
  }

  @Override
  public int read() throws IOException {
    int readByte = in.read();
    singleByte[0] = (byte) readByte;
    decode(singleByte);
    return readByte;
  }

  @Override
  public int read(char[] cbuf, int off, int len) throws IOException {
    int bytesRead = in.read(cbuf, off, len);
    decode(cbuf);
    return bytesRead;
  }

  @Override
  public int read(CharBuffer target) throws IOException {
    int bytesRead = in.read(target);
    decode(target);
    return bytesRead;
  }

  @Override
  public int read(char[] cbuf) throws IOException {
    int bytesRead = in.read(cbuf);
    decode(cbuf);
    return bytesRead;
  }

  private void decode(char[] cbuf) {
    decode(Unpooled.copiedBuffer(cbuf, charset));
  }

  private void decode(byte[] buf) {
    decode(Unpooled.wrappedBuffer(buf));
  }

  private void decode(CharBuffer charBuffer) {
    int position = charBuffer.position();
    decode(Unpooled.copiedBuffer(charBuffer, charset));
    charBuffer.position(position);
  }

  private void decode(ByteBuf content) {
    try {
      HttpContent httpContent = new DefaultHttpContent(content);
      captureDecoder.request(httpContent);
    } catch (Exception e) {
      if (failFast) {
        throw e;
      } else {
        LOG.error("Error decoding content", e);
      }
    }
  }

  @VisibleForTesting
  void setFailFast(boolean failFast) {
    this.failFast = failFast;
  }

  @Override
  public Reader unwrap() {
    return in;
  }
}
