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
import org.eclipse.jetty.server.HandlerContainer
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.server.handler.HandlerCollection
import spock.lang.Specification

/**
 * Integration tests for {@link JettyContainerServletCaptureListener}.
 */
class JettyContainerServletCaptureListenerIntegTest extends Specification {
  def 'listener configures handler'() {
    setup:
    def writer = Mock(CaptureWriter)
    def captureHandler = new CaptureHandler(writer)
    def server = new Server(18080);
    def handler = new ContextHandler()
    def listener = new JettyContainerServletCaptureListener(captureHandler)
    handler.addEventListener(listener)
    def handlerContainer = new HandlerCollection()
    handlerContainer.addHandler(handler)
    server.setHandler(handlerContainer)

    when:
    server.start()

    then:
    ((HandlerContainer) server.getHandler()).getChildHandlers().contains(captureHandler)

    cleanup:
    server.stop()
  }
}
