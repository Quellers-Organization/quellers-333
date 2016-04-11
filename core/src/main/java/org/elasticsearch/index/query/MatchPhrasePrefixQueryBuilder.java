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

package org.elasticsearch.index.query;

import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.search.MatchQuery;

import java.io.IOException;
import java.util.Objects;

/**
 * Match query is a query that analyzes the text and constructs a phrase prefix
 * query as the result of the analysis.
 */
public class MatchPhrasePrefixQueryBuilder extends AbstractQueryBuilder<MatchPhrasePrefixQueryBuilder> {

    public static final String NAME = "match_phrase_prefix";
    public static final ParseField QUERY_NAME_FIELD = new ParseField(NAME);
    public static final ParseField MAX_EXPANSIONS_FIELD = new ParseField("max_expansions");

    public static final MatchPhrasePrefixQueryBuilder PROTOTYPE = new MatchPhrasePrefixQueryBuilder("", "");

    private final String fieldName;

    private final Object value;

    private String analyzer;

    private int slop = MatchQuery.DEFAULT_PHRASE_SLOP;

    private int maxExpansions = FuzzyQuery.defaultMaxExpansions;

    public MatchPhrasePrefixQueryBuilder(String fieldName, Object value) {
        if (fieldName == null) {
            throw new IllegalArgumentException("[" + NAME + "] requires fieldName");
        }
        if (value == null) {
            throw new IllegalArgumentException("[" + NAME + "] requires query value");
        }
        this.fieldName = fieldName;
        this.value = value;
    }

    /** Returns the field name used in this query. */
    public String fieldName() {
        return this.fieldName;
    }

    /** Returns the value used in this query. */
    public Object value() {
        return this.value;
    }

    /**
     * Explicitly set the analyzer to use. Defaults to use explicit mapping
     * config for the field, or, if not set, the default search analyzer.
     */
    public MatchPhrasePrefixQueryBuilder analyzer(String analyzer) {
        this.analyzer = analyzer;
        return this;
    }

    /** Get the analyzer to use, if previously set, otherwise <tt>null</tt> */
    public String analyzer() {
        return this.analyzer;
    }

    /** Sets a slop factor for phrase queries */
    public MatchPhrasePrefixQueryBuilder slop(int slop) {
        if (slop < 0) {
            throw new IllegalArgumentException("No negative slop allowed.");
        }
        this.slop = slop;
        return this;
    }

    /** Get the slop factor for phrase queries. */
    public int slop() {
        return this.slop;
    }

    /**
     * The number of term expansions to use.
     */
    public MatchPhrasePrefixQueryBuilder maxExpansions(int maxExpansions) {
        if (maxExpansions < 0) {
            throw new IllegalArgumentException("No negative maxExpansions allowed.");
        }
        this.maxExpansions = maxExpansions;
        return this;
    }

    /**
     * Get the (optional) number of term expansions when using fuzzy or prefix
     * type query.
     */
    public int maxExpansions() {
        return this.maxExpansions;
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);
        builder.startObject(fieldName);

        builder.field(MatchQueryBuilder.QUERY_FIELD.getPreferredName(), value);
        if (analyzer != null) {
            builder.field(MatchQueryBuilder.ANALYZER_FIELD.getPreferredName(), analyzer);
        }
        builder.field(MatchPhraseQueryBuilder.SLOP_FIELD.getPreferredName(), slop);
        builder.field(MAX_EXPANSIONS_FIELD.getPreferredName(), maxExpansions);
        printBoostAndQueryName(builder);
        builder.endObject();
        builder.endObject();
    }

    @Override
    protected Query doToQuery(QueryShardContext context) throws IOException {
        // validate context specific fields
        if (analyzer != null && context.getAnalysisService().analyzer(analyzer) == null) {
            throw new QueryShardException(context, "[" + NAME + "] analyzer [" + analyzer + "] not found");
        }

        MatchQuery matchQuery = new MatchQuery(context);
        matchQuery.setAnalyzer(analyzer);
        matchQuery.setPhraseSlop(slop);
        matchQuery.setMaxExpansions(maxExpansions);

        return matchQuery.parse(MatchQuery.Type.PHRASE_PREFIX, fieldName, value);
    }

    @Override
    protected MatchPhrasePrefixQueryBuilder doReadFrom(StreamInput in) throws IOException {
        MatchPhrasePrefixQueryBuilder matchQuery = new MatchPhrasePrefixQueryBuilder(in.readString(), in.readGenericValue());
        matchQuery.slop = in.readVInt();
        matchQuery.maxExpansions = in.readVInt();
        matchQuery.analyzer = in.readOptionalString();
        return matchQuery;
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeString(fieldName);
        out.writeGenericValue(value);
        out.writeVInt(slop);
        out.writeVInt(maxExpansions);
        out.writeOptionalString(analyzer);
    }

    @Override
    protected boolean doEquals(MatchPhrasePrefixQueryBuilder other) {
        return Objects.equals(fieldName, other.fieldName) &&
                Objects.equals(value, other.value) &&
                Objects.equals(analyzer, other.analyzer)&&
                Objects.equals(slop, other.slop) &&
                Objects.equals(maxExpansions, other.maxExpansions);
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(fieldName, value, analyzer, slop, maxExpansions);
    }

    public static MatchPhrasePrefixQueryBuilder fromXContent(QueryParseContext parseContext) throws IOException {
        XContentParser parser = parseContext.parser();

        XContentParser.Token token = parser.nextToken();
        if (token != XContentParser.Token.FIELD_NAME) {
            throw new ParsingException(parser.getTokenLocation(), "[" + NAME + "] query malformed, no field");
        }
        String fieldName = parser.currentName();

        Object value = null;
        float boost = AbstractQueryBuilder.DEFAULT_BOOST;
        String analyzer = null;
        int slop = MatchQuery.DEFAULT_PHRASE_SLOP;
        int maxExpansion = FuzzyQuery.defaultMaxExpansions;
        String queryName = null;

        token = parser.nextToken();
        if (token == XContentParser.Token.START_OBJECT) {
            String currentFieldName = null;
            while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                if (token == XContentParser.Token.FIELD_NAME) {
                    currentFieldName = parser.currentName();
                } else if (token.isValue()) {
                    if (parseContext.parseFieldMatcher().match(currentFieldName, MatchQueryBuilder.QUERY_FIELD)) {
                        value = parser.objectText();
                    } else if (parseContext.parseFieldMatcher().match(currentFieldName, MatchQueryBuilder.ANALYZER_FIELD)) {
                        analyzer = parser.text();
                    } else if (parseContext.parseFieldMatcher().match(currentFieldName, AbstractQueryBuilder.BOOST_FIELD)) {
                        boost = parser.floatValue();
                    } else if (parseContext.parseFieldMatcher().match(currentFieldName, MatchPhraseQueryBuilder.SLOP_FIELD)) {
                        slop = parser.intValue();
                    } else if (parseContext.parseFieldMatcher().match(currentFieldName, MAX_EXPANSIONS_FIELD)) {
                        maxExpansion = parser.intValue();
                    } else if (parseContext.parseFieldMatcher().match(currentFieldName, AbstractQueryBuilder.NAME_FIELD)) {
                        queryName = parser.text();
                    } else {
                        throw new ParsingException(parser.getTokenLocation(),
                                "[" + NAME + "] query does not support [" + currentFieldName + "]");
                    }
                } else {
                    throw new ParsingException(parser.getTokenLocation(),
                            "[" + NAME + "] unknown token [" + token + "] after [" + currentFieldName + "]");
                }
            }
            parser.nextToken();
        } else {
            value = parser.objectText();
            // move to the next token
            token = parser.nextToken();
            if (token != XContentParser.Token.END_OBJECT) {
                throw new ParsingException(parser.getTokenLocation(), "[" + NAME
                        + "] query parsed in simplified form, with direct field name, "
                        + "but included more options than just the field name, possibly use its 'options' form, with 'query' element?");
            }
        }

        if (value == null) {
            throw new ParsingException(parser.getTokenLocation(), "No text specified for text query");
        }

        MatchPhrasePrefixQueryBuilder matchQuery = new MatchPhrasePrefixQueryBuilder(fieldName, value);
        matchQuery.analyzer(analyzer);
        matchQuery.slop(slop);
        matchQuery.maxExpansions(maxExpansion);
        matchQuery.queryName(queryName);
        matchQuery.boost(boost);
        return matchQuery;
    }
}
