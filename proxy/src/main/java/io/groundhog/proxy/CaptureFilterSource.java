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

import io.groundhog.base.URIScheme;
import io.groundhog.capture.CaptureController;
import io.groundhog.capture.CaptureHttpDecoder;
import io.groundhog.capture.CaptureWriter;
import io.groundhog.capture.DefaultCaptureHttpDecoder;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.net.HostAndPort;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public class CaptureFilterSource extends HttpFiltersSourceAdapter {
  private final CaptureWriter captureWriter;
  private final URIScheme scheme;
  private final HostAndPort hostAndPort;

  private Optional<CaptureHttpDecoder> captureDecoder = Optional.absent();
  private CaptureController captureController;

  @Inject
  CaptureFilterSource(@Assisted URIScheme scheme, @Assisted HostAndPort hostAndPort, CaptureWriter captureWriter,
                      CaptureController captureController) {
    this.captureWriter = checkNotNull(captureWriter);
    this.captureController = checkNotNull(captureController);
    this.scheme = checkNotNull(scheme);
    this.hostAndPort = checkNotNull(hostAndPort);
  }

  @Override
  public HttpFilters filterRequest(HttpRequest originalRequest, ChannelHandlerContext ctx) {
    checkNotNull(originalRequest);
    checkNotNull(ctx);
    if (captureDecoder.isPresent()) {
      return new CaptureHttpFilter(captureDecoder.get(), captureController, scheme, hostAndPort);
    } else {
      return new CaptureHttpFilter(new DefaultCaptureHttpDecoder(captureWriter), captureController, scheme, hostAndPort);
    }
  }

  @VisibleForTesting
  void setCaptureDecoder(CaptureHttpDecoder captureDecoder) {
    this.captureDecoder = Optional.of(captureDecoder);
  }

  @VisibleForTesting
  void setCaptureController(CaptureController captureController) {
    this.captureController = checkNotNull(captureController);
  }
}
