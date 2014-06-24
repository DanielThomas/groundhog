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

package io.groundhog.har

import com.google.common.io.Files
import io.groundhog.capture.DefaultCaptureRequest
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Tests for {@link HarFileCaptureWriter}.
 */
class HarFileCaptureWriterTest extends Specification {
  @Shared File tempDir

  def setupSpec() {
    tempDir = Files.createTempDir()
  }

  def cleanupSpec() {
    tempDir.deleteDir()
  }

  def 'queue is drained on shutdown'() {
    given:
    def queue = Mock(BlockingQueue)
    queue.isEmpty() >>> [false, false, true]
    queue.poll(_, _) >> { args ->
      TimeUnit unit = args[1]
      long millis = unit.toMillis(args[0])
      Thread.sleep(millis)
      null
    }
    def writer = new HarFileCaptureWriter(tempDir, true, false, false, false, queue)
    writer.startAsync()
    writer.awaitRunning()

    when:
    writer.stopAsync()
    writer.awaitTerminated(5, TimeUnit.SECONDS)

    then:
    notThrown(TimeoutException)
  }

  def 'URL has correct external form'() {
    given:
    def request = Mock(HttpRequest)
    request.getUri() >> "/"
    def headers = new DefaultHttpHeaders()
    headers.add(HttpHeaders.Names.HOST, "localhost")
    request.headers() >> headers

    def response = Mock(HttpResponse)
    def captureRequest = new DefaultCaptureRequest(System.currentTimeMillis(), request, response)

    expect:
    HarFileCaptureWriter.getUrl(captureRequest) == "http://localhost/"
  }

  def 'URL includes query string'() {
    given:
    def request = Mock(HttpRequest)
    request.getUri() >> "/request?key=value"
    def headers = new DefaultHttpHeaders()
    headers.add(HttpHeaders.Names.HOST, "localhost")
    request.headers() >> headers

    def response = Mock(HttpResponse)
    def captureRequest = new DefaultCaptureRequest(System.currentTimeMillis(), request, response)

    expect:
    HarFileCaptureWriter.getUrl(captureRequest) == "http://localhost/request?key=value"
  }

  def 'URL contains port when not default'() {
    given:
    def request = Mock(HttpRequest)
    request.getUri() >> "/"
    def headers = new DefaultHttpHeaders()
    headers.add(HttpHeaders.Names.HOST, "localhost:8080")
    request.headers() >> headers

    def response = Mock(HttpResponse)
    def captureRequest = new DefaultCaptureRequest(System.currentTimeMillis(), request, response)

    expect:
    HarFileCaptureWriter.getUrl(captureRequest) == "http://localhost:8080/"
  }

  def 'URL has correct external form, when host scheme included in URI'() {
    given:
    def request = Mock(HttpRequest)
    request.getUri() >> "http://localhost/"
    def response = Mock(HttpResponse)
    def captureRequest = new DefaultCaptureRequest(System.currentTimeMillis(), request, response)

    expect:
    HarFileCaptureWriter.getUrl(captureRequest) == "http://localhost/"
  }

  def 'URL infers protocol scheme for default HTTPS port'() {
    given:
    def request = Mock(HttpRequest)
    request.getUri() >> "/"
    def headers = new DefaultHttpHeaders()
    headers.add(HttpHeaders.Names.HOST, "localhost:443")
    request.headers() >> headers
    def response = Mock(HttpResponse)
    def captureRequest = new DefaultCaptureRequest(System.currentTimeMillis(), request, response)

    expect:
    HarFileCaptureWriter.getUrl(captureRequest) == "https://localhost/"
  }

  def 'URL has protocol scheme and port unchanged if specified'() {
    given:
    def request = Mock(HttpRequest)
    request.getUri() >> "https://localhost:8443/"
    def response = Mock(HttpResponse)
    def captureRequest = new DefaultCaptureRequest(System.currentTimeMillis(), request, response)

    expect:
    HarFileCaptureWriter.getUrl(captureRequest) == "https://localhost:8443/"
  }
}
