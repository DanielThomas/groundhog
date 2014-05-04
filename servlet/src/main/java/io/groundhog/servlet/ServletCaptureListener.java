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

import com.google.inject.Guice;
import com.google.inject.Injector;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A {@link ServletContextListener} that configures a supported servlet capture method at runtime, depending on the
 * container.
 *
 * @author Danny Thomas
 * @since 1.0
 */
public final class ServletCaptureListener implements ServletContextListener {
  private ServletContextListener listener;

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    checkNotNull(sce);
    Injector injector = Guice.createInjector(new ServletModule(sce.getServletContext()));
    listener = injector.getInstance(ContainerServletCaptureListener.class);
    listener.contextInitialized(sce);
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {
    checkNotNull(sce);
    listener.contextDestroyed(sce);
  }
}

