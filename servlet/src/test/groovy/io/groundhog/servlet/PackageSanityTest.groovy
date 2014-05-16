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

import com.google.common.testing.AbstractPackageSanityTests
import io.groundhog.capture.CaptureController
import io.groundhog.capture.CaptureWriter
import io.groundhog.capture.DefaultCaptureController
import io.groundhog.har.HarFileCaptureWriter
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import org.eclipse.jetty.server.Request

import javax.servlet.ServletInputStream

/**
 * Package sanity tests for {@link io.groundhog.servlet}.
 *
 * @author Danny Thomas
 * @since 1.0
 */
class PackageSanityTest extends AbstractPackageSanityTests {
  PackageSanityTest() {
    def writer = new HarFileCaptureWriter(new File(''), false, false, false)
    setDefault(CaptureWriter.class, writer)
    setDefault(HttpVersion.class, HttpVersion.HTTP_1_1)
    setDefault(HttpResponseStatus.class, HttpResponseStatus.OK)
    setDefault(BufferedReader.class, new BufferedReader(new StringReader('')))
    setDefault(ServletInputStream.class, new ServletInputStream() {
      @Override
      int read() throws IOException {
        return 0
      }
    })
    setDefault(Request.class, new Request(null, null))
    setDefault(CaptureHandler.class, new CaptureHandler(writer))
    def controller = new DefaultCaptureController(writer)
    setDefault(CaptureValve.class, new CaptureValve(writer, controller))
    setDefault(CaptureController.class, controller)
  }
}
