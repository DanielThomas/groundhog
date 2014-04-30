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

package io.groundhog.capture;

import io.groundhog.har.HttpArchive;

import com.google.common.base.Optional;
import com.google.common.net.HostAndPort;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

import java.util.Collection;
import java.util.Collections;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public class DefaultCaptureRequest implements CaptureRequest {
  private final long startedDateTime;
  private final HttpRequest request;
  private final HttpResponse response;

  public DefaultCaptureRequest(long startedDateTime, HttpRequest request, HttpResponse response) {
    this.startedDateTime = startedDateTime;
    this.request = checkNotNull(request);
    this.response = checkNotNull(response);
  }

  public long getStartedDateTime() {
    return startedDateTime;
  }

  public HttpRequest getRequest() {
    return request;
  }

  public HttpResponse getResponse() {
    return response;
  }

  @Override
  public Collection<HttpArchive.Param> getParams() {
    return Collections.emptyList();
  }

  @Override
  public Optional<String> getContent() {
    return Optional.absent();
  }
}
