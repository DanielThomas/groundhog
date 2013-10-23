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

import io.groundhog.base.HttpArchive;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.*;
import com.google.common.hash.HashCode;
import io.netty.handler.codec.http.Cookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * FIXME feels like there are all kinds of hidden locking issues and deadlocks here. Need to completely rethink this later
 * TODO this is static until I have dependency injection working
 *
 * @author Danny Thomas
 * @since 0.1
 */
public class UserAgentStorage {
  private static final Logger LOG = LoggerFactory.getLogger(UserAgentStorage.class);

  private static final int LOCK_TIMEOUT = 1000;

  private static final LoadingCache<HashCode, UserAgentData> DATA;
  private static final LoadingCache<HashCode, ReadWriteLock> LOCKS;

  static {
    CacheLoader<HashCode, UserAgentData> dataLoader = new CacheLoader<HashCode, UserAgentData>() {
      @Override
      public UserAgentData load(HashCode userAgent) throws Exception {
        return new UserAgentData();
      }
    };

    CacheLoader<HashCode, ReadWriteLock> lockLoader = new CacheLoader<HashCode, ReadWriteLock>() {
      @Override
      public ReadWriteLock load(HashCode userAgent) throws Exception {
        return new ReentrantReadWriteLock();
      }
    };

    DATA = CacheBuilder.newBuilder().build(dataLoader);
    LOCKS = CacheBuilder.newBuilder().build(lockLoader);
  }

  private static final UserAgentDataOperation<String, Set<Cookie>, Cookie> COOKIES = new UserAgentDataOperation<String, Set<Cookie>, Cookie>() {
    @Override
    public Set<Cookie> readOperation(UserAgentData data, Optional<String> value) {
      String uri = value.get();
      return getCookiesForUri(uri, data.getCookies());
    }

    @Override
    public void writeOperation(UserAgentData data, Cookie value) {
      Set<Cookie> cookies = data.getCookies();
      if (cookies.contains(value)) {
        cookies.remove(value);
      }
      cookies.add(value);
    }
  };

  private static final UserAgentDataOperation<String, Optional<HttpArchive.Param>, HttpArchive.Param> PARAM_OVERRIDES
      = new UserAgentDataOperation<String, Optional<HttpArchive.Param>, HttpArchive.Param>() {
    @Override
    public Optional<HttpArchive.Param> readOperation(UserAgentData data, Optional<String> value) {
      Map<String, HttpArchive.Param> postParameters = data.getPostParamOverrides();
      return Optional.fromNullable(postParameters.get(value.get()));
    }

    @Override
    public void writeOperation(UserAgentData data, HttpArchive.Param value) {
      Map<String, HttpArchive.Param> postParameters = data.getPostParamOverrides();
      postParameters.put(value.getName(), value);
    }
  };

  private static final Comparator<Cookie> COOKIE_PATH_COMPARATOR = new Comparator<Cookie>() {
    @Override
    public int compare(Cookie o1, Cookie o2) {
      int path1len = o1.getPath() == null ? 0 : o1.getPath().length();
      int path2len = o2.getPath() == null ? 0 : o2.getPath().length();
      return Integer.compare(path2len, path1len);
    }
  };

  public static void addSynonym(HashCode userAgent, HashCode synonym) {
    LOG.debug("Moving agent {} to {}", userAgent, synonym);
    Iterable<ReadWriteLock> locks = Arrays.asList(getLock(userAgent), getLock(synonym));
    try {
      for (ReadWriteLock lock : locks) {
        lock.writeLock().lock();
      }
      DATA.put(synonym, DATA.get(userAgent));
      DATA.invalidate(userAgent);
    } catch (ExecutionException e) {
      throw Throwables.propagate(e);
    } finally {
      for (ReadWriteLock lock : locks) {
        lock.writeLock().unlock();
      }
    }
  }

  private static ReadWriteLock getLock(HashCode userAgent) {
    try {
      return LOCKS.get(userAgent);
    } catch (ExecutionException e) {
      throw Throwables.propagate(e);
    }
  }

  public static void setCookie(HashCode userAgent, Cookie cookie) {
    COOKIES.write(userAgent, cookie);
  }

  public static Set<Cookie> getCookiesForUri(HashCode userAgent, String uri) {
    return COOKIES.read(userAgent, Optional.of(uri));
  }

  static Set<Cookie> getCookiesForUri(String uri, Set<Cookie> cookies) {
    CookiePathPredicate predicate = new CookiePathPredicate(uri);
    List<Cookie> sortedCookies = FluentIterable.from(Lists.newArrayList(cookies)).filter(predicate).toSortedList(COOKIE_PATH_COMPARATOR);
    return ImmutableSet.copyOf(sortedCookies);
  }

  public static void setOverridePostValue(HashCode userAgent, HttpArchive.Param param) {
    PARAM_OVERRIDES.write(userAgent, param);
  }

  public static Optional<HttpArchive.Param> getOverrideParam(HashCode userAgent, String name) {
    return PARAM_OVERRIDES.read(userAgent, Optional.of(name));
  }

  public static void block(HashCode hashCode) {
    getLock(hashCode).writeLock().lock();
  }

  public static void unblock(HashCode hashCode) {
    getLock(hashCode).writeLock().unlock();
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

  private static class UserAgentData {
    private final Set<Cookie> cookies = Sets.newLinkedHashSet();
    private final Map<String, HttpArchive.Param> postParamOverrides = Maps.newHashMap();

    public Set<Cookie> getCookies() {
      return cookies;
    }

    public Map<String, HttpArchive.Param> getPostParamOverrides() {
      return postParamOverrides;
    }
  }

  /**
   * Wraps operations against {@link io.groundhog.replay.UserAgentStorage.UserAgentData}, encapsulating locking and other logic.
   * </p>
   * TODO the triple barrelled generic declaration is a little clumsy, maybe not a good idea...
   *
   * @param <V> the type a value to be passed to read operations
   * @param <R> the type of object expected from a read operation
   * @param <W> the type of object provided to a write operation
   */
  private abstract static class UserAgentDataOperation<V, R, W> {

    public R read(HashCode userAgent, Optional<V> value) {
      try {
        lock(userAgent, false);
        return readOperation(getData(userAgent), value);
      } finally {
        unlock(userAgent, false);
      }
    }

    public void write(HashCode userAgent, W value) {
      try {
        lock(userAgent, true);
        writeOperation(getData(userAgent), value);
      } finally {
        unlock(userAgent, true);
      }
    }

    private void lock(HashCode hash, boolean write) {
      ReadWriteLock lock = getLock(hash);
      try {
        if (write) {
          lock.writeLock().tryLock(LOCK_TIMEOUT, TimeUnit.MILLISECONDS);
        } else {
          lock.readLock().tryLock(LOCK_TIMEOUT, TimeUnit.MILLISECONDS);
        }
      } catch (InterruptedException e) {
        throw Throwables.propagate(e);
      }
    }

    private void unlock(HashCode hash, boolean write) {
      ReadWriteLock lock = getLock(hash);
      if (write) {
        lock.writeLock().unlock();
      } else {
        lock.readLock().unlock();
      }
    }

    private UserAgentData getData(HashCode hash) {
      try {
        return DATA.get(hash);
      } catch (ExecutionException e) {
        throw Throwables.propagate(e);
      }
    }

    public abstract R readOperation(UserAgentData data, Optional<V> value);

    public abstract void writeOperation(UserAgentData data, W value);

  }

}
