/*
 * Copyright 2010 the original author or authors.
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

import com.google.common.base.Predicate
import com.google.common.testing.AbstractPackageSanityTests
import io.groundhog.base.HttpArchive
import io.netty.bootstrap.Bootstrap
import io.netty.handler.codec.http.Cookie
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion

/**
 * Package sanity tests for {@link io.groundhog.replay}.
 *
 * @author Danny Thomas
 * @since 0.1
 */
class PackageSanityTest extends AbstractPackageSanityTests {

  PackageSanityTest() {
    ignoreClasses(new Predicate<Class<?>>() {
      @Override
      boolean apply(Class<?> input) {
        Replay.class == input
      }
    })

    def postData = new HttpArchive.PostData("name", "value")
    setDefault(UserAgentRequest.class, new UserAgentRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/",
        postData, HttpHeaders.EMPTY_HEADERS, Collections.<Cookie> emptySet(), new File(""), 0l))
    setDefault(RequestDispatcher, new RequestDispatcher(new Bootstrap(), "localhost", 80))
    setDefault(HttpMethod, HttpMethod.GET)
    setDefault(HttpHeaders, HttpHeaders.EMPTY_HEADERS)
    setDefault(HttpVersion, HttpVersion.HTTP_1_1)
    setDefault(HttpArchive.PostData, postData)
  }

}
