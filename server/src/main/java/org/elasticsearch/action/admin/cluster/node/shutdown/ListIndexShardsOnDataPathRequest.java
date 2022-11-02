/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.action.admin.cluster.node.shutdown;

import org.elasticsearch.action.support.nodes.BaseNodesRequest;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.shard.ShardId;

import java.io.IOException;
import java.util.Set;

public class ListIndexShardsOnDataPathRequest extends BaseNodesRequest<ListIndexShardsOnDataPathRequest> {

    private final Set<ShardId> shardIds;

    public ListIndexShardsOnDataPathRequest(Set<ShardId> shardIds, String... nodeIds) {
        super(nodeIds);
        this.shardIds = Set.copyOf(shardIds);
        this.timeout(TimeValue.timeValueSeconds(30));
    }

    public ListIndexShardsOnDataPathRequest(StreamInput in) throws IOException {
        super(in);
        this.shardIds = in.readSet(ShardId::new);
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
