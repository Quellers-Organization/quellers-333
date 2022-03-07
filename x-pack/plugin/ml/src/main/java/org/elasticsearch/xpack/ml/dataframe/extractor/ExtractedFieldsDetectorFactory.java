/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.ml.dataframe.extractor;

import org.elasticsearch.logging.LogManager;
import org.elasticsearch.logging.Logger;
import org.elasticsearch.logging.ParameterizedMessage;
import org.elasticsearch.ResourceNotFoundException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsAction;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsRequest;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.elasticsearch.action.fieldcaps.FieldCapabilities;
import org.elasticsearch.action.fieldcaps.FieldCapabilitiesAction;
import org.elasticsearch.action.fieldcaps.FieldCapabilitiesRequest;
import org.elasticsearch.action.fieldcaps.FieldCapabilitiesResponse;
import org.elasticsearch.action.search.SearchAction;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.internal.Client;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.Cardinality;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xpack.core.ClientHelper;
import org.elasticsearch.xpack.core.ml.dataframe.DataFrameAnalyticsConfig;
import org.elasticsearch.xpack.core.ml.dataframe.analyses.FieldCardinalityConstraint;
import org.elasticsearch.xpack.core.ml.utils.ExceptionsHelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.elasticsearch.xpack.core.ClientHelper.ML_ORIGIN;
import static org.elasticsearch.xpack.core.ClientHelper.executeAsyncWithOrigin;

/**
 * A factory that retrieves all the parts necessary to build a {@link ExtractedFieldsDetector}.
 */
public class ExtractedFieldsDetectorFactory {

    private static final Logger LOGGER = LogManager.getLogger(ExtractedFieldsDetectorFactory.class);

    private final Client client;

    public ExtractedFieldsDetectorFactory(Client client) {
        this.client = Objects.requireNonNull(client);
    }

    public void createFromSource(DataFrameAnalyticsConfig config, ActionListener<ExtractedFieldsDetector> listener) {
        create(config.getSource().getIndex(), config, listener);
    }

    public void createFromDest(DataFrameAnalyticsConfig config, ActionListener<ExtractedFieldsDetector> listener) {
        create(new String[] { config.getDest().getIndex() }, config, listener);
    }

    private void create(String[] index, DataFrameAnalyticsConfig config, ActionListener<ExtractedFieldsDetector> listener) {
        AtomicInteger docValueFieldsLimitHolder = new AtomicInteger();
        AtomicReference<FieldCapabilitiesResponse> fieldCapsResponseHolder = new AtomicReference<>();

        // Step 4. Create cardinality by field map and build detector
        ActionListener<Map<String, Long>> fieldCardinalitiesHandler = ActionListener.wrap(fieldCardinalities -> {
            ExtractedFieldsDetector detector = new ExtractedFieldsDetector(
                config,
                docValueFieldsLimitHolder.get(),
                fieldCapsResponseHolder.get(),
                fieldCardinalities
            );
            listener.onResponse(detector);
        }, listener::onFailure);

        // Step 3. Get cardinalities for fields with constraints
        ActionListener<FieldCapabilitiesResponse> fieldCapabilitiesHandler = ActionListener.wrap(fieldCapabilitiesResponse -> {
            LOGGER.debug(() -> new ParameterizedMessage("[{}] Field capabilities response: {}", config.getId(), fieldCapabilitiesResponse));
            fieldCapsResponseHolder.set(fieldCapabilitiesResponse);
            getCardinalitiesForFieldsWithConstraints(index, config, fieldCapabilitiesResponse, fieldCardinalitiesHandler);
        }, listener::onFailure);

        // Step 2. Get field capabilities necessary to build the information of how to extract fields
        ActionListener<Integer> docValueFieldsLimitListener = ActionListener.wrap(docValueFieldsLimit -> {
            docValueFieldsLimitHolder.set(docValueFieldsLimit);
            getFieldCaps(index, config, fieldCapabilitiesHandler);
        }, listener::onFailure);

        // Step 1. Get doc value fields limit
        getDocValueFieldsLimit(index, docValueFieldsLimitListener);
    }

    private void getCardinalitiesForFieldsWithConstraints(
        String[] index,
        DataFrameAnalyticsConfig config,
        FieldCapabilitiesResponse fieldCapabilitiesResponse,
        ActionListener<Map<String, Long>> listener
    ) {
        List<FieldCardinalityConstraint> fieldCardinalityConstraints = config.getAnalysis().getFieldCardinalityConstraints();
        if (fieldCardinalityConstraints.isEmpty()) {
            listener.onResponse(Collections.emptyMap());
            return;
        }

        ActionListener<SearchResponse> searchListener = ActionListener.wrap(
            searchResponse -> buildFieldCardinalitiesMap(config, searchResponse, listener),
            listener::onFailure
        );

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().size(0)
            .query(config.getSource().getParsedQuery())
            .runtimeMappings(config.getSource().getRuntimeMappings());
        for (FieldCardinalityConstraint constraint : fieldCardinalityConstraints) {
            Map<String, FieldCapabilities> fieldCapsPerType = fieldCapabilitiesResponse.getField(constraint.getField());
            if (fieldCapsPerType == null) {
                throw ExceptionsHelper.badRequestException("no mappings could be found for field [{}]", constraint.getField());
            }
            for (FieldCapabilities fieldCaps : fieldCapsPerType.values()) {
                if (fieldCaps.isAggregatable() == false) {
                    throw ExceptionsHelper.badRequestException(
                        "field [{}] of type [{}] is non-aggregatable",
                        fieldCaps.getName(),
                        fieldCaps.getType()
                    );
                }
            }
            searchSourceBuilder.aggregation(
                AggregationBuilders.cardinality(constraint.getField())
                    .field(constraint.getField())
                    .precisionThreshold(constraint.getUpperBound() + 1)
            );
        }
        SearchRequest searchRequest = new SearchRequest(index).source(searchSourceBuilder);
        ClientHelper.executeWithHeadersAsync(config.getHeaders(), ML_ORIGIN, client, SearchAction.INSTANCE, searchRequest, searchListener);
    }

    private void buildFieldCardinalitiesMap(
        DataFrameAnalyticsConfig config,
        SearchResponse searchResponse,
        ActionListener<Map<String, Long>> listener
    ) {
        Aggregations aggs = searchResponse.getAggregations();
        if (aggs == null) {
            listener.onFailure(ExceptionsHelper.serverError("Unexpected null response when gathering field cardinalities"));
            return;
        }

        Map<String, Long> fieldCardinalities = new HashMap<>(config.getAnalysis().getFieldCardinalityConstraints().size());
        for (FieldCardinalityConstraint constraint : config.getAnalysis().getFieldCardinalityConstraints()) {
            Cardinality cardinality = aggs.get(constraint.getField());
            if (cardinality == null) {
                listener.onFailure(ExceptionsHelper.serverError("Unexpected null response when gathering field cardinalities"));
                return;
            }
            fieldCardinalities.put(constraint.getField(), cardinality.getValue());
        }
        listener.onResponse(fieldCardinalities);
    }

    private void getFieldCaps(String[] index, DataFrameAnalyticsConfig config, ActionListener<FieldCapabilitiesResponse> listener) {
        FieldCapabilitiesRequest fieldCapabilitiesRequest = new FieldCapabilitiesRequest();
        fieldCapabilitiesRequest.indices(index);
        fieldCapabilitiesRequest.indicesOptions(IndicesOptions.lenientExpandOpen());
        fieldCapabilitiesRequest.fields("*");
        fieldCapabilitiesRequest.runtimeFields(config.getSource().getRuntimeMappings());
        LOGGER.debug(() -> new ParameterizedMessage("[{}] Requesting field caps for index {}", config.getId(), Arrays.toString(index)));
        ClientHelper.executeWithHeaders(config.getHeaders(), ML_ORIGIN, client, () -> {
            client.execute(FieldCapabilitiesAction.INSTANCE, fieldCapabilitiesRequest, listener);
            // This response gets discarded - the listener handles the real response
            return null;
        });
    }

    private void getDocValueFieldsLimit(String[] index, ActionListener<Integer> docValueFieldsLimitListener) {
        ActionListener<GetSettingsResponse> settingsListener = ActionListener.wrap(getSettingsResponse -> {
            Integer minDocValueFieldsLimit = Integer.MAX_VALUE;

            ImmutableOpenMap<String, Settings> indexToSettings = getSettingsResponse.getIndexToSettings();
            for (var indexSettings : indexToSettings.values()) {
                Integer indexMaxDocValueFields = IndexSettings.MAX_DOCVALUE_FIELDS_SEARCH_SETTING.get(indexSettings);
                if (indexMaxDocValueFields < minDocValueFieldsLimit) {
                    minDocValueFieldsLimit = indexMaxDocValueFields;
                }
            }
            docValueFieldsLimitListener.onResponse(minDocValueFieldsLimit);
        }, e -> {
            Throwable cause = ExceptionsHelper.unwrapCause(e);
            if (cause instanceof IndexNotFoundException) {
                docValueFieldsLimitListener.onFailure(
                    new ResourceNotFoundException(
                        "cannot retrieve data because index " + ((IndexNotFoundException) cause).getIndex() + " does not exist"
                    )
                );
            } else {
                docValueFieldsLimitListener.onFailure(e);
            }
        });

        GetSettingsRequest getSettingsRequest = new GetSettingsRequest();
        getSettingsRequest.indices(index);
        getSettingsRequest.includeDefaults(true);
        getSettingsRequest.names(IndexSettings.MAX_DOCVALUE_FIELDS_SEARCH_SETTING.getKey());
        executeAsyncWithOrigin(client, ML_ORIGIN, GetSettingsAction.INSTANCE, getSettingsRequest, settingsListener);
    }
}
