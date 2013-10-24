package io.groundhog.replay;

import io.groundhog.base.HttpArchive;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import io.netty.handler.codec.http.Cookie;

import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * TODO look into the monitor waits for this class. Might need to swap out the coarse synchronisation for concurrent collections
 *
 * @author Danny Thomas
 * @since 0.1
 */
public class UserAgent {

  private final Set<Cookie> cookies = Sets.newLinkedHashSet();
  private final Map<String, HttpArchive.Param> postParamOverrides = Maps.newHashMap();

  private final Optional<HashCode> key;

  public UserAgent() {
    this.key = Optional.absent();
  }

  public UserAgent(HashCode key) {
    this.key = Optional.of(key);
  }

  public void setCookies(Collection<Cookie> cookies) {
    checkPersistent();
    synchronized (cookies) {
      for (Cookie cookie : cookies) {
        this.cookies.remove(cookie);
        this.cookies.add(cookie);
      }
    }
  }

  public Set<Cookie> getCookiesForUri(String uri) {
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

  public void setOverridePostValues(Collection<HttpArchive.Param> params) {
    checkPersistent();
    synchronized (postParamOverrides) {
      for (HttpArchive.Param param : params) {
        postParamOverrides.put(param.getName(), param);
      }
    }
  }

  public Optional<HttpArchive.Param> getOverrideParam(String name) {
    synchronized (postParamOverrides) {
      return Optional.fromNullable(postParamOverrides.get(name));
    }
  }

  private void checkPersistent() {
    checkState(isPersistent(), "This UA is not persistent");
  }

  public boolean isPersistent() {
    return key.isPresent();
  }

  private static class CookiePathPredicate implements Predicate<Cookie> {
    private final String uri;

    public CookiePathPredicate(String uri) {
      this.uri = checkNotNull(uri);
    }

    @Override
    public boolean apply(Cookie cookie) {
      String path = cookie.getPath();
      return path == null || path.isEmpty() || uri.startsWith(path);
    }
  }

  public Optional<HashCode> getKey() {
    return key;
  }

}
