package io.groundhog.replay;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public interface ResultListener {
  void result(boolean successful, String label, long start, long end, String method, String location, String httpVersion,
              String requestHeaders, int code, String reasonPhrase, String responseHeaders, int bytesRead);
}
