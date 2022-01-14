/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.mapper;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.AutomatonQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.lucene.BytesRefs;
import org.elasticsearch.common.lucene.search.AutomatonQueries;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.SearchExecutionContext;
import org.elasticsearch.index.query.support.QueryParsers;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.elasticsearch.search.SearchService.ALLOW_EXPENSIVE_QUERIES;

/** Base class for {@link MappedFieldType} implementations that use the same
 * representation for internal index terms as the external representation so
 * that partial matching queries such as prefix, wildcard and fuzzy queries
 * can be implemented. */
public abstract class StringFieldType extends TermBasedFieldType {

    private static final Pattern WILDCARD_PATTERN = Pattern.compile("(\\\\.)|([?*]+)");

    public StringFieldType(
        String name,
        boolean isIndexed,
        boolean isStored,
        boolean hasDocValues,
        TextSearchInfo textSearchInfo,
        Map<String, String> meta
    ) {
        super(name, isIndexed, isStored, hasDocValues, textSearchInfo, meta);
    }

    @Override
    public Query fuzzyQuery(
        Object value,
        Fuzziness fuzziness,
        int prefixLength,
        int maxExpansions,
        boolean transpositions,
        SearchExecutionContext context
    ) {
        if (context.allowExpensiveQueries() == false) {
            throw new ElasticsearchException(
                "[fuzzy] queries cannot be executed when '" + ALLOW_EXPENSIVE_QUERIES.getKey() + "' is set to false."
            );
        }
        failIfNotIndexed();
        return new FuzzyQuery(
            new Term(name(), indexedValueForSearch(value)),
            fuzziness.asDistance(BytesRefs.toString(value)),
            prefixLength,
            maxExpansions,
            transpositions
        );
    }

    @Override
    public Query prefixQuery(String value, MultiTermQuery.RewriteMethod method, boolean caseInsensitive, SearchExecutionContext context) {
        if (context.allowExpensiveQueries() == false) {
            throw new ElasticsearchException(
                "[prefix] queries cannot be executed when '"
                    + ALLOW_EXPENSIVE_QUERIES.getKey()
                    + "' is set to false. For optimised prefix queries on text "
                    + "fields please enable [index_prefixes]."
            );
        }
        failIfNotIndexed();
        if (caseInsensitive) {
            AutomatonQuery query = AutomatonQueries.caseInsensitivePrefixQuery((new Term(name(), indexedValueForSearch(value))));
            if (method != null) {
                query.setRewriteMethod(method);
            }
            return query;

        }
        PrefixQuery query = new PrefixQuery(new Term(name(), indexedValueForSearch(value)));
        if (method != null) {
            query.setRewriteMethod(method);
        }
        return query;
    }

    public static final String normalizeWildcardPattern(String fieldname, String value, Analyzer normalizer) {
        if (normalizer == null) {
            return value;
        }
        // we want to normalize everything except wildcard characters, e.g. F?o Ba* to f?o ba*, even if e.g there
        // is a char_filter that would otherwise remove them
        Matcher wildcardMatcher = WILDCARD_PATTERN.matcher(value);
        BytesRefBuilder sb = new BytesRefBuilder();
        int last = 0;

        while (wildcardMatcher.find()) {
            if (wildcardMatcher.start() > 0) {
                String chunk = value.substring(last, wildcardMatcher.start());

                BytesRef normalized = normalizer.normalize(fieldname, chunk);
                sb.append(normalized);
            }
            // append the matched group - without normalizing
            sb.append(new BytesRef(wildcardMatcher.group()));

            last = wildcardMatcher.end();
        }
        if (last < value.length()) {
            String chunk = value.substring(last);
            BytesRef normalized = normalizer.normalize(fieldname, chunk);
            sb.append(normalized);
        }
        return sb.toBytesRef().utf8ToString();
    }

    @Override
    public Query wildcardQuery(String value, MultiTermQuery.RewriteMethod method, boolean caseInsensitive, SearchExecutionContext context) {
        return wildcardQuery(value, method, caseInsensitive, false, context);
    }

    @Override
    public Query normalizedWildcardQuery(String value, MultiTermQuery.RewriteMethod method, SearchExecutionContext context) {
        return wildcardQuery(value, method, false, true, context);
    }

    protected Query wildcardQuery(
        String value,
        MultiTermQuery.RewriteMethod method,
        boolean caseInsensitive,
        boolean shouldNormalize,
        SearchExecutionContext context
    ) {
        failIfNotIndexed();
        if (context.allowExpensiveQueries() == false) {
            throw new ElasticsearchException(
                "[wildcard] queries cannot be executed when '" + ALLOW_EXPENSIVE_QUERIES.getKey() + "' is set to false."
            );
        }

        Term term;
        if (getTextSearchInfo().getSearchAnalyzer() != null && shouldNormalize) {
            value = normalizeWildcardPattern(name(), value, getTextSearchInfo().getSearchAnalyzer());
            term = new Term(name(), value);
        } else {
            term = new Term(name(), indexedValueForSearch(value));
        }
        if (caseInsensitive) {
            AutomatonQuery query = AutomatonQueries.caseInsensitiveWildcardQuery(term);
            QueryParsers.setRewriteMethod(query, method);
            return query;
        }
        WildcardQuery query = new WildcardQuery(term);
        QueryParsers.setRewriteMethod(query, method);
        return query;
    }

    @Override
    public Query regexpQuery(
        String value,
        int syntaxFlags,
        int matchFlags,
        int maxDeterminizedStates,
        MultiTermQuery.RewriteMethod method,
        SearchExecutionContext context
    ) {
        if (context.allowExpensiveQueries() == false) {
            throw new ElasticsearchException(
                "[regexp] queries cannot be executed when '" + ALLOW_EXPENSIVE_QUERIES.getKey() + "' is set to false."
            );
        }
        failIfNotIndexed();
        RegexpQuery query = new RegexpQuery(new Term(name(), indexedValueForSearch(value)), syntaxFlags, matchFlags, maxDeterminizedStates);
        if (method != null) {
            query.setRewriteMethod(method);
        }
        return query;
    }

    @Override
    public Query rangeQuery(
        Object lowerTerm,
        Object upperTerm,
        boolean includeLower,
        boolean includeUpper,
        SearchExecutionContext context
    ) {
        if (context.allowExpensiveQueries() == false) {
            throw new ElasticsearchException(
                "[range] queries on [text] or [keyword] fields cannot be executed when '"
                    + ALLOW_EXPENSIVE_QUERIES.getKey()
                    + "' is set to false."
            );
        }
        failIfNotIndexedNorDocValuesFallback(context);
        if (isIndexed()) {
            return new TermRangeQuery(
                name(),
                lowerTerm == null ? null : indexedValueForSearch(lowerTerm),
                upperTerm == null ? null : indexedValueForSearch(upperTerm),
                includeLower,
                includeUpper
            );
        } else {
            return SortedSetDocValuesField.newSlowRangeQuery(
                name(),
                lowerTerm == null ? null : indexedValueForSearch(lowerTerm),
                upperTerm == null ? null : indexedValueForSearch(upperTerm),
                includeLower,
                includeUpper);
        }
    }
}
