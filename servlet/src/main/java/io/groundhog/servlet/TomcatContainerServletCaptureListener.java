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

import io.groundhog.capture.CaptureWriter;
import io.groundhog.har.HarFileCaptureWriter;

import com.google.common.base.Throwables;
import org.apache.catalina.*;

import javax.inject.Inject;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import javax.servlet.ServletContextEvent;
import java.io.File;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A {@link ContainerServletCaptureListener} supporting the Tomcat container.
 *
 * @author Danny Thomas
 * @since 1.0
 */
public final class TomcatContainerServletCaptureListener implements ContainerServletCaptureListener {
  private final CaptureValve valve;

  @Inject
  TomcatContainerServletCaptureListener(CaptureValve valve) {
    this.valve = checkNotNull(valve);
  }

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    checkNotNull(sce);
    Pipeline pipeline = getHost().getPipeline();
    pipeline.addValve(valve);
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {
    checkNotNull(sce);
    Pipeline pipeline = getHost().getPipeline();
    for (Valve valve : pipeline.getValves()) {
      if (valve == this.valve) {
        pipeline.removeValve(valve);
        return;
      }
    }
  }

  private Host getHost() {
    Engine engine = (Engine) findService("Catalina").getContainer();
    return (Host) engine.findChild("localhost");
  }

  private static Service findService(String serviceName) {
    checkNotNull(serviceName);
    MBeanServer mBeanServer = MBeanServerFactory.findMBeanServer(null).get(0);
    ObjectName name;
    Server server;
    try {
      name = new ObjectName("Catalina", "type", "Server");
      server = (Server) mBeanServer.getAttribute(name, "managedResource");
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }

    for (Service service : server.findServices()) {
      if (service.getName().equals(serviceName)) {
        return service;
      }
    }

    throw new IllegalStateException(String.format("Service '%s' was not found", serviceName));
  }
}
