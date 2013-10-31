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

import com.google.common.base.Joiner;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.concurrent.EventExecutorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Danny Thomas
 * @since 0.1
 */
public class ReplayHandler extends ChannelDuplexHandler {
  private static final Logger LOG = LoggerFactory.getLogger(ReplayHandler.class);
  public static final String TRANSACTION_LABEL_HEADER = "X-Transaction-Label";

  private final ResultListener resultListener;

  private ReplayHttpRequest request;
  private HttpResponse response;
  private HttpResponse expectedResponse;
  private long started;
  private final AtomicInteger bytesRead = new AtomicInteger();

  public ReplayHandler(ChannelPipeline pipeline, ResultListener resultListener) throws Exception {
    checkNotNull(pipeline);
    this.resultListener = checkNotNull(resultListener);
    initPipeline(pipeline, false);
  }

  private void initPipeline(ChannelPipeline p, boolean ssl) throws Exception {
    p.addLast("bytesRead", new BytesReadHandler());

    if (ssl) {
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

      String label = request.headers().get(TRANSACTION_LABEL_HEADER);
      label = null == label ? request.getUri() : label;
      HttpResponseStatus expectedStatus = expectedResponse.getStatus();
      boolean success = expectedStatus.equals(actualStatus);

      Joiner.MapJoiner joiner = Joiner.on('\n').withKeyValueSeparator(": ");
      String requestHeaders = joiner.join(request.headers());
      String responseHeaders = joiner.join(response.headers());

      // FIXME this doesn't work if the request doesn't receive a response, need to find a way of handling that
      resultListener.result(success, label, started, ended, request.getMethod().name(), request.getUri(),
          request.getProtocolVersion().text(), requestHeaders, actualStatus.code(),
          actualStatus.reasonPhrase(), responseHeaders, bytesRead.get());
    }
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
