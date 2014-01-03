/*
 * Copyright 2010 the original author or authors.
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

import com.google.common.net.HostAndPort;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Danny Thomas
 * @since 0.1
 */
public class ProxyRequest {
  private final long startedDateTime;
  private final HostAndPort hostAndPort;
  private final HttpRequest request;
  private final HttpResponse response;

  public ProxyRequest(long startedDateTime, HostAndPort hostAndPort, HttpRequest request, HttpResponse response) {
    this.startedDateTime = startedDateTime;
    this.hostAndPort = checkNotNull(hostAndPort);
    this.request = checkNotNull(request);
    this.response = checkNotNull(response);
  }

  public long getStartedDateTime() {
    return startedDateTime;
  }

  public HostAndPort getHostAndPort() {
    return hostAndPort;
  }

  public HttpRequest getRequest() {
    return request;
  }

  public HttpResponse getResponse() {
    return response;
  }
}
