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

package io.groundhog.record;

import io.netty.handler.codec.http.HttpRequest;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersSource;

import java.io.File;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Danny Thomas
 * @since 0.1
 */
public class RecordFilterSource implements HttpFiltersSource {
  private final RequestWriter requestWriter;
  private final File uploadLocation;

  public RecordFilterSource(RequestWriter requestWriter, File uploadLocation) {
    this.requestWriter = checkNotNull(requestWriter);
    this.uploadLocation = checkNotNull(uploadLocation);
  }

  @Override
  public HttpFilters filterRequest(HttpRequest originalRequest) {
    return new RecordHttpRequestFilter(requestWriter, uploadLocation);
  }

  @Override
  public int getMaximumRequestBufferSizeInBytes() {
    return 0;
  }

  @Override
  public int getMaximumResponseBufferSizeInBytes() {
    return 0;
  }

}
