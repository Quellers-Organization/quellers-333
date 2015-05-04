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

package org.elasticsearch.common.lucene.search;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.index.mapper.internal.TypeFieldMapper;

import java.util.List;
import java.util.regex.Pattern;

/**
 *
 */
public class Queries {

    public static Query newMatchAllQuery() {
        return new MatchAllDocsQuery();
    }

    /** Return a query that matches no document. */
    public static Query newMatchNoDocsQuery() {
        return new BooleanQuery();
    }

    public static Filter newMatchAllFilter() {
        return new QueryWrapperFilter(newMatchAllQuery());
    }

    public static Filter newMatchNoDocsFilter() {
        return new QueryWrapperFilter(newMatchNoDocsQuery());
    }

    public static Filter newNestedFilter() {
        return new QueryWrapperFilter(new PrefixQuery(new Term(TypeFieldMapper.NAME, new BytesRef("__"))));
    }

    public static Filter newNonNestedFilter() {
        return new QueryWrapperFilter(not(newNestedFilter()));
    }

    /** Return a query that matches all documents but those that match the given query. */
    public static Query not(Query q) {
        BooleanQuery bq = new BooleanQuery();
        bq.add(new MatchAllDocsQuery(), Occur.MUST);
        bq.add(q, Occur.MUST_NOT);
        return bq;
    }

    public static boolean isNegativeQuery(Query q) {
        if (!(q instanceof BooleanQuery)) {
            return false;
        }
        List<BooleanClause> clauses = ((BooleanQuery) q).clauses();
        if (clauses.isEmpty()) {
            return false;
        }
        for (BooleanClause clause : clauses) {
            if (!clause.isProhibited()) return false;
        }
        return true;
    }

    public static Query fixNegativeQueryIfNeeded(Query q) {
        if (isNegativeQuery(q)) {
            BooleanQuery newBq = (BooleanQuery) q.clone();
            newBq.add(newMatchAllQuery(), BooleanClause.Occur.MUST);
            return newBq;
        }
        return q;
    }

    public static boolean isConstantMatchAllQuery(Query query) {
        if (query instanceof ConstantScoreQuery) {
            return isConstantMatchAllQuery(((ConstantScoreQuery) query).getQuery());
        } else if (query instanceof QueryWrapperFilter) {
            return isConstantMatchAllQuery(((QueryWrapperFilter) query).getQuery());
        } else if (query instanceof MatchAllDocsQuery) {
            return true;
        }
        return false;
    }

    public static void applyMinimumShouldMatch(BooleanQuery query, @Nullable String minimumShouldMatch) {
        if (minimumShouldMatch == null) {
            return;
        }
        int optionalClauses = 0;
        for (BooleanClause c : query.clauses()) {
            if (c.getOccur() == BooleanClause.Occur.SHOULD) {
                optionalClauses++;
            }
        }

        int msm = calculateMinShouldMatch(optionalClauses, minimumShouldMatch);
        if (0 < msm) {
            query.setMinimumNumberShouldMatch(msm);
        }
    }

    private static Pattern spaceAroundLessThanPattern = Pattern.compile("(\\s+<\\s*)|(\\s*<\\s+)");
    private static Pattern spacePattern = Pattern.compile(" ");
    private static Pattern lessThanPattern = Pattern.compile("<");

    public static int calculateMinShouldMatch(int optionalClauseCount, String spec) {
        int result = optionalClauseCount;
        spec = spec.trim();

        if (-1 < spec.indexOf("<")) {
            /* we have conditional spec(s) */
            spec = spaceAroundLessThanPattern.matcher(spec).replaceAll("<");
            for (String s : spacePattern.split(spec)) {
                String[] parts = lessThanPattern.split(s, 0);
                int upperBound = Integer.parseInt(parts[0]);
                if (optionalClauseCount <= upperBound) {
                    return result;
                } else {
                    result = calculateMinShouldMatch
                            (optionalClauseCount, parts[1]);
                }
            }
            return result;
        }

        /* otherwise, simple expression */

        if (-1 < spec.indexOf('%')) {
            /* percentage - assume the % was the last char.  If not, let Integer.parseInt fail. */
            spec = spec.substring(0, spec.length() - 1);
            int percent = Integer.parseInt(spec);
            float calc = (result * percent) * (1 / 100f);
            result = calc < 0 ? result + (int) calc : (int) calc;
        } else {
            int calc = Integer.parseInt(spec);
            result = calc < 0 ? result + calc : calc;
        }

        return (optionalClauseCount < result ?
                optionalClauseCount : (result < 0 ? 0 : result));

    }
}
