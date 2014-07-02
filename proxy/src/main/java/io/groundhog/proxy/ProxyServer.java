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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import org.littleshoot.proxy.*;
import org.littleshoot.proxy.extras.SelfSignedSslEngineSource;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.util.Queue;

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
    this.captureWriter = checkNotNull(captureWriter);
    this.filterSourceFactory = checkNotNull(filterSourceFactory);
    this.listenHttp = checkNotNull(listenHttp);
    this.targetHttp = checkNotNull(targetHttp);
    this.listenHttps = Optional.of(listenHttps);
    this.targetHttps = Optional.of(targetHttps);
  }

  @VisibleForTesting
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
    CaptureFilterSource filterSource = checkNotNull(filterSourceFactory.create(scheme));
    HttpProxyServerBootstrap bootstrap = DefaultHttpProxyServer.bootstrap().withAddress(toInetSocketAddress(listen)).withFiltersSource(filterSource);

    /*
     * We use chained proxy support to achieve rewriting to a single host, as it's the best way to achieve HTTPS support
     * without modifying LittleProxy.
     */
    final ChainedProxyAdapter proxyAdapter;
    if (URIScheme.HTTPS == scheme) {
      SelfSignedSslEngineSource sslEngineSource = new SelfSignedSslEngineSource(true);
      // Client to proxy connections
      bootstrap.withSslEngineSource(sslEngineSource).withAuthenticateSslClients(false);
      // Proxy to server connections
      proxyAdapter = new SslRewriteChainedProxy(target, sslEngineSource);
    } else {
      proxyAdapter = new RewriteChainedProxy(target);
    }
    bootstrap.withChainProxyManager(new ChainedProxyManager() {
      @Override
      public void lookupChainedProxies(HttpRequest httpRequest, Queue<ChainedProxy> chainedProxies) {
        chainedProxies.add(proxyAdapter);
      }
    });
    bootstrap.start();
  }

  private static class RewriteChainedProxy extends ChainedProxyAdapter {
    private final HostAndPort target;

    public RewriteChainedProxy(HostAndPort target) {
      this.target = checkNotNull(target);
    }

    @Override
    public InetSocketAddress getChainedProxyAddress() {
      return toInetSocketAddress(target);
    }

    /**
     * Filters the request to avoid the side-effects of chaining such as removing the VIA header.
     */
    @Override
    public void filterRequest(HttpObject httpObject) {
      if (httpObject instanceof HttpRequest) {
        HttpRequest proxyHttpRequest = (HttpRequest) httpObject;
        HttpHeaders headers = proxyHttpRequest.headers();
        headers.remove(HttpHeaders.Names.VIA);
        headers.set(HttpHeaders.Names.HOST, target.getHostText());
      }
    }
  }

  private static class SslRewriteChainedProxy extends RewriteChainedProxy {
    private final SslEngineSource sslEngineSource;

    public SslRewriteChainedProxy(HostAndPort target, SslEngineSource sslEngineSource) {
      super(target);
      this.sslEngineSource = checkNotNull(sslEngineSource);
    }

    @Override
    public boolean requiresEncryption() {
      return true;
    }

    @Override
    public SSLEngine newSslEngine() {
      return sslEngineSource.newSslEngine();
    }
  }

  private static InetSocketAddress toInetSocketAddress(HostAndPort hostAndPort) {
    return new InetSocketAddress(hostAndPort.getHostText(), hostAndPort.getPort());
  }

  @Override
  protected void shutDown() throws Exception {
    captureWriter.stopAsync();
    captureWriter.awaitTerminated();
  }
}
