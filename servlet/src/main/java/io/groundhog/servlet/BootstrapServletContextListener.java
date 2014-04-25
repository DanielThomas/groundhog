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

import io.groundhog.base.RequestWriter;

import com.google.common.base.Throwables;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.catalina.*;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.File;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public class BootstrapServletContextListener implements ServletContextListener {
  @Override
  public void contextInitialized(ServletContextEvent sce) {
    Injector injector = Guice.createInjector(new ServletModule());
    // Replace static construction with injection, singletons, etc later
    Pipeline pipeline = getHost().getPipeline();
    File recordingFile = new File("/tmp/recording.har");
    RequestWriter requestWriter = new RequestWriter(recordingFile, true, false, false);
    requestWriter.startAsync();
    pipeline.addValve(new CaptureValve(requestWriter));
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {
    Pipeline pipeline = getHost().getPipeline();
    for (Valve valve : pipeline.getValves()) {
      if (valve instanceof CaptureValve) {
        pipeline.removeValve(valve);
      }
    }
  }

  // For now, we'll couple this to Catalina and assume the host name
  public Host getHost() {
    Engine engine = (Engine) findService("Catalina").getContainer();
    return (Host) engine.findChild("localhost");
  }

  private static Service findService(String serviceName) {
    checkNotNull(serviceName);

    // FIXME For some reason, the MBeanServer findMBeanServer permissions aren't sticking in my bb-manifest, we'll need to solve that
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

