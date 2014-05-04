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

import io.groundhog.capture.CaptureRequest;
import io.groundhog.capture.CaptureWriter;
import io.groundhog.capture.DefaultHttpCaptureDecoder;
import io.groundhog.capture.HttpCaptureDecoder;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A capturing Jetty {@link Handler}.
 *
 * @author Danny Thomas
 * @since 1.0
 */
public final class CaptureHandler extends HandlerWrapper {
  private static final Logger LOG = LoggerFactory.getLogger(CaptureHandler.class);

  private final CaptureWriter writer;

  @Inject
  CaptureHandler(CaptureWriter writer) {
    this.writer = checkNotNull(writer);
  }

  @Override
  public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
    checkNotNull(target);
    checkNotNull(baseRequest);
    checkNotNull(request);
    checkNotNull(response);
    HttpCaptureDecoder captureDecoder = new DefaultHttpCaptureDecoder(new File("/tmp"));
    try {
      try {
        captureDecoder.request(CaptureValve.transformRequest(request));
      } catch (Exception e) {
        LOG.error("Error capturing request", e);
      }

      // Invoke the next valve without surrounding catch blocks, so we're not changing exception behaviour
      super.handle(target, baseRequest, new CaptureServletRequestHttpWrapper(request, captureDecoder), response);

      try {
        captureDecoder.response(CaptureValve.transformResponse(request, response));
        CaptureRequest captureRequest = captureDecoder.complete();
        writer.writeAsync(captureRequest);
      } catch (Exception e) {
        LOG.error("Error capturing response", e);
      }
    } finally {
      captureDecoder.destroy();
    }
  }
}
