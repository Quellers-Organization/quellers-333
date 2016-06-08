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

package org.elasticsearch.ingest;


import org.elasticsearch.ingest.core.IngestDocument;
import org.elasticsearch.ingest.core.Processor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;

public class IngestScriptProcessorIT extends AbstractScriptTestCase {

    public void testInlineMustache() throws Exception {
        Processor processor = createScriptProcessor("_value {{field}}", "mustache", "inline");
        IngestDocument ingestDocument = createIngestDocument(Collections.singletonMap("field", "value"));
        processor.execute(ingestDocument);
    }

    public void testInlinePainless() throws Exception {
        Processor processor = createScriptProcessor("ctx.put(\"field\",\"wow\"); return \"returned_val\";", "painless", "inline");
        IngestDocument ingestDocument = createIngestDocument(Collections.singletonMap("field", "cool"));
        processor.execute(ingestDocument);
        assertThat(ingestDocument.getFieldValue("returned", String.class), equalTo("returned_val"));
        assertThat(ingestDocument.getFieldValue("field", String.class), equalTo("wow"));
    }

    private ScriptProcessor createScriptProcessor(String script, String scriptLang, String type) throws Exception {
        ScriptProcessor.Factory factory = new ScriptProcessor.Factory(scriptService);
        Map<String, Object> config = new HashMap<>();
        config.put(type, script);
        config.put("lang", scriptLang);
        config.put("field", "returned");
        return factory.create(config);
    }

    private IngestDocument createIngestDocument(Map<String, Object> source) {
        return new IngestDocument("_index", "_type", "_id", null, null, null, null, source);
    }
}
