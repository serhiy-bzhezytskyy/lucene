/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.lucene.benchmark.jmh;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Compares a binary (2-ary) vs. ternary (3-ary) object min-heap on the {@code insertWithOverflow}
 * workload that drives {@code PriorityQueue}-based collectors (e.g. TopFieldCollector). Both heaps
 * are inlined so a single run measures them head to head. See GITHUB#16076.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class DaryHeapBenchmark {

  @Param({"10", "20", "100", "1000", "10000"})
  int topN;

  // Number of insertWithOverflow calls per invocation, simulating a large result set feeding a
  // fixed-size top-N queue.
  private static final int NUM_INSERTS = 200_000;

  private long[] values;

  @Setup
  public void setup() {
    Random r = new Random(42);
    values = new long[NUM_INSERTS];
    for (int i = 0; i < NUM_INSERTS; i++) {
      values[i] = r.nextLong();
    }
  }

  @Benchmark
  public long binaryHeap() {
    Heap heap = new Heap(topN, 2);
    for (long v : values) {
      heap.insertWithOverflow(v);
    }
    return heap.top();
  }

  @Benchmark
  public long ternaryHeap() {
    Heap heap = new Heap(topN, 3);
    for (long v : values) {
      heap.insertWithOverflow(v);
    }
    return heap.top();
  }

  /**
   * A minimal 1-based d-ary min-heap over longs, using the same index math and insertWithOverflow
   * semantics as {@code org.apache.lucene.util.PriorityQueue}. Kept boxing-free so the benchmark
   * measures heap structure, not allocation.
   */
  private static final class Heap {
    private final long[] heap;
    private final int maxSize;
    private final int arity;
    private int size;

    Heap(int maxSize, int arity) {
      this.maxSize = maxSize;
      this.arity = arity;
      this.heap = new long[maxSize + 1];
    }

    long top() {
      return heap[1];
    }

    boolean insertWithOverflow(long value) {
      if (size < maxSize) {
        size++;
        heap[size] = value;
        upHeap(size);
        return true;
      } else if (size > 0 && value > heap[1]) {
        heap[1] = value;
        downHeap(1);
        return true;
      }
      return false;
    }

    private void upHeap(int origPos) {
      int i = origPos;
      long node = heap[i];
      int j = ((i - 2) / arity) + 1;
      while (i > 1 && node < heap[j]) {
        heap[i] = heap[j];
        i = j;
        j = ((i - 2) / arity) + 1;
      }
      heap[i] = node;
    }

    private void downHeap(int i) {
      long node = heap[i];
      for (; ; ) {
        int firstChild = arity * (i - 1) + 2;
        if (firstChild > size) {
          break;
        }
        int lastChild = Math.min(firstChild + arity - 1, size);
        int best = firstChild;
        for (int c = firstChild + 1; c <= lastChild; c++) {
          if (heap[c] < heap[best]) {
            best = c;
          }
        }
        if (heap[best] >= node) {
          break;
        }
        heap[i] = heap[best];
        i = best;
      }
      heap[i] = node;
    }
  }
}
