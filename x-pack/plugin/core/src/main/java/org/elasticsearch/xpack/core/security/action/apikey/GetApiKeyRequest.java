/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.core.security.action.apikey;

import org.elasticsearch.TransportVersion;
import org.elasticsearch.TransportVersions;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.core.Nullable;

import java.io.IOException;
import java.util.Objects;

import static org.elasticsearch.action.ValidateActions.addValidationError;

/**
 * Request for get API key
 */
public final class GetApiKeyRequest extends ActionRequest {

    static TransportVersion API_KEY_ACTIVE_ONLY_PARAM_TRANSPORT_VERSION = TransportVersions.V_8_10_X;

    private final String realmName;
    private final String userName;
    private final String apiKeyId;
    private final String apiKeyName;
    private final boolean ownedByAuthenticatedUser;
    private final boolean withLimitedBy;
    private final boolean activeOnly;

    public GetApiKeyRequest(StreamInput in) throws IOException {
        super(in);
        realmName = textOrNull(in.readOptionalString());
        userName = textOrNull(in.readOptionalString());
        apiKeyId = textOrNull(in.readOptionalString());
        apiKeyName = textOrNull(in.readOptionalString());
        if (in.getTransportVersion().onOrAfter(TransportVersions.V_7_4_0)) {
            ownedByAuthenticatedUser = in.readOptionalBoolean();
        } else {
            ownedByAuthenticatedUser = false;
        }
        if (in.getTransportVersion().onOrAfter(TransportVersions.V_8_5_0)) {
            withLimitedBy = in.readBoolean();
        } else {
            withLimitedBy = false;
        }
        if (in.getTransportVersion().onOrAfter(API_KEY_ACTIVE_ONLY_PARAM_TRANSPORT_VERSION)) {
            activeOnly = in.readBoolean();
        } else {
            activeOnly = false;
        }
    }

    private GetApiKeyRequest(
        @Nullable String realmName,
        @Nullable String userName,
        @Nullable String apiKeyId,
        @Nullable String apiKeyName,
        boolean ownedByAuthenticatedUser,
        boolean withLimitedBy,
        boolean activeOnly
    ) {
        this.realmName = textOrNull(realmName);
        this.userName = textOrNull(userName);
        this.apiKeyId = textOrNull(apiKeyId);
        this.apiKeyName = textOrNull(apiKeyName);
        this.ownedByAuthenticatedUser = ownedByAuthenticatedUser;
        this.withLimitedBy = withLimitedBy;
        this.activeOnly = activeOnly;
    }

    private static String textOrNull(@Nullable String arg) {
        return Strings.hasText(arg) ? arg : null;
    }

    public String getRealmName() {
        return realmName;
    }

    public String getUserName() {
        return userName;
    }

    public String getApiKeyId() {
        return apiKeyId;
    }

    public String getApiKeyName() {
        return apiKeyName;
    }

    public boolean ownedByAuthenticatedUser() {
        return ownedByAuthenticatedUser;
    }

    public boolean withLimitedBy() {
        return withLimitedBy;
    }

    public boolean activeOnly() {
        return activeOnly;
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = null;
        if (Strings.hasText(apiKeyId) || Strings.hasText(apiKeyName)) {
            if (Strings.hasText(realmName) || Strings.hasText(userName)) {
                validationException = addValidationError(
                    "username or realm name must not be specified when the api key id or api key name is specified",
                    validationException
                );
            }
        }
        if (ownedByAuthenticatedUser) {
            if (Strings.hasText(realmName) || Strings.hasText(userName)) {
                validationException = addValidationError(
                    "neither username nor realm-name may be specified when retrieving owned API keys",
                    validationException
                );
            }
        }
        if (Strings.hasText(apiKeyId) && Strings.hasText(apiKeyName)) {
            validationException = addValidationError("only one of [api key id, api key name] can be specified", validationException);
        }
        return validationException;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalString(realmName);
        out.writeOptionalString(userName);
        out.writeOptionalString(apiKeyId);
        out.writeOptionalString(apiKeyName);
        if (out.getTransportVersion().onOrAfter(TransportVersions.V_7_4_0)) {
            out.writeOptionalBoolean(ownedByAuthenticatedUser);
        }
        if (out.getTransportVersion().onOrAfter(TransportVersions.V_8_5_0)) {
            out.writeBoolean(withLimitedBy);
        }
        if (out.getTransportVersion().onOrAfter(API_KEY_ACTIVE_ONLY_PARAM_TRANSPORT_VERSION)) {
            out.writeBoolean(activeOnly);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GetApiKeyRequest that = (GetApiKeyRequest) o;
        return ownedByAuthenticatedUser == that.ownedByAuthenticatedUser
            && Objects.equals(realmName, that.realmName)
            && Objects.equals(userName, that.userName)
            && Objects.equals(apiKeyId, that.apiKeyId)
            && Objects.equals(apiKeyName, that.apiKeyName)
            && withLimitedBy == that.withLimitedBy
            && activeOnly == that.activeOnly;
    }

    @Override
    public int hashCode() {
        return Objects.hash(realmName, userName, apiKeyId, apiKeyName, ownedByAuthenticatedUser, withLimitedBy, activeOnly);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String realmName = null;
        private String userName = null;
        private String apiKeyId = null;
        private String apiKeyName = null;
        private boolean ownedByAuthenticatedUser = false;
        private boolean withLimitedBy = false;
        private boolean activeOnly = false;

        public Builder realmName(String realmName) {
            this.realmName = realmName;
            return this;
        }

        public Builder userName(String userName) {
            this.userName = userName;
            return this;
        }

        public Builder apiKeyId(String apiKeyId) {
            this.apiKeyId = apiKeyId;
            return this;
        }

        public Builder apiKeyName(String apiKeyName) {
            this.apiKeyName = apiKeyName;
            return this;
        }

        public Builder ownedByAuthenticatedUser() {
            return ownedByAuthenticatedUser(true);
        }

        public Builder ownedByAuthenticatedUser(boolean ownedByAuthenticatedUser) {
            this.ownedByAuthenticatedUser = ownedByAuthenticatedUser;
            return this;
        }

        public Builder withLimitedBy() {
            return withLimitedBy(true);
        }

        public Builder withLimitedBy(boolean withLimitedBy) {
            this.withLimitedBy = withLimitedBy;
            return this;
        }

        public Builder activeOnly(boolean activeOnly) {
            this.activeOnly = activeOnly;
            return this;
        }

        public GetApiKeyRequest build() {
            return new GetApiKeyRequest(realmName, userName, apiKeyId, apiKeyName, ownedByAuthenticatedUser, withLimitedBy, activeOnly);
        }
    }
}
