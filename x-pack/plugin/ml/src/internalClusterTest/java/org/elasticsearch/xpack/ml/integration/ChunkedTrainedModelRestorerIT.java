/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.ml.integration;

import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.xpack.core.ml.inference.TrainedModelConfig;
import org.elasticsearch.xpack.core.ml.inference.persistence.InferenceIndexConstants;
import org.elasticsearch.xpack.ml.MlSingleNodeTestCase;
import org.elasticsearch.xpack.ml.inference.persistence.ChunkedTrainedModelRestorer;
import org.elasticsearch.xpack.ml.inference.persistence.TrainedModelDefinitionDoc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class ChunkedTrainedModelRestorerIT extends MlSingleNodeTestCase {

    public void testRestoreWithMultipleSearches() throws IOException, InterruptedException {
        String modelId = "test-multiple-searches";
        int numDocs = 22;
        List<String> modelDefs = new ArrayList<>(numDocs);

        for (int i=0; i<numDocs; i++) {
            // actual content of the model definition is not important here
            modelDefs.add("model_def_" + i);
        }

        List<TrainedModelDefinitionDoc> expectedDocs = createModelDefinitionDocs(modelDefs, modelId);
        putModelDefinitions(expectedDocs, InferenceIndexConstants.LATEST_INDEX_NAME, 0);


        ChunkedTrainedModelRestorer restorer = new ChunkedTrainedModelRestorer(modelId, client(), xContentRegistry());
        restorer.setSearchSize(5);
        List<TrainedModelDefinitionDoc> actualDocs = new ArrayList<>();

        AtomicReference<Exception> exceptionHolder = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        restorer.restoreModelDefinition(
            actualDocs::add,
            success -> latch.countDown(),
            failure -> {exceptionHolder.set(failure); latch.countDown();});

        latch.await();

        assertNull(exceptionHolder.get());
        assertEquals(actualDocs, expectedDocs);
    }

    public void testRestoreWithDocumentsInMultipleIndices() throws IOException, InterruptedException {
        String index1 = "foo-1";
        String index2 = "foo-2";

        for (String index : new String[]{index1, index2}) {
            client().admin().indices().prepareCreate(index)
                .setMapping(TrainedModelDefinitionDoc.DEFINITION.getPreferredName(), "type=binary",
                    InferenceIndexConstants.DOC_TYPE.getPreferredName(), "type=keyword",
                    TrainedModelConfig.MODEL_ID.getPreferredName(), "type=keyword").get();
        }

        String modelId = "test-multiple-indices";
        int numDocs = 24;
        List<String> modelDefs = new ArrayList<>(numDocs);

        for (int i=0; i<numDocs; i++) {
            // actual content of the model definition is not important here
            modelDefs.add("model_def_" + i);
        }

        List<TrainedModelDefinitionDoc> expectedDocs = createModelDefinitionDocs(modelDefs, modelId);
        int splitPoint = (numDocs / 2) -1;
        putModelDefinitions(expectedDocs.subList(0, splitPoint), index1, 0);
        putModelDefinitions(expectedDocs.subList(splitPoint, numDocs), index2, splitPoint);

        ChunkedTrainedModelRestorer restorer = new ChunkedTrainedModelRestorer(modelId, client(), xContentRegistry());
        restorer.setSearchSize(10);
        restorer.setSearchIndex("foo-*");

        AtomicReference<Exception> exceptionHolder = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        List<TrainedModelDefinitionDoc> actualDocs = new ArrayList<>();

        restorer.restoreModelDefinition(
            actualDocs::add,
            success -> latch.countDown(),
            failure -> {exceptionHolder.set(failure); latch.countDown();});

        latch.await();

        assertNull(exceptionHolder.get());
        // TODO this fails because the results are sorted by index first
        assertEquals(actualDocs, expectedDocs);
    }

    private List<TrainedModelDefinitionDoc> createModelDefinitionDocs(List<String> compressedDefinitions, String modelId) {
        int totalLength = compressedDefinitions.stream().map(String::length).reduce(0, Integer::sum);

        List<TrainedModelDefinitionDoc> docs = new ArrayList<>();
        for (int i = 0; i < compressedDefinitions.size(); i++) {
            docs.add(new TrainedModelDefinitionDoc.Builder()
                .setDocNum(i)
                .setCompressedString(compressedDefinitions.get(i))
                .setCompressionVersion(TrainedModelConfig.CURRENT_DEFINITION_COMPRESSION_VERSION)
                .setTotalDefinitionLength(totalLength)
                .setDefinitionLength(compressedDefinitions.get(i).length())
                .setEos(i == compressedDefinitions.size() - 1)
                .setModelId(modelId)
                .build());
        }

        return docs;
    }

    private void putModelDefinitions(List<TrainedModelDefinitionDoc> docs, String index, int startingDocNum) throws IOException {
        BulkRequestBuilder bulkRequestBuilder = client().prepareBulk();
        for (int i = 0; i < docs.size(); i++) {
            TrainedModelDefinitionDoc doc = docs.get(i);
            try (XContentBuilder xContentBuilder = doc.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS)) {
                IndexRequestBuilder indexRequestBuilder = client().prepareIndex(index)
                    .setSource(xContentBuilder)
                    .setId(TrainedModelDefinitionDoc.docId(doc.getModelId(), startingDocNum++));

                bulkRequestBuilder.add(indexRequestBuilder);
            }
        }

        BulkResponse bulkResponse = bulkRequestBuilder
            .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
            .get();
        if (bulkResponse.hasFailures()) {
            int failures = 0;
            for (BulkItemResponse itemResponse : bulkResponse) {
                if (itemResponse.isFailed()) {
                    failures++;
                    logger.error("Item response failure [{}]", itemResponse.getFailureMessage());
                }
            }
            fail("Bulk response contained " + failures + " failures");
        }
    }
}
