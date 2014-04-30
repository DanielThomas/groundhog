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

import io.groundhog.capture.CaptureRequest;
import io.groundhog.capture.CaptureWriter;
import io.groundhog.capture.DefaultHttpCaptureDecoder;
import io.groundhog.capture.HttpCaptureDecoder;

import io.netty.handler.codec.http.*;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.coyote.InputBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A capturing Tomcat {@link Valve}.
 *
 * @author Danny Thomas
 * @since 1.0
 */
public final class CaptureValve extends ValveBase implements Valve {
  private static final Logger LOG = LoggerFactory.getLogger(CaptureValve.class);

  private static final String INFO = "io.groundhog.servlet.CaptureValve/1.0";

  private final CaptureWriter writer;

  @Inject
  CaptureValve(CaptureWriter writer) {
    this.writer = checkNotNull(writer);
  }

  @Override
  public String getInfo() {
    return INFO;
  }

  @Override
  public void invoke(Request request, Response response) throws IOException, ServletException {
    checkNotNull(request);
    checkNotNull(response);
    HttpCaptureDecoder captureDecoder = new DefaultHttpCaptureDecoder(new File("/tmp"));
    wrapCoyoteInputBuffer(request, captureDecoder);
    try {
      try {
        captureDecoder.request(transformRequest(request));
      } catch (Exception e) {
        LOG.error("Error capturing request", e);
      }

      // Invoke the next valve without surrounding catch blocks, so we're not changing exception behaviour
      getNext().invoke(request, response);

      try {
        captureDecoder.response(transformResponse(request, response));
        CaptureRequest captureRequest = captureDecoder.complete();
        writer.writeAsync(captureRequest);
      } catch (Exception e) {
        LOG.error("Error capturing response", e);
      }
    } finally {
      unwrapCoyoteInputBuffer(request);
      captureDecoder.destroy();
    }
  }

  private void wrapCoyoteInputBuffer(Request request, HttpCaptureDecoder captureDecoder) {
    org.apache.coyote.Request coyoteRequest = request.getCoyoteRequest();
    InputBuffer inputBuffer = coyoteRequest.getInputBuffer();
    InputBuffer decodingInputBuffer = DecodingInputBuffer.wrap(inputBuffer, captureDecoder);
    coyoteRequest.setInputBuffer(decodingInputBuffer);
  }

  private void unwrapCoyoteInputBuffer(Request request) {
    org.apache.coyote.Request coyoteRequest = request.getCoyoteRequest();
    InputBuffer inputBuffer = coyoteRequest.getInputBuffer();
    if (inputBuffer.getClass().isAssignableFrom(DecodingInputBuffer.class)) {
      coyoteRequest.setInputBuffer(((DecodingInputBuffer) inputBuffer).unwrap());
    }
  }

  public static HttpObject transformRequest(HttpServletRequest request) {
    HttpVersion httpVersion = HttpVersion.valueOf(request.getProtocol());
    HttpMethod httpMethod = HttpMethod.valueOf(request.getMethod());
    HttpRequest httpRequest = new DefaultHttpRequest(httpVersion, httpMethod, request.getRequestURI());
    HttpHeaders httpHeaders = new DefaultHttpHeaders();
    for (Enumeration<String> headerNames = request.getHeaderNames(); headerNames.hasMoreElements(); ) {
      String headerName = headerNames.nextElement();
      httpHeaders.add(headerName, Collections.list(request.getHeaders(headerName)));
    }
    httpRequest.headers().set(httpHeaders);
    return httpRequest;
  }

  public static HttpObject transformResponse(HttpServletRequest request, HttpServletResponse response) {
    HttpResponseStatus status = HttpResponseStatus.valueOf(response.getStatus());
    HttpVersion httpVersion = HttpVersion.valueOf(request.getProtocol());
    return new DefaultHttpResponse(httpVersion, status);
  }
}
