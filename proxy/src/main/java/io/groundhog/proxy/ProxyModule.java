package io.groundhog.proxy;

import io.groundhog.capture.CaptureWriter;
import io.groundhog.har.HarFileCaptureWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class ProxyModule extends AbstractModule {
  private static final Logger LOG = LoggerFactory.getLogger(ProxyModule.class);
  
  private final String propertiesFilename = "../conf/config.properties";
  private final String outputFilename = "out/recording.har";
  private final String uploadDirectoryName = "uploads";

  @Override
  protected void configure() {
    Properties properties = new Properties();
    try {
      properties.load(new FileInputStream(propertiesFilename));
      Names.bindProperties(binder(), properties);
      
      File outputFile = new File(outputFilename);
      File uploadLocation = new File(outputFile.getParentFile(), uploadDirectoryName);
      CaptureWriter captureWriter = new HarFileCaptureWriter(outputFile, true, false, false);
      
      bind(CaptureWriter.class).toInstance(captureWriter);
      bind(File.class).annotatedWith(Names.named("UploadLocation")).toInstance(uploadLocation);
      
    } catch (FileNotFoundException e) {
      LOG.error("Failed to find properties file " + propertiesFilename);
    } catch (IOException e) {
      LOG.error("Failed to load properties file: " + e);
    }
  }
}
