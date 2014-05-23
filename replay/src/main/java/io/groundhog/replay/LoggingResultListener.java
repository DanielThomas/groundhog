package io.groundhog.replay;

import com.google.common.base.Optional;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public final class LoggingResultListener extends AbstractReplayResultListener implements ReplayResultListener {
  private Logger log = LoggerFactory.getLogger(LoggingResultListener.class);

  @Override
  public void success(HttpRequest request, HttpResponse response, UserAgent userAgent,
                      int bytesRead, long start, long end, Optional<Document> document) {
    checkNotNull(document);
    log.info("{} {} {} {}",
        getRequestLabel(request),
        response.getStatus().code(),
        getUserAgentKey(userAgent),
        end - start,
        bytesRead);
  }

  @Override
  public void failure(String failureReason, HttpRequest request, HttpResponse response, UserAgent userAgent,
                      int bytesRead, long start, long end, Optional<Document> document) {
    checkNotNull(document);
    log.error("{}. {} {} {} {}",
        checkNotNull(failureReason),
        getRequestLabel(request),
        response.getStatus().code(),
        getUserAgentKey(userAgent),
        end - start,
        bytesRead);
  }

  @Override
  public void failure(HttpRequest request, Optional<UserAgent> userAgent, Optional<Throwable> cause) {
    String label = getRequestLabel(request);
    String userAgentHash = getUserAgentKey(userAgent);
    if (cause.isPresent()) {
      Optional<String> errorMessage = getMessageForKnownException(cause.get());
      if (errorMessage.isPresent()) {
        log.error("{}: {} {}", label, userAgentHash, errorMessage.get());
      } else {
        log.error("{} {}", label, userAgentHash, cause.get());
      }
    } else {
      log.error("{} {}", label, userAgentHash);
    }
  }

  private String getRequestLabel(HttpRequest request) {
    return MessageFormatter.arrayFormat("\"{} {} {}\"", new Object[]{
        checkNotNull(request.getMethod()), checkNotNull(request.getUri()), checkNotNull(request.getProtocolVersion())}).getMessage();
  }
}

