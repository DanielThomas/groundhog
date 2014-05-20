package io.groundhog.replay;

import com.google.common.base.Optional;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public class LoggingResultListener extends AbstractReplayResultListener implements ReplayResultListener {
  private static final Logger LOG = LoggerFactory.getLogger(LoggingResultListener.class);

  @Override
  public void success(HttpRequest request, HttpResponse response, HttpResponse expectedResponse, int bytesRead,
                      long start, long end, Optional<Document> document) {
    String label = getLabel(request, response, expectedResponse, document);
    LOG.info("SUCCESS: {}: \"{} {} {}\" {} {} {}",
        checkNotNull(label),
        checkNotNull(request.getMethod()),
        checkNotNull(request.getUri()),
        checkNotNull(request.getProtocolVersion()),
        response.getStatus().code(),
        end - start,
        bytesRead);
  }

  @Override
  public void failure(String failureReason, HttpRequest request, HttpResponse response, HttpResponse expectedResponse, int bytesRead, long start, long end, Optional<Document> document) {
    String label = getLabel(request, response, expectedResponse, document);
    LOG.info("FAILURE: {}: {}: \"{} {} {}\" {} {} {}",
        checkNotNull(failureReason),
        checkNotNull(label),
        checkNotNull(request.getMethod()),
        checkNotNull(request.getUri()),
        checkNotNull(request.getProtocolVersion()),
        response.getStatus().code(),
        end - start,
        bytesRead);
  }

  @Override
  public void failure(HttpRequest request, Optional<Throwable> cause) {
    String label = getLabel(request);
    if (cause.isPresent()) {
      Optional<String> errorMessage = getErrorMessage(cause.get());
      if (errorMessage.isPresent()) {
        LOG.error("FAILURE: {}: {}", label, errorMessage.get());
      } else {
        LOG.error("FAILURE: {}", label, cause.get());
      }
    } else {
      LOG.error("FAILURE: {}", label);
    }
  }
}

