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
import io.groundhog.capture.CaptureWriter;

import com.google.common.base.Optional;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.littleshoot.proxy.HttpFiltersSource;
import org.littleshoot.proxy.HttpProxyServerBootstrap;
import org.littleshoot.proxy.extras.SelfSignedMitmManager;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A capturing proxy server.
 *
 * @author Danny Thomas
 * @since 1.0
 */
public final class ProxyServer extends AbstractIdleService {
  private static final Logger LOG = LoggerFactory.getLogger(ProxyServer.class);

  private final CaptureWriter captureWriter;
  private final CaptureFilterSourceFactory filterSourceFactory;
  private final HostAndPort listenHttp;
  private final Optional<HostAndPort> listenHttps;
  private final HostAndPort targetHttp;
  private final Optional<HostAndPort> targetHttps;

  @Inject
  ProxyServer(CaptureWriter captureWriter,
              CaptureFilterSourceFactory filterSourceFactory,
              @Named("listen.http") HostAndPort listenHttp,
              @Named("listen.https") HostAndPort listenHttps,
              @Named("target.http") HostAndPort targetHttp,
              @Named("target.https") HostAndPort targetHttps) {
    this(captureWriter, filterSourceFactory, listenHttp, targetHttp);
    // Disabled for now, this is actually less straight forward than I'd hoped. LittleProxy only MITMs on CONNECT, not regular requests
//    this.listenHttps = Optional.of(listenHttps);
//    this.targetHttps = Optional.of(targetHttps);
    // Keep the null tester happy
    checkNotNull(listenHttps);
    checkNotNull(targetHttps);
  }

  ProxyServer(CaptureWriter captureWriter, CaptureFilterSourceFactory filterSourceFactory, HostAndPort listen, HostAndPort target) {
    this.captureWriter = checkNotNull(captureWriter);
    this.filterSourceFactory = checkNotNull(filterSourceFactory);
    this.listenHttp = checkNotNull(listen);
    this.targetHttp = checkNotNull(target);
    this.listenHttps = Optional.absent();
    this.targetHttps = Optional.absent();
  }

  @Override
  protected void startUp() throws Exception {
    startProxy(URIScheme.HTTP, listenHttp, targetHttp);
    if (listenHttps.isPresent()) {
      startProxy(URIScheme.HTTPS, listenHttps.get(), targetHttps.get());
    }
  }

  private void startProxy(URIScheme scheme, HostAndPort listen, HostAndPort target) {
    LOG.info("Starting {} capture proxy on address {} for target {}. Configured writer {}", scheme, listen, target, captureWriter);
    HttpFiltersSource filterSource = filterSourceFactory.create(scheme, target);
    HttpProxyServerBootstrap bootstrap = DefaultHttpProxyServer.bootstrap().withAddress(toInetSocketAddress(listen)).withFiltersSource(filterSource);
    if (URIScheme.HTTPS == scheme) {
      bootstrap.withManInTheMiddle(new SelfSignedMitmManager());
    }
    bootstrap.start();
  }

  private InetSocketAddress toInetSocketAddress(HostAndPort hostAndPort) {
    return new InetSocketAddress(hostAndPort.getHostText(), hostAndPort.getPort());
  }

  @Override
  protected void shutDown() throws Exception {
    captureWriter.stopAsync();
    captureWriter.awaitTerminated();
  }
}