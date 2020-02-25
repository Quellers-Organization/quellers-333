/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.core.ilm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.UUIDs;
import org.elasticsearch.index.Index;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.elasticsearch.xpack.core.ilm.LifecycleExecutionState.ILM_CUSTOM_METADATA_KEY;
import static org.elasticsearch.xpack.core.ilm.LifecycleExecutionState.fromIndexMetadata;

/**
 * Generates a snapshot name for the given index and records it in the index metadata.
 */
public class GenerateSnapshotNameStep extends ClusterStateActionStep {

    public static final String NAME = "generate-snapshot-name";

    private static final Logger logger = LogManager.getLogger(CreateSnapshotStep.class);

    private static final IndexNameExpressionResolver.DateMathExpressionResolver DATE_MATH_RESOLVER =
        new IndexNameExpressionResolver.DateMathExpressionResolver();

    public GenerateSnapshotNameStep(StepKey key, StepKey nextStepKey) {
        super(key, nextStepKey);
    }

    @Override
    public ClusterState performAction(Index index, ClusterState clusterState) {
        IndexMetaData indexMetaData = clusterState.metaData().index(index);
        if (indexMetaData == null) {
            // Index must have been since deleted, ignore it
            logger.debug("[{}] lifecycle action for index [{}] executed but index no longer exists", getKey().getAction(), index.getName());
            return clusterState;
        }

        ClusterState.Builder newClusterStateBuilder = ClusterState.builder(clusterState);

        LifecycleExecutionState lifecycleState = fromIndexMetadata(indexMetaData);
        assert lifecycleState.getSnapshotName() == null : "index " + index.getName() + " should not have a snapshot generated by " +
            "the ilm policy but has " + lifecycleState.getSnapshotName();
        LifecycleExecutionState.Builder newCustomData = LifecycleExecutionState.builder(lifecycleState);
        String policy = indexMetaData.getSettings().get(LifecycleSettings.LIFECYCLE_NAME);
        String snapshotNamePrefix = ("<{now/d}-" + index.getName() + "-" + policy + ">").toLowerCase(Locale.ROOT);
        String snapshotName = generateSnapshotName(snapshotNamePrefix);
        ActionRequestValidationException validationException = validateGeneratedSnapshotName(snapshotNamePrefix, snapshotName);
        if (validationException != null) {
            logger.debug("unable to generate a snapshot name as part of policy [{}] for index [{}] due to [{}]",
                policy, index.getName(), validationException.getMessage());
            throw validationException;
        }
        newCustomData.setSnapshotName(snapshotName);

        IndexMetaData.Builder indexMetadataBuilder = IndexMetaData.builder(indexMetaData);
        indexMetadataBuilder.putCustom(ILM_CUSTOM_METADATA_KEY, newCustomData.build().asMap());
        newClusterStateBuilder.metaData(MetaData.builder(clusterState.getMetaData()).put(indexMetadataBuilder));
        return newClusterStateBuilder.build();
    }

    /**
     * Since snapshots need to be uniquely named, this method will resolve any date math used in
     * the provided name, as well as appending a unique identifier so expressions that may overlap
     * still result in unique snapshot names.
     */
    public static String generateSnapshotName(String name) {
        return generateSnapshotName(name, new ResolverContext());
    }

    public static String generateSnapshotName(String name, IndexNameExpressionResolver.Context context) {
        List<String> candidates = DATE_MATH_RESOLVER.resolve(context, Collections.singletonList(name));
        if (candidates.size() != 1) {
            throw new IllegalStateException("resolving snapshot name " + name + " generated more than one candidate: " + candidates);
        }
        // TODO: we are breaking the rules of UUIDs by lowercasing this here, find an alternative (snapshot names must be lowercase)
        return candidates.get(0) + "-" + UUIDs.randomBase64UUID().toLowerCase(Locale.ROOT);
    }

    /**
     * This is a context for the DateMathExpressionResolver, which does not require
     * {@code IndicesOptions} or {@code ClusterState} since it only uses the start
     * time to resolve expressions
     */
    public static final class ResolverContext extends IndexNameExpressionResolver.Context {
        public ResolverContext() {
            this(System.currentTimeMillis());
        }

        public ResolverContext(long startTime) {
            super(null, null, startTime, false, false);
        }

        @Override
        public ClusterState getState() {
            throw new UnsupportedOperationException("should never be called");
        }

        @Override
        public IndicesOptions getOptions() {
            throw new UnsupportedOperationException("should never be called");
        }
    }

    @Nullable
    public static ActionRequestValidationException validateGeneratedSnapshotName(String snapshotPrefix, String snapshotName) {
        ActionRequestValidationException err = new ActionRequestValidationException();
        if (Strings.hasText(snapshotPrefix) == false) {
            err.addValidationError("invalid snapshot name [" + snapshotPrefix + "]: cannot be empty");
        }
        if (snapshotName.contains("#")) {
            err.addValidationError("invalid snapshot name [" + snapshotPrefix + "]: must not contain '#'");
        }
        if (snapshotName.charAt(0) == '_') {
            err.addValidationError("invalid snapshot name [" + snapshotPrefix + "]: must not start with '_'");
        }
        if (snapshotName.toLowerCase(Locale.ROOT).equals(snapshotName) == false) {
            err.addValidationError("invalid snapshot name [" + snapshotPrefix + "]: must be lowercase");
        }
        if (Strings.validFileName(snapshotName) == false) {
            err.addValidationError("invalid snapshot name [" + snapshotPrefix + "]: must not contain contain the following characters " +
                Strings.INVALID_FILENAME_CHARS);
        }

        if (err.validationErrors().size() > 0) {
            return err;
        } else {
            return null;
        }
    }

}
