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

import io.groundhog.base.HttpArchive;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.google.common.base.Throwables;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.MalformedURLException;
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
 * @since 0.1
 */
public class RequestWriter extends AbstractExecutionThreadService {
  private static final Logger LOG = LoggerFactory.getLogger(RequestWriter.class);

  public static final int DEFAULT_HTTP_PORT = 80;
  public static final int DEFAULT_HTTPS_PORT = 443;
  public static final String HTTP_SCHEME = "http";
  public static final String HTTPS_SCHEME = "https";

  private final OutputStream outputStream;
  private final LinkedBlockingQueue<RecordRequest> requestQueue;
  private final DateFormat iso8601Format;

  private JsonGenerator generator;

  public RequestWriter(File recordingFile) throws FileNotFoundException {
    this(new FileOutputStream(recordingFile));
  }

  public RequestWriter(OutputStream outputStream) {
    this.outputStream = outputStream;
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
    generator = jsonFactory.createGenerator(outputStream, JsonEncoding.UTF8);
    generator.setPrettyPrinter(new DefaultPrettyPrinter());
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
    generator.flush();
    generator.close();
  }

  @Override
  protected void run() throws InterruptedException, IOException {
    while (isRunning()) {
      RecordRequest request = requestQueue.poll(1, TimeUnit.SECONDS);
      if (null != request) {
        writeEntry(request);
      }
    }
  }

  private void writeLogStart() throws IOException {
    generator.writeStartObject();
    generator.writeObjectFieldStart("log");
    generator.writeStringField("version", "1.2");
    generator.writeArrayFieldStart("entries");
  }

  private void writeLogEnd() throws IOException {
    generator.writeEndArray();
    generator.writeEndObject();
  }

  public void queue(RecordRequest recordRequest) {
    requestQueue.add(recordRequest);
  }

  private void writeEntry(RecordRequest recordRequest) throws IOException {
    generator.writeStartObject();
    String startedDateTime = iso8601Format.format(new Date(recordRequest.getStartedDateTime()));
    generator.writeStringField("startedDateTime", startedDateTime);

    generator.writeObjectFieldStart("request");
    writeRequest(recordRequest);
    generator.writeEndObject();

    generator.writeObjectFieldStart("response");
    writeResponse(recordRequest);
    generator.writeEndObject();

    generator.writeEndObject();
  }

  private void writeRequest(RecordRequest recordRequest) throws IOException {
    HttpRequest request = recordRequest.getRequest();
    HttpHeaders headers = request.headers();

    generator.writeStringField("method", request.getMethod().name());
    generator.writeStringField("url", getUrl(recordRequest));
    generator.writeStringField("httpVersion", request.getProtocolVersion().text());

    writeHeaders(headers);
    writeCookies(headers);
    if (recordRequest instanceof RecordPostRequest) {
      RecordPostRequest recordPostRequest = (RecordPostRequest) recordRequest;
      writePostData(recordPostRequest);
    }
  }

  private String getUrl(RecordRequest recordRequest) {
    HostAndPort hostAndPort = recordRequest.getHostAndPort();
    try {
      int port = hostAndPort.getPortOrDefault(80);
      String scheme = getUrlScheme(port);
      if (isDefaultPort(port, scheme)) {
        return new URL(scheme, hostAndPort.getHostText(), recordRequest.getRequest().getUri()).toExternalForm();
      } else {
        return new URL(scheme, hostAndPort.getHostText(), port, recordRequest.getRequest().getUri()).toExternalForm();
      }
    } catch (MalformedURLException e) {
      throw Throwables.propagate(e);
    }
  }

  private boolean isDefaultPort(int port, String scheme) {
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

  private String getUrlScheme(int port) {
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

  private void writeResponse(RecordRequest recordRequest) throws IOException {
    HttpResponse response = recordRequest.getResponse();
    HttpHeaders headers = response.headers();
    generator.writeNumberField("status", response.getStatus().code());
    writeHeaders(headers);
    writeCookies(headers);
  }

  private void writeHeaders(HttpHeaders headers) throws IOException {
    generator.writeArrayFieldStart("headers");
    for (Map.Entry<String, String> entry : headers.entries()) {
      generator.writeStartObject();
      generator.writeStringField("name", entry.getKey());
      generator.writeStringField("value", entry.getValue());
      generator.writeEndObject();
    }
    generator.writeEndArray();
  }

  private void writeCookies(HttpHeaders headers) throws IOException {
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

  private void writePostData(RecordPostRequest recordRequest) throws IOException {
    HttpHeaders headers = recordRequest.getRequest().headers();
    generator.writeObjectFieldStart("postData");
    generator.writeStringField("mimeType", headers.get(HttpHeaders.Names.CONTENT_TYPE));

    if (!recordRequest.getContent().isEmpty()) {
      generator.writeStringField("text", recordRequest.getContent());
    }

    if (!recordRequest.getParams().isEmpty()) {
      generator.writeArrayFieldStart("params");
      List<HttpArchive.Param> params = recordRequest.getParams();
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