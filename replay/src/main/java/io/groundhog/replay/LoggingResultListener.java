package io.groundhog.replay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Danny Thomas
 * @since 0.1
 */
public class LoggingResultListener implements ResultListener {
  private static final Logger LOG = LoggerFactory.getLogger(LoggingResultListener.class);

  @Override
  public void result(boolean successful, String label, long elapsed, String method, String location, String httpVersion,
                     String requestHeaders, int code, String reasonPhrase, String responseHeaders, int bytesRead) {
    checkNotNull(httpVersion);
    checkNotNull(requestHeaders);
    checkNotNull(reasonPhrase);
    checkNotNull(responseHeaders);
    LOG.debug("{}: {}: \"{} {} {}\" {} {} {}",
        successful ? "SUCCESS" : "FAILURE",
        checkNotNull(label),
        checkNotNull(method),
        checkNotNull(location),
        checkNotNull(httpVersion),
        code,
        elapsed,
        bytesRead);
  }

}
