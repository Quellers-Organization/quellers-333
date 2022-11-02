/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.action.admin.cluster.node.shutdown;

import org.elasticsearch.action.support.nodes.BaseNodeResponse;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.index.shard.ShardId;

import java.io.IOException;
import java.util.Set;

public class NodeListIndexShardsOnDataPathResponse extends BaseNodeResponse {

    private final Set<ShardId> shardIds;

    protected NodeListIndexShardsOnDataPathResponse(DiscoveryNode node, Set<ShardId> shardIds) {
        super(node);
        this.shardIds = Set.copyOf(shardIds);
    }

    protected NodeListIndexShardsOnDataPathResponse(StreamInput in) throws IOException {
        super(in);
        shardIds = in.readSet(ShardId::new);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeCollection(shardIds);
    }

    public Set<ShardId> getShardIds() {
        return shardIds;
    }
}
