package io.groundhog.replay;

/**
 * @author Danny Thomas
 * @since 0.1
 */
public interface ResultListener {

  void result(boolean successful, String label, long elapsed, int code, String reasonPhrase, int bytesRead);

}
