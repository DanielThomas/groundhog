package io.groundhog.replay;

import com.google.common.base.Throwables;
import com.google.common.net.HostAndPort;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * @author Michael Olague
 * @since 1.0
 */
public class ReplayModule extends AbstractModule {
  @Override
  protected void configure() {
    String propsLocation = "conf/properties.config";
    Properties props = new Properties();
    try {
      props.load(new FileInputStream(propsLocation));
    } catch (IOException io) {
      Throwables.propagate(io);
    }
    int port = Integer.parseInt(props.getProperty("replayPort"));
    String hostname = props.getProperty("replayHostName");
    File recordingFile = new File(props.getProperty("replayRecordingFile"));

    bind(ReplayClient.class);
    bind(ReplayResultListener.class).to(LoggingResultListener.class);
    bind(HostAndPort.class).annotatedWith(Names.named("target")).toInstance(HostAndPort.fromParts(hostname, port));
    bind(boolean.class).annotatedWith(Names.named("usessl")).toInstance(false);
    bind(RequestDispatcher.class).to(DefaultRequestDispatcher.class);
    bind(RequestReader.class).to(DefaultRequestReader.class);
    bind(File.class).toInstance(recordingFile);
  }
}
