package io.groundhog.replay;

import com.google.common.base.Optional;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import org.jsoup.nodes.Document;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public interface ReplayResultListener {
  void success(HttpRequest request, HttpResponse response, HttpResponse expectedResponse, int bytesRead,
                      long start, long end, Optional<Document> document);

  void failure(String failureReason, HttpRequest request, HttpResponse response, HttpResponse expectedResponse, int bytesRead,
               long start, long end, Optional<Document> document);

  void failure(HttpRequest request, Optional<Throwable> cause);
}
