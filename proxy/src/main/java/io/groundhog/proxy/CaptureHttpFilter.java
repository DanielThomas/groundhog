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

package io.groundhog.proxy;

import io.groundhog.capture.CaptureRequest;
import io.groundhog.capture.CaptureWriter;
import io.groundhog.capture.DefaultHttpCaptureDecoder;
import io.groundhog.capture.HttpCaptureDecoder;

import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import org.littleshoot.proxy.HttpFilters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A capturing {@link HttpFilters}.
 *
 * @author Danny Thomas
 * @since 1.0
 */
public class CaptureHttpFilter implements HttpFilters {
  private static final Logger LOG = LoggerFactory.getLogger(CaptureHttpFilter.class);

  private final HttpCaptureDecoder captureDecoder;
  private final CaptureWriter captureWriter;

  public CaptureHttpFilter(CaptureWriter captureWriter, File uploadLocation) {
    captureDecoder = new DefaultHttpCaptureDecoder(uploadLocation);
    this.captureWriter = checkNotNull(captureWriter);
  }

  @Override
  public HttpResponse requestPre(HttpObject httpObject) {
    checkNotNull(httpObject);
    try {
      captureDecoder.request(httpObject);
    } catch (Exception e) {
      LOG.error("Failed to capture request", e);
    }

    if (httpObject instanceof HttpRequest) {
      rewriteUri((HttpRequest) httpObject);
    }
    return null;
  }

  @Override
  public HttpResponse requestPost(HttpObject httpObject) {
    checkNotNull(httpObject);
    return null;
  }

  @Override
  public HttpObject responsePre(HttpObject httpObject) {
    checkNotNull(httpObject);
    try {
      captureDecoder.response(httpObject);
    } catch (Exception e) {
      LOG.error("Failed to capture response", e);
    }
    return httpObject;
  }

  @Override
  public HttpObject responsePost(HttpObject httpObject) {
    checkNotNull(httpObject);
    if (httpObject instanceof LastHttpContent) {
      try {
        CaptureRequest captureRequest = captureDecoder.complete();
        captureWriter.writeAsync(captureRequest);
      } catch (Exception e) {
        LOG.error("Failed to complete and write request", e);
      } finally {
        captureDecoder.destroy();
      }
    }
    return httpObject;
  }

  void rewriteUri(HttpRequest request) {
    if (!request.getUri().contains("://")) {
      request.setUri("http://localhost:8080" + request.getUri());
    }
  }
}
