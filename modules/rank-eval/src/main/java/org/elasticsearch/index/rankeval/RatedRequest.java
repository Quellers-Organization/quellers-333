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

package org.elasticsearch.index.rankeval;

import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.rankeval.RatedDocument.DocumentKey;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

/**
 * Definition of a particular query in the ranking evaluation request.<br>
 * This usually represents a single user search intent and consists of an id
 * (ideally human readable and referencing the search intent), the list of
 * indices to be queries and the {@link SearchSourceBuilder} that will be used
 * to create the search request for this search intent.<br>
 * Alternatively, a template id and template parameters can be provided instead.<br>
 * Finally, a list of rated documents for this query also needs to be provided.
 * <p>
 * The json structure in the rest request looks like this:
 * <pre>
 * {
 *    "id": "coffee_query",
 *    "request": {
 *        "query": {
 *            "match": { "beverage": "coffee" }
 *        }
 *    },
 *    "summary_fields": ["title"],
 *    "ratings": [
 *        {"_index": "my_index", "_id": "doc1", "rating": 0},
 *        {"_index": "my_index", "_id": "doc2","rating": 3},
 *        {"_index": "my_index", "_id": "doc3", "rating": 1}
 *    ]
 * }
 * </pre>
 */
@SuppressWarnings("unchecked")
public class RatedRequest implements Writeable, ToXContentObject {
    private final String id;
    private final List<String> summaryFields;
    private final List<RatedDocument> ratedDocs;
    // Search request to execute for this rated request. This can be null if template and corresponding parameters are supplied.
    @Nullable
    private SearchSourceBuilder testRequest;
    /**
     * Map of parameters to use for filling a query template, can be used
     * instead of providing testRequest.
     */
    private final Map<String, Object> params;
    @Nullable
    private String templateId;

    private RatedRequest(String id, List<RatedDocument> ratedDocs, SearchSourceBuilder testRequest,
            Map<String, Object> params, String templateId) {
        if (params != null && (params.size() > 0 && testRequest != null)) {
            throw new IllegalArgumentException(
                    "Ambiguous rated request: Set both, verbatim test request and test request "
                    + "template parameters.");
        }
        if (templateId != null && testRequest != null) {
            throw new IllegalArgumentException(
                    "Ambiguous rated request: Set both, verbatim test request and test request "
                    + "template parameters.");
        }
        if ((params == null || params.size() < 1) && testRequest == null) {
            throw new IllegalArgumentException(
                    "Need to set at least test request or test request template parameters.");
        }
        if ((params != null && params.size() > 0) && templateId == null) {
            throw new IllegalArgumentException(
                    "If template parameters are supplied need to set id of template to apply "
                    + "them to too.");
        }
        // check that not two documents with same _index/id are specified
        Set<DocumentKey> docKeys = new HashSet<>();
        for (RatedDocument doc : ratedDocs) {
            if (docKeys.add(doc.getKey()) == false) {
                String docKeyToString = doc.getKey().toString().replaceAll("\n", "").replaceAll("  ", " ");
                throw new IllegalArgumentException(
                        "Found duplicate rated document key [" + docKeyToString + "] in evaluation request [" + id + "]");
            }
        }

        this.id = id;
        this.testRequest = testRequest;
        this.ratedDocs = new ArrayList<>(ratedDocs);
        if (params != null) {
            this.params = new HashMap<>(params);
        } else {
            this.params = Collections.emptyMap();
        }
        this.templateId = templateId;
        this.summaryFields = new ArrayList<>();
    }

    public RatedRequest(String id, List<RatedDocument> ratedDocs, Map<String, Object> params,
            String templateId) {
        this(id, ratedDocs, null, params, templateId);
    }

    public RatedRequest(String id, List<RatedDocument> ratedDocs, SearchSourceBuilder testRequest) {
        this(id, ratedDocs, testRequest, new HashMap<>(), null);
    }

    public RatedRequest(StreamInput in) throws IOException {
        this.id = in.readString();
        testRequest = in.readOptionalWriteable(SearchSourceBuilder::new);

        int intentSize = in.readInt();
        ratedDocs = new ArrayList<>(intentSize);
        for (int i = 0; i < intentSize; i++) {
            ratedDocs.add(new RatedDocument(in));
        }
        this.params = in.readMap();
        int summaryFieldsSize = in.readInt();
        summaryFields = new ArrayList<>(summaryFieldsSize);
        for (int i = 0; i < summaryFieldsSize; i++) {
            this.summaryFields.add(in.readString());
        }
        this.templateId = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(id);
        out.writeOptionalWriteable(testRequest);

        out.writeInt(ratedDocs.size());
        for (RatedDocument ratedDoc : ratedDocs) {
            ratedDoc.writeTo(out);
        }
        out.writeMap(params);
        out.writeInt(summaryFields.size());
        for (String fieldName : summaryFields) {
            out.writeString(fieldName);
        }
        out.writeOptionalString(this.templateId);
    }

    public SearchSourceBuilder getTestRequest() {
        return testRequest;
    }

    /** return the user supplied request id */
    public String getId() {
        return id;
    }

    /** return the list of rated documents to evaluate. */
    public List<RatedDocument> getRatedDocs() {
        return Collections.unmodifiableList(ratedDocs);
    }

    /** return the parameters if this request uses a template, otherwise this will be empty. */
    public Map<String, Object> getParams() {
        return Collections.unmodifiableMap(this.params);
    }

    /** return the parameters if this request uses a template, otherwise this will be {@code null}. */
    public String getTemplateId() {
        return this.templateId;
    }

    /** returns a list of fields that should be included in the document summary for matched documents */
    public List<String> getSummaryFields() {
        return Collections.unmodifiableList(summaryFields);
    }

    public void addSummaryFields(List<String> summaryFields) {
        this.summaryFields.addAll(Objects.requireNonNull(summaryFields, "no summary fields supplied"));
    }

    private static final ParseField ID_FIELD = new ParseField("id");
    private static final ParseField REQUEST_FIELD = new ParseField("request");
    private static final ParseField RATINGS_FIELD = new ParseField("ratings");
    private static final ParseField PARAMS_FIELD = new ParseField("params");
    private static final ParseField FIELDS_FIELD = new ParseField("summary_fields");
    private static final ParseField TEMPLATE_ID_FIELD = new ParseField("template_id");

    private static final ConstructingObjectParser<RatedRequest, Void> PARSER = new ConstructingObjectParser<>("request",
            a -> new RatedRequest((String) a[0], (List<RatedDocument>) a[1], (SearchSourceBuilder) a[2], (Map<String, Object>) a[3],
                    (String) a[4]));

    static {
        PARSER.declareString(ConstructingObjectParser.constructorArg(), ID_FIELD);
        PARSER.declareObjectArray(ConstructingObjectParser.constructorArg(), (p, c) -> {
            return RatedDocument.fromXContent(p);
        }, RATINGS_FIELD);
        PARSER.declareObject(ConstructingObjectParser.optionalConstructorArg(), (p, c) ->
                SearchSourceBuilder.fromXContent(p, false), REQUEST_FIELD);
        PARSER.declareObject(ConstructingObjectParser.optionalConstructorArg(), (p, c) -> p.map(), PARAMS_FIELD);
        PARSER.declareStringArray(RatedRequest::addSummaryFields, FIELDS_FIELD);
        PARSER.declareString(ConstructingObjectParser.optionalConstructorArg(), TEMPLATE_ID_FIELD);
    }

    /**
     * parse from rest representation
     */
    public static RatedRequest fromXContent(XContentParser parser) {
        return PARSER.apply(parser, null);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(ID_FIELD.getPreferredName(), this.id);
        if (testRequest != null) {
            builder.field(REQUEST_FIELD.getPreferredName(), this.testRequest);
        }
        builder.startArray(RATINGS_FIELD.getPreferredName());
        for (RatedDocument doc : this.ratedDocs) {
            doc.toXContent(builder, params);
        }
        builder.endArray();
        if (this.templateId != null) {
            builder.field(TEMPLATE_ID_FIELD.getPreferredName(), this.templateId);
        }
        if (this.params.isEmpty() == false) {
            builder.startObject(PARAMS_FIELD.getPreferredName());
            for (Entry<String, Object> entry : this.params.entrySet()) {
                builder.field(entry.getKey(), entry.getValue());
            }
            builder.endObject();
        }
        if (this.summaryFields.isEmpty() == false) {
            builder.startArray(FIELDS_FIELD.getPreferredName());
            for (String field : this.summaryFields) {
                builder.value(field);
            }
            builder.endArray();
        }
        builder.endObject();
        return builder;
    }

    @Override
    public String toString() {
        return Strings.toString(this);
    }

    @Override
    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        RatedRequest other = (RatedRequest) obj;

        return Objects.equals(id, other.id) && Objects.equals(testRequest, other.testRequest)
                && Objects.equals(summaryFields, other.summaryFields)
                && Objects.equals(ratedDocs, other.ratedDocs)
                && Objects.equals(params, other.params)
                && Objects.equals(templateId, other.templateId);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id, testRequest, summaryFields, ratedDocs, params,
                templateId);
    }
}
