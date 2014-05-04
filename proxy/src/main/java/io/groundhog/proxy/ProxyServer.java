/*
 * Copyright 2013-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   whttp://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.groundhog.proxy;

import io.groundhog.har.HarFileCaptureWriter;
import io.groundhog.capture.CaptureWriter;

import com.google.common.util.concurrent.AbstractIdleService;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public final class ProxyServer extends AbstractIdleService {
  private static final Logger LOG = LoggerFactory.getLogger(ProxyServer.class);

  private CaptureWriter captureWriter;

  @Override
  protected void startUp() throws Exception {
    int port = 3128;

    File recordingFile = new File("out/recording.har");
    captureWriter = new HarFileCaptureWriter(recordingFile, true, false, false);
    File uploadLocation = new File(recordingFile.getParentFile(), "uploads");
    CaptureFilterSource filtersSource = new CaptureFilterSource(captureWriter, uploadLocation);

    captureWriter.startAsync();

    LOG.info("Starting recording server on port " + port);
    DefaultHttpProxyServer.bootstrap().withPort(port).withFiltersSource(filtersSource).start();
  }

  @Override
  protected void shutDown() throws Exception {
    captureWriter.stopAsync();
    captureWriter.awaitTerminated();
  }
}
