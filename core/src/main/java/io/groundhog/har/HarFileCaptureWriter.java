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

package io.groundhog.har;

import io.groundhog.Groundhog;
import io.groundhog.base.HttpRequests;
import io.groundhog.capture.CaptureRequest;
import io.groundhog.capture.CaptureWriter;
import io.groundhog.capture.DefaultCapturePostRequest;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Sets;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public class HarFileCaptureWriter extends AbstractExecutionThreadService implements CaptureWriter {
  private static final Logger LOG = LoggerFactory.getLogger(CaptureWriter.class);

  private static final Set<String> EXCLUDED_HEADERS = Sets.newHashSet(HttpHeaders.Names.HOST, HttpHeaders.Names.VIA);
  private static final int DEFAULT_HTTP_PORT = 80;
  private static final int DEFAULT_HTTPS_PORT = 443;
  private static final String HTTP_SCHEME = "http";
  private static final String HTTPS_SCHEME = "https";

  private final File recordingFile;
  private final LinkedBlockingQueue<CaptureRequest> requestQueue;
  private final boolean lightweight;
  private final boolean includeContent;
  private final boolean pretty;
  private final DateFormat iso8601Format;

  private JsonGenerator generator;

  public HarFileCaptureWriter(File recordingFile, boolean lightweight, boolean includeContent, boolean pretty) {
    this.recordingFile = checkNotNull(recordingFile);
    this.lightweight = lightweight;
    this.includeContent = includeContent;
    this.pretty = pretty;

    if (lightweight) {
      checkArgument(!this.includeContent, "Content cannot be included in lightweight recordings");
    }

    requestQueue = new LinkedBlockingQueue<>();

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
    LOG.info("Writer starting up");

    JsonFactory jsonFactory = new JsonFactory();
    generator = jsonFactory.createGenerator(new FileOutputStream(recordingFile), JsonEncoding.UTF8);
    if (pretty) {
      generator.setPrettyPrinter(new DefaultPrettyPrinter());
    }
    writeLogStart();
  }

  @Override
  protected void shutDown() throws Exception {
    LOG.info("Writer shutting down");
    while (!requestQueue.isEmpty()) {
      LOG.info("Waiting for request queue to be drained...");
      Thread.sleep(5000);
    }
    writeLogEnd();
    generator.close();
  }

  @Override
  protected void run() throws InterruptedException, IOException {
    while (isRunning()) {
      CaptureRequest request = requestQueue.poll(1, TimeUnit.SECONDS);
      if (null != request) {
        writeEntry(request);
      }
    }
  }

  private void writeLogStart() throws IOException {
    generator.writeStartObject();
    generator.writeObjectFieldStart("log");
    generator.writeStringField("version", "1.2");
    writeCreator();
    generator.writeArrayFieldStart("entries");
  }

  private void writeCreator() throws IOException {
    generator.writeObjectFieldStart("creator");
    // TODO get these from jar manifest
    generator.writeStringField("name", "Groundhog Capture");
    generator.writeStringField("version", Groundhog.getVersion());
    if (lightweight) {
      generator.writeStringField("comment", "lightweight");
    }
    generator.writeEndObject();
  }

  private void writeLogEnd() throws IOException {
    generator.writeEndArray();
    generator.writeEndObject();
  }

  @Override
  public void writeAsync(CaptureRequest captureRequest) {
    checkNotNull(captureRequest);
    if (isRunning()) {
      requestQueue.add(captureRequest);
    }
  }

  private void writeEntry(CaptureRequest captureRequest) throws IOException {
    generator.writeStartObject();
    String startedDateTime = iso8601Format.format(new Date(captureRequest.getStartedDateTime()));
    generator.writeStringField("startedDateTime", startedDateTime);
    writeRequest(captureRequest);
    writeResponse(captureRequest);
    generator.writeEndObject();
    generator.flush();
  }

  private void writeRequest(CaptureRequest captureRequest) throws IOException {
    generator.writeObjectFieldStart("request");
    HttpRequest request = captureRequest.getRequest();

    if (lightweight && HttpArchive.DEFAULT_METHOD != request.getMethod()) {
      generator.writeStringField("method", request.getMethod().name());
    }
    generator.writeStringField("url", getUrl(captureRequest));
    if (lightweight && HttpArchive.DEFAULT_HTTP_VERSION != request.getProtocolVersion()) {
      generator.writeStringField("httpVersion", request.getProtocolVersion().text());
    }

    HttpHeaders headers = request.headers();
    writeHeaders(headers, false);
    writeCookies(headers);

    if (captureRequest instanceof DefaultCapturePostRequest) {
      DefaultCapturePostRequest proxyPostRequest = (DefaultCapturePostRequest) captureRequest;
      writePostData(proxyPostRequest);
    }
    generator.writeEndObject();
  }

  @VisibleForTesting
  static String getUrl(CaptureRequest captureRequest) {
    HttpRequest request = captureRequest.getRequest();
    HostAndPort hostAndPort = HttpRequests.identifyHostAndPort(request);
    try {
      int port = hostAndPort.getPortOrDefault(80);
      String protocol = getUrlScheme(port);
      String host = hostAndPort.getHostText();
      URI uri = new URI(request.getUri());
      String file = uri.getPath();
      if (null != uri.getQuery()) {
        file = file + "?" + uri.getQuery();
      }
      if (isDefaultPort(port, protocol)) {
        return new URL(protocol, host, file).toExternalForm();
      } else {
        return new URL(protocol, host, port, file).toExternalForm();
      }
    } catch (URISyntaxException | MalformedURLException e) {
      throw Throwables.propagate(e);
    }
  }

  private static boolean isDefaultPort(int port, String scheme) {
    switch (scheme) {
      case HTTP_SCHEME: {
        return DEFAULT_HTTP_PORT == port;
      }
      case HTTPS_SCHEME: {
        return DEFAULT_HTTPS_PORT == port;
      }
      default:
        throw new IllegalArgumentException("Unknown protocol scheme: " + scheme);
    }
  }

  private static String getUrlScheme(int port) {
    // It seems like we can only infer protocol schemes based on port, fine for now in any case
    switch (port) {
      case DEFAULT_HTTPS_PORT:
      case 8443: {
        return HTTPS_SCHEME;
      }
      default:
        return HTTP_SCHEME;
    }
  }

  private void writeResponse(CaptureRequest captureRequest) throws IOException {
    generator.writeObjectFieldStart("response");
    HttpResponse response = captureRequest.getResponse();
    HttpHeaders headers = response.headers();
    generator.writeNumberField("status", response.getStatus().code());
    writeHeaders(headers, lightweight);
    writeCookies(headers);
    generator.writeEndObject();
  }

  private void writeHeaders(HttpHeaders headers, final boolean minimumOnly) throws IOException {
    Predicate<Map.Entry<String, String>> excludeHeader = new Predicate<Map.Entry<String, String>>() {
      @Override
      public boolean apply(Map.Entry<String, String> input) {
        String name = input.getKey();
        return EXCLUDED_HEADERS.contains(name) || (minimumOnly && !HttpArchive.MINIMUM_RESPONSE_HEADERS.contains(name));
      }
    };

    Iterator<Map.Entry<String, String>> it = FluentIterable.from(headers).filter(Predicates.not(excludeHeader)).iterator();
    if (it.hasNext()) {
      generator.writeArrayFieldStart("headers");
      while (it.hasNext()) {
        Map.Entry<String, String> entry = it.next();
        generator.writeStartObject();
        generator.writeStringField("name", entry.getKey());
        generator.writeStringField("value", entry.getValue());
        generator.writeEndObject();
      }
      generator.writeEndArray();
    }
  }

  private void writeCookies(HttpHeaders headers) throws IOException {
    if (lightweight) {
      return;
    }

    if (headers.contains(HttpHeaders.Names.COOKIE)) {
      generator.writeArrayFieldStart("cookies");
      Set<Cookie> cookies = CookieDecoder.decode(headers.get(HttpHeaders.Names.COOKIE));
      for (Cookie cookie : cookies) {
        generator.writeStartObject();
        generator.writeStringField("name", cookie.getName());
        generator.writeStringField("value", cookie.getValue());
        if (null != cookie.getPath()) {
          generator.writeStringField("path", cookie.getPath());
        }
        if (null != cookie.getDomain()) {
          generator.writeStringField("domain", cookie.getDomain());
        }
        if (Long.MIN_VALUE != cookie.getMaxAge()) {
          generator.writeStringField("expires", iso8601Format.format(new Date(cookie.getMaxAge())));
        }
        if (cookie.isHttpOnly()) {
          generator.writeBooleanField("httpOnly", true);
        }
        if (cookie.isSecure()) {
          generator.writeBooleanField("secure", true);
        }
        generator.writeEndObject();
      }
      generator.writeEndArray();
    }
  }

  private void writePostData(DefaultCapturePostRequest captureRequest) throws IOException {
    HttpHeaders headers = captureRequest.getRequest().headers();
    generator.writeObjectFieldStart("postData");
    generator.writeStringField("mimeType", headers.get(HttpHeaders.Names.CONTENT_TYPE));

    if (captureRequest.getContent().isPresent()) {
      generator.writeStringField("text", captureRequest.getContent().get());
    }

    if (!captureRequest.getParams().isEmpty()) {
      generator.writeArrayFieldStart("params");
      List<HttpArchive.Param> params = captureRequest.getParams();
      for (HttpArchive.Param param : params) {
        generator.writeStartObject();
        writeMandatoryStringField("name", param.getName());
        writeOptionalStringField("value", param.getValue());
        writeOptionalStringField("fileName", param.getFileName());
        writeOptionalStringField("contentType", param.getContentType());
        writeOptionalStringField("comment", param.getComment());
        generator.writeEndObject();
      }
      generator.writeEndArray();
    }
    generator.writeEndObject();
  }

  private void writeMandatoryStringField(String fieldName, String value) throws IOException {
    checkArgument(!value.isEmpty(), "A value must be set for field '%s'", fieldName);
    writeOptionalStringField(fieldName, value);
  }

  private void writeOptionalStringField(String fieldName, String value) throws IOException {
    checkNotNull(fieldName);
    checkNotNull(value);
    if (!value.isEmpty()) {
      generator.writeStringField(fieldName, value);
    }
  }
}
