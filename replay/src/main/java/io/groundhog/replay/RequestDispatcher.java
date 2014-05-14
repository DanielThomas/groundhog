package io.groundhog.replay;

import com.google.common.util.concurrent.Service;

/**
 * @author Michael Olague
 * @since 1.0
 */
public interface RequestDispatcher extends Service {
  void queue(DelayedUserAgentRequest request) throws InterruptedException;

  Service clearQueue();
}
