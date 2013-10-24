/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   whttp://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.groundhog.replay;

import com.google.common.base.Objects;
import com.google.common.primitives.Longs;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Danny Thomas
 * @since 0.1
 */
public class DelayedReplayRequest implements Delayed {
  // TODO make this configurable. This avoids requests at the very start of replay from piling on top on one another
  private static final int WARMUP_TIME = 250;

  private final UserAgentRequest request;
  private final long startedDateTime;
  private final long timeReplayStartedNanos;
  private final long firstRequestTime;

  public DelayedReplayRequest(UserAgentRequest request, long startedDateTime, long timeReplayStartedNanos, long firstRequestTime) {
    this.request = checkNotNull(request);
    this.startedDateTime = startedDateTime + WARMUP_TIME;
    this.timeReplayStartedNanos = timeReplayStartedNanos;
    this.firstRequestTime = firstRequestTime;
  }

  @Override
  public String toString() {
    Objects.ToStringHelper helper = Objects.toStringHelper(this);
    helper.add("startedDateTime", startedDateTime);
    helper.add("timeReplayStartedNanos", timeReplayStartedNanos);
    helper.add("firstRequestTime", firstRequestTime);
    helper.add("delay", getDelay(TimeUnit.MILLISECONDS));
    helper.add("request", request);
    return helper.toString();
  }

  @Override
  public long getDelay(TimeUnit unit) {
    long offset = getExpectedTime();
    long duration = System.nanoTime() - timeReplayStartedNanos;
    long delay = TimeUnit.MILLISECONDS.toNanos(offset) - duration;
    return unit.convert(delay, TimeUnit.NANOSECONDS);
  }

  @Override
  public int compareTo(Delayed o) {
    return Longs.compare(getDelay(TimeUnit.NANOSECONDS), o.getDelay(TimeUnit.NANOSECONDS));
  }

  public long getExpectedTime() {
    return firstRequestTime == 0 ? firstRequestTime : startedDateTime - firstRequestTime;
  }

  public UserAgentRequest getRequest() {
    return request;
  }

}
