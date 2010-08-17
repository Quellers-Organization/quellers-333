/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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

package org.elasticsearch.action.admin.indices.status;

import org.elasticsearch.action.support.broadcast.BroadcastShardOperationResponse;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.index.shard.IndexShardState;

import java.io.IOException;

import static org.elasticsearch.cluster.routing.ImmutableShardRouting.*;
import static org.elasticsearch.common.unit.ByteSizeValue.*;

/**
 * @author kimchy (shay.banon)
 */
public class ShardStatus extends BroadcastShardOperationResponse {

    public static class Docs {
        public static final Docs UNKNOWN = new Docs();

        int numDocs = -1;
        int maxDoc = -1;
        int deletedDocs = -1;

        public int numDocs() {
            return numDocs;
        }

        public int getNumDocs() {
            return numDocs();
        }

        public int maxDoc() {
            return maxDoc;
        }

        public int getMaxDoc() {
            return maxDoc();
        }

        public int deletedDocs() {
            return deletedDocs;
        }

        public int getDeletedDocs() {
            return deletedDocs();
        }
    }

    private ShardRouting shardRouting;

    IndexShardState state;

    ByteSizeValue storeSize;

    long translogId = -1;

    long translogOperations = -1;

    Docs docs = Docs.UNKNOWN;

    PeerRecoveryStatus peerRecoveryStatus;

    GatewayRecoveryStatus gatewayRecoveryStatus;

    ShardStatus() {
    }

    ShardStatus(ShardRouting shardRouting) {
        super(shardRouting.index(), shardRouting.id());
        this.shardRouting = shardRouting;
    }

    public ShardRouting shardRouting() {
        return this.shardRouting;
    }

    public ShardRouting getShardRouting() {
        return shardRouting();
    }

    public IndexShardState state() {
        return state;
    }

    public IndexShardState getState() {
        return state();
    }

    public ByteSizeValue storeSize() {
        return storeSize;
    }

    public ByteSizeValue getStoreSize() {
        return storeSize();
    }

    public long translogId() {
        return translogId;
    }

    public long getTranslogId() {
        return translogId();
    }

    public long translogOperations() {
        return translogOperations;
    }

    public long getTranslogOperations() {
        return translogOperations();
    }

    public Docs docs() {
        return docs;
    }

    public Docs getDocs() {
        return docs();
    }

    public PeerRecoveryStatus peerRecoveryStatus() {
        return peerRecoveryStatus;
    }

    public PeerRecoveryStatus getPeerRecoveryStatus() {
        return peerRecoveryStatus();
    }

    public GatewayRecoveryStatus gatewayRecoveryStatus() {
        return gatewayRecoveryStatus;
    }

    public GatewayRecoveryStatus getGatewayRecoveryStatus() {
        return gatewayRecoveryStatus();
    }

    public static ShardStatus readIndexShardStatus(StreamInput in) throws IOException {
        ShardStatus shardStatus = new ShardStatus();
        shardStatus.readFrom(in);
        return shardStatus;
    }

    @Override public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        shardRouting.writeTo(out);
        out.writeByte(state.id());
        if (storeSize == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            storeSize.writeTo(out);
        }
        out.writeLong(translogId);
        out.writeLong(translogOperations);
        if (docs == Docs.UNKNOWN) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            out.writeInt(docs.numDocs());
            out.writeInt(docs.maxDoc());
            out.writeInt(docs.deletedDocs());
        }
        if (peerRecoveryStatus == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            out.writeByte(peerRecoveryStatus.stage.value());
            out.writeVLong(peerRecoveryStatus.startTime);
            out.writeVLong(peerRecoveryStatus.time);
            out.writeVLong(peerRecoveryStatus.throttlingTime);
            out.writeVLong(peerRecoveryStatus.indexSize);
            out.writeVLong(peerRecoveryStatus.reusedIndexSize);
            out.writeVLong(peerRecoveryStatus.recoveredIndexSize);
            out.writeVLong(peerRecoveryStatus.recoveredTranslogOperations);
        }

        if (gatewayRecoveryStatus == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            out.writeByte(gatewayRecoveryStatus.stage.value());
            out.writeVLong(gatewayRecoveryStatus.startTime);
            out.writeVLong(gatewayRecoveryStatus.time);
            out.writeVLong(gatewayRecoveryStatus.throttlingTime);
            out.writeVLong(gatewayRecoveryStatus.indexThrottlingTime);
            out.writeVLong(gatewayRecoveryStatus.indexSize);
            out.writeVLong(gatewayRecoveryStatus.reusedIndexSize);
            out.writeVLong(gatewayRecoveryStatus.recoveredIndexSize);
            out.writeVLong(gatewayRecoveryStatus.recoveredTranslogOperations);
        }
    }

    @Override public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        shardRouting = readShardRoutingEntry(in);
        state = IndexShardState.fromId(in.readByte());
        if (in.readBoolean()) {
            storeSize = readBytesSizeValue(in);
        }
        translogId = in.readLong();
        translogOperations = in.readLong();
        if (in.readBoolean()) {
            docs = new Docs();
            docs.numDocs = in.readInt();
            docs.maxDoc = in.readInt();
            docs.deletedDocs = in.readInt();
        }
        if (in.readBoolean()) {
            peerRecoveryStatus = new PeerRecoveryStatus(PeerRecoveryStatus.Stage.fromValue(in.readByte()),
                    in.readVLong(), in.readVLong(), in.readVLong(), in.readVLong(), in.readVLong(), in.readVLong(), in.readVLong());
        }

        if (in.readBoolean()) {
            gatewayRecoveryStatus = new GatewayRecoveryStatus(GatewayRecoveryStatus.Stage.fromValue(in.readByte()),
                    in.readVLong(), in.readVLong(), in.readVLong(), in.readVLong(), in.readVLong(), in.readVLong(), in.readVLong(), in.readVLong());
        }
    }
}
