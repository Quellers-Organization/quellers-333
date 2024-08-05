/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.rank.random;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.search.rank.context.RankFeaturePhaseRankCoordinatorContext;
import org.elasticsearch.search.rank.feature.RankFeatureDoc;

import java.util.Arrays;
import java.util.Comparator;

/**
 * A {@code RankFeaturePhaseRankCoordinatorContext} that performs a rerank inference call to determine relevance scores for documents within
 * the provided rank window.
 */
public class RandomRankFeaturePhaseRankCoordinatorContext extends RankFeaturePhaseRankCoordinatorContext {

    protected final Float minScore;

    public RandomRankFeaturePhaseRankCoordinatorContext(int size, int from, int rankWindowSize, Float minScore) {
        super(size, from, rankWindowSize);
        this.minScore = minScore;
    }

    @Override
    protected void computeScores(RankFeatureDoc[] featureDocs, ActionListener<float[]> scoreListener) {
        // Generate random scores
        float[] scores = new float[featureDocs.length];
        for (int i = 0; i < featureDocs.length; i++) {
            scores[i] = (float) Math.random();
        }
        scoreListener.onResponse(scores);
    }

    /**
     * Sorts documents by score descending and discards those with a score less than minScore.
     * @param originalDocs documents to process
     */
    @Override
    protected RankFeatureDoc[] preprocess(RankFeatureDoc[] originalDocs) {
        return Arrays.stream(originalDocs)
            .filter(doc -> minScore == null || doc.score >= minScore)
            .sorted(Comparator.comparing((RankFeatureDoc doc) -> doc.score).reversed())
            .toArray(RankFeatureDoc[]::new);
    }

}
