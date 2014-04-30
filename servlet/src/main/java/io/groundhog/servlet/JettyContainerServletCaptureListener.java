/*
 * Copyright 2010 the original author or authors.
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

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;

import javax.inject.Inject;
import javax.servlet.ServletContextEvent;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A {@link ContainerServletCaptureListener} supporting the Jetty container.
 *
 * @author Danny Thomas
 * @since 1.0
 */
public final class JettyContainerServletCaptureListener implements ContainerServletCaptureListener {
  private final CaptureHandler captureHandler;

  @Inject
  JettyContainerServletCaptureListener(CaptureHandler captureHandler) {
    this.captureHandler = checkNotNull(captureHandler);
  }

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    checkNotNull(sce);
    ContextHandler.Context source = (ContextHandler.Context) sce.getSource();
    Server server = source.getContextHandler().getServer();
    Handler handler = server.getHandler();
    if (handler instanceof HandlerCollection) {
      HandlerCollection handlerCollection = (HandlerCollection) handler;
      handlerCollection.addHandler(captureHandler);
    } else {
      throw new IllegalStateException("This application requires a HandlerCollection, found " + handler);
    }
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {
    checkNotNull(sce);
  }
}
