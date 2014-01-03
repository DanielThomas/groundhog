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

import io.groundhog.base.HttpArchive;

import com.google.common.net.HostAndPort;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

import java.util.Collections;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Danny Thomas
 * @since 0.1
 */
public class ProxyPostRequest extends ProxyRequest {
  private final List<HttpArchive.Param> params;
  private final String content;

  public ProxyPostRequest(long startedDateTime, HostAndPort hostAndPort, HttpRequest request, HttpResponse response, List<HttpArchive.Param> params) {
    this(startedDateTime, hostAndPort, request, response, "", params);
  }

  public ProxyPostRequest(long startedDateTime, HostAndPort hostAndPort, HttpRequest request, HttpResponse response, String content) {
    this(startedDateTime, hostAndPort, request, response, content, Collections.<HttpArchive.Param>emptyList());
  }

  private ProxyPostRequest(long startedDateTime, HostAndPort hostAndPort, HttpRequest request, HttpResponse response, String content, List<HttpArchive.Param> params) {
    super(startedDateTime, hostAndPort, request, response);
    this.content = checkNotNull(content);
    this.params = checkNotNull(params);
  }

  public List<HttpArchive.Param> getParams() {
    return params;
  }

  public String getContent() {
    return content;
  }
}
