/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.aggregations.bucket.terms;

import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.fielddata.IndexFieldData;
import org.elasticsearch.index.fielddata.IndexNumericFieldData;
import org.elasticsearch.index.mapper.FieldMapper;
import org.elasticsearch.index.mapper.core.DateFieldMapper;
import org.elasticsearch.index.mapper.ip.IpFieldMapper;
import org.elasticsearch.script.SearchScript;
import org.elasticsearch.search.aggregations.Aggregator;
import org.elasticsearch.search.aggregations.AggregatorFactory;
import org.elasticsearch.search.aggregations.support.FieldContext;
import org.elasticsearch.search.aggregations.support.ValuesSource;
import org.elasticsearch.search.aggregations.support.ValuesSourceConfig;
import org.elasticsearch.search.aggregations.support.bytes.BytesValuesSource;
import org.elasticsearch.search.aggregations.support.numeric.NumericValuesSource;
import org.elasticsearch.search.aggregations.support.numeric.ValueFormatter;
import org.elasticsearch.search.aggregations.support.numeric.ValueParser;
import org.elasticsearch.search.internal.SearchContext;

import java.io.IOException;
import java.util.Map;

/**
 *
 */
public class TermsParser implements Aggregator.Parser {

    @Override
    public String type() {
        return StringTerms.TYPE.name();
    }

    // TODO add support for shard_size (vs. size) a la terms facets
    // TODO add support for term filtering (regexp/include/exclude) a la terms facets

    @Override
    public AggregatorFactory parse(String aggregationName, XContentParser parser, SearchContext context) throws IOException {

        String field = null;
        String script = null;
        String scriptLang = null;
        Map<String, Object> scriptParams = null;
        Terms.ValueType valueType = null;
        int requiredSize = 10;
        int shardSize = -1;
        String orderKey = "_count";
        boolean orderAsc = false;
        String format = null;
        boolean assumeUnique = false;


        XContentParser.Token token;
        String currentFieldName = null;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token == XContentParser.Token.VALUE_STRING) {
                if ("field".equals(currentFieldName)) {
                    field = parser.text();
                } else if ("script".equals(currentFieldName)) {
                    script = parser.text();
                } else if ("script_lang".equals(currentFieldName) || "scriptLang".equals(currentFieldName)) {
                    scriptLang = parser.text();
                } else if ("value_type".equals(currentFieldName) || "valueType".equals(currentFieldName)) {
                    valueType = Terms.ValueType.resolveType(parser.text());
                } else if ("format".equals(currentFieldName)) {
                    format = parser.text();
                }
            } else if (token == XContentParser.Token.VALUE_BOOLEAN) {
                if ("script_values_unique".equals(currentFieldName)) {
                    assumeUnique = parser.booleanValue();
                }
            } else if (token == XContentParser.Token.VALUE_NUMBER) {
                if ("size".equals(currentFieldName)) {
                    requiredSize = parser.intValue();
                } else if ("shard_size".equals(currentFieldName) || "shardSize".equals(currentFieldName)) {
                    shardSize = parser.intValue();
                }
            } else if (token == XContentParser.Token.START_OBJECT) {
                if ("params".equals(currentFieldName)) {
                    scriptParams = parser.map();
                } else if ("order".equals(currentFieldName)) {
                    while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                        if (token == XContentParser.Token.FIELD_NAME) {
                            orderKey = parser.currentName();
                        } else if (token == XContentParser.Token.VALUE_STRING) {
                            String dir = parser.text();
                            orderAsc = "asc".equalsIgnoreCase(dir);
                            //TODO: do we want to throw a parse error if the alternative is not "desc"???
                        }
                    }
                }
            }
        }

        // shard_size cannot be smaller than size as we need to at least fetch <size> entries from every shards in order to return <size>
        if (shardSize < requiredSize) {
            shardSize = requiredSize;
        }

        InternalOrder order = resolveOrder(orderKey, orderAsc);
        SearchScript searchScript = null;
        if (script != null) {
            searchScript = context.scriptService().search(context.lookup(), scriptLang, script, scriptParams);
        }

        if (field == null) {

            Class<? extends ValuesSource> valueSourceType = script == null ?
                    ValuesSource.class : // unknown, will inherit whatever is in the context
                    valueType != null ? valueType.scriptValueType.getValuesSourceType() : // the user explicitly defined a value type
                    BytesValuesSource.class; // defaulting to bytes

            ValuesSourceConfig<?> config = new ValuesSourceConfig(valueSourceType);
            if (valueType != null) {
                config.scriptValueType(valueType.scriptValueType);
            }
            config.script(searchScript);
            if (!assumeUnique) {
                config.ensureUnique(true);
            }
            return new TermsAggregatorFactory(aggregationName, config, order, requiredSize, shardSize);
        }

        FieldMapper<?> mapper = context.smartNameFieldMapper(field);
        if (mapper == null) {
            ValuesSourceConfig<?> config = new ValuesSourceConfig<BytesValuesSource>(BytesValuesSource.class);
            config.unmapped(true);
            return new TermsAggregatorFactory(aggregationName, config, order, requiredSize, shardSize);
        }
        IndexFieldData<?> indexFieldData = context.fieldData().getForField(mapper);

        ValuesSourceConfig<?> config;

        if (mapper instanceof DateFieldMapper) {
            DateFieldMapper dateMapper = (DateFieldMapper) mapper;
            ValueFormatter formatter = format == null ?
                    new ValueFormatter.DateTime(dateMapper.dateTimeFormatter()) :
                    new ValueFormatter.DateTime(format);
            config = new ValuesSourceConfig<NumericValuesSource>(NumericValuesSource.class)
                    .formatter(formatter)
                    .parser(new ValueParser.DateMath(dateMapper.dateMathParser()));

        } else if (mapper instanceof IpFieldMapper) {
            config = new ValuesSourceConfig<NumericValuesSource>(NumericValuesSource.class)
                    .formatter(ValueFormatter.IPv4)
                    .parser(ValueParser.IPv4);

        } else if (indexFieldData instanceof IndexNumericFieldData) {
            config = new ValuesSourceConfig<NumericValuesSource>(NumericValuesSource.class);
            if (format != null) {
                config.formatter(new ValueFormatter.Number.Pattern(format));
            }

        } else {
            config = new ValuesSourceConfig<BytesValuesSource>(BytesValuesSource.class);
            // TODO: it will make sense to set false instead here if the aggregator factory uses
            // ordinals instead of hash tables
            config.needsHashes(true);
        }

        config.script(searchScript);

        config.fieldContext(new FieldContext(field, indexFieldData));

        // We need values to be unique to be able to run terms aggs efficiently
        if (!assumeUnique) {
            config.ensureUnique(true);
        }

        return new TermsAggregatorFactory(aggregationName, config, order, requiredSize, shardSize);
    }

    static InternalOrder resolveOrder(String key, boolean asc) {
        if ("_term".equals(key)) {
            return (InternalOrder) (asc ? InternalOrder.TERM_ASC : InternalOrder.TERM_DESC);
        }
        if ("_count".equals(key)) {
            return (InternalOrder) (asc ? InternalOrder.COUNT_ASC : InternalOrder.COUNT_DESC);
        }
        int i = key.indexOf('.');
        if (i < 0) {
            return Terms.Order.aggregation(key, asc);
        }
        return Terms.Order.aggregation(key.substring(0, i), key.substring(i+1), asc);
    }

}
