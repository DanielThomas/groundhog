/*
 * Copyright 2013-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.groundhog.replay;

import io.groundhog.har.HttpArchive;

import com.google.common.base.Optional;
import io.netty.handler.codec.http.Cookie;

import java.util.Collection;
import java.util.Set;

/**
 * A {@link UserAgent} that does not support persistence of it's state.
 *
 * @author Danny Thomas
 * @since 1.0
 */
public final class NonPersistentUserAgent implements UserAgent {
  @Override
  public void tryBlock(long timeout) {
    throw unsupportedOperation();
  }

  @Override
  public void releaseBlock() {
    throw unsupportedOperation();
  }

  @Override
  public void setCookies(Collection<Cookie> cookies) {
    throw unsupportedOperation();
  }

  @Override
  public void setOverridePostValues(Collection<HttpArchive.Param> params) {
    throw unsupportedOperation();
  }

  @Override
  public Set<Cookie> getCookiesForUri(String uri) {
    throw unsupportedOperation();
  }

  @Override
  public Optional<HttpArchive.Param> getOverrideParam(String name) {
    throw unsupportedOperation();
  }

  @Override
  public boolean isPersistent() {
    return false;
  }

  private UnsupportedOperationException unsupportedOperation() {
    return new UnsupportedOperationException("This is a non-persistent UA. This operation is not supported");
  }
}
