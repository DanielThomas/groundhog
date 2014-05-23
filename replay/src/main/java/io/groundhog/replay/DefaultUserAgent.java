package io.groundhog.replay;

import io.groundhog.har.HttpArchive;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.assistedinject.Assisted;
import io.netty.handler.codec.http.Cookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Objects.ToStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public final class DefaultUserAgent implements UserAgent {
  private final Set<Cookie> cookies;
  private final Map<String, HttpArchive.Param> postParamOverrides;
  private final Semaphore block;

  private final HashCode key;

  // Default access because there seems to be an intermittent problem with assisted injection and access control
  Logger log = LoggerFactory.getLogger(DefaultUserAgent.class);

  @Inject
  DefaultUserAgent(@Assisted HashCode key) {
    this.key = checkNotNull(key);
    cookies = Sets.newLinkedHashSet();
    postParamOverrides = Maps.newHashMap();
    block = new Semaphore(1, true);
  }

  @Override
  public HashCode getKey() {
    return key;
  }

  @Override
  public String toString() {
    ToStringHelper helper = Objects.toStringHelper(this);
    helper.add("persistent", isPersistent());
    helper.add("key", key);
    return helper.toString();
  }

  @Override
  public void tryBlock(long timeout) {
    try {
      if (!block.tryAcquire(timeout, TimeUnit.MILLISECONDS)) {
        block.release();
        int queueLength = block.getQueueLength();
        throw new UncheckedTimeoutException("Timeout during block acquire for " + key + ", queue length " + queueLength);
      }
    } catch (InterruptedException e) {
      throw Throwables.propagate(e);
    }
  }

  @Override
  public void releaseBlock() {
    block.release();
  }

  @Override
  public void setCookies(Collection<Cookie> cookies) {
    checkNotNull(cookies);
    synchronized (this.cookies) {
      for (Cookie cookie : cookies) {
        this.cookies.remove(cookie);
        this.cookies.add(cookie);
      }
    }
  }

  @Override
  public Set<Cookie> getCookiesForUri(String uri) {
    checkNotNull(uri);
    synchronized (cookies) {
      CookiePathPredicate predicate = new CookiePathPredicate(uri);
      // Temporary list because FluentIterable.toSortedSet considers compare == 0 to be duplicates
      List<Cookie> sortedCookies = FluentIterable.from(cookies).filter(predicate).toSortedList(COOKIE_PATH_COMPARATOR);
      return ImmutableSet.copyOf(sortedCookies);
    }
  }

  /**
   * Sort cookies in descending order by path length.
   */
  private static final Comparator<Cookie> COOKIE_PATH_COMPARATOR = new Comparator<Cookie>() {
    @Override
    public int compare(Cookie o1, Cookie o2) {
      int path1len = o1.getPath() == null ? 0 : o1.getPath().length();
      int path2len = o2.getPath() == null ? 0 : o2.getPath().length();
      return Integer.compare(path2len, path1len);
    }
  };

  @Override
  public void setOverridePostValues(Collection<HttpArchive.Param> params) {
    checkNotNull(params);
    synchronized (postParamOverrides) {
      for (HttpArchive.Param param : params) {
        postParamOverrides.put(param.getName(), param);
      }
    }
  }

  @Override
  public Optional<HttpArchive.Param> getOverrideParam(String name) {
    checkNotNull(name);
    synchronized (postParamOverrides) {
      return Optional.fromNullable(postParamOverrides.get(name));
    }
  }

  @Override
  public boolean isPersistent() {
    return true;
  }

  private static class CookiePathPredicate implements Predicate<Cookie> {
    private final String uri;

    public CookiePathPredicate(String uri) {
      this.uri = checkNotNull(uri);
    }

    @Override
    public boolean apply(@Nullable Cookie cookie) {
      String path = cookie != null ? cookie.getPath() : null;
      return path == null || path.isEmpty() || uri.startsWith(path);
    }
  }
}
