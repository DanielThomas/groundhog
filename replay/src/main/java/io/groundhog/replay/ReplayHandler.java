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

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.net.MediaType;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public class ReplayHandler extends ChannelDuplexHandler {
  private static final Logger LOG = LoggerFactory.getLogger(ReplayHandler.class);
  public static final String TRANSACTION_LABEL_HEADER = "X-Transaction-Label";
  public static final String UNTITLED_PAGE_LABEL = "Untitled Page";

  private final ResultListener resultListener;

  private ReplayHttpRequest request;
  private HttpResponse response;
  private HttpResponse expectedResponse;
  private long started;
  private final AtomicInteger bytesRead = new AtomicInteger();

  public ReplayHandler(ChannelPipeline pipeline, ResultListener resultListener, boolean useSSL) throws Exception {
    checkNotNull(pipeline);
    this.resultListener = checkNotNull(resultListener);
    initPipeline(pipeline, useSSL);
  }

  private void initPipeline(ChannelPipeline p, boolean useSSL) throws Exception {
    p.addLast("bytesRead", new BytesReadHandler());

    if (useSSL) {
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, null, null);
      SSLEngine engine = context.createSSLEngine();
      engine.setUseClientMode(true);
      p.addLast("ssl", new SslHandler(engine));
    }

    p.addLast("codec", new HttpClientCodec());

    p.addLast("inflater", new HttpContentDecompressor());

    p.addLast("chunkedWriter", new ChunkedWriteHandler());

    p.addLast("ua", new UserAgentHandler());

    p.addLast("replay", this);
  }

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
    checkNotNull(ctx);
    checkNotNull(msg);
    checkNotNull(promise);
    if (msg instanceof ReplayHttpRequest) {
      started = System.currentTimeMillis();
      request = (ReplayHttpRequest) msg;
      expectedResponse = request.getExpectedResponse();
    } else if (msg instanceof HttpRequest) {
      throw new IllegalStateException("A request was handled that did not extend ReplayHttpRequest: " + msg.getClass());
    }

    super.write(ctx, msg, promise);
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    checkNotNull(ctx);
    checkNotNull(msg);
    if (msg instanceof HttpResponse) {
      response = (HttpResponse) msg;
    } else if (msg instanceof LastHttpContent) {
      long ended = System.currentTimeMillis();

      HttpResponseStatus actualStatus = response.getStatus();

      HttpResponseStatus expectedStatus = expectedResponse.getStatus();
      boolean success = expectedStatus.equals(actualStatus);

      Joiner.MapJoiner joiner = Joiner.on('\n').withKeyValueSeparator(": ");
      String requestHeaders = joiner.join(request.headers());
      String responseHeaders = joiner.join(response.headers());

      String label = getLabel(request, expectedResponse, response, (ReplayLastHttpContent) msg);

      // FIXME this doesn't work if the request doesn't receive a response, need to find a way of handling that
      resultListener.result(success, label, started, ended, request.getMethod().name(), request.getUri(),
          request.getProtocolVersion().text(), requestHeaders, actualStatus.code(),
          actualStatus.reasonPhrase(), responseHeaders, bytesRead.get());
    }
  }

  static String getLabel(ReplayHttpRequest request, HttpResponse expectedResponse, HttpResponse response,
                                 ReplayLastHttpContent content) {
    StringBuilder label = new StringBuilder();
    String headerLabel = request.headers().get(TRANSACTION_LABEL_HEADER);
    if (null == headerLabel) {
      label.append(getResourceType(response));
      label.append(": ");
      boolean hasDocument = content.getDocument().isPresent();
      if (hasDocument) {
        Document document = content.getDocument().get();
        String title = document.title();
        label.append(title.isEmpty() ? UNTITLED_PAGE_LABEL : title);
        label.append(" - ");
      }
      HttpMethod method = request.getMethod();
      if (HttpMethod.GET != method) {
        label.append(method.name());
        label.append(" ");
      }
      label.append(request.getUri());
      HttpResponseStatus status = expectedResponse.getStatus();
      if (HttpResponseStatus.OK != status) {
        label.append(" : ");
        label.append(status);
      }
    } else {
      label.append(headerLabel);
    }
    return label.toString();
  }

  static String getResourceType(HttpResponse response) {
    String contentType = Strings.nullToEmpty(response.headers().get(HttpHeaders.Names.CONTENT_TYPE));
    if (!contentType.isEmpty()) {
      MediaType type = MediaType.parse(contentType);
      if (type.is(MediaType.HTML_UTF_8)) {
        return "Page";
      } else if (type.is(MediaType.ANY_AUDIO_TYPE)) {
        return "Audio";
      } else if (type.is(MediaType.ANY_IMAGE_TYPE)) {
        return "Image";
      } else if (type.is(MediaType.ANY_VIDEO_TYPE)) {
        return "Video";
      } else if (type.is(MediaType.JAVASCRIPT_UTF_8) || type.is(MediaType.TEXT_JAVASCRIPT_UTF_8)) {
        return "Script";
      } else if (type.is(MediaType.CSS_UTF_8)) {
        return "Stylesheet";
      } else if (type.is(MediaType.create("application", "application/x-font-woff"))) {
        return "Font";
      }
    }
    return "Other";
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    checkNotNull(ctx);
    checkNotNull(cause);
    LOG.error("Caught exception in handler", cause);
    ctx.close();
  }

  private class BytesReadHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      try {
        if (msg instanceof ByteBuf) {
          bytesRead.addAndGet(((ByteBuf) msg).readableBytes());
        }
      } finally {
        super.channelRead(ctx, msg);
      }
    }
  }
}
