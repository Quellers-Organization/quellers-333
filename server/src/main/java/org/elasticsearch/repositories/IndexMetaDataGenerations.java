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

package org.elasticsearch.repositories;

import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.hash.MessageDigests;
import org.elasticsearch.common.io.stream.OutputStreamStreamOutput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.snapshots.SnapshotId;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tracks the blob uuids of blobs containing {@link IndexMetaData} for snapshots as well as the hash of
 * each of these blobs.
 * Before writing a new {@link IndexMetaData} blob during snapshot finalization in
 * {@link org.elasticsearch.repositories.blobstore.BlobStoreRepository#finalizeSnapshot} an instance of {@link IndexMetaData} should be
 * hashed and then used to check if already exists in the repository
 * via {@link #getIndexMetaBlobId(String)}.
 */
public final class IndexMetaDataGenerations {

    public static final IndexMetaDataGenerations EMPTY = new IndexMetaDataGenerations(Collections.emptyMap(), Collections.emptyMap());

    /**
     * Map of a tuple of {@link SnapshotId} and {@link IndexId} to metadata hash.
     */
    final Map<String, String> lookup;

    /**
     * Map of index metadata hash to blob uuid.
     */
    final Map<String, String> hashes;

    IndexMetaDataGenerations(Map<String, String> lookup, Map<String, String> hashes) {
        assert hashes.keySet().equals(Set.copyOf(lookup.values())) :
            "Hash mappings " + hashes +" don't track the same blob ids as the lookup map " + lookup;
        this.lookup = Map.copyOf(lookup);
        this.hashes = Map.copyOf(hashes);
    }

    /**
     * Get the blob id by the hash of {@link IndexMetaData} computed via {@link #hashIndexMetaData} or {@code null} if none is
     * tracked for the hash.
     *
     * @param metaHash hash of {@link IndexMetaData}
     * @return blob id for the given metadata hash or {@code null} if the hash is not part of the repository yet
     */
    @Nullable
    public String getIndexMetaBlobId(String metaHash) {
        return hashes.get(metaHash);
    }

    /**
     * Get the blob id by {@link SnapshotId} and {@link IndexId} and fall back to the value of {@link SnapshotId#getUUID()} if none is
     * known to enable backwards compatibility with versions older than
     * {@link org.elasticsearch.snapshots.SnapshotsService#SHARD_GEN_IN_REPO_DATA_VERSION}.
     *
     * @param snapshotId Snapshot Id
     * @param indexId    Index Id
     * @return blob id for the given index metadata
     */
    public String indexMetaBlobId(SnapshotId snapshotId, IndexId indexId) {
        final String hash = lookup.get(lookupKey(snapshotId, indexId));
        if (hash == null) {
            return snapshotId.getUUID();
        } else {
            return hashes.get(hash);
        }
    }

    /**
     * Create a new instance with the snapshot and index metadata uuids and hashes added.
     *
     * @param snapshotId SnapshotId
     * @param newLookup new mappings of index + snapshot to index metadata hash
     * @param newHashes new mappings of index metadata hash to blob id
     * @return instance with added snapshot
     */
    public IndexMetaDataGenerations withAddedSnapshot(SnapshotId snapshotId, Map<IndexId, String> newLookup,
                                                      Map<String, String> newHashes) {
        final Map<String, String> updatedIndexMetaLookup = new HashMap<>(this.lookup);
        final Map<String, String> updatedIndexMetaHashes = new HashMap<>(hashes);
        updatedIndexMetaHashes.putAll(newHashes);
        updatedIndexMetaLookup.putAll(newLookup.entrySet().stream().collect(
            Collectors.toMap(e -> IndexMetaDataGenerations.lookupKey(snapshotId, e.getKey()), Map.Entry::getValue)));
        return new IndexMetaDataGenerations(updatedIndexMetaLookup, updatedIndexMetaHashes);
    }

    /**
     * Create a new instance with the given snapshot removed.
     *
     * @param snapshotId SnapshotId
     * @param indexIds indices in the snapshot
     * @return new instance without the given snapshot
     */
    public IndexMetaDataGenerations withRemovedSnapshot(SnapshotId snapshotId, Collection<IndexId> indexIds) {
        final Map<String, String> updatedIndexMetaLookup = new HashMap<>(lookup);
        for (final IndexId indexId : indexIds) {
            updatedIndexMetaLookup.remove(lookupKey(snapshotId, indexId));
        }
        final Map<String, String> updatedIndexMetaDataHashes = new HashMap<>(hashes);
        updatedIndexMetaDataHashes.keySet().removeIf(k -> updatedIndexMetaLookup.containsValue(k) == false);
        return new IndexMetaDataGenerations(updatedIndexMetaLookup, updatedIndexMetaDataHashes);
    }

    /**
     * Serialize and return the hex encoded SHA256 of {@link IndexMetaData}.
     *
     * @param indexMetaData IndexMetaData
     * @return hex encoded SHA-256
     */
    public static String hashIndexMetaData(IndexMetaData indexMetaData) {
        MessageDigest digest = MessageDigests.sha256();
        try (StreamOutput hashOut = new OutputStreamStreamOutput(new OutputStream() {
            @Override
            public void write(int b) {
                digest.update((byte) b);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                digest.update(b, off, len);
            }
        })) {
            indexMetaData.writeTo(hashOut);
        } catch (IOException e) {
            throw new AssertionError("No actual IO happens here", e);
        }
        return MessageDigests.toHexString(digest.digest());
    }

    private static String lookupKey(SnapshotId snapshotId, IndexId indexId) {
        return snapshotId.getUUID() + "-" + indexId.getId();
    }
}
