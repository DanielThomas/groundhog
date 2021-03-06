/*
 * Copyright 2013-2014 the original author or authors.
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

package io.groundhog.capture;

import com.google.common.util.concurrent.Service;
import io.netty.handler.codec.http.multipart.FileUpload;

import java.io.IOException;

/**
 * A {@link Service} providing asynchronous writing of {@link CaptureRequest}s.
 *
 * @author Danny Thomas
 * @since 1.0
 */
public interface CaptureWriter extends Service {
  /**
   * Write a request asynchronously.
   *
   * @param captureRequest the captured request to be written
   */
  public void writeAsync(CaptureRequest captureRequest);

  /**
   * Write a file upload. The write operation may block.
   *
   * @param fileUpload the {@link io.netty.handler.codec.http.multipart.FileUpload}
   * @param startedDateTime the time in milliseconds of the request that this upload is for
   */
  public void writeUpload(FileUpload fileUpload, long startedDateTime) throws IOException;
}
