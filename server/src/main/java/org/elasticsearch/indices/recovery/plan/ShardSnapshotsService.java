/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.indices.recovery.plan;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.elasticsearch.Version;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.cluster.snapshots.get.shard.GetShardSnapshotAction;
import org.elasticsearch.action.admin.cluster.snapshots.get.shard.GetShardSnapshotRequest;
import org.elasticsearch.action.admin.cluster.snapshots.get.shard.GetShardSnapshotResponse;
import org.elasticsearch.action.support.ThreadedActionListener;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.blobstore.BlobContainer;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.index.snapshots.blobstore.BlobStoreIndexShardSnapshot;
import org.elasticsearch.repositories.RepositoriesService;
import org.elasticsearch.repositories.ShardSnapshotInfo;
import org.elasticsearch.repositories.blobstore.BlobStoreRepository;
import org.elasticsearch.snapshots.Snapshot;
import org.elasticsearch.threadpool.ThreadPool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static org.elasticsearch.indices.recovery.RecoverySettings.SNAPSHOT_RECOVERIES_SUPPORTED_VERSION;

public class ShardSnapshotsService {
    private final Logger logger = LogManager.getLogger(ShardSnapshotsService.class);

    private final Client client;
    private final RepositoriesService repositoriesService;
    private final ThreadPool threadPool;
    private final Supplier<Version> masterNodeVersionSupplier;

    public ShardSnapshotsService(Client client,
                                 RepositoriesService repositoriesService,
                                 ThreadPool threadPool,
                                 Supplier<Version> masterNodeVersionSupplier) {
        this.client = client;
        this.repositoriesService = repositoriesService;
        this.threadPool = threadPool;
        this.masterNodeVersionSupplier = masterNodeVersionSupplier;
    }

    public void fetchAvailableSnapshotsInAllRepositories(ShardId shardId, ActionListener<List<ShardSnapshot>> listener) {
        if (shardId == null) {
            throw new IllegalArgumentException("SharId was null but a value was expected");
        }
        final GetShardSnapshotRequest request = GetShardSnapshotRequest.latestSnapshotInAllRepositories(shardId);
        sendRequest(request, listener);
    }

    public void fetchAvailableSnapshots(String repository, ShardId shardId, ActionListener<List<ShardSnapshot>> listener) {
        if (Strings.isNullOrEmpty(repository)) {
            throw new IllegalArgumentException("A repository should be specified");
        }
        if (shardId == null) {
            throw new IllegalArgumentException("SharId was null but a value was expected");
        }
        GetShardSnapshotRequest request =
            GetShardSnapshotRequest.latestSnapshotInRepositories(shardId, Collections.singletonList(repository));
        sendRequest(request, listener);
    }

    private void sendRequest(GetShardSnapshotRequest request, ActionListener<List<ShardSnapshot>> listener) {
        if (masterNodeVersionSupplier.get().onOrAfter(SNAPSHOT_RECOVERIES_SUPPORTED_VERSION) == false) {
            listener.onResponse(Collections.emptyList());
        }

        client.execute(GetShardSnapshotAction.INSTANCE,
            request,
            new ThreadedActionListener<>(logger, threadPool, ThreadPool.Names.GENERIC, listener.map(this::fetchSnapshotFiles), false)
        );
    }

    private List<ShardSnapshot> fetchSnapshotFiles(GetShardSnapshotResponse shardSnapshotResponse) {
        assert Thread.currentThread().getName().contains(ThreadPool.Names.GENERIC);

        if (shardSnapshotResponse.getRepositoryShardSnapshots().isEmpty()) {
            return Collections.emptyList();
        }

        Collection<ShardSnapshotInfo> shardSnapshots = shardSnapshotResponse.getRepositoryShardSnapshots().values();
        List<ShardSnapshot> shardSnapshotData = new ArrayList<>(shardSnapshots.size());
        for (ShardSnapshotInfo shardSnapshot : shardSnapshots) {
            final List<BlobStoreIndexShardSnapshot.FileInfo> snapshotFiles = getSnapshotFileList(shardSnapshot);
            if (snapshotFiles.isEmpty() == false) {
                shardSnapshotData.add(new ShardSnapshot(shardSnapshot, snapshotFiles));
            }
        }
        return shardSnapshotData;
    }

    private List<BlobStoreIndexShardSnapshot.FileInfo> getSnapshotFileList(ShardSnapshotInfo shardSnapshotInfo) {
        try {
            final Snapshot snapshot = shardSnapshotInfo.getSnapshot();
            BlobStoreRepository blobStoreRepository = (BlobStoreRepository) repositoriesService.repository(snapshot.getRepository());
            BlobContainer blobContainer = blobStoreRepository.shardContainer(shardSnapshotInfo.getIndexId(),
                shardSnapshotInfo.getShardId().getId());
            BlobStoreIndexShardSnapshot blobStoreIndexShardSnapshot =
                blobStoreRepository.loadShardSnapshot(blobContainer, snapshot.getSnapshotId());

            return blobStoreIndexShardSnapshot.indexFiles();
        } catch (Exception e) {
            logger.warn(new ParameterizedMessage("Unable to fetch shard snapshot files for {}", shardSnapshotInfo), e);
            return Collections.emptyList();
        }
    }
}
