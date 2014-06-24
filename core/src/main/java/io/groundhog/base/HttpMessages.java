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

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.net.HostAndPort;
import com.google.common.net.MediaType;
import io.netty.handler.codec.http.*;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Static utility methods pertaining to {@link io.netty.handler.codec.http.HttpMessage} instances.
 *
 * @author Danny Thomas
 * @since 1.0
 */
public class HttpMessages {
  private HttpMessages() {
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
          throw new IllegalArgumentException("The HOST header may not be null for requests with host relative uris");
        }
      }
    } catch (URISyntaxException e) {
      throw Throwables.propagate(e);
    }
  }

  public static MediaType getMediaType(HttpMessage message) {
    checkNotNull(message);
    String contentType = message.headers().get(HttpHeaders.Names.CONTENT_TYPE);
    return null == contentType ? MediaType.OCTET_STREAM : MediaType.parse(contentType);
  }

  public static URL getUrl(HttpRequest request) {
    return getUrl(request, Optional.<URIScheme>absent());
  }

  public static URL getUrl(HttpRequest request, URIScheme defaultScheme) {
    return getUrl(request, Optional.of(defaultScheme));
  }

  private static URL getUrl(HttpRequest request, Optional<URIScheme> defaultScheme) {
    try {
      final URI uri = new URI(request.getUri());
      String file = uri.getPath();
      if (null != uri.getQuery()) {
        file = file + "?" + uri.getQuery();
      }
      QueryStringDecoder decoder = new QueryStringDecoder(file);
      QueryStringEncoder encoder = new QueryStringEncoder(decoder.path());
      for (Map.Entry<String, List<String>> entry : decoder.parameters().entrySet()) {
        for (String value : entry.getValue()) {
          encoder.addParam(entry.getKey(), value);
        }
      }
      file = encoder.toString();
      final URIScheme scheme;
      final String host;
      final int port;
      if (null == uri.getScheme()) {
        HostAndPort hostAndPort = HttpMessages.identifyHostAndPort(request);
        if (defaultScheme.isPresent()) {
          scheme = defaultScheme.get();
          port = hostAndPort.getPortOrDefault(scheme.defaultPort());
        } else {
          port = hostAndPort.getPortOrDefault(URIScheme.HTTP.defaultPort());
          scheme = URIScheme.fromPortOrDefault(port, URIScheme.HTTP);
        }
        host = hostAndPort.getHostText();
      } else {
        scheme = URIScheme.valueOf(uri.getScheme().toUpperCase());
        host = uri.getHost();
        port = uri.getPort();
      }
      return port == scheme.defaultPort() ? new URL(scheme.name(), host, file) : new URL(scheme.name(), host, port, file);
    } catch (URISyntaxException | MalformedURLException e) {
      throw Throwables.propagate(e);
    }
  }
}
