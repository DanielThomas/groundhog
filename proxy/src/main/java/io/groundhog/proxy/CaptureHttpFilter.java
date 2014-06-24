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

import io.groundhog.base.HttpMessages;
import io.groundhog.base.URIScheme;
import io.groundhog.capture.CaptureController;
import io.groundhog.capture.CaptureHttpDecoder;

import io.netty.handler.codec.http.*;
import org.littleshoot.proxy.HttpFilters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A capturing {@link HttpFilters}.
 *
 * @author Danny Thomas
 * @since 1.0
 */
final class CaptureHttpFilter implements HttpFilters {
  private static final Logger LOG = LoggerFactory.getLogger(CaptureHttpFilter.class);

  private final URIScheme scheme;
  private final CaptureHttpDecoder captureDecoder;
  private final CaptureController captureController;

  CaptureHttpFilter(URIScheme scheme, CaptureHttpDecoder captureDecoder, CaptureController captureController) {
    this.scheme = checkNotNull(scheme);
    this.captureDecoder = checkNotNull(captureDecoder);
    this.captureController = checkNotNull(captureController);
  }

  @Override
  public HttpResponse requestPre(HttpObject httpObject) {
    checkNotNull(httpObject);
    try {
      if (httpObject instanceof HttpRequest) {
        HttpRequest request = (HttpRequest) httpObject;
        if (captureController.isControlRequest(request)) {
          return captureController.handleControlRequest(request);
        }
        captureDecoder.request(request, scheme);
      } else {
        captureDecoder.request(httpObject);
      }
    } catch (Exception e) {
      LOG.error("Failed to capture request", e);
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
    return httpObject;
  }
}
