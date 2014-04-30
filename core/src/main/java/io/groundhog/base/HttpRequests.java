/*
 * Copyright 2010 the original author or authors.
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
import io.netty.handler.codec.http.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Enumeration;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Static utility methods pertaining to {@link io.netty.handler.codec.http.HttpRequest} instances.
 *
 * @author Danny Thomas
 * @since 0.1
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
        checkArgument(null != hostHeader, "The host header may not be null for requests with host relative uris");
        return HostAndPort.fromString(hostHeader);
      }
    } catch (URISyntaxException e) {
      throw Throwables.propagate(e);
    }
  }
}
