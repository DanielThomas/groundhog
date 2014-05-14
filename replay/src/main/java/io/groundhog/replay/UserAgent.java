package io.groundhog.replay;


import com.google.common.base.Optional;
import com.google.common.hash.HashCode;
import io.groundhog.har.HttpArchive;
import io.netty.handler.codec.http.Cookie;

import java.util.Collection;
import java.util.Set;

/**
 * TODO look into the monitor waits for this class. Might need to swap out the coarse synchronisation for concurrent collections
 *
 * @author Danny Thomas
 * @since 1.0
 */
public interface UserAgent {

  void tryBlock(long timeout);

  void releaseBlock();

  void setCookies(Collection<Cookie> cookies);

  void setOverridePostValues(Collection<HttpArchive.Param> params);

  Set<Cookie> getCookiesForUri(String uri);

  Optional<HttpArchive.Param> getOverrideParam(String name);

  boolean isPersistent();

  public Optional<HashCode> getKey();
}
