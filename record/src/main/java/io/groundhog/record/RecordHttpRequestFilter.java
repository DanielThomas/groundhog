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

package io.groundhog.record;

import io.groundhog.base.HttpArchive;
import io.groundhog.base.HttpRequests;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.net.HostAndPort;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import org.littleshoot.proxy.HttpFilters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Danny Thomas
 * @since 0.1
 */
public class RecordHttpRequestFilter implements HttpFilters {
  private static final Logger LOG = LoggerFactory.getLogger(RecordHttpRequestFilter.class);

  private static final Set<HttpMethod> POST_DECODE_METHODS = Sets.newHashSet(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH);
  private static final String TEXT_PLAIN = "text/plain";

  private final RequestWriter requestWriter;
  private final File uploadLocation;

  private long startedDateTime;
  private HttpRequest request;
  private boolean isPost;
  private HttpPostRequestDecoder decoder;
  private List<HttpArchive.Param> params;
  private StringBuilder content;

  public RecordHttpRequestFilter(RequestWriter requestWriter, File uploadLocation) {
    this.requestWriter = checkNotNull(requestWriter);
    this.uploadLocation = checkNotNull(uploadLocation);
  }

  @Override
  public HttpResponse requestPre(HttpObject httpObject) {
    checkNotNull(httpObject);
    try {
      if (httpObject instanceof HttpRequest) {
        startedDateTime = System.currentTimeMillis();
        request = (HttpRequest) httpObject;
        rewriteUri(request);
      } else if (httpObject instanceof HttpContent) {
        HttpContent chunk = ((HttpContent) httpObject);
        HttpMethod method = request.getMethod();
        if (POST_DECODE_METHODS.contains(method)) {
          isPost = true;
          chunk = chunk.duplicate();
          String contentType = request.headers().get(HttpHeaders.Names.CONTENT_TYPE);
          if (contentType.startsWith(TEXT_PLAIN)) {
            if (null == content) {
              content = new StringBuilder();
            }
            ByteBuf buf = chunk.content();
            content.append(buf.copy().toString(Charsets.UTF_8));
          } else {
            if (null == decoder) {
              decoder = new HttpPostRequestDecoder(request);
            }
            decoder.offer(chunk);

            readAvailableData();

            if (chunk instanceof LastHttpContent) {
              decoder.destroy();
            }
          }
        }
      }
    } catch (Throwable t) {
      if (t instanceof ThreadDeath) {
        throw t;
      }

      LOG.error("Failed to record request", t);
    }

    return null;
  }

  void rewriteUri(HttpRequest request) {
    // FIXME quick and dirty hack for reverse proxying, once configuration has been added, proxy loops need to be detected here also
    if (!request.getUri().contains("://")) {
      request.setUri("http://localhost:8080" + request.getUri());
    }
  }

  @Override
  public HttpResponse requestPost(HttpObject httpObject) {
    checkNotNull(httpObject);
    return null;
  }

  @Override
  public void responsePre(HttpObject httpObject) {
    checkNotNull(httpObject);
    if (httpObject instanceof HttpResponse) {
      HttpResponse response = (HttpResponse) httpObject;
      HostAndPort hostAndPort = HttpRequests.identifyHostAndPort(request);
      RecordRequest recordRequest;
      if (isPost) {
        if (null == decoder) {
          recordRequest = new RecordPostRequest(startedDateTime, hostAndPort, request, response, content.toString());
        } else {
          recordRequest = new RecordPostRequest(startedDateTime, hostAndPort, request, response, params);
        }
      } else {
        recordRequest = new RecordRequest(startedDateTime, hostAndPort, request, response);
      }
      requestWriter.queue(recordRequest);
    }
  }

  @Override
  public void responsePost(HttpObject httpObject) {
    checkNotNull(httpObject);
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
            //noinspection ResultOfMethodCallIgnored
            destFile.getParentFile().mkdirs();
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

}
