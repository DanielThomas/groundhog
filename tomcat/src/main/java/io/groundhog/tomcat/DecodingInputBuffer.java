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

package io.groundhog.tomcat;

import io.groundhog.capture.HttpCaptureDecoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpContent;
import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.tomcat.util.buf.ByteChunk;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An {@link java.io.BufferedReader} wrapper, that decodes read bytes to a {@link io.groundhog.capture.HttpCaptureDecoder}
 * as {@link io.netty.handler.codec.http.HttpContent}.
 *
 * @author Danny Thomas
 * @since 1.0
 */
public class DecodingInputBuffer implements InputBuffer {
  private final InputBuffer inputBuffer;
  private final HttpCaptureDecoder captureDecoder;

  public static DecodingInputBuffer wrap(InputBuffer inputBuffer, HttpCaptureDecoder captureDecoder) {
    return new DecodingInputBuffer(inputBuffer, captureDecoder);
  }

  private DecodingInputBuffer(InputBuffer inputBuffer, HttpCaptureDecoder captureDecoder) {
    this.inputBuffer = checkNotNull(inputBuffer);
    this.captureDecoder = checkNotNull(captureDecoder);
  }

  @Override
  public int doRead(ByteChunk chunk, Request request) throws IOException {
    int readBytes = inputBuffer.doRead(chunk, request);
    decodeChunk(chunk);
    return readBytes;
  }

  private void decodeChunk(ByteChunk chunk) {
    ByteBuf content = Unpooled.wrappedBuffer(chunk.getBytes(), chunk.getOffset(), chunk.getLength());
    HttpContent httpContent = new DefaultHttpContent(content);
    captureDecoder.request(httpContent);
  }

  public InputBuffer unwrap() {
    return inputBuffer;
  }
}
