package io.groundhog.proxy;

import io.groundhog.base.URIScheme;
import io.groundhog.capture.CaptureController;
import io.groundhog.capture.CaptureWriter;
import io.groundhog.capture.DefaultCaptureController;
import io.groundhog.har.HarFileCaptureWriter;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.net.HostAndPort;
import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.name.Names;
import io.netty.channel.ChannelHandler;
import org.littleshoot.proxy.HttpFiltersSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class ProxyModule extends AbstractModule {
  private static final Logger LOG = LoggerFactory.getLogger(ProxyModule.class);

  private static final int PARENT_LIMIT = 2;
  private static final String PROPERTIES_FILENAME = "conf/config.properties";

  @Override
  protected void configure() {
    Properties properties = new Properties();
    try {
      // Depending on the type of distribution, the conf directory can be one or two directories up
      File parentDir = new File(System.getProperty("user.dir"));
      Optional<File> configFile = findConfigInParent(parentDir, PARENT_LIMIT);
      File propertiesFile;
      if (!configFile.isPresent()) {
        propertiesFile = new File("src/dist", PROPERTIES_FILENAME); // Gradle application run task
        if (!propertiesFile.exists()) {
          propertiesFile = new File("proxy/src/dist", PROPERTIES_FILENAME); // IntelliJ launch
        }
        LOG.warn("Could not locate {} in current or parent directories up to {} levels deep. Falling back to developer config {}",
            PROPERTIES_FILENAME, PARENT_LIMIT, propertiesFile);
      } else {
        propertiesFile = configFile.get();
      }
      properties.load(new FileInputStream(propertiesFile));
    } catch (IOException e) {
      LOG.error("Failed to load properties file");
      Throwables.propagate(e);
    }

    for (String addressType : Arrays.asList("target", "listen")) {
      String host = properties.getProperty(addressType + ".address");
      for (URIScheme uriScheme : URIScheme.values()) {
        String prefix = addressType + "." + uriScheme.scheme();
        int port = Integer.valueOf(properties.getProperty(prefix + "_port"));
        bind(HostAndPort.class).annotatedWith(Names.named(prefix)).toInstance(HostAndPort.fromParts(host, port));
      }
    }

    File outputLocation = new File(properties.getProperty("output.location"));
    checkArgument(outputLocation.isDirectory(), "output.location must be a directory and must exist");

    String outputCompression = properties.getProperty("output.compression");
    CaptureWriter captureWriter = new HarFileCaptureWriter(outputLocation, true, false, false, "gzip".equals(outputCompression));
    bind(CaptureWriter.class).toInstance(captureWriter);
    bind(CaptureController.class).to(DefaultCaptureController.class);
    install(new FactoryModuleBuilder().implement(HttpFiltersSource.class, CaptureFilterSource.class).build(CaptureFilterSourceFactory.class));
  }

  private Optional<File> findConfigInParent(File parentDir, int limit) {
    checkNotNull(parentDir);
    checkArgument(limit > 0, "Limit must be greater than zero");
    File currentDir = parentDir;
    for (int i = 0; i <= limit; i++) {
      if (null == currentDir) {
        break;
      }
      File propertiesFile = new File(currentDir, PROPERTIES_FILENAME);
      if (propertiesFile.exists()) {
        return Optional.of(propertiesFile);
      }
      currentDir = currentDir.getParentFile();
    }
    return Optional.absent();
  }
}
