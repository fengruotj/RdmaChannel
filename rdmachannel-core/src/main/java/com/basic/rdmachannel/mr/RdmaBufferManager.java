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

package com.basic.rdmachannel.mr;

import com.basic.rdmachannel.channel.ExecutorsServiceContext;
import com.basic.rdmachannel.channel.RdmaChannelConf;
import com.ibm.disni.rdma.verbs.IbvPd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * locate com.ibm.disni.channel
 * Created by MasterTj on 2019/1/22.
 * RDMABufferManager  用于申请RDMABuffer
 */
public class RdmaBufferManager {
  private class AllocatorStack {
    private final AtomicInteger totalAlloc = new AtomicInteger(0);
    private final AtomicInteger preAllocs = new AtomicInteger(0);
    private final ConcurrentLinkedDeque<RdmaBuffer> stack = new ConcurrentLinkedDeque<>();
    private final int length;
    private long lastAccess;
    private final AtomicLong idleBuffersSize = new AtomicLong(0);

    /**
     * 设置需要分配的RDMABuffer的Length大小
     * @param length
     */
    private AllocatorStack(int length) {
      this.length = length;
    }

    private int getTotalAlloc() {
      return totalAlloc.get();
    }

    private int getTotalPreAllocs() {
      return preAllocs.get();
    }

    /**
     * 返回一个长度为Length的RdmaBuffer
     * @return
     * @throws IOException
     */
    private RdmaBuffer get() throws IOException {
      lastAccess = System.nanoTime();
      RdmaBuffer rdmaBuffer = stack.pollFirst();
      if (rdmaBuffer == null) {
        totalAlloc.getAndIncrement();
        return new RdmaBuffer(getPd(), length);
      } else {
        idleBuffersSize.addAndGet(-length);
        return rdmaBuffer;
      }
    }

    /**
     * 回收一个RdmaBuffer
     * @param rdmaBuffer
     */
    private void put(RdmaBuffer rdmaBuffer) {
      rdmaBuffer.clean();
      lastAccess = System.nanoTime();
      stack.addLast(rdmaBuffer);
      idleBuffersSize.addAndGet(length);
    }


    /**
     * 预分配一定数量的RdmaBuffer
     * @param numBuffers
     * @throws IOException
     */
    private void preallocate(int numBuffers) throws IOException {
      RdmaBuffer[] preAllocatedBuffers = RdmaBuffer.preAllocate(getPd(), length, numBuffers);
      for (int i = 0; i < numBuffers; i++) {
        put(preAllocatedBuffers[i]);
        preAllocs.getAndIncrement();
      }
    }

    private void close() {
      while (!stack.isEmpty()) {
        RdmaBuffer rdmaBuffer = stack.poll();
        if (rdmaBuffer != null) {
          rdmaBuffer.free();
        }
      }
    }
  }

  private static final Logger logger = LoggerFactory.getLogger(RdmaBufferManager.class);
  private static final int MIN_BLOCK_SIZE = 16 * 1024;

  private final int minimumAllocationSize;
  private final AtomicBoolean startedCleanStacks = new AtomicBoolean(false);
  /**
   * 分配RdmaBufferStack的Map
   */
  private final ConcurrentHashMap<Integer, AllocatorStack> allocStackMap =
    new ConcurrentHashMap<>();
  private IbvPd pd;
  private final boolean useOdp;
  private long maxCacheSize;
  private static final ExecutorService executorService =
          ExecutorsServiceContext.getInstance();

  public RdmaBufferManager(IbvPd pd, boolean isExecutor, RdmaChannelConf conf) throws IOException {
    this.pd = pd;
    this.minimumAllocationSize = Math.min(1024, MIN_BLOCK_SIZE);
    this.maxCacheSize = conf.maxBufferAllocationSize();
    if (conf.useOdp(pd.getContext())) {
      useOdp = true;
    } else {
      useOdp = false;
    }
  }

  /**
   * Pre allocates specified number of buffers of particular size in a single MR.
   * 预先分配一定数量的Buffers在一个特定大小的MR中
   * @throws IOException
   */
  public void preAllocate(int buffSize, int buffCount) throws IOException {
    long totalSize = ((long) buffCount) * buffSize;
    // Disni uses int for length, so we can only allocate and register up to 2GB in a single call
    if (totalSize > (Integer.MAX_VALUE - 1)) {
      int numAllocs = (int)(totalSize / Integer.MAX_VALUE) + 1;
      for (int i = 0; i < numAllocs; i++) {
        getOrCreateAllocatorStack(buffSize).preallocate(buffCount / numAllocs);
      }
    } else {
      getOrCreateAllocatorStack(buffSize).preallocate(buffCount);
    }
  }

  /**
   * 如果长度为Length的AllocatorStack存在则返回
   * 如果长度为Length的AllocatorStack不存在则创建
   * @param length
   * @return
   */
  private AllocatorStack getOrCreateAllocatorStack(int length) {
    AllocatorStack allocatorStack = allocStackMap.get(length);
    if (allocatorStack == null) {
      allocStackMap.putIfAbsent(length, new AllocatorStack(length));
      allocatorStack = allocStackMap.get(length);
    }

    return allocatorStack;
  }

  /**
   * 创建一个长度为length的AllocatorStack
   * @param length
   * @return
   * @throws IOException
   */
  public RdmaBuffer get(int length) throws IOException {
    // Round up length to the nearest power of two, or the minimum block size
    if (length < minimumAllocationSize) {
      length = minimumAllocationSize;
    } else {
      length--;
      length |= length >> 1;
      length |= length >> 2;
      length |= length >> 4;
      length |= length >> 8;
      length |= length >> 16;
      length++;
    }
    return getOrCreateAllocatorStack(length).get();
  }

  public void put(RdmaBuffer buf) {
    AllocatorStack allocatorStack = allocStackMap.get(buf.getLength());
    if (allocatorStack == null) {
      buf.free();
    } else {
      allocatorStack.put(buf);
      if (startedCleanStacks.compareAndSet(false, true)) {
        FutureTask<Void> cleaner = new FutureTask<>(() -> {
          // Check the size of current idling buffers
          long idleBuffersSize = allocStackMap
              .reduceValuesToLong(100L, allocStack -> allocStack.idleBuffersSize.get(),
                  0L, Long::sum);
          // If it reached out 90% of idle buffer capacity, clean old stacks
          if (idleBuffersSize > maxCacheSize * 0.90) {
            cleanLRUStacks(idleBuffersSize);
          } else {
            startedCleanStacks.compareAndSet(true, false);
          }
          return null;
        });
        executorService.execute(cleaner);
      }
    }
  }

  private void cleanLRUStacks(long idleBuffersSize) {
    logger.debug("Current idle buffer size {}KB exceed 90% of maxCacheSize {}KB." +
        " Cleaning LRU idle stacks", idleBuffersSize / 1024, maxCacheSize / 1024);
    long totalCleaned = 0;
    // Will clean up to 65% of capacity
    long needToClean = idleBuffersSize - (long)(maxCacheSize * 0.65);
    Iterator<AllocatorStack> stacks = allocStackMap.values().stream()
      .filter(s -> !s.stack.isEmpty())
      .sorted(Comparator.comparingLong(s -> s.lastAccess)).iterator();
    while (stacks.hasNext()) {
      AllocatorStack lruStack = stacks.next();
      while (!lruStack.stack.isEmpty() && totalCleaned < needToClean) {
        RdmaBuffer rdmaBuffer = lruStack.stack.pollFirst();
        if (rdmaBuffer != null) {
          rdmaBuffer.free();
          totalCleaned += lruStack.length;
          lruStack.idleBuffersSize.addAndGet(-lruStack.length);
        }
      }
      logger.debug("Cleaned {} KB of idle stacks of size {} KB",
          totalCleaned / 1024, lruStack.length / 1024);
    }
    startedCleanStacks.compareAndSet(true, false);
  }

  public IbvPd getPd() { return this.pd; }

  boolean useOdp() { return this.useOdp; }

  public void stop() {
    logger.info("Rdma buffers allocation statistics:");
    for (Integer size : allocStackMap.keySet()) {
      AllocatorStack allocatorStack = allocStackMap.remove(size);
      if (allocatorStack != null) {
        logger.info( "Pre allocated {}, allocated {} buffers of size {} KB",
            allocatorStack.getTotalPreAllocs(), allocatorStack.getTotalAlloc(), (size / 1024));
        allocatorStack.close();
      }
    }

    executorService.shutdown();
  }
}
