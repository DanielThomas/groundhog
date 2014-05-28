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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.inject.Inject;
import io.groundhog.har.HttpArchive;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.GZIPInputStream;

import static com.google.common.base.Preconditions.*;

/**
 * A reader that uses the Jackson streaming API to translate HARs into requests.
 *
 * @author Danny Thomas
 * @since 1.0
 */
public class DefaultRequestReader implements RequestReader {
  private static final String REQUIRED_HAR_VERSION = "1.2";
  private static final Set<String> SKIPPED_ENTRY_FIELDS = ImmutableSortedSet.of("pageref", "serverIPAddress",
          "connection", "comment");
  private static final Set<String> SKIPPED_REQUEST_FIELDS = ImmutableSortedSet.of("headersSize", "bodySize", "comment");

  private final File uploadLocation;
  private final JsonParser parser;
  private final SimpleDateFormat iso8601Format;

  private boolean lightweight;
  private State state = State.START;

  @Inject
  DefaultRequestReader(File recordingFile, File uploadLocation) throws IOException {
    this.uploadLocation = checkNotNull(uploadLocation);

    JsonFactory jsonFactory = new JsonFactory();
    String filename = recordingFile.getName();
    String ext = Files.getFileExtension(filename);
    InputStream inStream;
    if (ext.equals("gz")) {
      inStream = new GZIPInputStream(new FileInputStream(recordingFile));
    } else {
      inStream = new FileInputStream(recordingFile);
    }
    parser = jsonFactory.createParser(inStream);
    TimeZone tz = TimeZone.getTimeZone("UTC");
    iso8601Format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    iso8601Format.setTimeZone(tz);
  }

  @Override
  public UserAgentRequest readRequest() throws IOException {
    if (State.START == state) {
      state = seekEntries();
    } else if (State.ENTRIES != state) {
      throw new IOException("No requests are available. State: " + state);
    }
    return parseEntry();
  }

  @Override
  public boolean isLastRequest(UserAgentRequest request) {
    return request instanceof LastUserAgentRequest;
  }

  private State seekEntries() throws IOException {
    checkToken(parser.nextToken(), JsonToken.START_OBJECT);
    checkToken(parser.nextToken(), JsonToken.FIELD_NAME);
    checkState("log".equals(parser.getCurrentName()), "The root element must be 'log', found %s",
            parser.getCurrentName());

    checkObjectStart(parser.nextToken());
    while (JsonToken.END_OBJECT != parser.nextToken()) {
      String fieldName = parser.getCurrentName();
      switch (fieldName) {
        case "version": {
          String version = getTextValue();
          checkArgument(REQUIRED_HAR_VERSION.equals(version), "HAR version 1.2 required. Found %s");
          break;
        }
        case "creator": {
          HttpArchive.Creator creator = parseCreator();
          lightweight = null != creator && creator.getComment().contains("lightweight");
          break;
        }
        case "browser": {
          skipObject();
          break;
        }
        case "pages": {
          skipArray();
          break;
        }
        case "entries": {
          checkArrayStart(parser.nextToken());
          // Seek to the first entry start
          checkObjectStart(parser.nextToken());
          return State.ENTRIES;
        }
        case "comment": {
          getTextValue();
          break;
        }
        default: {
          throw new IllegalStateException("Unexpected field " + fieldName);
        }
      }
    }
    return State.END;
  }

  private HttpArchive.Creator parseCreator() throws IOException {
    checkObjectStart(parser.nextToken());

    String name = "";
    String version = "";
    String comment = "";
    while (JsonToken.END_OBJECT != parser.nextToken()) {
      String fieldName = parser.getCurrentName();
      switch (fieldName) {
        case "name": {
          name = getTextValue();
          break;
        }
        case "version": {
          version = getTextValue();
          break;
        }
        case "comment": {
          comment = getTextValue();
          break;
        }
      }
    }

    return new HttpArchive.Creator(name, version, comment);
  }

  private UserAgentRequest parseEntry() throws IOException {
    checkObjectStart(parser.getCurrentToken());

    UserAgentRequest userAgentRequest = null;
    HttpResponse expectedResponse = null;
    long startedDateTime = 0;

    while (JsonToken.END_OBJECT != parser.nextToken()) {
      String fieldName = parser.getCurrentName();
      switch (fieldName) {
        case "startedDateTime": {
          try {
            String value = getTextValue();
            startedDateTime = iso8601Format.parse(value).getTime();
          } catch (ParseException e) {
            throw Throwables.propagate(e);
          }
          break;
        }
        case "time": {
          getFloatValue();
          break;
        }
        case "request": {
          checkState(0 != startedDateTime, "startedDateTime must come before the request");
          userAgentRequest = parseRequest(startedDateTime, lightweight);
          break;
        }
        case "response": {
          expectedResponse = parseResponse();
          break;
        }
        case "cache": {
          parseCache();
          break;
        }
        case "timings": {
          skipObject();
          break;
        }
        default: {
          if (SKIPPED_ENTRY_FIELDS.contains(fieldName)) {
            getTextValue();
          } else {
            checkState(false, "Unknown field name '%s'. Location '%s'", fieldName, parser.getCurrentLocation());
          }
        }
      }
    }
    JsonToken jsonToken = parser.nextToken();
    if (JsonToken.START_OBJECT == jsonToken) {
      //noinspection ConstantConditions
      return new UserAgentRequest(userAgentRequest, expectedResponse);
    } else if (JsonToken.END_ARRAY == jsonToken) {
      state = State.END;
      //noinspection ConstantConditions
      return new LastUserAgentRequest(userAgentRequest, expectedResponse);
    } else {
      throw new IOException(String.format("Unexpected token '%s'. Location '%s'", jsonToken, parser.getCurrentLocation()));
    }
  }

  private UserAgentRequest parseRequest(long startedDateTime, boolean lightweight) throws IOException {
    checkObjectStart(parser.nextToken());

    HttpVersion httpVersion = lightweight ? HttpArchive.DEFAULT_HTTP_VERSION : null;
    HttpMethod method = lightweight ? HttpArchive.DEFAULT_METHOD : null;
    HttpHeaders headers = HttpHeaders.EMPTY_HEADERS;
    Set<Cookie> cookies = Collections.emptySet();

    String uri = null;
    HttpArchive.PostData postData = null;
    while (JsonToken.END_OBJECT != parser.nextToken()) {
      String fieldName = parser.getCurrentName();

      if (SKIPPED_REQUEST_FIELDS.contains(fieldName)) {
        parser.nextToken();
        continue;
      }

      switch (fieldName) {
        case "method": {
          method = HttpMethod.valueOf(getTextValue());
          break;
        }
        case "url": {
          URL url = new URL(getTextValue());
          uri = url.getFile();
          break;
        }
        case "httpVersion": {
          httpVersion = HttpVersion.valueOf(getTextValue());
          break;
        }
        case "headers": {
          headers = parseHeaders();
          break;
        }
        case "postData": {
          postData = parsePostData();
          break;
        }
        case "cookies": {
          cookies = parseCookies();
          break;
        }
        case "queryString": {
          skipArray();
          break;
        }
        default: {
          checkState(false, "Unknown field name '%s'. Location '%s'", fieldName, parser.getCurrentLocation());
        }
      }
    }

    if (lightweight) {
      cookies = decodeCookies(headers, cookies);
    }
    //noinspection ConstantConditions
    return new UserAgentRequest(httpVersion, method, uri, Optional.fromNullable(postData), headers, cookies, uploadLocation, startedDateTime);
  }

  private Set<Cookie> decodeCookies(HttpHeaders headers, Set<Cookie> defaultCookies) {
    checkState(defaultCookies.isEmpty(), "Cookies should not have been provided by a lightweight data source");
    String cookie = headers.get(HttpHeaders.Names.COOKIE);
    if (null != cookie) {
      return CookieDecoder.decode(cookie);
    }
    return defaultCookies;
  }

  private HttpHeaders parseHeaders() throws IOException {
    checkArrayStart(parser.nextToken());
    HttpHeaders headers = new DefaultHttpHeaders(false);
    while (JsonToken.END_ARRAY != parser.nextToken()) {
      checkObjectStart(parser.getCurrentToken());

      String name = null;
      String value = null;

      while (JsonToken.END_OBJECT != parser.nextToken()) {
        String fieldName = parser.getCurrentName();
        switch (fieldName) {
          case "name": {
            name = getTextValue();
            break;
          }
          case "value": {
            value = getTextValue();
            break;
          }
          case "comment": {
            getTextValue();
            break;
          }
        }

        if (null != name && null != value) {
          headers.add(name, value);
          name = null;
          value = null;
        }
      }
    }

    return headers;
  }

  private Set<Cookie> parseCookies() throws IOException {
    checkArrayStart(parser.nextToken());

    Set<Cookie> cookies = Sets.newHashSet();
    while (JsonToken.END_ARRAY != parser.nextToken()) {
      Cookie cookie = null;
      while (JsonToken.END_OBJECT != parser.nextToken()) {
        String fieldName = parser.getCurrentName();
        if ("name".equals(fieldName)) {
          cookie = new DefaultCookie(getTextValue(), "");
          continue;
        } else if (null == cookie) {
          throw new IllegalStateException("The cookie name must come before all other cookie fields");
        }

        switch (fieldName) {
          case "value": {
            cookie.setValue(getTextValue());
            break;
          }
          case "path": {
            cookie.setPath(getTextValue());
            break;
          }
          case "domain": {
            cookie.setDomain(getTextValue());
            break;
          }
          case "expires": {
            try {
              Optional<String> expiresValue = getOptionalTextValue();
              if (expiresValue.isPresent()) {
                long time = iso8601Format.parse(expiresValue.get()).getTime();
                cookie.setMaxAge(time);
              } else {
                cookie.setMaxAge(Long.MIN_VALUE);
              }
            } catch (ParseException e) {
              throw new IOException("Parse error parsing cookie expires", e);
            }
            break;
          }
          case "httpOnly": {
            cookie.setHttpOnly(getBooleanValue());
            break;
          }
          case "secure": {
            cookie.setSecure(getBooleanValue());
            break;
          }
          case "comment": {
            getTextValue();
            break;
          }
          default: {
            checkState(false, "Unknown field name '%s'. Location '%s'", fieldName, parser.getCurrentLocation());
          }
        }
      }

      cookies.add(cookie);
    }

    return cookies;
  }

  private HttpArchive.PostData parsePostData() throws IOException {
    checkObjectStart(parser.nextToken());

    String mimeType = null;
    String text = "";
    List<HttpArchive.Param> params = Collections.emptyList();

    while (JsonToken.END_OBJECT != parser.nextToken()) {
      String fieldName = parser.getCurrentName();
      switch (fieldName) {
        case "mimeType": {
          mimeType = getTextValue();
          break;
        }
        case "params": {
          params = parsePostDataParams();
          break;
        }
        case "text": {
          text = getTextValue();
          break;
        }
        case "comment": {
          getTextValue();
        }
      }
    }

    checkArgument(null != mimeType, "Field 'mimeType' was not found. postData entry ending '%s'", parser.getCurrentLocation());
    checkArgument(null != text || null != params, "Field 'text' or 'params' was not found. postData entry ending '%s'", parser.getCurrentLocation());
    //noinspection ConstantConditions
    return new HttpArchive.PostData(mimeType, text, params);
  }

  private List<HttpArchive.Param> parsePostDataParams() throws IOException {
    checkArrayStart(parser.nextToken());
    List<HttpArchive.Param> params = Lists.newArrayList();
    while (JsonToken.END_ARRAY != parser.nextToken()) {
      params.add(parsePostDataParam());
    }
    return params;
  }

  private HttpArchive.Param parsePostDataParam() throws IOException {
    checkObjectStart(parser.getCurrentToken());

    String name = "";
    String value = "";
    String comment = "";
    String fileName = "";
    String contentType = "";
    while (JsonToken.END_OBJECT != parser.nextToken()) {
      String fieldName = parser.getCurrentName();
      switch (fieldName) {
        case "name": {
          name = getTextValue();
          break;
        }
        case "value": {
          value = getTextValue();
          break;
        }
        case "fileName": {
          fileName = getTextValue();
          break;
        }
        case "contentType": {
          contentType = getTextValue();
          break;
        }
        case "comment": {
          comment = getTextValue();
          break;
        }
      }
    }

    checkState(!name.isEmpty(), "'%s' was not set for postData object. Location '%s'", "name", parser.getCurrentLocation());

    return new HttpArchive.Param(name, value, fileName, contentType, comment);
  }

  private HttpResponse parseResponse() throws IOException {
    checkObjectStart(parser.nextToken());

    HttpVersion httpVersion = HttpVersion.HTTP_1_1;
    HttpResponseStatus status = HttpResponseStatus.OK;
    HttpHeaders headers = HttpHeaders.EMPTY_HEADERS;

    while (JsonToken.END_OBJECT != parser.nextToken()) {
      String fieldName = parser.getCurrentName();
      switch (fieldName) {
        case "statusText":
        case "redirectURL":
        case "comment": {
          getTextValue();
          break;
        }
        case "httpVersion": {
          httpVersion = HttpVersion.valueOf(getTextValue());
          break;
        }
        case "cookies": {
          skipArray();
          break;
        }
        case "headers": {
          headers = parseHeaders();
          break;
        }
        case "content": {
          skipObject();
          break;
        }
        case "status": {
          status = HttpResponseStatus.valueOf(getIntegerValue());
          break;
        }
        case "headersSize":
        case "bodySize": {
          getIntegerValue();
          break;
        }
      }
    }

    HttpResponse response = new DefaultHttpResponse(httpVersion, status);
    response.headers().set(headers);
    return response;
  }

  private void parseCache() throws IOException {
    checkObjectStart(parser.nextToken());
    while (JsonToken.END_OBJECT != parser.nextToken()) {
      String fieldName = parser.getCurrentName();
      switch (fieldName) {
        case "beforeRequest":
        case "afterRequest": {
          skipObject();
          break;
        }
        case "comment": {
          getTextValue();
          break;
        }
      }
    }
  }

  private String getTextValue() throws IOException {
    checkToken(parser.nextToken(), JsonToken.VALUE_STRING);
    return parser.getText();
  }

  /**
   * Get a text value, or {@link Optional#absent()} if null.
   */
  private Optional<String> getOptionalTextValue() throws IOException {
    checkToken(parser.nextToken(), JsonToken.VALUE_STRING, JsonToken.VALUE_NULL);
    String text = parser.getText();
    return "null".equals(text) ? Optional.<String>absent() : Optional.of(text);
  }

  private int getIntegerValue() throws IOException {
    checkToken(parser.nextToken(), JsonToken.VALUE_NUMBER_INT);
    return parser.getIntValue();
  }

  private float getFloatValue() throws IOException {
    checkToken(parser.nextToken(), JsonToken.VALUE_NUMBER_FLOAT);
    return parser.getFloatValue();
  }

  private boolean getBooleanValue() throws IOException {
    checkToken(parser.nextToken(), JsonToken.VALUE_TRUE, JsonToken.VALUE_FALSE);
    return parser.getBooleanValue();
  }

  private void skipObject() throws IOException {
    JsonToken token = parser.nextToken();
    checkObjectStart(token);
    while (JsonToken.END_OBJECT != token) {
      token = parser.nextToken();
    }
  }

  private void skipArray() throws IOException {
    JsonToken token = parser.nextToken();
    checkArrayStart(token);
    while (JsonToken.END_ARRAY != token) {
      token = parser.nextToken();
    }
  }

  private void checkObjectStart(JsonToken token) throws IOException {
    checkToken(token, JsonToken.START_OBJECT);
  }

  private void checkArrayStart(JsonToken token) throws IOException {
    checkToken(token, JsonToken.START_ARRAY);
  }

  private void checkToken(JsonToken actual, JsonToken... expected) throws IOException {
    HashSet<JsonToken> expectedSet = Sets.newHashSet(expected);
    checkArgument(expectedSet.contains(actual), "Unexpected token. Actual '%s', expected '%s'. Location '%s'",
            actual, expectedSet, parser.getCurrentName(), parser.getCurrentLocation());
  }

  private enum State {
    START,
    ENTRIES,
    END
  }
}
