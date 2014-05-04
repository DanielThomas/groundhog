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

package io.groundhog.servlet

import io.groundhog.capture.CaptureWriter
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.client.api.ContentResponse
import org.eclipse.jetty.server.Server
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

/**
 * Integration tests for {@link CaptureServletRequestHttpWrapper}.
 */
class CaptureHandlerIntegTest extends Specification {
  @Shared Server server
  @Shared HttpClient client
  @Shared CaptureWriter writer

  def setupSpec() {
    server = new Server(18080);
    writer = Mock(CaptureWriter.class)
    server.setHandler(new CaptureHandler(writer))
    server.start();

    client = new HttpClient();
    client.start();
  }

  def cleanupSpec() {
    server.stop()
    client.stop()
  }

  @Ignore // Appears to be failing due to a threading/concurrency issue, more work needed
  def 'GET for root document returns 404, and captured request matches'() {
    when:
    ContentResponse response = client.GET('http://localhost:18080/')

    then:
    response.getStatus() == 404
    1 * writer.writeAsync(_)
  }
}
