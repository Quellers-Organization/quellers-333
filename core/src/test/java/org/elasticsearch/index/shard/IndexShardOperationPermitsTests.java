/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.index.shard;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.PlainActionFuture;
import org.elasticsearch.common.lease.Releasable;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.threadpool.TestThreadPool;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.threadpool.ThreadPoolStats;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;

public class IndexShardOperationPermitsTests extends ESTestCase {

    private static ThreadPool threadPool;

    private IndexShardOperationPermits permits;

    @BeforeClass
    public static void setupThreadPool() {
        threadPool = new TestThreadPool("IndexShardOperationsLockTests");
    }

    @AfterClass
    public static void shutdownThreadPool() {
        ThreadPool.terminate(threadPool, 30, TimeUnit.SECONDS);
        threadPool = null;
    }

    @Before
    public void createIndexShardOperationsLock() {
         permits = new IndexShardOperationPermits(new ShardId("blubb", "id", 0), logger, threadPool);
    }

    @After
    public void checkNoInflightOperations() {
        assertThat(permits.semaphore.availablePermits(), equalTo(Integer.MAX_VALUE));
        assertThat(permits.getActiveOperationsCount(), equalTo(0));
    }

    public void testAllOperationsInvoked() throws InterruptedException, TimeoutException, ExecutionException {
        int numThreads = 10;

        List<PlainActionFuture<Releasable>> futures = new ArrayList<>();
        List<Thread> operationThreads = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(numThreads / 2);
        for (int i = 0; i < numThreads; i++) {
            PlainActionFuture<Releasable> future = new PlainActionFuture<Releasable>() {
                @Override
                public void onResponse(Releasable releasable) {
                    releasable.close();
                    super.onResponse(releasable);
                }
            };
            Thread thread = new Thread() {
                public void run() {
                    latch.countDown();
                    permits.acquire(future, ThreadPool.Names.GENERIC, true);
                }
            };
            futures.add(future);
            operationThreads.add(thread);
        }

        CountDownLatch blockFinished = new CountDownLatch(1);
        threadPool.generic().execute(() -> {
            try {
                latch.await();
                blockAndWait().close();
                blockFinished.countDown();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        for (Thread thread : operationThreads) {
            thread.start();
        }

        for (PlainActionFuture<Releasable> future : futures) {
            assertNotNull(future.get(1, TimeUnit.MINUTES));
        }

        for (Thread thread : operationThreads) {
            thread.join();
        }

        blockFinished.await();
    }


    public void testOperationsInvokedImmediatelyIfNoBlock() throws ExecutionException, InterruptedException {
        PlainActionFuture<Releasable> future = new PlainActionFuture<>();
        permits.acquire(future, ThreadPool.Names.GENERIC, true);
        assertTrue(future.isDone());
        future.get().close();
    }

    public void testOperationsIfClosed() throws ExecutionException, InterruptedException {
        PlainActionFuture<Releasable> future = new PlainActionFuture<>();
        permits.close();
        permits.acquire(future, ThreadPool.Names.GENERIC, true);
        ExecutionException exception = expectThrows(ExecutionException.class, future::get);
        assertThat(exception.getCause(), instanceOf(IndexShardClosedException.class));
    }

    public void testBlockIfClosed() throws ExecutionException, InterruptedException {
        permits.close();
        expectThrows(IndexShardClosedException.class, () -> permits.syncBlockOperations(randomInt(10), TimeUnit.MINUTES,
            () -> { throw new IllegalArgumentException("fake error"); }));
    }

    public void testOperationsDelayedIfBlock() throws ExecutionException, InterruptedException, TimeoutException {
        PlainActionFuture<Releasable> future = new PlainActionFuture<>();
        try (Releasable ignored = blockAndWait()) {
            permits.acquire(future, ThreadPool.Names.GENERIC, true);
            assertFalse(future.isDone());
        }
        future.get(1, TimeUnit.HOURS).close();
    }

    /**
     * Tests that the ThreadContext is restored when a operation is executed after it has been delayed due to a block
     */
    public void testThreadContextPreservedIfBlock() throws ExecutionException, InterruptedException, TimeoutException {
        final ThreadContext context = threadPool.getThreadContext();
        final Function<ActionListener<Releasable>, Boolean> contextChecker = (listener) -> {
            if ("bar".equals(context.getHeader("foo")) == false) {
                listener.onFailure(new IllegalStateException("context did not have value [bar] for header [foo]. Actual value [" +
                    context.getHeader("foo") + "]"));
            } else if ("baz".equals(context.getTransient("bar")) == false) {
                listener.onFailure(new IllegalStateException("context did not have value [baz] for transient [bar]. Actual value [" +
                    context.getTransient("bar") + "]"));
            } else {
                return true;
            }
            return false;
        };
        PlainActionFuture<Releasable> future = new PlainActionFuture<Releasable>() {
            @Override
            public void onResponse(Releasable releasable) {
                if (contextChecker.apply(this)) {
                    super.onResponse(releasable);
                }
            }
        };
        PlainActionFuture<Releasable> future2 = new PlainActionFuture<Releasable>() {
            @Override
            public void onResponse(Releasable releasable) {
                if (contextChecker.apply(this)) {
                    super.onResponse(releasable);
                }
            }
        };

        try (Releasable ignored = blockAndWait()) {
            // we preserve the thread context here so that we have a different context in the call to acquire than the context present
            // when the releasable is closed
            try (ThreadContext.StoredContext ignore = context.newStoredContext(false)) {
                context.putHeader("foo", "bar");
                context.putTransient("bar", "baz");
                // test both with and without a executor name
                permits.acquire(future, ThreadPool.Names.GENERIC, true);
                permits.acquire(future2, null, true);
            }
            assertFalse(future.isDone());
        }
        future.get(1, TimeUnit.HOURS).close();
        future2.get(1, TimeUnit.HOURS).close();
    }

    protected Releasable blockAndWait() throws InterruptedException {
        CountDownLatch blockAcquired = new CountDownLatch(1);
        CountDownLatch releaseBlock = new CountDownLatch(1);
        CountDownLatch blockReleased = new CountDownLatch(1);
        boolean throwsException = randomBoolean();
        IndexShardClosedException exception = new IndexShardClosedException(new ShardId("blubb", "id", 0));
        threadPool.generic().execute(() -> {
                try {
                    permits.syncBlockOperations(1, TimeUnit.MINUTES, () -> {
                        try {
                            blockAcquired.countDown();
                            releaseBlock.await();
                            if (throwsException) {
                                throw exception;
                            }
                        } catch (InterruptedException e) {
                            throw new RuntimeException();
                        }
                    });
                } catch (Exception e) {
                    if (e != exception) {
                        throw new RuntimeException(e);
                    }
                } finally {
                    blockReleased.countDown();
                }
            });
        blockAcquired.await();
        return () -> {
            releaseBlock.countDown();
            try {
                blockReleased.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
    }

    public void testAsyncBlockOperationsOperationWhileBlocked() throws InterruptedException {
        final CountDownLatch blockAcquired = new CountDownLatch(1);
        final CountDownLatch releaseBlock = new CountDownLatch(1);
        final AtomicBoolean blocked = new AtomicBoolean();
        permits.asyncBlockOperations(30, TimeUnit.MINUTES, () -> {
            blocked.set(true);
            blockAcquired.countDown();
            releaseBlock.await();
        });
        blockAcquired.await();
        assertTrue(blocked.get());

        // an operation that is submitted while there is a delay in place should be delayed
        final CountDownLatch delayedOperation = new CountDownLatch(1);
        final AtomicBoolean delayed = new AtomicBoolean();
        final Thread thread = new Thread(() ->
                permits.acquire(
                        new ActionListener<Releasable>() {
                            @Override
                            public void onResponse(Releasable releasable) {
                                delayed.set(true);
                                releasable.close();
                                delayedOperation.countDown();
                            }

                            @Override
                            public void onFailure(Exception e) {

                            }
                        },
                        ThreadPool.Names.GENERIC,
                        false));
        thread.start();
        assertFalse(delayed.get());
        releaseBlock.countDown();
        delayedOperation.await();
        assertTrue(delayed.get());
        thread.join();
    }

    public void testAsyncBlockOperationsOperationBeforeBlocked() throws InterruptedException, BrokenBarrierException {
        final CountDownLatch operationExecuting = new CountDownLatch(1);
        final CountDownLatch firstOperationLatch = new CountDownLatch(1);
        final CountDownLatch firstOperationComplete = new CountDownLatch(1);
        final Thread firstOperationThread = new Thread(() -> {
            permits.acquire(
                    new ActionListener<Releasable>() {
                        @Override
                        public void onResponse(Releasable releasable) {
                            operationExecuting.countDown();
                            try {
                                firstOperationLatch.await();
                            } catch (final InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            releasable.close();
                            firstOperationComplete.countDown();
                        }

                        @Override
                        public void onFailure(Exception e) {
                            throw new RuntimeException(e);
                        }
                    },
                    ThreadPool.Names.GENERIC,
                    false);
        });
        firstOperationThread.start();

        operationExecuting.await();

        // now we will delay operations while the first operation is still executing (because it is latched)
        final CountDownLatch blockedLatch = new CountDownLatch(1);
        final AtomicBoolean onBlocked = new AtomicBoolean();
        permits.asyncBlockOperations(30, TimeUnit.MINUTES, () -> {
            onBlocked.set(true);
            blockedLatch.countDown();
        });

        assertFalse(onBlocked.get());

        // if we submit another operation, it should be delayed
        final CountDownLatch secondOperationExecuting = new CountDownLatch(1);
        final CountDownLatch secondOperationComplete = new CountDownLatch(1);
        final AtomicBoolean secondOperation = new AtomicBoolean();
        final Thread secondOperationThread = new Thread(() -> {
                secondOperationExecuting.countDown();
                permits.acquire(
                        new ActionListener<Releasable>() {
                            @Override
                            public void onResponse(Releasable releasable) {
                                secondOperation.set(true);
                                releasable.close();
                                secondOperationComplete.countDown();
                            }

                            @Override
                            public void onFailure(Exception e) {
                                throw new RuntimeException(e);
                            }
                        },
                        ThreadPool.Names.GENERIC,
                        false);
        });
        secondOperationThread.start();

        secondOperationExecuting.await();
        assertFalse(secondOperation.get());

        firstOperationLatch.countDown();
        firstOperationComplete.await();
        blockedLatch.await();
        assertTrue(onBlocked.get());

        secondOperationComplete.await();
        assertTrue(secondOperation.get());

        firstOperationThread.join();
        secondOperationThread.join();
    }

    public void testAsyncBlockOperationsRace() throws Exception {
        // we racily submit operations and a delay, and then ensure that all operations were actually completed
        final int operations = scaledRandomIntBetween(1, 64);
        final CyclicBarrier barrier = new CyclicBarrier(1 + 1 + operations);
        final CountDownLatch operationLatch = new CountDownLatch(1 + operations);
        final Set<Integer> values = Collections.newSetFromMap(new ConcurrentHashMap<>());
        final List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < operations; i++) {
            final int value = i;
            final Thread thread = new Thread(() -> {
                try {
                    barrier.await();
                } catch (final BrokenBarrierException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
                permits.acquire(
                        new ActionListener<Releasable>() {
                            @Override
                            public void onResponse(Releasable releasable) {
                                values.add(value);
                                releasable.close();
                                operationLatch.countDown();
                            }

                            @Override
                            public void onFailure(Exception e) {

                            }
                        },
                        ThreadPool.Names.GENERIC,
                        false);
            });
            thread.start();
            threads.add(thread);
        }

        final Thread blockingThread = new Thread(() -> {
            try {
                barrier.await();
            } catch (final BrokenBarrierException | InterruptedException e) {
                throw new RuntimeException(e);
            }
            permits.asyncBlockOperations(30, TimeUnit.MINUTES, () -> {
                values.add(operations);
                operationLatch.countDown();
            });
        });
        blockingThread.start();

        barrier.await();

        operationLatch.await();
        for (final Thread thread : threads) {
            thread.join();
        }
        blockingThread.join();

        // check that all operations completed
        for (int i = 0; i < operations; i++) {
            assertTrue(values.contains(i));
        }
        assertTrue(values.contains(operations));
        /*
         * The block operation is executed on another thread and the operations can have completed before this thread has returned all the
         * permits to the semaphore. We wait here until all generic threads are idle as an indication that all permits have been returned to
         * the semaphore.
         */
        awaitBusy(() -> {
            for (final ThreadPoolStats.Stats stats : threadPool.stats()) {
                if (ThreadPool.Names.GENERIC.equals(stats.getName())) {
                    return stats.getActive() == 0;
                }
            }
            return false;
        });
    }

    public void testActiveOperationsCount() throws ExecutionException, InterruptedException {
        PlainActionFuture<Releasable> future1 = new PlainActionFuture<>();
        permits.acquire(future1, ThreadPool.Names.GENERIC, true);
        assertTrue(future1.isDone());
        assertThat(permits.getActiveOperationsCount(), equalTo(1));

        PlainActionFuture<Releasable> future2 = new PlainActionFuture<>();
        permits.acquire(future2, ThreadPool.Names.GENERIC, true);
        assertTrue(future2.isDone());
        assertThat(permits.getActiveOperationsCount(), equalTo(2));

        future1.get().close();
        assertThat(permits.getActiveOperationsCount(), equalTo(1));
        future1.get().close(); // check idempotence
        assertThat(permits.getActiveOperationsCount(), equalTo(1));
        future2.get().close();
        assertThat(permits.getActiveOperationsCount(), equalTo(0));

        try (Releasable releasable = blockAndWait()) {
            assertThat(permits.getActiveOperationsCount(), equalTo(0));
        }

        PlainActionFuture<Releasable> future3 = new PlainActionFuture<>();
        permits.acquire(future3, ThreadPool.Names.GENERIC, true);
        assertTrue(future3.isDone());
        assertThat(permits.getActiveOperationsCount(), equalTo(1));
        future3.get().close();
        assertThat(permits.getActiveOperationsCount(), equalTo(0));
    }
}
