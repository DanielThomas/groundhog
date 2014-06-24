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

import javax.annotation.concurrent.Immutable;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * @author Danny Thomas
 * @since 1.0
 */
@Immutable
public enum URIScheme {
  HTTP("http", 80),
  HTTPS("https", 443);

  private final String scheme;
  private final int defaultPort;

  private URIScheme(String scheme, int defaultPort) {
    this.scheme = scheme;
    this.defaultPort = defaultPort;
  }

  public String scheme() {
    return scheme;
  }

  public int defaultPort() {
    return defaultPort;
  }

  public static URIScheme fromPort(int port) {
    Optional<URIScheme> uriScheme = fromPortInternal(port);
    checkArgument(uriScheme.isPresent(), "No matching protocol scheme for port " + port);
    return uriScheme.get();
  }

  public static URIScheme fromPortOrDefault(int port, URIScheme defaultScheme) {
    return fromPortInternal(port).or(defaultScheme);
  }

  private static Optional<URIScheme> fromPortInternal(int port) {
    checkArgument(port > 0, "port must be greater than zero");
    for (URIScheme scheme : values()) {
      if (scheme.defaultPort() == port) {
        return Optional.of(scheme);
      }
    }
    return Optional.absent();
  }
}
