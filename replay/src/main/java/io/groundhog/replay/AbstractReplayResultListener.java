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
import com.google.common.hash.HashCode;
import com.google.common.net.MediaType;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
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

  private static final String APPLICATION = "application";
  private static final String TEXT = "text";
  private static final String JAVASCRIPT = "javascript";
  private static final String JSON = "json";
  private static final String XML = "xml";
  private static final MediaType APPLICATION_WOFF = MediaType.create(APPLICATION, "x-font-woff");
  private static final MediaType TEXT_HTML = MediaType.create(TEXT, "html");
  private static final MediaType TEXT_CSS = MediaType.create(TEXT, "css");
  private static final MediaType APPLICATION_JAVASCRIPT = MediaType.create(APPLICATION, JAVASCRIPT);
  private static final MediaType TEXT_JAVASCRIPT = MediaType.create(TEXT, JAVASCRIPT);
  private static final MediaType TEXT_JSON = MediaType.create(TEXT, JSON);
  private static final MediaType APPLICATION_JSON = MediaType.create(APPLICATION, JSON);
  private static final MediaType TEXT_XML = MediaType.create(TEXT, XML);
  private static final MediaType APPLICATION_XML = MediaType.create(APPLICATION, XML);

  protected static Optional<String> getMessageForKnownException(Throwable cause) {
    //noinspection ThrowableResultOfMethodCallIgnored
    checkNotNull(cause);
    if (cause instanceof ClosedChannelException) {
      return Optional.of("Connection closed");
    }
    return Optional.absent();
  }

  protected static String getLabel(HttpRequest request) {
    return getLabel(request, Optional.<HttpResponse>absent(), Optional.<Document>absent());
  }

  protected static String getLabel(HttpRequest request, HttpResponse response, Optional<Document> document) {
    return getLabel(request, Optional.of(response), document);
  }

  protected static String getLabel(HttpRequest request, Optional<HttpResponse> response, Optional<Document> document) {
    checkNotNull(request);
    checkNotNull(response);
    checkNotNull(document);
    StringBuilder label = new StringBuilder();
    String headerLabel = request.headers().get(TRANSACTION_LABEL_HEADER);
    if (null == headerLabel) {
      label.append(request.getUri());
      if (response.isPresent()) {
        HttpResponse httpResponse = response.get();
        String contentType = Strings.nullToEmpty(httpResponse.headers().get(HttpHeaders.Names.CONTENT_TYPE));
        label.append(" (");
        label.append(httpResponse.getStatus());
        if (!contentType.isEmpty()) {
          label.append(", ");
          MediaType type = MediaType.parse(contentType);
          label.append(getMediaTypeLabel(type));
          if (document.isPresent()) {
            String title = document.get().title();
            label.append(": ");
            label.append(title.isEmpty() ? UNTITLED_PAGE_LABEL : title);
          }
        }
        label.append(")");
      }
    } else {
      label.append(headerLabel);
    }
    return label.toString();
  }

  protected static String getMediaTypeLabel(MediaType type) {
    if (type.is(TEXT_HTML)) {
      return "Page";
    } else if (type.is(MediaType.ANY_AUDIO_TYPE)) {
      return "Audio";
    } else if (type.is(MediaType.ANY_IMAGE_TYPE)) {
      return "Image";
    } else if (type.is(MediaType.ANY_VIDEO_TYPE)) {
      return "Video";
    } else if (type.is(TEXT_JAVASCRIPT) || type.is(APPLICATION_JAVASCRIPT)) {
      return "JavaScript";
    } else if (type.is(TEXT_JSON) || type.is(APPLICATION_JSON)) {
      return "JSON";
    } else if (type.is(TEXT_XML) || type.is(APPLICATION_XML)) {
      return "XML";
    } else if (type.is(TEXT_CSS)) {
      return "Stylesheet";
    } else if (type.is(APPLICATION_WOFF)) {
      return "Font";
    }
    return "Other";
  }

  protected static String getUserAgentKey(Optional<UserAgent> userAgent) {
    return userAgent.isPresent() ? getUserAgentKey(userAgent.get()) : "";
  }

  protected static String getUserAgentKey(UserAgent userAgent) {
    HashCode key = userAgent.getKey();
    return key.asInt() == 0 ? "-" : key.toString();
  }
}
