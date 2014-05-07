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

package io.groundhog.capture;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.util.concurrent.Service;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Quick and dirty handling of control requests in lieu of a proper command and control API.
 *
 * @author Danny Thomas
 * @since 1.0
 */
public class CaptureHttpController {
  private static final Logger LOG = LoggerFactory.getLogger(CaptureHttpController.class);

  public static boolean isControlRequest(HttpRequest request) {
    checkNotNull(request);
    return request.getUri().startsWith("/groundhog");
  }

  public static FullHttpResponse handleControlRequest(HttpRequest request, CaptureWriter captureWriter) {
    checkNotNull(request);
    checkNotNull(captureWriter);

    List<String> parts = Splitter.on('/').limit(3).splitToList(request.getUri());
    if (parts.size() != 3) {
      return statusResponse(Service.State.FAILED, "No command provided, expected /groundhog/<command>");
    }

    String command = parts.get(2);
    switch (command) {
      case "start": {
        if (captureWriter.isRunning()) {
          return statusResponse(Service.State.FAILED, "Capture is already running");
        } else if (captureWriter.state() == Service.State.NEW) {
          captureWriter.startAsync();
          captureWriter.awaitRunning();
          return statusResponse(Service.State.RUNNING, "Started capture");
        } else {
          return statusResponse(Service.State.FAILED, "Capture has already completed, and is a one shot service");
        }
      }
      case "stop": {
        if (captureWriter.isRunning()) {
          captureWriter.stopAsync();
          captureWriter.awaitTerminated();
          return statusResponse(Service.State.TERMINATED, "Stopped capture");
        } else {
          return statusResponse(Service.State.FAILED, "Stopped capture");
        }
      }
      case "status": {
        return statusResponse(captureWriter.state(), "Success");
      }
      default: {
        return statusResponse(Service.State.FAILED, "Unknown command: " + command);
      }
    }
  }

  private static FullHttpResponse statusResponse(Service.State state, String message) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    JsonFactory jsonFactory = new JsonFactory();
    JsonGenerator generator;
    try {
      generator = jsonFactory.createGenerator(outputStream);
    } catch (IOException e) {
      String errorMessage = "Could not create JSON generator";
      LOG.error(errorMessage, e);
      return errorResponse(errorMessage);
    }

    try {
      generator.writeStartObject();
      generator.writeStringField("state", state.toString());
      generator.writeStringField("message", message);
      generator.writeEndObject();
      generator.close();
    } catch (IOException e) {
      String errorMessage = "Error writing";
      LOG.error(errorMessage, e);
      return errorResponse(errorMessage);
    }

    ByteBuf content = Unpooled.wrappedBuffer(outputStream.toByteArray());
    FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content);
    response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/html");
    return response;
  }

  private static FullHttpResponse errorResponse(String message) {
    ByteBuf content = Unpooled.copiedBuffer(message, Charsets.UTF_8);
    FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, content);
    response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/html");
    return response;
  }
}
