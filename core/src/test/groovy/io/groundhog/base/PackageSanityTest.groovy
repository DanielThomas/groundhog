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

package io.groundhog.base;

import com.google.common.testing.AbstractPackageSanityTests
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpVersion;

/**
 * Package sanity tests for {@link io.groundhog.base}.
 */
class PackageSanityTest extends AbstractPackageSanityTests {
  PackageSanityTest() {
    def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/")
    request.headers().add(HttpHeaders.Names.HOST, "localhost")
    setDefault(HttpRequest, request)
  }
}
