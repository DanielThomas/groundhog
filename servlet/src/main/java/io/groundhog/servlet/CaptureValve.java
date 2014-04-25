/*
 * Copyright 2010 the original author or authors.
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

package io.groundhog.servlet;

import io.groundhog.base.CapturedPostRequest;
import io.groundhog.base.CapturedRequest;
import io.groundhog.base.RequestWriter;

import com.google.common.net.HostAndPort;
import io.netty.handler.codec.http.*;
import org.apache.catalina.Valve;
import org.apache.catalina.comet.CometEvent;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public class CaptureValve implements Valve {
  private static final Logger LOG = LoggerFactory.getLogger(CaptureValve.class);
  private static final String INFO = "io.groundhog.servlet.CaptureValve/1.0";
  private final RequestWriter writer;
  private Valve next;

  CaptureValve(RequestWriter writer) {
    this.writer = checkNotNull(writer);
  }

  @Override
  public String getInfo() {
    return INFO;
  }

  @Override
  public Valve getNext() {
    return next;
  }

  @Override
  public void setNext(Valve valve) {
    next = valve;
  }

  @Override
  public void backgroundProcess() {
  }

  @Override
  public void invoke(Request request, Response response) throws IOException, ServletException {
    if (!writer.isRunning()) {
      getNext().invoke(request, response);
      return;
    }

    long startedDateTime = System.currentTimeMillis();
    HostAndPort hostAndPort = HostAndPort.fromParts(request.getServerName(), request.getServerPort());

    // TODO protocol/version should come from request.getProtocol()
    HttpMethod httpMethod = HttpMethod.valueOf(request.getMethod());
    HttpRequest httpRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, httpMethod, request.getRequestURI());
    // TODO set headers, etc
    // TODO decode POST/PUT

    getNext().invoke(request, response);

    HttpResponse httpResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(response.getStatus()));
    CapturedRequest capturedRequest;
    if (HttpMethod.POST == httpMethod && HttpMethod.PUT == httpMethod) {
      capturedRequest = new CapturedPostRequest(startedDateTime, hostAndPort, httpRequest, httpResponse, "");
    } else {
      capturedRequest = new CapturedRequest(startedDateTime, hostAndPort, httpRequest, httpResponse);
    }
    if (writer.isRunning()) {
      writer.queue(capturedRequest);
    }
  }

  @Override
  public void event(Request request, Response response, CometEvent event) throws IOException, ServletException {
  }

  @Override
  public boolean isAsyncSupported() {
    return false;
  }
}
