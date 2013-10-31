package io.groundhog.replay;

import java.net.URL;

/**
 * @author Danny Thomas
 * @since 0.1
 */
public interface ResultListener {

  void result(boolean successful, String label, long start, long end, String method, String location, String httpVersion,
                     String requestHeaders, int code, String reasonPhrase, String responseHeaders, int bytesRead);

}
