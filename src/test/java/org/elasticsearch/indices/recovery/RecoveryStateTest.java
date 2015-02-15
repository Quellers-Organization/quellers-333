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
package org.elasticsearch.indices.recovery;

import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.transport.DummyTransportAddress;
import org.elasticsearch.common.util.concurrent.AbstractRunnable;
import org.elasticsearch.common.util.concurrent.ConcurrentCollections;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.test.ElasticsearchTestCase;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.Matchers.closeTo;

public class RecoveryStateTest extends ElasticsearchTestCase {

    public void testPercentage() {
        RecoveryState state = new RecoveryState();
        RecoveryState.Index index = state.getIndex();
        index.totalByteCount(100);
        index.reusedByteCount(20);
        index.recoveredByteCount(80);
        assertThat((double) index.percentBytesRecovered(), closeTo(80.0d, 0.1d));

        index.totalFileCount(100);
        index.reusedFileCount(80);
        index.recoveredFileCount(20);
        assertThat((double) index.percentFilesRecovered(), closeTo(20.0d, 0.1d));

        index.totalByteCount(0);
        index.reusedByteCount(0);
        index.recoveredByteCount(0);
        assertThat((double) index.percentBytesRecovered(), closeTo(0d, 0.1d));

        index.totalFileCount(0);
        index.reusedFileCount(0);
        index.recoveredFileCount(0);
        assertThat((double) index.percentFilesRecovered(), closeTo(00.0d, 0.1d));

        index.totalByteCount(10);
        index.reusedByteCount(0);
        index.recoveredByteCount(10);
        assertThat((double) index.percentBytesRecovered(), closeTo(100d, 0.1d));

        index.totalFileCount(20);
        index.reusedFileCount(0);
        index.recoveredFileCount(20);
        assertThat((double) index.percentFilesRecovered(), closeTo(100.0d, 0.1d));
    }

    public void testConcurrentWriteSerialize() {
        final RecoveryState state = new RecoveryState();

        final CyclicBarrier barrier = new CyclicBarrier(2);
        final int iterations = scaledRandomIntBetween(10, 200);
        final AtomicBoolean stop = new AtomicBoolean();
        final Queue<Throwable> errors = ConcurrentCollections.newBlockingQueue();
        Thread readThread = new Thread(new AbstractRunnable() {
            @Override
            public void onFailure(Throwable t) {
                errors.add(t);
            }

            @Override
            protected void doRun() throws Exception {
                barrier.await();
                while (stop.get() == false) {
                    BytesStreamOutput output = new BytesStreamOutput();
                    state.writeTo(output);
                    BytesStreamInput input = new BytesStreamInput(output.bytes());
                    RecoveryState.readRecoveryState(input);
                    state.toXContent(JsonXContent.contentBuilder(), ToXContent.EMPTY_PARAMS);
                }
            }
        });
        readThread.start();

        Thread writeThread = new Thread(new AbstractRunnable() {
            @Override
            public void onFailure(Throwable t) {
                errors.add(t);
            }

            @Override
            protected void doRun() throws Exception {
                barrier.await();
                for (int i = 0; i < iterations; i++) {
                    state.setSourceNode(new DiscoveryNode("" + i, DummyTransportAddress.INSTANCE, randomVersion()));
                    state.getTranslog().addTranslogOperations(i);
                    state.getTranslog().incrementTranslogOperations();
                    state.getTranslog().startTime(System.currentTimeMillis());
                    state.getTranslog().time(System.currentTimeMillis() + i);
                    state.getIndex().startTime(System.currentTimeMillis());
                    state.getIndex().time(System.currentTimeMillis() + i);
                    state.getIndex().addFileDetail("f" + i, i * 10);
                    state.getIndex().addFileDetail("f1" + i, i, i / 2);
                    state.getIndex().addFileDetails(Arrays.asList("f2" + i, "f3" + i), Arrays.asList(1L + i, 2L + i));
                    state.getIndex().addRecoveredByteCount(i);
                    state.getIndex().addRecoveredFileCount(i);
                    state.getIndex().addReusedFileDetail("r" + i, i * 10);
                    state.getIndex().addReusedFileDetails(Arrays.asList("r1" + i, "r2" + i), Arrays.asList(1L + i, 2L + i));
                    state.getIndex().recoveredByteCount(i);
                    state.getIndex().recoveredFileCount(i);
                    state.getIndex().reusedByteCount(i);
                    state.getIndex().
                }
            }
        });
    }
}
