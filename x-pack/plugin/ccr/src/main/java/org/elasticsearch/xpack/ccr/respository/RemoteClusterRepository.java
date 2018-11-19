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

package org.elasticsearch.xpack.ccr.respository;

import com.carrotsearch.hppc.cursors.ObjectObjectCursor;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.index.SegmentInfos;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.Version;
import org.elasticsearch.action.admin.cluster.state.ClusterStateResponse;
import org.elasticsearch.action.support.PlainActionFuture;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.cluster.metadata.RepositoryMetaData;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.routing.RecoverySource;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.lucene.Lucene;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ConcurrentCollections;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.engine.EngineException;
import org.elasticsearch.index.seqno.SequenceNumbers;
import org.elasticsearch.index.shard.IndexShard;
import org.elasticsearch.index.shard.IndexShardRecoveryException;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.index.snapshots.IndexShardRestoreFailedException;
import org.elasticsearch.index.snapshots.IndexShardSnapshotStatus;
import org.elasticsearch.index.store.Store;
import org.elasticsearch.index.store.StoreFileMetaData;
import org.elasticsearch.index.translog.Translog;
import org.elasticsearch.indices.recovery.RecoveryState;
import org.elasticsearch.repositories.IndexId;
import org.elasticsearch.repositories.Repository;
import org.elasticsearch.repositories.RepositoryData;
import org.elasticsearch.snapshots.SnapshotId;
import org.elasticsearch.snapshots.SnapshotInfo;
import org.elasticsearch.snapshots.SnapshotShardFailure;
import org.elasticsearch.snapshots.SnapshotState;
import org.elasticsearch.xpack.ccr.Ccr;
import org.elasticsearch.xpack.ccr.CcrLicenseChecker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RemoteClusterRepository extends AbstractLifecycleComponent implements Repository {

    public final static String SNAPSHOT_UUID = "_latest_";
    public final static String TYPE = "_remote_cluster_";

    private final RepositoryMetaData metadata;
    private final String remoteClusterAlias;
    private final Client remoteClient;
    private final CcrLicenseChecker ccrLicenseChecker;

    public RemoteClusterRepository(RepositoryMetaData metadata, Client client, CcrLicenseChecker ccrLicenseChecker, Settings settings) {
        super(settings);
        this.metadata = metadata;
        this.remoteClusterAlias = metadata.name();
        this.ccrLicenseChecker = ccrLicenseChecker;
        this.remoteClient = client.getRemoteClusterClient(remoteClusterAlias);
    }

    @Override
    protected void doStart() {

    }

    @Override
    protected void doStop() {

    }

    @Override
    protected void doClose() throws IOException {

    }

    @Override
    public RepositoryMetaData getMetadata() {
        return metadata;
    }

    @Override
    public SnapshotInfo getSnapshotInfo(SnapshotId snapshotId) {
        assert SNAPSHOT_UUID.equals(snapshotId.getUUID()) : "RemoteClusterRepository only supports the _latest_ as the UUID";
        // TODO: Perhaps add version
        return new SnapshotInfo(snapshotId, Collections.singletonList(snapshotId.getName()), SnapshotState.SUCCESS);
    }

    @Override
    public MetaData getSnapshotGlobalMetaData(SnapshotId snapshotId) {
        assert SNAPSHOT_UUID.equals(snapshotId.getUUID()) : "RemoteClusterRepository only supports the _latest_ as the UUID";
        ClusterStateResponse response = remoteClient.admin().cluster().prepareState().clear().setMetaData(true).get();
        return response.getState().metaData();
    }

    @Override
    public IndexMetaData getSnapshotIndexMetaData(SnapshotId snapshotId, IndexId index) {
        assert SNAPSHOT_UUID.equals(snapshotId.getUUID()) : "RemoteClusterRepository only supports the _latest_ as the UUID";
        String leaderIndex = index.getName();

        ClusterStateResponse response = remoteClient
            .admin()
            .cluster()
            .prepareState()
            .clear()
            .setMetaData(true)
            .setIndices(leaderIndex)
            .get();

        // Validates whether the leader cluster has been configured properly:
        PlainActionFuture<String[]> future = PlainActionFuture.newFuture();
        IndexMetaData leaderIndexMetaData = response.getState().metaData().index(leaderIndex);
        ccrLicenseChecker.fetchLeaderHistoryUUIDs(remoteClient, leaderIndexMetaData, future::onFailure, future::onResponse);
        String[] leaderHistoryUuids = future.actionGet();

        IndexMetaData.Builder imdBuilder = IndexMetaData.builder(leaderIndexMetaData);
        // Adding the leader index uuid for each shard as custom metadata:
        Map<String, String> metadata = new HashMap<>();
        metadata.put(Ccr.CCR_CUSTOM_METADATA_LEADER_INDEX_SHARD_HISTORY_UUIDS, String.join(",", leaderHistoryUuids));
        metadata.put(Ccr.CCR_CUSTOM_METADATA_LEADER_INDEX_UUID_KEY, leaderIndexMetaData.getIndexUUID());
        metadata.put(Ccr.CCR_CUSTOM_METADATA_LEADER_INDEX_NAME_KEY, leaderIndexMetaData.getIndex().getName());
        metadata.put(Ccr.CCR_CUSTOM_METADATA_REMOTE_CLUSTER_NAME_KEY, remoteClusterAlias);
        imdBuilder.putCustom(Ccr.CCR_CUSTOM_METADATA_KEY, metadata);

        imdBuilder.settings(leaderIndexMetaData.getSettings());

        // Copy mappings from leader IMD to follow IMD
        for (ObjectObjectCursor<String, MappingMetaData> cursor : leaderIndexMetaData.getMappings()) {
            imdBuilder.putMapping(cursor.value);
        }

        imdBuilder.setRoutingNumShards(leaderIndexMetaData.getRoutingNumShards());

        return imdBuilder.build();
    }

    @Override
    public RepositoryData getRepositoryData() {
        ClusterStateResponse response = remoteClient.admin().cluster().prepareState().clear().setMetaData(true).get();
        MetaData remoteMetaData = response.getState().getMetaData();

        Map<String, SnapshotId> copiedSnapshotIds = new HashMap<>();
        Map<String, SnapshotState> snapshotStates = new HashMap<>(copiedSnapshotIds.size());
        Map<IndexId, Set<SnapshotId>> indexSnapshots = new HashMap<>(copiedSnapshotIds.size());

        ImmutableOpenMap<String, IndexMetaData> remoteIndices = remoteMetaData.getIndices();
        for (String indexName : remoteMetaData.getConcreteAllIndices()) {
            SnapshotId snapshotId = new SnapshotId(indexName, SNAPSHOT_UUID);
            copiedSnapshotIds.put(indexName, snapshotId);
            snapshotStates.put(indexName, SnapshotState.SUCCESS);
            Index index = remoteIndices.get(indexName).getIndex();
            indexSnapshots.put(new IndexId(indexName, index.getUUID()), Collections.singleton(snapshotId));
        }

        return new RepositoryData(1, copiedSnapshotIds, snapshotStates, indexSnapshots, Collections.emptyList());
    }

    @Override
    public void initializeSnapshot(SnapshotId snapshotId, List<IndexId> indices, MetaData metaData) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SnapshotInfo finalizeSnapshot(SnapshotId snapshotId, List<IndexId> indices, long startTime, String failure, int totalShards,
                                         List<SnapshotShardFailure> shardFailures, long repositoryStateId, boolean includeGlobalState) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteSnapshot(SnapshotId snapshotId, long repositoryStateId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getSnapshotThrottleTimeInNanos() {
        return 0;
    }

    @Override
    public long getRestoreThrottleTimeInNanos() {
        return 0;
    }

    @Override
    public String startVerification() {
        return null;
    }

    @Override
    public void endVerification(String verificationToken) {

    }

    @Override
    public void verify(String verificationToken, DiscoveryNode localNode) {

    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public void snapshotShard(IndexShard shard, Store store, SnapshotId snapshotId, IndexId indexId, IndexCommit snapshotIndexCommit,
                              IndexShardSnapshotStatus snapshotStatus) {
        throw new UnsupportedOperationException("Cannot snapshot shard for RemoteClusterRepository");
    }

    @Override
    public void restoreShard(IndexShard indexShard, SnapshotId snapshotId, Version version, IndexId indexId, ShardId shardId,
                             RecoveryState recoveryState) {
        final Store store = indexShard.store();
        store.incRef();
        try {
            store.createEmpty();
        } catch (EngineException | IOException e) {
            throw new IndexShardRecoveryException(shardId, "failed to recover from gateway", e);
        } finally {
            store.decRef();
        }
    }

    @Override
    public IndexShardSnapshotStatus getShardSnapshotStatus(SnapshotId snapshotId, Version version, IndexId indexId, ShardId shardId) {
        throw new UnsupportedOperationException();
    }
}
