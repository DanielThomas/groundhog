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

package io.groundhog.jmeter;

import io.groundhog.base.URIScheme;
import io.groundhog.replay.*;

import com.google.common.net.HostAndPort;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import io.netty.bootstrap.Bootstrap;
import org.apache.jmeter.samplers.SampleResult;

import java.io.File;
import java.util.Queue;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public final class JMeterModule extends AbstractReplayModule {
  private final File recordingFile;
  private final Queue<SampleResult> results;
  private final URIScheme scheme;
  private final HostAndPort targetHostAndPort;

  public JMeterModule(File recordingFile, Queue<SampleResult> results, URIScheme scheme, HostAndPort targetHostAndPort) {
    this.recordingFile = checkNotNull(recordingFile);
    this.results = checkNotNull(results);
    this.scheme = checkNotNull(scheme);
    this.targetHostAndPort = checkNotNull(targetHostAndPort);
  }

  @Override
  protected void configureReplay() {
    bind(File.class).toInstance(recordingFile);
    bind(HostAndPort.class).annotatedWith(Names.named("target")).toInstance(targetHostAndPort);
    bind(boolean.class).annotatedWith(Names.named("usessl")).toInstance(URIScheme.HTTPS == scheme);
    bind(ReplayResultListener.class).toInstance(new HarReplayResultListener(results, scheme, targetHostAndPort));
  }
}
