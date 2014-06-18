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

import io.groundhog.base.HttpMessages;
import io.groundhog.base.URIScheme;
import io.groundhog.capture.CaptureWriter;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.net.HostAndPort;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import org.littleshoot.proxy.*;
import org.littleshoot.proxy.extras.SelfSignedSslEngineSource;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
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
      configureHttps(bootstrap);
    }
    bootstrap.start();
  }

  /**
   * This is a little hacky, however it's the best way to achieve HTTPS support without modifying LittleProxy. The MITM
   * manager only supports the CONNECT method, so we need to use a {@link ChainedProxy} to re-encrypt connections to
   * the intended host.
   */
  private void configureHttps(HttpProxyServerBootstrap bootstrap) {
    final SelfSignedSslEngineSource sslEngineSource = new SelfSignedSslEngineSource(true);

    // Client to proxy connections
    bootstrap.withSslEngineSource(sslEngineSource).withAuthenticateSslClients(false);

    // Proxy to server connections
    ChainedProxyManager chainedProxyManager = new ChainedProxyManager() {
      @Override
      public void lookupChainedProxies(final HttpRequest httpRequest, Queue<ChainedProxy> chainedProxies) {
        chainedProxies.add(new ChainedProxyAdapter() {
          @Override
          public boolean requiresEncryption() {
            return true;
          }

          @Override
          public SSLEngine newSslEngine() {
            return sslEngineSource.newSslEngine();
          }

          @Override
          public InetSocketAddress getChainedProxyAddress() {
            HostAndPort hostAndPort = HttpMessages.identifyHostAndPort(httpRequest);
            return toInetSocketAddress(hostAndPort);
          }

          /**
           * Filters the request to avoid the side-effects of chaining: removes the VIA header and ensures the URI is
           * bare without scheme or hostname.
           */
          @Override
          public void filterRequest(HttpObject httpObject) {
            if (httpObject instanceof HttpRequest) {
              HttpRequest proxyHttpRequest = (HttpRequest) httpObject;
              try {
                URL url = new URL(proxyHttpRequest.getUri());
                proxyHttpRequest.setUri(url.getFile());
              } catch (MalformedURLException e) {
                throw Throwables.propagate(e);
              }
              proxyHttpRequest.headers().remove(HttpHeaders.VIA);
            }
          }
        });
      }
    };
    bootstrap.withChainProxyManager(chainedProxyManager);
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