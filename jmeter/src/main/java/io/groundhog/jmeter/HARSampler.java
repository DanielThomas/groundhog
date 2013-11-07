package io.groundhog.jmeter;

import io.groundhog.replay.ReplayClient;
import io.groundhog.replay.ResultListener;

import com.google.common.base.Throwables;
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
 * @since 0.1
 */
public class HARSampler extends AbstractSampler implements TestBean, ThreadListener, ResultListener {
  private static final Logger LOG = LoggingManager.getLoggerForClass();

  private final BlockingQueue<SampleResult> resultQueue = new LinkedBlockingQueue<>();

  private String filename;
  private ReplayClient client;

  @Override
  public SampleResult sample(Entry entry) {
    if (null == client) {
      LOG.info("Creating replay client for filename " + filename);
      client = new ReplayClient(new File(filename), this);
      client.startAsync();
      client.awaitRunning();
    }

    while (true) {
      if (client.isRunning()) {
        try {
          SampleResult result = resultQueue.poll(5000, TimeUnit.MILLISECONDS);
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

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = checkNotNull(filename);
  }

  @Override
  public void threadStarted() {
  }

  @Override
  public void threadFinished() {
    if (client.isRunning()) {
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
    // FIXME get the host and scheme into the listener
    try {
      result.setURL(new URL("http://somehost/" + checkNotNull(location)));
    } catch (MalformedURLException e) {
      throw Throwables.propagate(e);
    }
    checkNotNull(httpVersion);
    result.setRequestHeaders(checkNotNull(requestHeaders));
    result.setResponseCode(String.valueOf(code));
    result.setResponseMessage(checkNotNull(reasonPhrase));
    result.setResponseHeaders(checkNotNull(responseHeaders));
    result.setBytes(bytesRead);
    resultQueue.add(result);
  }
}
