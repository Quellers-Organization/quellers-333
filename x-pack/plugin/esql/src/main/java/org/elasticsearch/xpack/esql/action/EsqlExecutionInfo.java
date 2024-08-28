/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.action;

import org.elasticsearch.action.search.ShardSearchFailure;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.util.concurrent.ConcurrentCollections;
import org.elasticsearch.core.Predicates;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.rest.action.RestActions;
import org.elasticsearch.transport.RemoteClusterAware;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.ToXContentFragment;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Holds execution metadata about ES|QL queries.
 * The Cluster object is patterned after the SearchResponse.Cluster object.
 */
public class EsqlExecutionInfo implements ToXContentFragment {
    // for cross-cluster scenarios where cluster names are shown in API responses, use this string
    // rather than empty string (RemoteClusterAware.LOCAL_CLUSTER_GROUP_KEY) we use internally
    public static final String LOCAL_CLUSTER_NAME_REPRESENTATION = "(local)";

    public static final ParseField _CLUSTERS_FIELD = new ParseField("_clusters");
    public static final ParseField TOTAL_FIELD = new ParseField("total");
    public static final ParseField SUCCESSFUL_FIELD = new ParseField("successful");
    public static final ParseField SKIPPED_FIELD = new ParseField("skipped");
    public static final ParseField RUNNING_FIELD = new ParseField("running");
    public static final ParseField PARTIAL_FIELD = new ParseField("partial");
    public static final ParseField FAILED_FIELD = new ParseField("failed");

    // key to map is clusterAlias on the primary querying cluster of a CCS minimize_roundtrips=true query
    // the Map itself is immutable after construction - all Clusters will be accounted for at the start of the search
    // updates to the Cluster occur with the updateCluster method that given the key to map transforms an
    // old Cluster Object to a new Cluster Object with the remapping function.
    public final Map<String, Cluster> clusterInfo;
    private Predicate<String> skipUnavailablePredicate;

    public EsqlExecutionInfo() {
        this(Predicates.always());  // default all clusters to skip_unavailable=true
    }

    /**
     * TODO: DOCUMENT this param and why needed
     * @param skipUnavailablePredicate
     */
    public EsqlExecutionInfo(Predicate<String> skipUnavailablePredicate) {
        this.clusterInfo = ConcurrentCollections.newConcurrentMap();  // MP TODO: does this need to be a ConcurrentHashMap?
        this.skipUnavailablePredicate = skipUnavailablePredicate;
    }

    // MP TODO: is there a better way to supply this info? Awkward to have it here
    /**
     * @param clusterAlias to lookup skip_unavailable from
     * @return skip_unavailable setting (true/false)
     * @throws org.elasticsearch.transport.NoSuchRemoteClusterException if clusterAlias is unknown to this node's RemoteClusterService
     */
    public boolean isSkipUnavailable(String clusterAlias) {
        if (RemoteClusterAware.LOCAL_CLUSTER_GROUP_KEY.equals(clusterAlias)) {
            return false;
        }
        return skipUnavailablePredicate.test(clusterAlias);
    }

    public Cluster getCluster(String clusterAlias) {
        return clusterInfo.get(clusterAlias);
    }

    // MP FIXME: remove this function once switch over to using the one below
    @Deprecated
    public void swapCluster(Cluster cluster) {
        // MP TODO: this probably needs to follow the thread safe swapCluster model in SearchResponse, but doing fast-n-easy very for now
        clusterInfo.put(cluster.getClusterAlias(), cluster);
    }

    /**
     * Utility to swap a Cluster object. Guidelines for the remapping function:
     * <ul>
     * <li> The remapping function should return a new Cluster object to swap it for
     * the existing one.</li>
     * <li> If in the remapping function you decide to abort the swap you must return
     * the original Cluster object to keep the map unchanged.</li>
     * <li> Do not return {@code null}. If the remapping function returns {@code null},
     * the mapping is removed (or remains absent if initially absent).</li>
     * <li> If the remapping function itself throws an (unchecked) exception, the exception
     * is rethrown, and the current mapping is left unchanged. Throwing exception therefore
     * is OK, but it is generally discouraged.</li>
     * <li> The remapping function may be called multiple times in a CAS fashion underneath,
     * make sure that is safe to do so.</li>
     * </ul>
     * @param clusterAlias key with which the specified value is associated
     * @param remappingFunction function to swap the oldCluster to a newCluster
     * @return the new Cluster object
     */
    public Cluster swapCluster(String clusterAlias, BiFunction<String, Cluster, Cluster> remappingFunction) {
        return clusterInfo.compute(clusterAlias, remappingFunction);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        if (clusterInfo.size() > 0) {
            builder.startObject(_CLUSTERS_FIELD.getPreferredName());
            builder.field(TOTAL_FIELD.getPreferredName(), clusterInfo.size());
            builder.field(SUCCESSFUL_FIELD.getPreferredName(), getClusterStateCount(Cluster.Status.SUCCESSFUL));
            builder.field(SKIPPED_FIELD.getPreferredName(), getClusterStateCount(Cluster.Status.SKIPPED));
            builder.field(RUNNING_FIELD.getPreferredName(), getClusterStateCount(Cluster.Status.RUNNING));
            builder.field(PARTIAL_FIELD.getPreferredName(), getClusterStateCount(Cluster.Status.PARTIAL));
            builder.field(FAILED_FIELD.getPreferredName(), getClusterStateCount(Cluster.Status.FAILED));
            if (clusterInfo.size() > 0) {
                builder.startObject("details");
                for (Cluster cluster : clusterInfo.values()) {
                    cluster.toXContent(builder, params);
                }
                builder.endObject();
            }
            builder.endObject();
        }
        return builder;
    }

    /**
     * @param status the state you want to query
     * @return how many clusters are currently in a specific state
     */
    public int getClusterStateCount(Cluster.Status status) {
        assert clusterInfo.size() > 0 : "ClusterMap in EsqlExecutionInfo must not be empty";
        return (int) clusterInfo.values().stream().filter(cluster -> cluster.getStatus() == status).count();
    }

    @Override
    public String toString() {
        return "EsqlExecutionInfo{" + "clusters=" + clusterInfo + '}';
    }

    /**
     * Represents the search metadata about a particular cluster involved in a cross-cluster search.
     * The Cluster object can represent either the local cluster or a remote cluster.
     * For the local cluster, clusterAlias should be specified as RemoteClusterAware.LOCAL_CLUSTER_GROUP_KEY.
     * Its XContent is put into the "details" section the "_clusters" entry in the REST query response.
     * This is an immutable class, so updates made during the search progress (especially important for async
     * CCS searches) must be done by replacing the Cluster object with a new one.
     */
    public static class Cluster implements ToXContentFragment, Writeable {
        public static final ParseField INDICES_FIELD = new ParseField("indices");
        public static final ParseField STATUS_FIELD = new ParseField("status");
        public static final ParseField TOOK = new ParseField("took");

        private final String clusterAlias;
        private final String indexExpression; // original index expression from the user for this cluster
        private final boolean skipUnavailable;
        private final Cluster.Status status;
        private final Integer totalShards;
        private final Integer successfulShards;
        private final Integer skippedShards;
        private final Integer failedShards;
        private final List<ShardSearchFailure> failures;
        private final TimeValue took;  // search latency in millis for this cluster sub-search

        /**
         * Marks the status of a Cluster search involved in a Cross-Cluster search.
         */
        public enum Status {
            RUNNING,     // still running
            SUCCESSFUL,  // all shards completed search
            PARTIAL,     // only some shards completed the search, partial results from cluster
            SKIPPED,     // entire cluster was skipped
            FAILED;      // search was failed due to errors on this cluster

            @Override
            public String toString() {
                return this.name().toLowerCase(Locale.ROOT);
            }
        }

        public Cluster(String clusterAlias, String indexExpression) {
            this(clusterAlias, indexExpression, true, Cluster.Status.RUNNING, null, null, null, null, null, null);
        }

        /**
         * Create a Cluster object representing the initial RUNNING state of a Cluster.
         *
         * @param clusterAlias clusterAlias as defined in the remote cluster settings or RemoteClusterAware.LOCAL_CLUSTER_GROUP_KEY
         *                     for the local cluster
         * @param indexExpression the original (not resolved/concrete) indices expression provided for this cluster.
         * @param skipUnavailable whether this Cluster is marked as skip_unavailable in remote cluster settings
         */
        public Cluster(String clusterAlias, String indexExpression, boolean skipUnavailable) {
            this(clusterAlias, indexExpression, skipUnavailable, Cluster.Status.RUNNING, null, null, null, null, null, null);
        }

        /**
         * Create a Cluster with a new Status and one or more ShardSearchFailures. This constructor
         * should only be used for fatal failures where shard counters (total, successful, skipped, failed)
         * are not known (unset).
         * @param clusterAlias clusterAlias as defined in the remote cluster settings or RemoteClusterAware.LOCAL_CLUSTER_GROUP_KEY
         *                     for the local cluster
         * @param indexExpression the original (not resolved/concrete) indices expression provided for this cluster.
         * @param skipUnavailable whether cluster is marked as skip_unavailable in remote cluster settings
         * @param status current status of the search on this Cluster
         * @param failures list of failures that occurred during the search on this Cluster
         */
        public Cluster(
            String clusterAlias,
            String indexExpression,
            boolean skipUnavailable,
            Cluster.Status status,
            List<ShardSearchFailure> failures
        ) {
            this(clusterAlias, indexExpression, skipUnavailable, status, null, null, null, null, failures, null);
        }

        public Cluster(
            String clusterAlias,
            String indexExpression,
            boolean skipUnavailable,
            Cluster.Status status,
            Integer totalShards,
            Integer successfulShards,
            Integer skippedShards,
            Integer failedShards,
            List<ShardSearchFailure> failures,
            TimeValue took
        ) {
            assert clusterAlias != null : "clusterAlias cannot be null";
            assert indexExpression != null : "indexExpression of Cluster cannot be null";
            assert status != null : "status of Cluster cannot be null";
            this.clusterAlias = clusterAlias;
            this.indexExpression = indexExpression;
            this.skipUnavailable = skipUnavailable;
            this.status = status;
            this.totalShards = totalShards;
            this.successfulShards = successfulShards;
            this.skippedShards = skippedShards;
            this.failedShards = failedShards;
            this.failures = failures == null ? Collections.emptyList() : Collections.unmodifiableList(failures);
            this.took = took;
        }

        public Cluster(StreamInput in) throws IOException {
            this.clusterAlias = in.readString();
            this.indexExpression = in.readString();
            this.status = Cluster.Status.valueOf(in.readString().toUpperCase(Locale.ROOT));
            this.totalShards = in.readOptionalInt();
            this.successfulShards = in.readOptionalInt();
            this.skippedShards = in.readOptionalInt();
            this.failedShards = in.readOptionalInt();
            Long took = in.readOptionalLong();
            if (took == null) {
                this.took = null;
            } else {
                this.took = new TimeValue(took);
            }
            this.failures = Collections.unmodifiableList(in.readCollectionAsList(ShardSearchFailure::readShardSearchFailure));
            this.skipUnavailable = in.readBoolean();
        }

        /**
         * Since the Cluster object is immutable, use this Builder class to create
         * a new Cluster object using the "copyFrom" Cluster passed in and set only
         * changed values.
         *
         * Since the clusterAlias, indexExpression and skipUnavailable fields are
         * never changed once set, this Builder provides no setter method for them.
         * All other fields can be set and override the value in the "copyFrom" Cluster.
         */
        public static class Builder {
            private String indexExpression;
            private Cluster.Status status;
            private Integer totalShards;
            private Integer successfulShards;
            private Integer skippedShards;
            private Integer failedShards;
            private List<ShardSearchFailure> failures;
            private TimeValue took;
            private final Cluster original;

            public Builder(Cluster copyFrom) {
                this.original = copyFrom;
            }

            /**
             * @return new Cluster object using the new values passed in via setters
             *         or the values in the "copyFrom" Cluster object set in the
             *         Builder constructor.
             */
            public Cluster build() {
                return new Cluster(
                    original.getClusterAlias(),
                    indexExpression == null ? original.getIndexExpression() : indexExpression,
                    original.isSkipUnavailable(),
                    status != null ? status : original.getStatus(),
                    totalShards != null ? totalShards : original.getTotalShards(),
                    successfulShards != null ? successfulShards : original.getSuccessfulShards(),
                    skippedShards != null ? skippedShards : original.getSkippedShards(),
                    failedShards != null ? failedShards : original.getFailedShards(),
                    failures != null ? failures : original.getFailures(),
                    took != null ? took : original.getTook()
                );
            }

            public Cluster.Builder setIndexExpression(String indexExpression) {
                this.indexExpression = indexExpression;
                return this;
            }

            public Cluster.Builder setStatus(Cluster.Status status) {
                this.status = status;
                return this;
            }

            public Cluster.Builder setTotalShards(int totalShards) {
                this.totalShards = totalShards;
                return this;
            }

            public Cluster.Builder setSuccessfulShards(int successfulShards) {
                this.successfulShards = successfulShards;
                return this;
            }

            public Cluster.Builder setSkippedShards(int skippedShards) {
                this.skippedShards = skippedShards;
                return this;
            }

            public Cluster.Builder setFailedShards(int failedShards) {
                this.failedShards = failedShards;
                return this;
            }

            public Cluster.Builder setFailures(List<ShardSearchFailure> failures) {
                this.failures = failures;
                return this;
            }

            public Cluster.Builder setTook(TimeValue took) {
                this.took = took;
                return this;
            }
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeString(clusterAlias);
            out.writeString(indexExpression);
            out.writeString(status.toString());
            out.writeOptionalInt(totalShards);
            out.writeOptionalInt(successfulShards);
            out.writeOptionalInt(skippedShards);
            out.writeOptionalInt(failedShards);
            out.writeOptionalLong(took == null ? null : took.millis());
            out.writeCollection(failures);
            out.writeBoolean(skipUnavailable);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            String name = clusterAlias;
            if (clusterAlias.equals("")) {
                name = LOCAL_CLUSTER_NAME_REPRESENTATION;
            }
            builder.startObject(name);
            {
                builder.field(STATUS_FIELD.getPreferredName(), getStatus().toString());
                builder.field(INDICES_FIELD.getPreferredName(), indexExpression);
                if (took != null) {
                    builder.field(TOOK.getPreferredName(), took.millis());
                }
                if (totalShards != null) {
                    builder.startObject(RestActions._SHARDS_FIELD.getPreferredName());
                    builder.field(RestActions.TOTAL_FIELD.getPreferredName(), totalShards);
                    if (successfulShards != null) {
                        builder.field(RestActions.SUCCESSFUL_FIELD.getPreferredName(), successfulShards);
                    }
                    if (skippedShards != null) {
                        builder.field(RestActions.SKIPPED_FIELD.getPreferredName(), skippedShards);
                    }
                    if (failedShards != null) {
                        builder.field(RestActions.FAILED_FIELD.getPreferredName(), failedShards);
                    }
                    builder.endObject();
                }
                if (failures != null && failures.size() > 0) {
                    builder.startArray(RestActions.FAILURES_FIELD.getPreferredName());
                    for (ShardSearchFailure failure : failures) {
                        failure.toXContent(builder, params);
                    }
                    builder.endArray();
                }
            }
            builder.endObject();
            return builder;
        }

        public String getClusterAlias() {
            return clusterAlias;
        }

        public String getIndexExpression() {
            return indexExpression;
        }

        public boolean isSkipUnavailable() {
            return skipUnavailable;
        }

        public Cluster.Status getStatus() {
            return status;
        }

        public List<ShardSearchFailure> getFailures() {
            return failures;
        }

        public TimeValue getTook() {
            return took;
        }

        public Integer getTotalShards() {
            return totalShards;
        }

        public Integer getSuccessfulShards() {
            return successfulShards;
        }

        public Integer getSkippedShards() {
            return skippedShards;
        }

        public Integer getFailedShards() {
            return failedShards;
        }

        @Override
        public String toString() {
            return "Cluster{"
                + "alias='"
                + clusterAlias
                + '\''
                + ", status="
                + status
                + ", totalShards="
                + totalShards
                + ", successfulShards="
                + successfulShards
                + ", skippedShards="
                + skippedShards
                + ", failedShards="
                + failedShards
                + ", failures(sz)="
                + failures.size()
                + ", took="
                + took
                + ", indexExpression='"
                + indexExpression
                + '\''
                + ", skipUnavailable="
                + skipUnavailable
                + '}';
        }
    }
}
