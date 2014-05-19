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

package io.groundhog.servlet;

import io.groundhog.capture.CaptureWriter;
import io.groundhog.har.HarFileCaptureWriter;

import com.google.inject.AbstractModule;

import javax.servlet.ServletContext;

import java.io.File;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Guice binding module for the servlet-based capture.
 *
 * @author Danny Thomas
 * @since 1.0
 */
public final class ServletModule extends AbstractModule {
  private final ServletContext servletContext;

  public ServletModule(ServletContext servletContext) {
    this.servletContext = checkNotNull(servletContext);
  }

  @Override
  protected void configure() {
    String serverInfo = servletContext.getServerInfo();
    Class<? extends ContainerServletCaptureListener> listenerClass;
    if (serverInfo.startsWith("Apache Tomcat/")) {
      listenerClass = TomcatContainerServletCaptureListener.class;
    } else if (serverInfo.startsWith("jetty/")) { // since Jetty 6.1.11 - see JETTY-623
      listenerClass = JettyContainerServletCaptureListener.class;
    } else {
      throw new IllegalArgumentException("Server " + serverInfo + "is not supported");
    }

    bind(ContainerServletCaptureListener.class).to(listenerClass);

    File outputLocation = new File("/tmp");
    CaptureWriter captureWriter = new HarFileCaptureWriter(outputLocation, true, false, false, false);
    bind(CaptureWriter.class).toInstance(captureWriter);
  }
}
