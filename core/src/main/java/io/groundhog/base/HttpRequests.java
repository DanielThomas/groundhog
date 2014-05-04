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

package io.groundhog.base;

import com.google.common.base.Throwables;
import com.google.common.net.HostAndPort;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;

import java.net.URI;
import java.net.URISyntaxException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Static utility methods pertaining to {@link io.netty.handler.codec.http.HttpRequest} instances.
 *
 * @author Danny Thomas
 * @since 1.0
 */
public class HttpRequests {
  private HttpRequests() {
  }

  public static HostAndPort identifyHostAndPort(HttpRequest httpRequest) {
    checkNotNull(httpRequest);
    URI uri;
    try {
      uri = new URI(httpRequest.getUri());
      if (null != uri.getHost()) {
        return -1 == uri.getPort() ? HostAndPort.fromString(uri.getHost()) : HostAndPort.fromParts(uri.getHost(), uri.getPort());
      } else {
        String hostHeader = httpRequest.headers().get(HttpHeaders.Names.HOST);
        if (null != hostHeader) {
          return HostAndPort.fromString(hostHeader);
        } else {
          throw new IllegalArgumentException("The host header may not be null for requests with host relative uris");
        }
      }
    } catch (URISyntaxException e) {
      throw Throwables.propagate(e);
    }
  }
}
