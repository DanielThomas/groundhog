package io.groundhog.jmeter;

import io.groundhog.base.URIScheme;
import io.groundhog.replay.ReplayClient;
import io.groundhog.replay.ResultListener;

import com.google.common.base.Throwables;
import com.google.common.net.HostAndPort;
import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.testelement.ThreadListener;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Logger;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public class HarReplaySampler extends AbstractSampler implements TestBean, ThreadListener, ResultListener {
  private static final Logger LOG = LoggingManager.getLoggerForClass();

  private final BlockingQueue<SampleResult> results = new LinkedBlockingQueue<>();

  private String filename;
  private URIScheme scheme;
  private String host;
  private int port;
  private ReplayClient client;

  @Override
  public SampleResult sample(Entry entry) {
    if (null == client) {
      LOG.info("Creating replay client for filename " + filename);
      client = new ReplayClient(new File(filename), HostAndPort.fromParts(host, port), URIScheme.HTTPS == scheme, this);
      client.startAsync();
      client.awaitRunning();
    }

    while (true) {
      if (client.isRunning() || !results.isEmpty()) {
        try {
          SampleResult result = results.poll(500, TimeUnit.MILLISECONDS);
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
  public void threadStarted() {
  }

  @Override
  public void threadFinished() {
    if (null != client && client.isRunning()) {
      client.stopAsync();
      client.awaitTerminated();
    }
  }

  @Override
  public void result(boolean successful, String label, long start, long end, String method, String location, String httpVersion,
                     String requestHeaders, int code, String reasonPhrase, String responseHeaders, int bytesRead) {
    SampleResult result = SampleResult.createTestSample(start, end);
    result.setSuccessful(successful);
    result.setSampleLabel(checkNotNull(label));
    checkNotNull(method);
    checkNotNull(location);
    try {
      result.setURL(new URL(scheme.getScheme(), host, port, location));
    } catch (MalformedURLException e) {
      throw Throwables.propagate(e);
    }
    checkNotNull(httpVersion);
    result.setRequestHeaders(checkNotNull(requestHeaders));
    result.setResponseCode(String.valueOf(code));
    result.setResponseMessage(checkNotNull(reasonPhrase));
    result.setResponseHeaders(checkNotNull(responseHeaders));
    result.setBytes(bytesRead);
    results.add(result);
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
}
