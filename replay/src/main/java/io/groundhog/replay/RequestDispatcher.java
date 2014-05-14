package io.groundhog.replay;

import com.google.common.util.concurrent.Service;

/**
 * @author Michael Olague
 * @since 1.0
 */
public interface RequestDispatcher extends Service {

  Service clearQueue();

  void queue(DelayedReplayRequest request) throws InterruptedException;

}
