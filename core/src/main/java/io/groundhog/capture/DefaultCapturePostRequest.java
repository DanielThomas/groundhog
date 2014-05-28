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

package io.groundhog.capture;

import io.groundhog.har.HttpArchive;

import com.google.common.collect.ImmutableList;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

import java.util.Collections;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public class DefaultCapturePostRequest extends DefaultCaptureRequest {
  private final List<HttpArchive.Param> params;
  private final String content;

  public DefaultCapturePostRequest(long startedDateTime, HttpRequest request, HttpResponse response, List<HttpArchive.Param> params) {
    this(startedDateTime, request, response, "", ImmutableList.copyOf(params));
  }

  public DefaultCapturePostRequest(long startedDateTime, HttpRequest request, HttpResponse response, String content) {
    this(startedDateTime, request, response, content, Collections.<HttpArchive.Param>emptyList());
  }

  private DefaultCapturePostRequest(long startedDateTime, HttpRequest request, HttpResponse response, String content, List<HttpArchive.Param> params) {
    super(startedDateTime, request, response);
    this.params = checkNotNull(params);
    this.content = checkNotNull(content);
  }

  @Override
  public List<HttpArchive.Param> getParams() {
    return params;
  }

  @Override
  public String getContent() {
    return content;
  }
}
