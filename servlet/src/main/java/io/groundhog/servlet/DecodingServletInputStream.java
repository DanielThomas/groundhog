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

package io.groundhog.servlet;

import io.groundhog.capture.HttpCaptureDecoder;

import com.google.common.annotations.VisibleForTesting;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpContent;
import org.apache.coyote.InputBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletInputStream;
import java.io.IOException;
import java.io.InputStream;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A {@link ServletInputStream} that decodes read bytes using a {@link HttpCaptureDecoder}.
 *
 * @author Danny Thomas
 * @since 1.0
 */
public final class DecodingServletInputStream extends ServletInputStream {
  private static final Logger LOG = LoggerFactory.getLogger(DecodingInputBuffer.class);

  protected final ServletInputStream in;
  private final HttpCaptureDecoder captureDecoder;

  private byte[] singleByte = new byte[1];
  private boolean failFast;

  public static DecodingServletInputStream wrap(ServletInputStream in, HttpCaptureDecoder captureDecoder) {
    return new DecodingServletInputStream(in, captureDecoder);
  }

  private DecodingServletInputStream(ServletInputStream in, HttpCaptureDecoder captureDecoder) {
    this.in = checkNotNull(in);
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
  public int read(byte[] b) throws IOException {
    int bytesRead = in.read(b);
    decode(b);
    return bytesRead;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    int bytesRead = in.read(b, off, len);
    decode(b);
    return bytesRead;
  }

  @Override
  public int readLine(byte[] b, int off, int len) throws IOException {
    int bytesRead = in.readLine(b, off, len);
    decode(b);
    return bytesRead;
  }

  private void decode(byte[] buf) {
    try {
      ByteBuf content = Unpooled.wrappedBuffer(buf);
      HttpContent httpContent = new DefaultHttpContent(content);
      captureDecoder.request(httpContent);
    } catch (Exception e) {
      if (failFast) {
        throw e;
      } else {
        LOG.error("Error decoding buf", e);
      }
    }
  }

  @Override
  public long skip(long n) throws IOException {
    return in.skip(n);
  }

  @Override
  public int available() throws IOException {
    return in.available();
  }

  @Override
  public void close() throws IOException {
    in.close();
  }

  @Override
  public synchronized void mark(int readlimit) {
    in.mark(readlimit);
  }

  @Override
  public synchronized void reset() throws IOException {
    in.reset();
  }

  @Override
  public boolean markSupported() {
    return in.markSupported();
  }

  @VisibleForTesting
  void setFailFast(boolean failFast) {
    this.failFast = failFast;
  }
}
