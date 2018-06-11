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

package org.elasticsearch.action.admin.indices.settings.get;

import org.elasticsearch.Version;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.settings.IndexScopedSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.RandomCreateIndexGenerator;
import org.elasticsearch.test.AbstractStreamableXContentTestCase;
import org.junit.Assert;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_NUMBER_OF_REPLICAS;
import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_NUMBER_OF_SHARDS;
import static org.elasticsearch.index.IndexSettings.INDEX_REFRESH_INTERVAL_SETTING;

public class GetSettingsResponseTests extends AbstractStreamableXContentTestCase<GetSettingsResponse> {

    /*
    index.number_of_shards=2,index.number_of_replicas=1.  The below base64'd bytes were generated by
    code from the 6.2.2 tag.
     */
    private static final String TEST_6_2_2_RESPONSE_BYTES =
        "AQppbmRleF9uYW1lAhhpbmRleC5udW1iZXJfb2ZfcmVwbGljYXMAATEWaW5kZXgubnVtYmVyX29mX3NoYXJkcwABMg==";

    /* This response object was generated using similar code to the code used to create the above bytes */
    private static final GetSettingsResponse TEST_6_2_2_RESPONSE_INSTANCE = getExpectedTest622Response();

    @Override
    protected GetSettingsResponse createBlankInstance() {
        return new GetSettingsResponse();
    }

    @Override
    protected GetSettingsResponse createTestInstance() {
        HashMap<String, Settings> indexToSettings = new HashMap<>();
        HashMap<String, Settings> indexToDefaultSettings = new HashMap<>();

        IndexScopedSettings indexScopedSettings = IndexScopedSettings.DEFAULT_SCOPED_SETTINGS;

        Set<String> indexNames = new HashSet<String>();
        int numIndices = randomIntBetween(1, 5);
        for (int x=0;x<numIndices;x++) {
            String indexName = randomAlphaOfLength(5);
            indexNames.add(indexName);
        }

        for (String indexName : indexNames) {
            Settings.Builder builder = Settings.builder();
            builder.put(RandomCreateIndexGenerator.randomIndexSettings());
            /*
            We must ensure that *something* is in the settings response as we optimize away empty settings
            blocks in x content responses
             */
            builder.put("index.refresh_interval", "1s");
            indexToSettings.put(indexName, builder.build());
        }
        ImmutableOpenMap<String, Settings> immutableIndexToSettings =
            ImmutableOpenMap.<String, Settings>builder().putAll(indexToSettings).build();


        if (randomBoolean()) {
            for (String indexName : indexToSettings.keySet()) {
                Settings defaultSettings = indexScopedSettings.diff(indexToSettings.get(indexName), Settings.EMPTY);
                indexToDefaultSettings.put(indexName, defaultSettings);
            }
        }

        ImmutableOpenMap<String, Settings> immutableIndexToDefaultSettings =
            ImmutableOpenMap.<String, Settings>builder().putAll(indexToDefaultSettings).build();

        return new GetSettingsResponse(immutableIndexToSettings, immutableIndexToDefaultSettings);
    }

    @Override
    protected GetSettingsResponse doParseInstance(XContentParser parser) throws IOException {
        return GetSettingsResponse.fromXContent(parser);
    }

    @Override
    protected Predicate<String> getRandomFieldsExcludeFilter() {
        //we do not want to add new fields at the root (index-level), or inside settings blocks
        return f -> f.equals("") || f.contains(".settings") || f.contains(".defaults");
    }

    private static GetSettingsResponse getExpectedTest622Response() {
    /* This is a fairly direct copy of the code used to generate the base64'd response above -- with the caveat that the constructor
    has been modified so that the code compiles on this version of elasticsearch
     */
        HashMap<String, Settings> indexToSettings = new HashMap<>();
        Settings.Builder builder = Settings.builder();

        builder.put(SETTING_NUMBER_OF_SHARDS, 2);
        builder.put(SETTING_NUMBER_OF_REPLICAS, 1);
        indexToSettings.put("index_name", builder.build());
        GetSettingsResponse response = new GetSettingsResponse(ImmutableOpenMap.<String, Settings>builder().putAll(indexToSettings).build
            (), ImmutableOpenMap.of());
        return response;
    }

    private static GetSettingsResponse getResponseWithNewFields() {
        HashMap<String, Settings> indexToDefaultSettings = new HashMap<>();
        Settings.Builder builder = Settings.builder();

        builder.put(INDEX_REFRESH_INTERVAL_SETTING.getKey(), "1s");
        indexToDefaultSettings.put("index_name", builder.build());
        ImmutableOpenMap<String, Settings> defaultsMap = ImmutableOpenMap.<String, Settings>builder().putAll(indexToDefaultSettings)
            .build();
        return new GetSettingsResponse(getExpectedTest622Response().getIndexToSettings(), defaultsMap);
    }

    public void testCanDecode622Response() throws IOException {
        StreamInput si = StreamInput.wrap(Base64.getDecoder().decode(TEST_6_2_2_RESPONSE_BYTES));
        si.setVersion(Version.V_6_2_2);
        GetSettingsResponse response = new GetSettingsResponse();
        response.readFrom(si);

        Assert.assertEquals(TEST_6_2_2_RESPONSE_INSTANCE, response);
    }

    public void testCanOutput622Response() throws IOException {
        GetSettingsResponse responseWithExtraFields = getResponseWithNewFields();
        BytesStreamOutput bso = new BytesStreamOutput();
        bso.setVersion(Version.V_6_2_2);
        responseWithExtraFields.writeTo(bso);

        String base64OfResponse = Base64.getEncoder().encodeToString(BytesReference.toBytes(bso.bytes()));

        Assert.assertEquals(TEST_6_2_2_RESPONSE_BYTES, base64OfResponse);
    }
}
