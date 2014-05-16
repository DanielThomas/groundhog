/*
 * Copyright 2013-2014 the original author or authors.
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

package io.groundhog.proxy;

import io.groundhog.capture.*;
import io.groundhog.capture.DefaultCaptureHttpDecoder;
import io.groundhog.capture.CaptureHttpDecoder;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;

import java.io.File;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public class CaptureFilterSource extends HttpFiltersSourceAdapter {
  private final File uploadLocation;
  private final String protocol;
  private final String host;
  private final int port;

  private CaptureWriter captureWriter;
  private CaptureController captureController;

  @Inject
  CaptureFilterSource(CaptureWriter captureWriter, CaptureController captureController,
                      @Named("UploadLocation") File uploadLocation, @Named("target.scheme") String protocol,
                      @Named("target.host") String host, @Named("target.port") int port) {
    this.captureWriter = checkNotNull(captureWriter);
    this.captureController = checkNotNull(captureController);
    this.uploadLocation = checkNotNull(uploadLocation);
    this.protocol = checkNotNull(protocol);
    this.host = checkNotNull(host);
    checkArgument(port > 0, "Port must be greater than zero");
    this.port = port;
  }

  @Override
  public HttpFilters filterRequest(HttpRequest originalRequest, ChannelHandlerContext ctx) {
    checkNotNull(originalRequest);
    checkNotNull(ctx);
    CaptureHttpDecoder captureDecoder = new DefaultCaptureHttpDecoder(uploadLocation);
    return new CaptureHttpFilter(captureDecoder, captureWriter, captureController, protocol, host, port);
  }

  @VisibleForTesting
  void setCaptureWriter(CaptureWriter captureWriter) {
    this.captureWriter = checkNotNull(captureWriter);
  }

  @VisibleForTesting
  void setCaptureController(CaptureController captureController) {
    this.captureController = checkNotNull(captureController);
  }
}
