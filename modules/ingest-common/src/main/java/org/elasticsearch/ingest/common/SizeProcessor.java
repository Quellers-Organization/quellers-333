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

package org.elasticsearch.ingest.common;

import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.ingest.AbstractProcessor;
import org.elasticsearch.ingest.ConfigurationUtils;
import org.elasticsearch.ingest.IngestDocument;
import org.elasticsearch.ingest.Processor;

import java.io.IOException;
import java.util.Map;

/**
 * Processor that adds the size of the current JSON payload as `_size` field by default.
 */
public final class SizeProcessor extends AbstractProcessor {

    public static final String TYPE = "size";
    private static final String DEFAULT_TARGET = "_meta.size";
    private static final boolean DEFAULT_CONSIDER_SIZE_FIELD = false;

    private final String target;
    private final boolean considerSizeField;

    SizeProcessor(String tag, String target, boolean considerSizeField)  {
        super(tag);
        this.target = target;
        this.considerSizeField = considerSizeField;
    }

    String getTarget() {
        return target;
    }

    boolean isConsiderSizeField() {
        return considerSizeField;
    }

    @Override
    public void execute(IngestDocument document) {
        // Let's compute the size of a JSON representation of the current document
        SizeIngestDocument sizeIngestDocument = new SizeIngestDocument(document);
        String s = XContentHelper.toString(sizeIngestDocument);
        // Note that the size does not include the new size field itself which is added here
        document.setFieldValue(target, s.length());

        if (considerSizeField) {
            // We parse again the same document to get its real size, including size field
            sizeIngestDocument = new SizeIngestDocument(document);
            s = XContentHelper.toString(sizeIngestDocument);
            document.setFieldValue(target, s.length());
        }
    }

    @Override
    public String getType() {
        return TYPE;
    }

    public static final class Factory implements Processor.Factory {

        @Override
        public SizeProcessor create(Map<String, Processor.Factory> registry, String processorTag,
                                    Map<String, Object> config) throws Exception {
            return new SizeProcessor(processorTag,
                ConfigurationUtils.readStringProperty(TYPE, processorTag, config, "target", DEFAULT_TARGET),
                ConfigurationUtils.readBooleanProperty(TYPE, processorTag, config, "consider_size_field", DEFAULT_CONSIDER_SIZE_FIELD));
        }
    }

    private class SizeIngestDocument implements ToXContent {

        private final IngestDocument document;

        SizeIngestDocument(IngestDocument document) {
            this.document = document;
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            // We want to ignore metadata
            document.extractMetadata();
            for (String key : document.getSourceAndMetadata().keySet()) {
                builder.field(key, document.getSourceAndMetadata().get(key));
            }
            return builder;
        }
    }
}
