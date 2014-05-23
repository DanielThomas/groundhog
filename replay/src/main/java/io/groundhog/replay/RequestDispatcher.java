package io.groundhog.replay;

import com.google.common.util.concurrent.Service;

/**
 * @author Michael Olague
 * @since 1.0
 */
public interface RequestDispatcher extends Service {
  /**
   * The number of milliseconds that the dispatcher can skew from real-time, before warnings are logged.
   */
  public static final int SKEW_THRESHOLD_MILLIS = 250;

  void queue(DelayedUserAgentRequest request) throws InterruptedException;

  Service clearQueue();
}
