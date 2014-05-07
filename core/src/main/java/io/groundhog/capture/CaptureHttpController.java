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

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.util.concurrent.Service;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Quick and dirty handling of control requests in lieu of a proper command and control API.
 *
 * @author Danny Thomas
 * @since 1.0
 */
public class CaptureHttpController {
  public static boolean isControlRequest(HttpRequest request) {
    checkNotNull(request);
    return request.getUri().startsWith("/groundhog");
  }

  public static FullHttpResponse handleControlRequest(HttpRequest request, CaptureWriter captureWriter) {
    checkNotNull(request);
    checkNotNull(captureWriter);
    ByteBuf content;
    List<String> parts = Splitter.on('/').limit(3).splitToList(request.getUri());
    if (parts.size() != 3) {
      content = Unpooled.copiedBuffer("No command provided, expected /groundhog/<command>", Charsets.UTF_8);
      FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, content);
      response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/html");
      return response;
    }

    String command = parts.get(2);
    switch (command) {
      case "start": {
        if (captureWriter.isRunning()) {
          content = Unpooled.copiedBuffer("Capture is already running", Charsets.UTF_8);
        } else if (captureWriter.state() == Service.State.NEW) {
          captureWriter.startAsync();
          captureWriter.awaitRunning();
          content = Unpooled.copiedBuffer("Started capture", Charsets.UTF_8);
        } else {
          content = Unpooled.copiedBuffer("Capture has already completed, and is a one shot service (state: " + captureWriter.state() + ")", Charsets.UTF_8);
        }
        break;
      }
      case "stop": {
        if (captureWriter.isRunning()) {
          captureWriter.stopAsync();
          captureWriter.awaitTerminated();
          content = Unpooled.copiedBuffer("Stopped capture", Charsets.UTF_8);
        } else {
          content = Unpooled.copiedBuffer("Capture is not running", Charsets.UTF_8);
        }
        break;
      }
      case "status": {
        content = Unpooled.copiedBuffer("Capture state: " + captureWriter.state(), Charsets.UTF_8);
        break;
      }
      default: {
        content = Unpooled.copiedBuffer("Unknown command: " + command, Charsets.UTF_8);
      }
    }
    FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content);
    response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/html");
    return response;
  }
}
