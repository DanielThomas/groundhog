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

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.servlet.ServletContextHandler
import spock.lang.Specification

/**
 * Integration tests for {@link ServletCaptureListener}.
 */
class ServletCaptureListenerIntegTest extends Specification {
  def 'initalising context with Jetty server succeeds'() {
    def listener = new ServletCaptureListener()
    def server = new Server()
    def handlerCollection = new HandlerCollection()
    def contextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS)
    contextHandler.setContextPath('/')
    contextHandler.addEventListener(listener)
    handlerCollection.addHandler(contextHandler)
    server.setHandler(handlerCollection)

    when:
    server.start()

    then:
    noExceptionThrown()

    cleanup:
    server.stop()
  }
}
