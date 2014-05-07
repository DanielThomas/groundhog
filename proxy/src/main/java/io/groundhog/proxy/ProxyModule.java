package io.groundhog.proxy;

import io.groundhog.capture.CaptureWriter;
import io.groundhog.har.HarFileCaptureWriter;

import com.google.common.base.Throwables;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ProxyModule extends AbstractModule {
  private static final Logger LOG = LoggerFactory.getLogger(ProxyModule.class);

  @Override
  protected void configure() {
    Properties properties = new Properties();
    try {
      String propertiesFilename = "conf/config.properties";
      properties.load(new FileInputStream(propertiesFilename));
    } catch (IOException e) {
      LOG.error("Failed to load properties file");
      Throwables.propagate(e);
    }

    Names.bindProperties(binder(), properties);

    String outputFilename = "out/recording.har";
    File outputFile = new File(outputFilename);
    String uploadDirectoryName = "uploads";
    File uploadLocation = new File(outputFile.getParentFile(), uploadDirectoryName);
    bind(File.class).annotatedWith(Names.named("UploadLocation")).toInstance(uploadLocation);

    CaptureWriter captureWriter = new HarFileCaptureWriter(outputFile, true, false, false);
    bind(CaptureWriter.class).toInstance(captureWriter);
  }
}
