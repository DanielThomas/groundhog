package io.groundhog.replay;

import java.io.IOException;

/**
 * A reader that provides {@link UserAgentRequest}s.
 *
 * @author Michael Olague
 * @since 1.0
 */
public interface RequestReader {
  /**
   * Read a request.
   *
   * @return a {@link UserAgentRequest}, or if the request is the last available {@link LastUserAgentRequest}.
   * @throws IOException if an unexpected IO error occurs
   */
  UserAgentRequest readRequest() throws IOException;

  /**
   * Convenience method to determine if the request is a {@link LastUserAgentRequest}.
   *
   * @param request the request
   * @return if the request is a {@link LastUserAgentRequest}
   */
  boolean isLastRequest(UserAgentRequest request);
}
