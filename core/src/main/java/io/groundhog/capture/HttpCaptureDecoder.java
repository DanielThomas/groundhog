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

package io.groundhog.capture;

import io.netty.handler.codec.http.HttpObject;

/**
 * A stateful decoder, capable of capturing HTTP state from Netty {@link HttpObject}s and converting into a
 * {@code CaptureRequest}.
 * <p/>
 * This is coupled to the Netty HTTP codecs, and has the same expectations of the order of {@link HttpObject} classes,
 * in particular {@link io.netty.handler.codec.http.LastHttpContent} to signal the end of HTTP content.
 * <p/>
 * {@link #destroy()} must be called to clean up temporary resources.
 *
 * @author Danny Thomas
 * @since 1.0
 */
public interface HttpCaptureDecoder {
  /**
   * Process request objects.
   *
   * @param httpObject the {@link HttpObject} to be processed
   */
  void request(HttpObject httpObject);

  /**
   * Processes response objects.
   *
   * @param httpObject the {@link HttpObject} to be processed
   */
  void response(HttpObject httpObject);

  /**
   * Complete the capture.
   */
  CaptureRequest complete();

  /**
   * Destroy the decoder, cleaning up any temporary resources.
   */
  void destroy();
}
