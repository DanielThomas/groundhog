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

package io.groundhog.jmeter

import com.google.common.net.HostAndPort;
import com.google.common.testing.AbstractPackageSanityTests;

/**
 * Package sanity tests for {@link io.groundhog.jmeter}.
 *
 * @author Danny Thomas
 * @since 0.1
 */
class PackageSanityTest extends AbstractPackageSanityTests {
  PackageSanityTest() {
    setDefault(HostAndPort, HostAndPort.fromHost("localhost"))
  }
}
