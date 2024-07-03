/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.services.cohere.rerank;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.TransportVersion;
import org.elasticsearch.TransportVersions;
import org.elasticsearch.common.ValidationException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.inference.ModelConfigurations;
import org.elasticsearch.inference.ServiceSettings;
import org.elasticsearch.inference.SimilarityMeasure;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xpack.inference.services.ConfigurationParseContext;
import org.elasticsearch.xpack.inference.services.cohere.CohereRateLimitServiceSettings;
import org.elasticsearch.xpack.inference.services.cohere.CohereService;
import org.elasticsearch.xpack.inference.services.settings.FilteredXContentObject;
import org.elasticsearch.xpack.inference.services.settings.RateLimitSettings;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Objects;

import static org.elasticsearch.xpack.inference.services.ServiceFields.MODEL_ID;
import static org.elasticsearch.xpack.inference.services.ServiceFields.URL;
import static org.elasticsearch.xpack.inference.services.ServiceUtils.convertToUri;
import static org.elasticsearch.xpack.inference.services.ServiceUtils.createOptionalUri;
import static org.elasticsearch.xpack.inference.services.ServiceUtils.extractOptionalString;
import static org.elasticsearch.xpack.inference.services.cohere.CohereServiceSettings.DEFAULT_RATE_LIMIT_SETTINGS;
import static org.elasticsearch.xpack.inference.services.cohere.CohereServiceSettings.OLD_MODEL_ID_FIELD;

public class CohereRerankServiceSettings extends FilteredXContentObject implements ServiceSettings, CohereRateLimitServiceSettings {
    public static final String NAME = "cohere_rerank_service_settings";

    private static final Logger logger = LogManager.getLogger(CohereRerankServiceSettings.class);

    public static CohereRerankServiceSettings fromMap(Map<String, Object> map, ConfigurationParseContext context) {
        ValidationException validationException = new ValidationException();

        String url = extractOptionalString(map, URL, ModelConfigurations.SERVICE_SETTINGS, validationException);
        URI uri = convertToUri(url, URL, ModelConfigurations.SERVICE_SETTINGS, validationException);
        String oldModelId = extractOptionalString(map, OLD_MODEL_ID_FIELD, ModelConfigurations.SERVICE_SETTINGS, validationException);
        RateLimitSettings rateLimitSettings = RateLimitSettings.of(
            map,
            DEFAULT_RATE_LIMIT_SETTINGS,
            validationException,
            CohereService.NAME,
            context
        );

        String modelId = extractOptionalString(map, MODEL_ID, ModelConfigurations.SERVICE_SETTINGS, validationException);

        if (context == ConfigurationParseContext.REQUEST && oldModelId != null) {
            logger.info("The cohere [service_settings.model] field is deprecated. Please use [service_settings.model_id] instead.");
        }

        if (validationException.validationErrors().isEmpty() == false) {
            throw validationException;
        }

        return new CohereRerankServiceSettings(uri, modelId, rateLimitSettings);
    }

    private final URI uri;

    private final String modelId;

    private final RateLimitSettings rateLimitSettings;

    public CohereRerankServiceSettings(@Nullable URI uri, @Nullable String modelId, @Nullable RateLimitSettings rateLimitSettings) {
        this.uri = uri;
        this.modelId = modelId;
        this.rateLimitSettings = Objects.requireNonNullElse(rateLimitSettings, DEFAULT_RATE_LIMIT_SETTINGS);
    }

    public CohereRerankServiceSettings(@Nullable String url, @Nullable String modelId, @Nullable RateLimitSettings rateLimitSettings) {
        this(createOptionalUri(url), modelId, rateLimitSettings);
    }

    public CohereRerankServiceSettings(StreamInput in) throws IOException {
        this.uri = createOptionalUri(in.readOptionalString());

        if (in.getTransportVersion().before(TransportVersions.ML_INFERENCE_COHERE_UNUSED_RERANK_SETTINGS_REMOVED)) {
            // An older node sends these fields, so we need to skip them to progress through the serialized data
            in.readOptionalEnum(SimilarityMeasure.class);
            in.readOptionalVInt();
            in.readOptionalVInt();
        }

        this.modelId = in.readOptionalString();

        if (in.getTransportVersion().onOrAfter(TransportVersions.ML_INFERENCE_RATE_LIMIT_SETTINGS_ADDED)) {
            this.rateLimitSettings = new RateLimitSettings(in);
        } else {
            this.rateLimitSettings = DEFAULT_RATE_LIMIT_SETTINGS;
        }
    }

    public URI uri() {
        return uri;
    }

    public String modelId() {
        return modelId;
    }

    @Override
    public RateLimitSettings rateLimitSettings() {
        return rateLimitSettings;
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();

        toXContentFragmentOfExposedFields(builder, params);

        builder.endObject();
        return builder;
    }

    @Override
    protected XContentBuilder toXContentFragmentOfExposedFields(XContentBuilder builder, Params params) throws IOException {
        if (uri != null) {
            builder.field(URL, uri.toString());
        }

        if (modelId != null) {
            builder.field(MODEL_ID, modelId);
        }

        rateLimitSettings.toXContent(builder, params);

        return builder;
    }

    @Override
    public TransportVersion getMinimalSupportedVersion() {
        return TransportVersions.ML_INFERENCE_COHERE_RERANK;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        var uriToWrite = uri != null ? uri.toString() : null;
        out.writeOptionalString(uriToWrite);

        if (out.getTransportVersion().before(TransportVersions.ML_INFERENCE_COHERE_UNUSED_RERANK_SETTINGS_REMOVED)) {
            // An old node expects this data to be present, so we need to send at least the booleans
            // indicating that the fields are not set
            out.writeOptionalEnum(null);
            out.writeOptionalVInt(null);
            out.writeOptionalVInt(null);
        }

        out.writeOptionalString(modelId);

        if (out.getTransportVersion().onOrAfter(TransportVersions.ML_INFERENCE_RATE_LIMIT_SETTINGS_ADDED)) {
            rateLimitSettings.writeTo(out);
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        CohereRerankServiceSettings that = (CohereRerankServiceSettings) object;
        return Objects.equals(uri, that.uri)
            && Objects.equals(modelId, that.modelId)
            && Objects.equals(rateLimitSettings, that.rateLimitSettings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, modelId, rateLimitSettings);
    }
}
