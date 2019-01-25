/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.basic.disni.mr;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public abstract class ManagedBuffer {

  /** Number of bytes of the data. */
  public abstract long size();

  /**
   * Exposes this buffer's data as an NIO ByteBuffer. Changing the position and limit of the
   * returned ByteBuffer should not affect the content of this buffer.
   */
  // TODO: Deprecate this, usage may require expensive memory mapping or allocation.
  public abstract ByteBuffer nioByteBuffer() throws IOException;

  /**
   * Exposes this buffer's data as an InputStream. The underlying implementation does not
   * necessarily check for the length of bytes read, so the caller is responsible for making sure
   * it does not go over the limit.
   */
  public abstract InputStream createInputStream() throws IOException;

  /**
   * Increment the reference count by one if applicable.
   */
  public abstract ManagedBuffer retain();

  /**
   * If applicable, decrement the reference count by one and deallocates the buffer if the
   * reference count reaches zero.
   */
  public abstract ManagedBuffer release();

  public abstract Object convertToNetty() throws IOException;
}
