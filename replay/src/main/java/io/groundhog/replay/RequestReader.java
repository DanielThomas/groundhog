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

package io.groundhog.replay;

import io.groundhog.base.HttpArchive;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.Service;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.google.common.base.Preconditions.*;

/**
 * A reader that uses the Jackson streaming API to translate HAR logs into requests.
 * <p/>
 * If an object mapping based approach is desired, the browsermob project has some ready made classes:
 * <p/>
 * https://github.com/cburroughs/browsermob-proxy/tree/morestats/src/main/java/org/browsermob/core/har/
 * <p/>
 * TODO cut down on the amount of code duplication in this file
 *
 * @author Danny Thomas
 * @since 0.1
 */
public class RequestReader extends AbstractExecutionThreadService {
  private static final Logger LOG = LoggerFactory.getLogger(RequestReader.class);

  private static final Set<String> SKIPPED_ENTRY_FIELDS = ImmutableSortedSet.of("pageref", "serverIPAddress",
      "connection", "comment");
  private static final Set<String> SKIPPED_REQUEST_FIELDS = ImmutableSortedSet.of("headersSize", "bodySize", "comment");

  private final RequestDispatcher dispatcher;
  private final File recordingFile;
  private final SimpleDateFormat iso8601Format;
  private final File uploadLocation;

  private JsonParser parser;

  public RequestReader(File recordingFile, RequestDispatcher dispatcher, File uploadLocation) {
    this.recordingFile = checkNotNull(recordingFile);
    this.dispatcher = checkNotNull(dispatcher);
    this.uploadLocation = checkNotNull(uploadLocation);

    TimeZone tz = TimeZone.getTimeZone("UTC");
    iso8601Format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    iso8601Format.setTimeZone(tz);
  }

  @Override
  protected String serviceName() {
    return getClass().getSimpleName();
  }

  @Override
  protected void startUp() throws Exception {
    LOG.info("Reader starting up");

    JsonFactory jsonFactory = new JsonFactory();
    parser = jsonFactory.createParser(new FileInputStream(recordingFile));
  }

  @Override
  protected void run() throws Exception {
    parseLog();
    stopAsync();
  }

  private void parseLog() throws IOException {
    long timeReplayStartedNanos = System.nanoTime();
    long firstRequestTime = 0;
    long lastRequestTime;
    HttpArchive.Creator creator = null;

    checkToken(parser.nextToken(), JsonToken.START_OBJECT);
    checkToken(parser.nextToken(), JsonToken.FIELD_NAME);
    checkState("log".equals(parser.getCurrentName()), "The root element must be 'log', found %s",
        parser.getCurrentName());

    checkObjectStart(parser.nextToken());
    while (isRunning() && JsonToken.END_OBJECT != parser.nextToken()) {
      String fieldName = parser.getCurrentName();
      switch (fieldName) {
        case "version": {
          String version = getTextValue();
          checkArgument("1.2".equals(version), "HAR version 1.2 required. Found %s");
          break;
        }
        case "creator": {
          creator = parseCreator();
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
          while (isRunning() && JsonToken.END_ARRAY != parser.nextToken()) {
            boolean lightweight = null != creator && creator.getComment().contains("lightweight");
            lastRequestTime = parseAndDispatchEntry(timeReplayStartedNanos, firstRequestTime, lightweight);
            if (0l == firstRequestTime) {
              firstRequestTime = lastRequestTime;
            }
          }
          break;
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

  private long parseAndDispatchEntry(long timeReplayStartedNanos, long firstRequestTime, boolean lightweight) throws IOException {
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
            checkState(false, "Unknown field name %s. Location %s", fieldName, parser.getCurrentLocation());
          }
        }
      }
    }

    checkArgument(null != userAgentRequest, "A userAgentRequest was not parsed for the entry. Entry ending %s", parser.getCurrentLocation());
    checkArgument(null != expectedResponse, "An expected response was not parsed for the entry. Entry ending %s", parser.getCurrentLocation());
    checkArgument(0 != startedDateTime, "A startedDateTime was not parsed for the entry. Entry ending %s", parser.getCurrentLocation());

    userAgentRequest = new UserAgentRequest(userAgentRequest, expectedResponse);
    DelayedReplayRequest delayedRequest = new DelayedReplayRequest(userAgentRequest, startedDateTime, timeReplayStartedNanos,
        firstRequestTime);
    try {
      dispatcher.queue(delayedRequest);
    } catch (InterruptedException e) {
      throw Throwables.propagate(e);
    }

    return startedDateTime;
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
          checkState(false, "Unknown field name %s. Location %s", fieldName, parser.getCurrentLocation());
        }
      }
    }

    if (lightweight) {
      cookies = decodeCookies(headers, cookies);
    }

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
    HttpHeaders headers = new DefaultHttpHeaders();
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
              cookie.setMaxAge(iso8601Format.parse(getTextValue()).getTime());
            } catch (ParseException e) {
              throw Throwables.propagate(e);
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
            checkState(false, "Unknown field name '%s'. Location %s", fieldName, parser.getCurrentLocation());
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

    checkArgument(null != mimeType, "Field 'mimeType' was not found. postData entry ending %s", parser.getCurrentLocation());
    checkArgument(null != text || null != params, "Field 'text' or 'params' was not found. postData entry ending %s", parser.getCurrentLocation());

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

    checkState(!name.isEmpty(), "'%s' was not set for postData object. Location %s", "name", parser.getCurrentLocation());

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
    checkArgument(expectedSet.contains(actual), "Unexpected token. Actual %s, expected %s. Location %s",
        actual, expectedSet, parser.getCurrentName(), parser.getCurrentLocation());
  }

}
