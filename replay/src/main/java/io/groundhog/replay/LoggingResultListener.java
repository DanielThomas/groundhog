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
  public void result(boolean successful, String label, long elapsed, int code, String reasonPhrase, int bytesRead) {
    LOG.info("{}: success: {}, elapsed: {}, code: {}, reasonPhrase: {}, bytesRead: {}",
        successful ? "SUCCESS" : "FAILURE",
        checkNotNull(label),
        elapsed,
        code,
        checkNotNull(reasonPhrase),
        bytesRead);
  }

}
