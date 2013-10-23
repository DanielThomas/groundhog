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

import com.google.common.collect.Sets;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.DefaultCookie;
import org.junit.Test;

import java.util.Arrays;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

/**
 * Tests for {@link UserAgentStorage}.
 *
 * @author Danny Thomas
 * @since 0.1
 */
public class UserAgentStorageTest {

  @Test
  public void getCookiesForUrl_FilteredAndOrderedCorrectly() {
    Cookie noPath1 = getCookie("cookie1");
    Cookie noPath2 = getCookie("cookie2");
    Cookie noPath3 = getCookie("cookie3");

    Cookie rootPath = getCookie("JSESSIONID");
    rootPath.setPath("/");
    Cookie matchingPath = getCookie("JSESSIONID");
    matchingPath.setPath("/test");
    Cookie nonmatchingPath = getCookie("JSESSIONID");
    nonmatchingPath.setPath("/anotherpath");


    Set<Cookie> cookies = Sets.newLinkedHashSet(Arrays.asList(noPath3, noPath1, rootPath, matchingPath, noPath2, nonmatchingPath));
    Set<Cookie> cookiesForUri = UserAgentStorage.getCookiesForUri("/test/path", cookies);

    assertThat(cookiesForUri.size(), equalTo(5));
    assertThat(cookiesForUri, contains(matchingPath, rootPath, noPath3, noPath1, noPath2));
  }

  private Cookie getCookie(String s) {
    return new DefaultCookie(s, "value");
  }

}
