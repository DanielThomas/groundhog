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

import io.groundhog.capture.CaptureWriter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersSource;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import java.io.File;

import static com.google.common.base.Preconditions.*;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public class CaptureFilterSource implements HttpFiltersSource {
  private final CaptureWriter captureWriter;
  private final File uploadLocation;
  private final String protocol;
  private final String host;
  private final int port;

  @Inject
  public CaptureFilterSource(CaptureWriter captureWriter,
      @Named("UploadLocation") File uploadLocation,
      @Named("target.protocol") String protocol,
      @Named("target.host") String host,
      @Named("target.port") int port) {
    this.captureWriter = checkNotNull(captureWriter);
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
    return new CaptureHttpFilter(captureWriter, uploadLocation, protocol, host, port);
  }

  @Override
  public int getMaximumRequestBufferSizeInBytes() {
    return 0;
  }

  @Override
  public int getMaximumResponseBufferSizeInBytes() {
    return 0;
  }
}
