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
import com.google.common.base.Charsets;
import org.apache.coyote.InputBuffer;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A {@link HttpServletRequestWrapper} that wraps the {@link #getInputStream()} and {@link #getReader()} return values
 * with decoding input classes.
 *
 * @author Danny Thomas
 * @since 1.0
 */
public final class CaptureServletRequestHttpWrapper extends HttpServletRequestWrapper {
  private final HttpCaptureDecoder captureDecoder;
  private ServletInputStream inputStream;
  private BufferedReader reader;

  public CaptureServletRequestHttpWrapper(HttpServletRequest request, HttpCaptureDecoder captureDecoder) {
    super(checkNotNull(request));
    this.captureDecoder = checkNotNull(captureDecoder);
  }

  @Override
  public ServletInputStream getInputStream() throws IOException {
    if (null == inputStream) {
      inputStream = DecodingServletInputStream.wrap(super.getInputStream(), captureDecoder);
    }
    return inputStream;
  }

  @Override
  public BufferedReader getReader() throws IOException {
    if (null == reader) {
      reader = new BufferedReader(DecodingReader.wrap(super.getReader(), getCharset(), captureDecoder));
    }
    return reader;
  }

  @VisibleForTesting
  Charset getCharset() {
    String encoding = getCharacterEncoding();
    Charset charset;
    if (null == encoding) {
      charset = Charsets.ISO_8859_1;
    } else {
      charset = Charset.forName(encoding);
    }
    return charset;
  }
}
