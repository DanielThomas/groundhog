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

import io.groundhog.har.HttpArchive;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public class UserAgentHandler extends ChannelDuplexHandler {
  private static final Logger LOG = LoggerFactory.getLogger(UserAgentHandler.class);

  /**
   * POST fields to be overridden with values parsed from received documents.
   * <p/>
   * TODO configure this externally
   */
  private static final Set<String> OVERRIDE_POST_FIELDS = Sets.newHashSet("blackboard.platform.security.NonceUtil.nonce");

  private ReplayHttpRequest request;
  private UserAgent userAgent;
  private HttpResponse response;
  private ByteBuf content;
  private Document document;

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
    if (msg instanceof ReplayHttpRequest) {
      request = (ReplayHttpRequest) msg;
      userAgent = request.getUserAgent();
      if (userAgent.isPersistent() && request.isBlocking()) {
        userAgent.tryBlock(250);
      }
      setRequestCookies();
    }

    super.write(ctx, msg, promise);
  }

  public void setRequestCookies() {
    Set<Cookie> cookies = userAgent.getCookiesForUri(request.getUri());
    String encodedCookies = ClientCookieEncoder.encode(cookies);
    if (!encodedCookies.isEmpty()) {
      request.headers().add(HttpHeaders.Names.COOKIE, encodedCookies);
    }
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof HttpResponse) {
      response = (HttpResponse) msg;
      if (userAgent.isPersistent()) {
        parseCookies();
        if (request.isBlocking()) {
          userAgent.releaseBlock();
        }
      }
    } else if (msg instanceof HttpContent) {
      parseDocument((HttpContent) msg);
      if (msg instanceof LastHttpContent) {
        if (userAgent.isPersistent() && null != document) {
          scrapeFormFields();
        }
        super.channelRead(ctx, new ReplayLastHttpContent((LastHttpContent) msg, Optional.fromNullable(document)));
        return;
      }
    }

    super.channelRead(ctx, msg);
  }

  private void parseCookies() {
    HttpHeaders headers = response.headers();
    FluentIterable<Cookie> cookies = FluentIterable.from(headers.getAll(HttpHeaders.Names.SET_COOKIE)).transform(UserAgentRequest.HEADER_TO_COOKIE);
    userAgent.setCookies(cookies.toList());
  }

  private void parseDocument(HttpContent httpContent) {
    if (userAgent.isPersistent() && HttpMethod.GET == request.getMethod() && hasHtmlContentType(response)) {
      ByteBuf byteBuf = httpContent.content().duplicate();
      if (null == content) {
        content = Unpooled.buffer();
      }
      content.writeBytes(byteBuf);

      if (httpContent instanceof LastHttpContent) {
        // FIXME use correct encoding. See org.jsoup.helper.DataUtil.parseByteData()
        String decodedContent = content.toString(Charsets.UTF_8);
        // TODO look into incrementally parsing the document to avoid needing to hold onto the content
        document = Jsoup.parse(decodedContent);
        content.release();
      }
    }
  }

  private boolean hasHtmlContentType(HttpResponse response) {
    String contentType = response.headers().get(HttpHeaders.Names.CONTENT_TYPE);
    return contentType != null && contentType.startsWith("text/html");
  }

  private void scrapeFormFields() {
    Elements elements = document.select("form input[type=hidden]");
    List<HttpArchive.Param> params = Lists.newArrayList();
    for (Element element : elements) {
      Attributes attributes = element.attributes();
      if (OVERRIDE_POST_FIELDS.contains(attributes.get("name"))) {
        HttpArchive.Param param = new HttpArchive.Param(attributes.get("name"), attributes.get("value"));
        params.add(param);
      }
    }
    userAgent.setOverridePostValues(params);
  }
}
