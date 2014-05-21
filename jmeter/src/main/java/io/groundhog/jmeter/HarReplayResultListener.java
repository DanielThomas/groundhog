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

package io.groundhog.jmeter;

import io.groundhog.base.URIScheme;
import io.groundhog.replay.AbstractReplayResultListener;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.net.HostAndPort;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.jmeter.samplers.SampleResult;
import org.jsoup.nodes.Document;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Queue;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public class HarReplayResultListener extends AbstractReplayResultListener {
  private final Queue<SampleResult> resultQueue;
  private final URIScheme scheme;
  private final HostAndPort hostAndPort;

  public HarReplayResultListener(Queue<SampleResult> resultQueue, URIScheme scheme, HostAndPort hostAndPort) {
    this.resultQueue = checkNotNull(resultQueue);
    this.scheme = checkNotNull(scheme);
    this.hostAndPort = checkNotNull(hostAndPort);
  }

  @Override
  public void success(HttpRequest request, HttpResponse response, HttpResponse expectedResponse, int bytesRead,
                      long start, long end, Optional<Document> document) {
    queueResult(request, response, expectedResponse, bytesRead, start, end, document, Optional.<String>absent());
  }

  @Override
  public void failure(String failureReason, HttpRequest request, HttpResponse response, HttpResponse expectedResponse, int bytesRead, long start, long end, Optional<Document> document) {
    queueResult(request, response, expectedResponse, bytesRead, start, end, document, Optional.of(failureReason));
  }

  @Override
  public void failure(HttpRequest request, Optional<Throwable> cause) {
    SampleResult result = SampleResult.createTestSample(0);
    result.setSuccessful(false);
    result.setSampleLabel(getLabel(request));
    result.setResponseCode("A failure occurred");
    if (cause.isPresent()) {
      Optional<String> knownErrorMessage = getMessageForKnownException(cause.get());
      if (knownErrorMessage.isPresent()) {
        result.setResponseMessage(knownErrorMessage.get());
      } else {
        StringWriter stackTrace = new StringWriter();
        //noinspection ThrowableResultOfMethodCallIgnored
        cause.get().printStackTrace(new PrintWriter(stackTrace));
        result.setResponseMessage(stackTrace.toString());
      }
    }
  }

  private void queueResult(HttpRequest request, HttpResponse response, HttpResponse expectedResponse, int bytesRead,
                           long start, long end, Optional<Document> document, Optional<String> failureReason) {
    SampleResult result = SampleResult.createTestSample(start, end);
    result.setSamplerData(request.toString());
    boolean hasFailure = failureReason.isPresent();
    result.setSuccessful(!hasFailure);
    if (hasFailure) {
      result.setResponseCode("A failure occurred");
      result.setResponseMessage(failureReason.get());
    } else {
      HttpResponseStatus status = response.getStatus();
      result.setResponseCode(String.valueOf(status.code()));
      result.setResponseMessage(status.reasonPhrase());
    }
    result.setSampleLabel(getLabel(request, response, expectedResponse, document));
    if (document.isPresent()) {
      Document responseData = document.get();
      result.setResponseData(responseData.outerHtml(), responseData.outputSettings().charset().name());
    } else {
      result.setSamplerData("No data received");
    }
    try {
      result.setURL(new URL(scheme.getScheme(), hostAndPort.getHostText(), hostAndPort.getPortOrDefault(scheme.getDefaultPort()), request.getUri()));
    } catch (MalformedURLException e) {
      throw Throwables.propagate(e);
    }

    Joiner.MapJoiner joiner = Joiner.on('\n').withKeyValueSeparator(": ");
    String requestHeaders = joiner.join(request.headers());
    String responseHeaders = joiner.join(response.headers());

    result.setRequestHeaders(checkNotNull(requestHeaders));
    result.setResponseHeaders(checkNotNull(responseHeaders));
    result.setBytes(bytesRead);
    resultQueue.add(result);
  }
}
