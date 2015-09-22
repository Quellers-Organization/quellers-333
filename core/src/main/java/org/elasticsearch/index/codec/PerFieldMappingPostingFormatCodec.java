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

package org.elasticsearch.index.codec;

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.lucene50.Lucene50StoredFieldsFormat;
import org.apache.lucene.codecs.lucene53.Lucene53Codec;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.lucene.Lucene;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.mapper.core.CompletionFieldMapper;
import org.elasticsearch.index.percolator.QueryMetadataService;

/**
 * {@link PerFieldMappingPostingFormatCodec This postings format} is the default
 * {@link PostingsFormat} for Elasticsearch. It utilizes the
 * {@link MapperService} to lookup a {@link PostingsFormat} per field. This
 * allows users to change the low level postings format for individual fields
 * per index in real time via the mapping API. If no specific postings format is
 * configured for a specific field the default postings format is used.
 */
// LUCENE UPGRADE: make sure to move to a new codec depending on the lucene version
public class PerFieldMappingPostingFormatCodec extends Lucene53Codec {
    private final ESLogger logger;
    private final MapperService mapperService;

    static {
        assert Codec.forName(Lucene.LATEST_CODEC).getClass().isAssignableFrom(PerFieldMappingPostingFormatCodec.class) : "PerFieldMappingPostingFormatCodec must subclass the latest lucene codec: " + Lucene.LATEST_CODEC;
    }

    public PerFieldMappingPostingFormatCodec(Lucene50StoredFieldsFormat.Mode compressionMode, MapperService mapperService, ESLogger logger) {
        super(compressionMode);
        this.mapperService = mapperService;
        this.logger = logger;
    }

    @Override
    public PostingsFormat getPostingsFormatForField(String field) {
        final MappedFieldType indexName = mapperService.indexName(field);
        if (indexName == null) {
            // the query terms are an percolator implementation detail and are added as Lucene indexed fields which are hidden
            // We shouldn't expose these fields in the mapping, because they don't appear in the document source.
            if (indexName.names().indexName().startsWith(QueryMetadataService.QUERY_METADATA) == false) {
                logger.warn("no index mapper found for field: [{}] returning default postings format", field);
            }
        } else if (indexName instanceof CompletionFieldMapper.CompletionFieldType) {
            // CompletionFieldMapper needs a special postings format
            final CompletionFieldMapper.CompletionFieldType fieldType = (CompletionFieldMapper.CompletionFieldType) indexName;
            final PostingsFormat defaultFormat = super.getPostingsFormatForField(field);
            return fieldType.postingsFormat(defaultFormat);
        }
        return super.getPostingsFormatForField(field);
    }

}
