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

package io.groundhog.har;

import com.google.common.base.Objects;
import com.google.common.collect.Sets;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public class HttpArchive {
  public static final HttpVersion DEFAULT_HTTP_VERSION = HttpVersion.HTTP_1_1;
  public static final HttpMethod DEFAULT_METHOD = HttpMethod.GET;
  public static final Set<String> MINIMUM_RESPONSE_HEADERS = Sets.newHashSet(HttpHeaders.Names.SET_COOKIE);

  public static class Creator {
    private final String name;
    private final String version;
    private final String comment;

    public Creator(String name, String version, String comment) {
      this.name = checkNotNull(name);
      this.version = checkNotNull(version);
      this.comment = checkNotNull(comment);
    }

    public String getName() {
      return name;
    }

    public String getVersion() {
      return version;
    }

    public String getComment() {
      return comment;
    }
  }

  public static class PostData {
    private final String mimeType;
    private final String text;
    private final List<Param> params;

    public PostData(String mimeType, String text) {
      this(mimeType, text, Collections.<Param>emptyList());
    }

    public PostData(String mimeType, List<Param> params) {
      this(mimeType, "", params);
    }

    public PostData(String mimeType, String text, List<Param> params) {
      this.mimeType = checkNotNull(mimeType);
      this.text = checkNotNull(text);
      this.params = checkNotNull(params);
    }

    public String getMimeType() {
      return mimeType;
    }

    public String getText() {
      return text;
    }

    public List<Param> getParams() {
      return params;
    }
  }

  public static class Param {
    private final String name;
    private final String value;
    private final String fileName;
    private final String contentType;
    private final String comment;

    public Param(String name, String value) {
      this(name, value, "", "", "");
    }

    public Param(String name, String fileName, String contentType) {
      this(name, "", fileName, contentType, "");
    }

    public Param(String name, String value, String fileName, String contentType, String comment) {
      this.name = checkNotNull(name);
      this.value = checkNotNull(value);
      this.comment = checkNotNull(comment);
      this.fileName = checkNotNull(fileName);
      this.contentType = checkNotNull(contentType);
    }

    public String getName() {
      return name;
    }

    public String getValue() {
      return value;
    }

    public String getFileName() {
      return fileName;
    }

    public String getContentType() {
      return contentType;
    }

    public String getComment() {
      return comment;
    }

    @Override
    public String toString() {
      Objects.ToStringHelper helper = Objects.toStringHelper(this);
      helper.add("name", name);
      helper.add("value", value);
      helper.add("comment", comment);
      helper.add("fileName", fileName);
      helper.add("contentType", contentType);
      return helper.toString();
    }
  }
}
