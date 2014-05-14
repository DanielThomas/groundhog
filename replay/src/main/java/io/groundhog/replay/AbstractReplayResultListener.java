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

package io.groundhog.replay;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.net.MediaType;
import io.netty.handler.codec.http.*;
import org.jsoup.nodes.Document;

import java.nio.channels.ClosedChannelException;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public abstract class AbstractReplayResultListener implements ReplayResultListener {
  public static final String TRANSACTION_LABEL_HEADER = "X-Transaction-Label";
  public static final String UNTITLED_PAGE_LABEL = "Untitled Page";
  private static final MediaType APPLICATION_WOFF = MediaType.create("application", "x-font-woff");

  protected static Optional<String> getErrorMessage(Throwable cause) {
    //noinspection ThrowableResultOfMethodCallIgnored
    checkNotNull(cause);
    if (cause instanceof ClosedChannelException) {
      return Optional.of("Connection closed");
    }
    return Optional.absent();
  }

  protected static String getLabel(HttpRequest request) {
    checkNotNull(request);
    StringBuilder label = new StringBuilder();
    String headerLabel = request.headers().get(TRANSACTION_LABEL_HEADER);
    if (null == headerLabel) {
      HttpMethod method = request.getMethod();
      label.append(method.name());
      label.append(" ");
      label.append(request.getUri());
    } else {
      label.append(headerLabel);
    }
    return label.toString();
  }

  protected static String getLabel(HttpRequest request, HttpResponse response, HttpResponse expectedResponse,
                                   Optional<Document> document) {
    checkNotNull(request);
    checkNotNull(response);
    checkNotNull(expectedResponse);
    checkNotNull(document);
    StringBuilder label = new StringBuilder();
    String headerLabel = request.headers().get(TRANSACTION_LABEL_HEADER);
    if (null == headerLabel) {
      label.append(getResourceType(response));
      label.append(": ");
      if (document.isPresent()) {
        String title = document.get().title();
        label.append(title.isEmpty() ? UNTITLED_PAGE_LABEL : title);
        label.append(" - ");
      }
      HttpMethod method = request.getMethod();
      if (HttpMethod.GET != method) {
        label.append(method.name());
        label.append(" ");
      }
      label.append(request.getUri());
      HttpResponseStatus status = expectedResponse.getStatus();
      if (HttpResponseStatus.OK != status) {
        label.append(" : ");
        label.append(status);
      }
    } else {
      label.append(headerLabel);
    }
    return label.toString();
  }

  protected static String getResourceType(HttpResponse response) {
    checkNotNull(response);
    String contentType = Strings.nullToEmpty(response.headers().get(HttpHeaders.Names.CONTENT_TYPE));
    if (!contentType.isEmpty()) {
      MediaType type = MediaType.parse(contentType);
      if (type.is(MediaType.HTML_UTF_8)) {
        return "Page";
      } else if (type.is(MediaType.ANY_AUDIO_TYPE)) {
        return "Audio";
      } else if (type.is(MediaType.ANY_IMAGE_TYPE)) {
        return "Image";
      } else if (type.is(MediaType.ANY_VIDEO_TYPE)) {
        return "Video";
      } else if (type.is(MediaType.JAVASCRIPT_UTF_8) || type.is(MediaType.TEXT_JAVASCRIPT_UTF_8)) {
        return "Script";
      } else if (type.is(MediaType.CSS_UTF_8)) {
        return "Stylesheet";
      } else if (type.is(APPLICATION_WOFF)) {
        return "Font";
      }
    }
    return "Other";
  }
}
