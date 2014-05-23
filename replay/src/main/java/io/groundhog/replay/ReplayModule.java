package io.groundhog.replay;

import com.google.common.net.HostAndPort;
import com.google.inject.name.Names;

import java.io.File;

/**
 * @author Michael Olague
 * @author Danny Thomas
 * @since 1.0
 */
public final class ReplayModule extends AbstractReplayModule {
  @Override
  protected void configureReplay() {
    File captureFile = new File("/tmp/Dannys-MacBook-Pro.local-1400874087859/capture.har.gz");
    String hostname = "localhost";
    int port = 8080;
    bind(File.class).toInstance(captureFile);
    bind(HostAndPort.class).annotatedWith(Names.named("target")).toInstance(HostAndPort.fromParts(hostname, port));
    bind(boolean.class).annotatedWith(Names.named("usessl")).toInstance(false);
    bind(ReplayResultListener.class).to(LoggingResultListener.class);
  }
}
