package io.groundhog.jmeter;

import io.groundhog.base.URIScheme;
import io.groundhog.replay.ReplayClient;
import io.groundhog.replay.RequestDispatcher;

import com.google.common.net.HostAndPort;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.Interruptible;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.testelement.ThreadListener;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Logger;

import java.io.File;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public class HarReplaySampler extends AbstractSampler implements TestBean, ThreadListener, Interruptible {
  private static final Logger LOG = LoggingManager.getLoggerForClass();

  private final BlockingQueue<SampleResult> results = new LinkedBlockingQueue<>();

  private String filename;
  private URIScheme scheme;
  private String host;
  private int port;
  private int connectionTimeout;
  private int socketReadTimeout;
  private ReplayClient client;

  @Override
  public SampleResult sample(Entry entry) {
    if (null == client) {
      try {
        HostAndPort hostAndPort = HostAndPort.fromParts(host, port);
        Module jmeterModule = new JMeterModule(new File(filename), results, scheme, hostAndPort, connectionTimeout, socketReadTimeout);
        Injector injector = Guice.createInjector(new JMeterSlf4jModule(), jmeterModule);
        client = injector.getInstance(ReplayClient.class);

        LOG.info("Starting replay for " + filename + " using " + scheme.scheme() + " against " + hostAndPort);
        client.startAsync();
        client.awaitRunning();
      } catch (Exception e) {
        LOG.error("Fatal error while initialising replay. Stopping current thread", e);
        JMeterContextService.getContext().getThread().stop();
      }
    }

    while (true) {
      if (client.isRunning() || !results.isEmpty()) {
        try {
          SampleResult result = results.poll(RequestDispatcher.SKEW_THRESHOLD_MILLIS, TimeUnit.MILLISECONDS);
          if (null != result) {
            return result;
          }
        } catch (InterruptedException e) {
          break;
        }
      } else {
        LOG.info("Replay client has stopped. Stopping current thread");
        JMeterContextService.getContext().getThread().stop();
        break;
      }
    }
    return null;
  }

  @Override
  public boolean interrupt() {
    threadFinished();
    return true;
  }

  @Override
  public void threadStarted() {
  }

  @Override
  public void threadFinished() {
    if (null != client && client.isRunning()) {
      client.stopAsync();
      client.awaitTerminated();
    }
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = checkNotNull(filename);
  }

  public int getScheme() {
    return scheme.ordinal();
  }

  public void setScheme(int scheme) {
    this.scheme = URIScheme.values()[scheme];
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = checkNotNull(host);
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public void setConnectionTimeout (int connectionTimeout) { this.connectionTimeout = connectionTimeout; }

  public int getConnectionTimeout() { return this.connectionTimeout; }

  public void setSocketReadTimeout(int socketReadTimeout) { this.socketReadTimeout = socketReadTimeout; }

  public int getSocketReadTimeout() { return this.socketReadTimeout; }
}
