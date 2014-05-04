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

package io.groundhog.capture;

import io.groundhog.har.HttpArchive;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public class DefaultHttpCaptureDecoder implements HttpCaptureDecoder {
  private static final Logger LOG = LoggerFactory.getLogger(DefaultHttpCaptureDecoder.class);

  private static final Set<HttpMethod> POST_DECODE_METHODS = Sets.newHashSet(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH);
  private static final String TEXT_PLAIN = "text/plain";

  private final File uploadLocation;

  private long startedDateTime;
  private HttpRequest request;
  private boolean isPost;
  private HttpPostRequestDecoder decoder;
  private List<HttpArchive.Param> params;
  private StringBuilder content;
  private HttpResponse response;

  public DefaultHttpCaptureDecoder(File uploadLocation) {
    this.uploadLocation = checkNotNull(uploadLocation);
  }

  @Override
  public void request(HttpObject httpObject) {
    checkNotNull(httpObject);
    if (httpObject instanceof HttpRequest) {
      startedDateTime = System.currentTimeMillis();
      // Duplicate the request, so the state can't be modified
      HttpRequest originalRequest = (HttpRequest) httpObject;
      this.request = new DefaultHttpRequest(originalRequest.getProtocolVersion(), originalRequest.getMethod(), originalRequest.getUri());
      this.request.headers().set(originalRequest.headers());
    } else if (httpObject instanceof HttpContent) {
      HttpContent chunk = ((HttpContent) httpObject);
      HttpMethod method = request.getMethod();
      String contentType = request.headers().get(HttpHeaders.Names.CONTENT_TYPE);
      if (POST_DECODE_METHODS.contains(method)) {
        isPost = true;
        chunk = chunk.duplicate();
        if (contentType.startsWith(TEXT_PLAIN)) {
          if (null == content) {
            content = new StringBuilder();
          }
          ByteBuf buf = chunk.content();
          content.append(buf.copy().toString(Charsets.UTF_8));
        } else if (isDecodedContentType(contentType)) {
          if (null == decoder) {
            decoder = new HttpPostRequestDecoder(request);
          }
          decoder.offer(chunk);
          readAvailableData();
        }
      } else {
        LOG.debug("Unsupported POST content type ", contentType);
      }
    }
  }

  private boolean isDecodedContentType(String contentType) {
    return contentType.startsWith(HttpHeaders.Values.MULTIPART_FORM_DATA) || contentType.startsWith(HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED);
  }

  private void readAvailableData() {
    if (null == params) {
      params = Lists.newArrayList();
    }

    try {
      while (decoder.hasNext()) {
        InterfaceHttpData data = decoder.next();
        HttpArchive.Param param;
        if (data instanceof Attribute) {
          Attribute attr = (Attribute) data;
          try {
            param = new HttpArchive.Param(attr.getName(), attr.getValue());
          } catch (IOException e) {
            throw Throwables.propagate(e);
          }
        } else if (data instanceof FileUpload) {
          FileUpload upload = (FileUpload) data;
          param = new HttpArchive.Param(upload.getName(), upload.getFilename(), upload.getContentType());
          File destFile = new File(uploadLocation, String.format("%s/%s", startedDateTime, upload.getFilename()));
          try {
            checkState(destFile.getParentFile().mkdirs(), "Did not successfully create upload location %s", destFile);
            upload.renameTo(destFile);
          } catch (IOException e) {
            throw Throwables.propagate(e);
          } finally {
            decoder.removeHttpDataFromClean(upload);
          }
        } else {
          throw new IllegalStateException("Unexpected data" + data.getClass());
        }
        params.add(param);
      }
    } catch (HttpPostRequestDecoder.EndOfDataDecoderException e) {
      LOG.debug("Reached end of chunk");
    }
  }

  @Override
  public void response(HttpObject httpObject) {
    checkNotNull(httpObject);
    if (httpObject instanceof HttpResponse) {
      // Signal to the decoder that the request has completed, because we've started to process a response
      request(LastHttpContent.EMPTY_LAST_CONTENT);
      response = (HttpResponse) httpObject;
    }
  }

  @Override
  public CaptureRequest complete() {
    checkState(null != request, "Request hasn't been set");
    checkState(null != response, "Response hasn't been set");
    CaptureRequest captureRequest;
    if (isPost) {
      if (null != content) {
        captureRequest = new DefaultCapturePostRequest(startedDateTime, request, response, content.toString());
      } else if (null != decoder) {
        captureRequest = new DefaultCapturePostRequest(startedDateTime, request, response, params);
      } else {
        captureRequest = new DefaultCaptureRequest(startedDateTime, request, response);
      }
    } else {
      captureRequest = new DefaultCaptureRequest(startedDateTime, request, response);
    }
    return captureRequest;
  }

  @Override
  public void destroy() {
    if (null != decoder) {
      decoder.destroy();
    }
  }
}
