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

package org.elasticsearch.action.admin.cluster.snapshots.status;


public enum SnapshotIndexShardStage {

    /**
     * Snapshot hasn't started yet
     */
    INIT((byte)0),
    /**
     * Index files are being copied
     */
    STARTED((byte)1),
    /**
     * Snapshot metadata is being written
     */
    FINALIZE((byte)2),
    /**
     * Snapshot completed successfully
     */
    DONE((byte)3),
    /**
     * Snapshot failed
     */
    FAILURE((byte)4);

    private final byte value;

    SnapshotIndexShardStage(byte value) {
        this.value = value;
    }

    /**
     * Returns code that represents the snapshot state
     *
     * @return code for the state
     */
    public byte value() {
        return value;
    }

    /**
     * Generate snapshot state from code
     *
     * @param value the state code
     * @return state
     */
    public static SnapshotIndexShardStage fromValue(byte value) {
        switch (value) {
            case 0:
                return INIT;
            case 1:
                return STARTED;
            case 2:
                return FINALIZE;
            case 3:
                return DONE;
            case 4:
                return FAILURE;
            default:
                throw new IllegalArgumentException("No snapshot shard stage for value [" + value + "]");
        }
    }
}
