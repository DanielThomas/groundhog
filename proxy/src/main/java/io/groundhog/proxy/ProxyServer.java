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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import io.groundhog.capture.CaptureWriter;

import java.net.InetSocketAddress;

import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.AbstractIdleService;
import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public final class ProxyServer extends AbstractIdleService {
  private static final Logger LOG = LoggerFactory.getLogger(ProxyServer.class);
  
  private CaptureWriter captureWriter;
  private CaptureFilterSource captureFilterSource;
  private String listenAddress;
  private int listenPort;

  @Inject
  public ProxyServer(CaptureWriter captureWriter,
      CaptureFilterSource captureFilterSource,
      @Named("listen.address") String listenAddress,
      @Named("listen.port") int listenPort) {
    this.captureWriter = checkNotNull(captureWriter);
    this.captureFilterSource = checkNotNull(captureFilterSource);
    this.listenAddress = checkNotNull(listenAddress);
    checkArgument(listenPort > 0, "Listener port must be greater than zero");
    this.listenPort = listenPort;
  }

  @Override
  protected void startUp() throws Exception {
    LOG.info("Starting recording server on address {}:{}, capture writer {}", listenAddress, listenPort, captureWriter);
    DefaultHttpProxyServer.bootstrap().withAddress(new InetSocketAddress(listenAddress, listenPort)).withFiltersSource(captureFilterSource).start();
  }

  @Override
  protected void shutDown() throws Exception {
    captureWriter.stopAsync();
    captureWriter.awaitTerminated();
  }
}