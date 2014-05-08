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

package io.groundhog.replay

import com.google.common.base.Charsets
import com.google.common.hash.Hashing
import io.netty.handler.codec.http.Cookie
import io.netty.handler.codec.http.DefaultCookie
import spock.lang.Specification

/**
 * Tests for {@link UserAgent}.
 *
 * @author Danny Thomas
 * @since 1.0
 */
class UserAgentTest extends Specification {
  def 'returns cookies, ordered and filtered correctly'() {
    UserAgent userAgent = new UserAgent(Hashing.goodFastHash(32).hashString("test", Charsets.UTF_8))
    Cookie noPath1 = getCookie("cookie1")
    Cookie noPath1DifferentValue = new DefaultCookie("cookie1", "newvalue")
    Cookie noPath2 = getCookie("cookie2")
    Cookie noPath3 = getCookie("cookie3")

    Cookie rootPath = getCookie("JSESSIONID")
    rootPath.setPath("/")
    Cookie matchingPath = getCookie("JSESSIONID")
    matchingPath.setPath("/test")
    Cookie nonmatchingPath = getCookie("JSESSIONID")
    nonmatchingPath.setPath("/anotherpath")

    when:
    userAgent.setCookies([noPath3, noPath1, noPath1DifferentValue, rootPath, matchingPath, noPath2, nonmatchingPath])

    then:
    Set<Cookie> cookiesForUri = userAgent.getCookiesForUri("/test/path")
    cookiesForUri.size() == 5
    cookiesForUri.toList() == [matchingPath, rootPath, noPath3, noPath1DifferentValue, noPath2]
  }

  def getCookie(name) {
    new DefaultCookie(name, "value")
  }
}
