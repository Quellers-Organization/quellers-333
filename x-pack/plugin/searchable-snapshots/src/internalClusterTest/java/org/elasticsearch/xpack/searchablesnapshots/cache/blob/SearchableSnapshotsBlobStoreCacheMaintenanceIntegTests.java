/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.searchablesnapshots.cache.blob;

import org.elasticsearch.action.admin.cluster.snapshots.restore.RestoreSnapshotResponse;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.OriginSettingClient;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.core.Tuple;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.reindex.ReindexPlugin;
import org.elasticsearch.repositories.fs.FsRepository;
import org.elasticsearch.xpack.core.ClientHelper;
import org.elasticsearch.xpack.core.searchablesnapshots.MountSearchableSnapshotRequest.Storage;
import org.elasticsearch.xpack.searchablesnapshots.BaseFrozenSearchableSnapshotsIntegTestCase;
import org.elasticsearch.xpack.searchablesnapshots.SearchableSnapshots;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.elasticsearch.cluster.metadata.IndexMetadata.INDEX_NUMBER_OF_SHARDS_SETTING;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHitCount;
import static org.elasticsearch.xpack.searchablesnapshots.SearchableSnapshots.SNAPSHOT_BLOB_CACHE_INDEX;
import static org.elasticsearch.xpack.searchablesnapshots.SearchableSnapshots.SNAPSHOT_INDEX_ID_SETTING;
import static org.elasticsearch.xpack.searchablesnapshots.SearchableSnapshots.SNAPSHOT_INDEX_NAME_SETTING;
import static org.elasticsearch.xpack.searchablesnapshots.SearchableSnapshots.SNAPSHOT_SNAPSHOT_ID_SETTING;
import static org.elasticsearch.xpack.searchablesnapshots.SearchableSnapshots.SNAPSHOT_SNAPSHOT_NAME_SETTING;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

public class SearchableSnapshotsBlobStoreCacheMaintenanceIntegTests extends BaseFrozenSearchableSnapshotsIntegTestCase {

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        final List<Class<? extends Plugin>> plugins = new ArrayList<>(super.nodePlugins());
        plugins.add(ReindexPlugin.class);
        return plugins;
    }

    /**
     * Test that snapshot blob cache entries are deleted from the system index after the corresponding searchable snapshot index is deleted
     */
    public void testCleanUpAfterIndicesAreDeleted() throws Exception {
        final String repositoryName = "repository";
        createRepository(repositoryName, FsRepository.TYPE);

        final Map<String, Tuple<Settings, Long>> mountedIndices = mountRandomIndicesWithCache(repositoryName, 3, 10);
        ensureYellow(SNAPSHOT_BLOB_CACHE_INDEX);
        refreshSystemIndex(true);

        final long numberOfEntriesInCache = numberOfEntriesInCache();
        logger.info("--> found [{}] entries in snapshot blob cache", numberOfEntriesInCache);
        assertThat(numberOfEntriesInCache, equalTo(mountedIndices.values().stream().mapToLong(Tuple::v2).sum()));

        final List<String> indicesToDelete = randomSubsetOf(randomIntBetween(1, mountedIndices.size()), mountedIndices.keySet());
        assertAcked(client().admin().indices().prepareDelete(indicesToDelete.toArray(String[]::new)));

        final long expectedDeletedEntriesInCache = mountedIndices.entrySet()
            .stream()
            .filter(e -> indicesToDelete.contains(e.getKey()))
            .mapToLong(entry -> entry.getValue().v2())
            .sum();
        logger.info("--> deleting indices [{}] with [{}] entries in snapshot blob cache", indicesToDelete, expectedDeletedEntriesInCache);

        assertBusy(() -> {
            refreshSystemIndex(true);
            assertThat(numberOfEntriesInCache(), equalTo(numberOfEntriesInCache - expectedDeletedEntriesInCache));

            for (String mountedIndex : mountedIndices.keySet()) {
                final Settings indexSettings = mountedIndices.get(mountedIndex).v1();
                assertHitCount(
                    systemClient().prepareSearch(SNAPSHOT_BLOB_CACHE_INDEX)
                        .setQuery(
                            BlobStoreCacheMaintenanceService.buildDeleteByQuery(
                                INDEX_NUMBER_OF_SHARDS_SETTING.get(indexSettings),
                                SNAPSHOT_SNAPSHOT_ID_SETTING.get(indexSettings),
                                SNAPSHOT_INDEX_ID_SETTING.get(indexSettings)
                            )
                        )
                        .setSize(0)
                        .get(),
                    indicesToDelete.contains(mountedIndex) ? 0L : mountedIndices.get(mountedIndex).v2()
                );
            }
        });

        final Set<String> remainingIndices = mountedIndices.keySet()
            .stream()
            .filter(Predicate.not(indicesToDelete::contains))
            .collect(Collectors.toSet());

        if (remainingIndices.isEmpty() == false) {
            final List<String> moreIndicesToDelete = randomSubsetOf(randomIntBetween(1, remainingIndices.size()), remainingIndices);

            final String randomMountedIndex = randomFrom(moreIndicesToDelete);
            final Settings randomIndexSettings = getIndexSettings(randomMountedIndex);
            final String snapshotId = SNAPSHOT_SNAPSHOT_ID_SETTING.get(randomIndexSettings);
            final String snapshotName = SNAPSHOT_SNAPSHOT_NAME_SETTING.get(randomIndexSettings);
            final String snapshotIndexName = SNAPSHOT_INDEX_NAME_SETTING.get(randomIndexSettings);

            final String remainingMountedIndex = "mounted-remaining-index";
            mountSnapshot(
                repositoryName,
                snapshotName,
                snapshotIndexName,
                remainingMountedIndex,
                Settings.EMPTY,
                randomFrom(Storage.values())
            );
            ensureGreen(remainingMountedIndex);

            assertExecutorIsIdle(SearchableSnapshots.CACHE_FETCH_ASYNC_THREAD_POOL_NAME);
            assertExecutorIsIdle(SearchableSnapshots.CACHE_PREWARMING_THREAD_POOL_NAME);
            waitForBlobCacheFillsToComplete();
            ensureClusterStateConsistency();

            logger.info(
                "--> deleting more mounted indices [{}] with snapshot [{}/{}] of index [{}] is still mounted as index [{}]",
                moreIndicesToDelete,
                snapshotId,
                snapshotIndexName,
                snapshotIndexName,
                remainingMountedIndex
            );
            assertAcked(client().admin().indices().prepareDelete(moreIndicesToDelete.toArray(String[]::new)));

            assertBusy(() -> {
                refreshSystemIndex(true);

                for (String mountedIndex : mountedIndices.keySet()) {
                    final Settings indexSettings = mountedIndices.get(mountedIndex).v1();

                    final long remainingEntriesInCache = systemClient().prepareSearch(SNAPSHOT_BLOB_CACHE_INDEX)
                        .setQuery(
                            BlobStoreCacheMaintenanceService.buildDeleteByQuery(
                                INDEX_NUMBER_OF_SHARDS_SETTING.get(indexSettings),
                                SNAPSHOT_SNAPSHOT_ID_SETTING.get(indexSettings),
                                SNAPSHOT_INDEX_ID_SETTING.get(indexSettings)
                            )
                        )
                        .setSize(0)
                        .get()
                        .getHits()
                        .getTotalHits().value;

                    if (indicesToDelete.contains(mountedIndex)) {
                        assertThat(remainingEntriesInCache, equalTo(0L));
                    } else if (snapshotId.equals(SNAPSHOT_SNAPSHOT_ID_SETTING.get(indexSettings))) {
                        assertThat(remainingEntriesInCache, greaterThanOrEqualTo(mountedIndices.get(randomMountedIndex).v2()));
                    } else if (moreIndicesToDelete.contains(mountedIndex)) {
                        assertThat(remainingEntriesInCache, equalTo(0L));
                    } else {
                        assertThat(remainingEntriesInCache, equalTo(mountedIndices.get(mountedIndex).v2()));
                    }
                }
            });
        }

        logger.info("--> deleting indices, maintenance service should clean up snapshot blob cache index");
        assertAcked(client().admin().indices().prepareDelete("mounted-*"));
        assertBusy(() -> {
            refreshSystemIndex(true);
            assertHitCount(systemClient().prepareSearch(SNAPSHOT_BLOB_CACHE_INDEX).setSize(0).get(), 0L);
        });
    }

    public void testPeriodicMaintenance() throws Exception {
        ensureStableCluster(internalCluster().getNodeNames().length, TimeValue.timeValueSeconds(60L));

        createRepository("repo", FsRepository.TYPE);
        Map<String, Tuple<Settings, Long>> mountedIndices = mountRandomIndicesWithCache("repo", 1, 3);
        ensureYellow(SNAPSHOT_BLOB_CACHE_INDEX);
        refreshSystemIndex(true);
        assertThat(numberOfEntriesInCache(), equalTo(mountedIndices.values().stream().mapToLong(Tuple::v2).sum()));

        createRepository("other", FsRepository.TYPE);
        Map<String, Tuple<Settings, Long>> otherMountedIndices = mountRandomIndicesWithCache("other", 1, 3);
        refreshSystemIndex(true);
        assertThat(
            numberOfEntriesInCache(),
            equalTo(Stream.concat(mountedIndices.values().stream(), otherMountedIndices.values().stream()).mapToLong(Tuple::v2).sum())
        );

        createRepository("backup", FsRepository.TYPE);
        createSnapshot("backup", "backup", List.of(SNAPSHOT_BLOB_CACHE_INDEX));

        final Set<String> indicesToDelete = new HashSet<>(mountedIndices.keySet());
        indicesToDelete.add(randomFrom(otherMountedIndices.keySet()));

        assertAcked(systemClient().admin().indices().prepareDelete(SNAPSHOT_BLOB_CACHE_INDEX));
        assertAcked(client().admin().indices().prepareDelete(indicesToDelete.toArray(String[]::new)));
        assertAcked(client().admin().cluster().prepareDeleteRepository("repo"));
        ensureClusterStateConsistency();

        refreshSystemIndex(false);
        assertThat(numberOfEntriesInCache(), equalTo(0L));

        assertAcked(client().admin().cluster().prepareUpdateSettings()
            .setTransientSettings(
                Settings.builder()
                    .put(
                        BlobStoreCacheMaintenanceService.SNAPSHOT_SNAPSHOT_CLEANUP_INTERVAL_SETTING.getKey(),
                        TimeValue.timeValueSeconds(1L))
            )
        );
        try {
            final RestoreSnapshotResponse restoreResponse = client().admin()
                .cluster()
                .prepareRestoreSnapshot("backup", "backup")
                .setIndices(SNAPSHOT_BLOB_CACHE_INDEX)
                .setWaitForCompletion(true)
                .get();
            assertThat(restoreResponse.getRestoreInfo().successfulShards(), equalTo(1));
            assertThat(restoreResponse.getRestoreInfo().failedShards(), equalTo(0));

            final long expectNumberOfRemainingCacheEntries = otherMountedIndices.entrySet()
                .stream()
                .filter(e -> indicesToDelete.contains(e.getKey()) == false)
                .mapToLong(e -> e.getValue().v2())
                .sum();

            assertBusy(() -> {
                refreshSystemIndex(true);
                assertThat(numberOfEntriesInCache(), equalTo(expectNumberOfRemainingCacheEntries));
            }, 3000L, TimeUnit.SECONDS);

        } finally {
            assertAcked(client().admin().cluster().prepareUpdateSettings()
                .setTransientSettings(
                    Settings.builder()
                        .putNull(BlobStoreCacheMaintenanceService.SNAPSHOT_SNAPSHOT_CLEANUP_INTERVAL_SETTING.getKey())
                )
            );
        }
    }

    /**
     * @return a {@link Client} that can be used to query the blob store cache system index
     */
    private Client systemClient() {
        return new OriginSettingClient(client(), ClientHelper.SEARCHABLE_SNAPSHOTS_ORIGIN);
    }

    private long numberOfEntriesInCache() {
        return systemClient().prepareSearch(SNAPSHOT_BLOB_CACHE_INDEX)
            .setIndicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN)
            .setTrackTotalHits(true)
            .setSize(0)
            .get()
            .getHits()
            .getTotalHits().value;
    }

    private void refreshSystemIndex(boolean failIfNotExist) {
        try {
            final RefreshResponse refreshResponse = systemClient().admin()
                .indices()
                .prepareRefresh(SNAPSHOT_BLOB_CACHE_INDEX)
                .setIndicesOptions(failIfNotExist ? RefreshRequest.DEFAULT_INDICES_OPTIONS : IndicesOptions.LENIENT_EXPAND_OPEN)
                .get();
            assertThat(refreshResponse.getSuccessfulShards(), failIfNotExist ? greaterThan(0) : greaterThanOrEqualTo(0));
            assertThat(refreshResponse.getFailedShards(), equalTo(0));
        } catch (IndexNotFoundException indexNotFoundException) {
            throw new AssertionError("unexpected", indexNotFoundException);
        }
    }

    private Settings getIndexSettings(String indexName) {
        return client().admin().indices().prepareGetSettings(indexName).get().getIndexToSettings().get(indexName);
    }

    private Map<String, Tuple<Settings, Long>> mountRandomIndicesWithCache(String repositoryName, int min, int max) throws Exception {
        refreshSystemIndex(false);
        long previousNumberOfCachedEntries = numberOfEntriesInCache();

        final int nbIndices = randomIntBetween(min, max);
        logger.info("--> generating [{}] indices with cached entries in system index...", nbIndices);
        final Map<String, Tuple<Settings, Long>> mountedIndices = new HashMap<>();

        int i = 0;
        while (mountedIndices.size() < nbIndices) {
            final String indexName = "index-" + i;
            createIndex(indexName, Settings.builder().put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 0).build());

            final List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
            for (int n = 1000; n > 0; n--) {
                indexRequestBuilders.add(
                    client().prepareIndex(indexName)
                        .setSource(
                            XContentFactory.smileBuilder()
                                .startObject()
                                .field("text_a", randomRealisticUnicodeOfCodepointLength(10))
                                .field("text_b", randomRealisticUnicodeOfCodepointLength(10))
                                .endObject()
                        )
                );
            }
            indexRandom(true, indexRequestBuilders);

            createSnapshot(repositoryName, "snapshot-" + i, List.of(indexName));
            assertAcked(client().admin().indices().prepareDelete(indexName));

            final String mountedIndex = "mounted-index-" + i + "-in-" + repositoryName;
            mountSnapshot(repositoryName, "snapshot-" + i, "index-" + i, mountedIndex, Settings.EMPTY, randomFrom(Storage.values()));

            ensureGreen(mountedIndex);
            assertExecutorIsIdle(SearchableSnapshots.CACHE_FETCH_ASYNC_THREAD_POOL_NAME);
            assertExecutorIsIdle(SearchableSnapshots.CACHE_PREWARMING_THREAD_POOL_NAME);
            waitForBlobCacheFillsToComplete();

            refreshSystemIndex(false);
            final long numberOfEntriesInCache = numberOfEntriesInCache();
            if (numberOfEntriesInCache > previousNumberOfCachedEntries) {
                final long nbEntries = numberOfEntriesInCache - previousNumberOfCachedEntries;
                logger.info("--> mounted index [{}] has [{}] entries in cache", mountedIndex, nbEntries);
                mountedIndices.put(mountedIndex, Tuple.tuple(getIndexSettings(mountedIndex), nbEntries));
            } else {
                logger.info("--> mounted index [{}] did not generate any entry in cache, skipping", mountedIndex);
                assertAcked(client().admin().indices().prepareDelete(mountedIndex));
            }
            previousNumberOfCachedEntries = numberOfEntriesInCache;
            i += 1;
        }
        return Collections.unmodifiableMap(mountedIndices);
    }
}
