/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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

import org.apache.lucene.search.Query;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;

/**
 * Query parser for JSON Queries.
 *
 * @author Cedric Champeau
 */
public class JSONQueryParser implements QueryParser {

    public static final String NAME = "json";

    @Inject public JSONQueryParser() {
    }

    @Override public String[] names() {
        return new String[] {NAME};
    }

    @Override public Query parse(QueryParseContext parseContext) throws IOException, QueryParsingException {
        System.out.println("parseContext = " + parseContext);
        XContentParser parser = parseContext.parser();
        XContentParser.Token token;
        Query query = null;
        String currentFieldName = null;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token == XContentParser.Token.START_OBJECT && "value".equals(currentFieldName)) {
                query = parseContext.parseInnerQuery();
            }
        }
        return query;
    }
}
