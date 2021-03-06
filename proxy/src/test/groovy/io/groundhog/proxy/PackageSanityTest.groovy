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

package io.groundhog.proxy

import com.google.common.base.Predicate
import com.google.common.net.HostAndPort
import com.google.common.testing.AbstractPackageSanityTests
import io.groundhog.base.URIScheme
import io.groundhog.capture.CaptureController
import io.groundhog.capture.DefaultCaptureController
import io.groundhog.har.HarFileCaptureWriter
import io.groundhog.capture.CaptureWriter

import javax.annotation.Nullable

/**
 * Package sanity tests for {@link io.groundhog.replay}.
 *
 * @author Danny Thomas
 * @since 1.0
 */
class PackageSanityTest extends AbstractPackageSanityTests {
  PackageSanityTest() {
    ignoreClasses(new Predicate<Class<?>>() {
      @Override
      boolean apply(@Nullable Class<?> input) {
        Proxy.class == input
      }
    })
    CaptureWriter writer = new HarFileCaptureWriter(new File(''), false, false, false, false)
    CaptureController controller = new DefaultCaptureController(writer)
    CaptureFilterSource filterSource = new CaptureFilterSource(URIScheme.HTTP, writer, controller)
    CaptureFilterSourceFactory filterSourceFactory = new CaptureFilterSourceFactory() {
      @Override
      CaptureFilterSource create(URIScheme scheme) {
        return filterSource
      }
    }
    setDefault(CaptureWriter.class, writer)
    setDefault(CaptureFilterSource.class, filterSource)
    setDefault(ProxyServer.class, new ProxyServer(writer, filterSourceFactory, HostAndPort.fromParts('localhost', 8080), HostAndPort.fromParts('localhost', 8080)))
    setDefault(CaptureController, new DefaultCaptureController(writer))
    setDefault(HostAndPort, HostAndPort.fromParts('localhost', 8080))
  }
}
