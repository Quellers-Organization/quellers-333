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

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermRangeQuery;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.lucene.search.Queries;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.mapper.FieldMapper;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.mapper.internal.FieldNamesFieldMapper;

import java.io.IOException;
import java.util.List;

/**
 *
 */
public class ExistsQueryParser implements QueryParser {

    public static final String NAME = "exists";

    @Inject
    public ExistsQueryParser() {
    }

    @Override
    public String[] names() {
        return new String[]{NAME};
    }

    @Override
    public Query parse(QueryParseContext parseContext) throws IOException, QueryParsingException {
        XContentParser parser = parseContext.parser();

        String fieldPattern = null;
        String queryName = null;

        XContentParser.Token token;
        String currentFieldName = null;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token.isValue()) {
                if ("field".equals(currentFieldName)) {
                    fieldPattern = parser.text();
                } else if ("_name".equals(currentFieldName)) {
                    queryName = parser.text();
                } else {
                    throw new QueryParsingException(parseContext, "[exists] query does not support [" + currentFieldName + "]");
                }
            }
        }

        if (fieldPattern == null) {
            throw new QueryParsingException(parseContext, "exists must be provided with a [field]");
        }

        return newFilter(parseContext, fieldPattern, queryName);
    }

    public static Query newFilter(QueryParseContext parseContext, String fieldPattern, String queryName) {
        final FieldNamesFieldMapper fieldNamesMapper = (FieldNamesFieldMapper)parseContext.mapperService().fullName(FieldNamesFieldMapper.NAME);

        MapperService.SmartNameObjectMapper smartNameObjectMapper = parseContext.smartObjectMapper(fieldPattern);
        if (smartNameObjectMapper != null && smartNameObjectMapper.hasMapper()) {
            // automatic make the object mapper pattern
            fieldPattern = fieldPattern + ".*";
        }

        List<String> fields = parseContext.simpleMatchToIndexNames(fieldPattern);
        if (fields.isEmpty()) {
            // no fields exists, so we should not match anything
            return Queries.newMatchNoDocsQuery();
        }

        BooleanQuery boolFilter = new BooleanQuery();
        for (String field : fields) {
            FieldMapper mapper = parseContext.fieldMapper(field);
            Query filter = null;
            if (fieldNamesMapper!= null && fieldNamesMapper.enabled()) {
                final String f;
                if (mapper != null) {
                    f = mapper.names().indexName();
                } else {
                    f = field;
                }
                filter = fieldNamesMapper.termQuery(f, parseContext);
            }
            // if _field_names are not indexed, we need to go the slow way
            if (filter == null && mapper != null) {
                filter = mapper.rangeQuery(null, null, true, true, parseContext);
            }
            if (filter == null) {
                filter = new TermRangeQuery(field, null, null, true, true);
            }
            boolFilter.add(filter, BooleanClause.Occur.SHOULD);
        }

        if (queryName != null) {
            parseContext.addNamedQuery(queryName, boolFilter);
        }
        return new ConstantScoreQuery(boolFilter);
    }

}
