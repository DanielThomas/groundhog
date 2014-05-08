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

package io.groundhog.replay;

import io.groundhog.har.HttpArchive;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import io.netty.handler.codec.http.*;

import java.io.File;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public class UserAgentRequest extends DefaultHttpRequest {
  private final Optional<HttpArchive.PostData> postData;
  private final Set<Cookie> cookies;
  private final File uploadLocation;
  private final long startedDateTime;
  private final Optional<HttpResponse> expectedResponse;

  public UserAgentRequest(HttpVersion httpVersion, HttpMethod method, String uri, Optional<HttpArchive.PostData> postData, HttpHeaders headers,
                          Set<Cookie> cookies, File uploadLocation, long startedDateTime) {
    super(httpVersion, method, uri);
    this.postData = checkNotNull(postData);
    headers().set(headers);
    this.cookies = checkNotNull(cookies);
    this.uploadLocation = checkNotNull(uploadLocation);
    this.startedDateTime = startedDateTime;
    this.expectedResponse = Optional.absent();
  }

  public UserAgentRequest(UserAgentRequest request, HttpResponse expectedResponse) {
    super(request.getProtocolVersion(), request.getMethod(), request.getUri(), false);
    this.postData = request.postData;
    headers().set(request.headers());
    this.cookies = request.cookies;
    this.uploadLocation = request.uploadLocation;
    this.startedDateTime = request.startedDateTime;
    this.expectedResponse = Optional.of(expectedResponse);
  }

  @Override
  public String toString() {
    Objects.ToStringHelper helper = Objects.toStringHelper(this);
    helper.add("uri", getUri());
    return helper.toString();
  }

  public Optional<HttpArchive.PostData> getPostData() {
    return postData;
  }

  public Set<Cookie> getCookies() {
    return cookies;
  }

  public File getUploadLocation() {
    return uploadLocation;
  }

  public long getStartedDateTime() {
    return startedDateTime;
  }

  public Optional<HttpResponse> getExpectedResponse() {
    return expectedResponse;
  }
}
