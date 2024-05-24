/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.search.rank.rerank;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.document.DocumentField;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.rank.RankShardResult;
import org.elasticsearch.search.rank.context.RankFeaturePhaseRankShardContext;
import org.elasticsearch.search.rank.feature.RankFeatureDoc;
import org.elasticsearch.search.rank.feature.RankFeatureShardResult;

import java.util.Arrays;

/**
 * {@link RerankingRankFeaturePhaseRankShardContext} is running on each shard is responsible for extracting string data for a set of docids
 * for a given field, and pass them back as {@link RankFeatureShardResult}.
 */
public class RerankingRankFeaturePhaseRankShardContext extends RankFeaturePhaseRankShardContext {

    private static final Logger logger = LogManager.getLogger(RerankingRankFeaturePhaseRankShardContext.class);

    public RerankingRankFeaturePhaseRankShardContext(String field) {
        super(field);
    }

    // This currently makes use of a new FetchContext initialized just with the FetchFieldsPhase processor, so that we build
    // search hits containing info on just the requested field. This should probably need to be revisited and maybe reworked.
    @Nullable
    public RankShardResult buildRankFeatureShardResult(SearchHits hits, int shardId) {
        try {
            RankFeatureDoc[] rankFeatureDocs = new RankFeatureDoc[hits.getHits().length];
            for (int i = 0; i < hits.getHits().length; i++) {
                rankFeatureDocs[i] = new RankFeatureDoc(hits.getHits()[i].docId(), hits.getHits()[i].getScore(), shardId);
                DocumentField docField = hits.getHits()[i].field(field);
                if (docField != null) {
                    rankFeatureDocs[i].featureData(docField.getValue().toString());
                }
            }
            return new RankFeatureShardResult(rankFeatureDocs);
        } catch (Exception ex) {
            logger.info(
                "Error while fetching feature data for {field: "
                    + field
                    + "} and {docids: "
                    + Arrays.stream(hits.getHits()).map(SearchHit::docId).toList()
                    + "}.",
                ex
            );
            return null;
        }
    }
}
