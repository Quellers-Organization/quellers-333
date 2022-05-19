/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.action.update;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.internal.Requests;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.core.Tuple;
import org.elasticsearch.index.VersionType;
import org.elasticsearch.index.engine.DocumentMissingException;
import org.elasticsearch.index.engine.DocumentSourceMissingException;
import org.elasticsearch.index.get.GetResult;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.mapper.RoutingFieldMapper;
import org.elasticsearch.index.shard.IndexShard;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.script.UpdateScript;
import org.elasticsearch.script.field.Op;
import org.elasticsearch.search.lookup.SourceLookup;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentType;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Helper for translating an update request to an index, delete request or update response.
 */
public class UpdateHelper {

    private static final Logger logger = LogManager.getLogger(UpdateHelper.class);

    private final ScriptService scriptService;

    public UpdateHelper(ScriptService scriptService) {
        this.scriptService = scriptService;
    }

    /**
     * Prepares an update request by converting it into an index or delete request or an update response (no action).
     */
    public Result prepare(UpdateRequest request, IndexShard indexShard, LongSupplier nowInMillis) throws IOException {
        final GetResult getResult = indexShard.getService().getForUpdate(request.id(), request.ifSeqNo(), request.ifPrimaryTerm());
        return prepare(indexShard.shardId(), request, getResult, nowInMillis);
    }

    /**
     * Prepares an update request by converting it into an index or delete request or an update response (no action, in the event of a
     * noop).
     */
    protected Result prepare(ShardId shardId, UpdateRequest request, final GetResult getResult, LongSupplier nowInMillis) {
        if (getResult.isExists() == false) {
            // If the document didn't exist, execute the update request as an upsert
            return prepareUpsert(shardId, request, getResult, nowInMillis);
        } else if (getResult.internalSourceRef() == null) {
            // no source, we can't do anything, throw a failure...
            throw new DocumentSourceMissingException(shardId, request.id());
        } else if (request.script() == null && request.doc() != null) {
            // The request has no script, it is a new doc that should be merged with the old document
            return prepareUpdateIndexRequest(shardId, request, getResult, request.detectNoop());
        } else {
            // The request has a script (or empty script), execute the script and prepare a new index request
            return prepareUpdateScriptRequest(shardId, request, getResult, nowInMillis);
        }
    }

    /**
     * Execute a scripted upsert, where there is an existing upsert document and a script to be executed. The script is executed and a new
     * Tuple of operation and updated {@code _source} is returned.
     */
    Tuple<Op, Map<String, Object>> executeScriptedUpsert(Script script, UpdateScript.Metadata metadata) {
        // Tell the script that this is a create and not an update
        UpdateScript.Metadata md = executeScript(script, metadata); // TODO(stu): this is insert from upsert
        return new Tuple<>(lenientGetOp(md, logger, script.getIdOrCode()), md.getSource());
    }

    /**
     * Prepare the request for upsert, executing the upsert script if present, and returning a {@code Result} containing a new
     * {@code IndexRequest} to be executed on the primary and replicas.
     */
    Result prepareUpsert(ShardId shardId, UpdateRequest request, final GetResult getResult, LongSupplier nowInMillis) {
        if (request.upsertRequest() == null && request.docAsUpsert() == false) {
            throw new DocumentMissingException(shardId, request.id());
        }
        IndexRequest indexRequest = request.docAsUpsert() ? request.doc() : request.upsertRequest();
        if (request.scriptedUpsert() && request.script() != null) {
            // Run the script to perform the create logic
            IndexRequest upsert = request.upsertRequest();
            Tuple<Op, Map<String, Object>> upsertResult = executeScriptedUpsert(
                request.script,
                new UpdateScript.Metadata(getResult.getIndex(), getResult.getId(), Op.CREATE, nowInMillis.getAsLong(), upsert.sourceAsMap())
            );
            switch (upsertResult.v1()) {
                case CREATE -> indexRequest = Requests.indexRequest(request.index()).source(upsertResult.v2());
                case NOOP -> {
                    UpdateResponse update = new UpdateResponse(
                        shardId,
                        getResult.getId(),
                        getResult.getSeqNo(),
                        getResult.getPrimaryTerm(),
                        getResult.getVersion(),
                        DocWriteResponse.Result.NOOP
                    );
                    update.setGetResult(getResult);
                    return new Result(update, DocWriteResponse.Result.NOOP, upsertResult.v2(), XContentType.JSON);
                }
                default ->
                    // It's fine to throw an exception here, the leniency is handled/logged by `executeScriptedUpsert`
                    throw new IllegalArgumentException("unknown upsert operation, got: " + upsertResult.v1());
            }
        }

        indexRequest.index(request.index())
            .id(request.id())
            .setRefreshPolicy(request.getRefreshPolicy())
            .routing(request.routing())
            .timeout(request.timeout())
            .waitForActiveShards(request.waitForActiveShards())
            // it has to be a "create!"
            .create(true);

        if (request.versionType() != VersionType.INTERNAL) {
            // in all but the internal versioning mode, we want to create the new document using the given version.
            indexRequest.version(request.version()).versionType(request.versionType());
        }

        return new Result(indexRequest, DocWriteResponse.Result.CREATED, null, null);
    }

    /**
     * Calculate a routing value to be used, either the included index request's routing, or retrieved document's routing when defined.
     */
    @Nullable
    static String calculateRouting(GetResult getResult, @Nullable IndexRequest updateIndexRequest) {
        if (updateIndexRequest != null && updateIndexRequest.routing() != null) {
            return updateIndexRequest.routing();
        } else if (getResult.getFields().containsKey(RoutingFieldMapper.NAME)) {
            return getResult.field(RoutingFieldMapper.NAME).getValue().toString();
        } else {
            return null;
        }
    }

    /**
     * Prepare the request for merging the existing document with a new one, can optionally detect a noop change. Returns a {@code Result}
     * containing a new {@code IndexRequest} to be executed on the primary and replicas.
     */
    static Result prepareUpdateIndexRequest(ShardId shardId, UpdateRequest request, GetResult getResult, boolean detectNoop) {
        final IndexRequest currentRequest = request.doc();
        final String routing = calculateRouting(getResult, currentRequest);
        final Tuple<XContentType, Map<String, Object>> sourceAndContent = XContentHelper.convertToMap(getResult.internalSourceRef(), true);
        final XContentType updateSourceContentType = sourceAndContent.v1();
        final Map<String, Object> updatedSourceAsMap = sourceAndContent.v2();

        final boolean noop = XContentHelper.update(updatedSourceAsMap, currentRequest.sourceAsMap(), detectNoop) == false;

        // We can only actually turn the update into a noop if detectNoop is true to preserve backwards compatibility and to handle cases
        // where users repopulating multi-fields or adding synonyms, etc.
        if (detectNoop && noop) {
            UpdateResponse update = new UpdateResponse(
                shardId,
                getResult.getId(),
                getResult.getSeqNo(),
                getResult.getPrimaryTerm(),
                getResult.getVersion(),
                DocWriteResponse.Result.NOOP
            );
            update.setGetResult(
                extractGetResult(
                    request,
                    request.index(),
                    getResult.getSeqNo(),
                    getResult.getPrimaryTerm(),
                    getResult.getVersion(),
                    updatedSourceAsMap,
                    updateSourceContentType,
                    getResult.internalSourceRef()
                )
            );
            return new Result(update, DocWriteResponse.Result.NOOP, updatedSourceAsMap, updateSourceContentType);
        } else {
            final IndexRequest finalIndexRequest = Requests.indexRequest(request.index())
                .id(request.id())
                .routing(routing)
                .source(updatedSourceAsMap, updateSourceContentType)
                .setIfSeqNo(getResult.getSeqNo())
                .setIfPrimaryTerm(getResult.getPrimaryTerm())
                .waitForActiveShards(request.waitForActiveShards())
                .timeout(request.timeout())
                .setRefreshPolicy(request.getRefreshPolicy());
            return new Result(finalIndexRequest, DocWriteResponse.Result.UPDATED, updatedSourceAsMap, updateSourceContentType);
        }
    }

    /**
     * Prepare the request for updating an existing document using a script. Executes the script and returns a {@code Result} containing
     * either a new {@code IndexRequest} or {@code DeleteRequest} (depending on the script's returned "op" value) to be executed on the
     * primary and replicas.
     */
    Result prepareUpdateScriptRequest(ShardId shardId, UpdateRequest request, GetResult getResult, LongSupplier nowInMillis) {
        final IndexRequest currentRequest = request.doc();
        final String routing = calculateRouting(getResult, currentRequest);
        final Tuple<XContentType, Map<String, Object>> sourceAndContent = XContentHelper.convertToMap(getResult.internalSourceRef(), true);
        final XContentType updateSourceContentType = sourceAndContent.v1();

        UpdateScript.Metadata md = executeScript(
            request.script,
            new UpdateScript.Metadata(
                getResult.getIndex(),
                getResult.getId(),
                getResult.getVersion(),
                routing,
                Op.INDEX, // The default operation is "index"
                nowInMillis.getAsLong(),
                MapperService.SINGLE_MAPPING_NAME,
                sourceAndContent.v2()
            )
        );
        Op op = lenientGetOp(md, logger, request.script.getIdOrCode());
        final Map<String, Object> updatedSourceAsMap = md.getSource();

        switch (op) {
            case INDEX -> {
                final IndexRequest indexRequest = Requests.indexRequest(request.index())
                    .id(request.id())
                    .routing(routing)
                    .source(updatedSourceAsMap, updateSourceContentType)
                    .setIfSeqNo(getResult.getSeqNo())
                    .setIfPrimaryTerm(getResult.getPrimaryTerm())
                    .waitForActiveShards(request.waitForActiveShards())
                    .timeout(request.timeout())
                    .setRefreshPolicy(request.getRefreshPolicy());
                return new Result(indexRequest, DocWriteResponse.Result.UPDATED, updatedSourceAsMap, updateSourceContentType);
            }
            case DELETE -> {
                DeleteRequest deleteRequest = Requests.deleteRequest(request.index())
                    .id(request.id())
                    .routing(routing)
                    .setIfSeqNo(getResult.getSeqNo())
                    .setIfPrimaryTerm(getResult.getPrimaryTerm())
                    .waitForActiveShards(request.waitForActiveShards())
                    .timeout(request.timeout())
                    .setRefreshPolicy(request.getRefreshPolicy());
                return new Result(deleteRequest, DocWriteResponse.Result.DELETED, updatedSourceAsMap, updateSourceContentType);
            }
            default -> {
                // If it was neither an INDEX or DELETE operation, treat it as a noop
                UpdateResponse update = new UpdateResponse(
                    shardId,
                    getResult.getId(),
                    getResult.getSeqNo(),
                    getResult.getPrimaryTerm(),
                    getResult.getVersion(),
                    DocWriteResponse.Result.NOOP
                );
                update.setGetResult(
                    extractGetResult(
                        request,
                        request.index(),
                        getResult.getSeqNo(),
                        getResult.getPrimaryTerm(),
                        getResult.getVersion(),
                        updatedSourceAsMap,
                        updateSourceContentType,
                        getResult.internalSourceRef()
                    )
                );
                return new Result(update, DocWriteResponse.Result.NOOP, updatedSourceAsMap, updateSourceContentType);
            }
        }
    }

    private UpdateScript.Metadata executeScript(Script script, UpdateScript.Metadata metadata) {
        try {
            if (scriptService != null) {
                UpdateScript.Factory factory = scriptService.compile(script, UpdateScript.CONTEXT);
                UpdateScript executableScript = factory.newInstance(script.getParams(), metadata);
                executableScript.execute();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to execute script", e);
        }
        return metadata;
    }

    /**
     * Applies {@link UpdateRequest#fetchSource()} to the _source of the updated document to be returned in a update response.
     */
    public static GetResult extractGetResult(
        final UpdateRequest request,
        String concreteIndex,
        long seqNo,
        long primaryTerm,
        long version,
        final Map<String, Object> source,
        XContentType sourceContentType,
        @Nullable final BytesReference sourceAsBytes
    ) {
        if (request.fetchSource() == null || request.fetchSource().fetchSource() == false) {
            return null;
        }

        BytesReference sourceFilteredAsBytes = sourceAsBytes;
        if (request.fetchSource().includes().length > 0 || request.fetchSource().excludes().length > 0) {
            SourceLookup sourceLookup = new SourceLookup();
            sourceLookup.setSource(source);
            Object value = sourceLookup.filter(request.fetchSource());
            try {
                final int initialCapacity = sourceAsBytes != null ? Math.min(1024, sourceAsBytes.length()) : 1024;
                BytesStreamOutput streamOutput = new BytesStreamOutput(initialCapacity);
                try (XContentBuilder builder = new XContentBuilder(sourceContentType.xContent(), streamOutput)) {
                    builder.value(value);
                    sourceFilteredAsBytes = BytesReference.bytes(builder);
                }
            } catch (IOException e) {
                throw new ElasticsearchException("Error filtering source", e);
            }
        }

        // TODO when using delete/none, we can still return the source as bytes by generating it (using the sourceContentType)
        return new GetResult(
            concreteIndex,
            request.id(),
            seqNo,
            primaryTerm,
            version,
            true,
            sourceFilteredAsBytes,
            Collections.emptyMap(),
            Collections.emptyMap()
        );
    }

    public static class Result {

        private final Writeable action;
        private final DocWriteResponse.Result result;
        private final Map<String, Object> updatedSourceAsMap;
        private final XContentType updateSourceContentType;

        public Result(
            Writeable action,
            DocWriteResponse.Result result,
            Map<String, Object> updatedSourceAsMap,
            XContentType updateSourceContentType
        ) {
            this.action = action;
            this.result = result;
            this.updatedSourceAsMap = updatedSourceAsMap;
            this.updateSourceContentType = updateSourceContentType;
        }

        @SuppressWarnings("unchecked")
        public <T extends Writeable> T action() {
            return (T) action;
        }

        public DocWriteResponse.Result getResponseResult() {
            return result;
        }

        public Map<String, Object> updatedSourceAsMap() {
            return updatedSourceAsMap;
        }

        public XContentType updateSourceContentType() {
            return updateSourceContentType;
        }
    }

    protected Op lenientGetOp(UpdateScript.Metadata md, Logger logger, String scriptId) {
        try {
            return md.getOp();
        } catch (IllegalArgumentException err) {
            // TODO: can we remove this leniency yet?? (this comment from 1907c466, April 2017 -@stu)
            logger.warn("[{}] for script [{}], doing nothing...", err.getMessage(), scriptId);
            return Op.NOOP;
        }
    }

    /**
     * Field names used to populate the script context
     */
    public static class ContextFields {
        public static final String CTX = "ctx";
        public static final String OP = "op";
        public static final String SOURCE = "_source";
        public static final String NOW = "_now";
        public static final String INDEX = "_index";
        public static final String TYPE = "_type";
        public static final String ID = "_id";
        public static final String VERSION = "_version";
        public static final String ROUTING = "_routing";
    }
}
