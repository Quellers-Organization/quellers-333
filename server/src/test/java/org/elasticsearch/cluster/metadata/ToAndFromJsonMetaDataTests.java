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

package org.elasticsearch.cluster.metadata;

import org.elasticsearch.Version;
import org.elasticsearch.action.admin.indices.rollover.RolloverInfo;
import org.elasticsearch.cluster.coordination.CoordinationMetaData;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.compress.CompressedXContent;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.TestCustomMetaData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.elasticsearch.cluster.metadata.AliasMetaData.newAliasMetaDataBuilder;
import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_VERSION_CREATED;
import static org.elasticsearch.cluster.metadata.MetaData.CONTEXT_MODE_API;
import static org.elasticsearch.cluster.metadata.MetaData.CONTEXT_MODE_GATEWAY;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class ToAndFromJsonMetaDataTests extends ESTestCase {

    public void testSimpleJsonFromAndTo() throws IOException {
        MetaData metaData = MetaData.builder()
                .put(IndexTemplateMetaData.builder("foo")
                        .patterns(Collections.singletonList("bar"))
                        .order(1)
                        .settings(Settings.builder()
                                .put("setting1", "value1")
                                .put("setting2", "value2"))
                        .putAlias(newAliasMetaDataBuilder("alias-bar1"))
                        .putAlias(newAliasMetaDataBuilder("alias-bar2").filter("{\"term\":{\"user\":\"kimchy\"}}"))
                        .putAlias(newAliasMetaDataBuilder("alias-bar3").routing("routing-bar")))
                .put("component_template", new ComponentTemplate(
                    new Template(Settings.builder().put("setting", "value").build(),
                        new CompressedXContent("{\"baz\":\"eggplant\"}"),
                        Collections.singletonMap("alias", AliasMetaData.builder("alias").build())),
                    5L, Collections.singletonMap("my_meta", Collections.singletonMap("foo", "bar"))))
                .put("index_templatev2", new IndexTemplateV2(Arrays.asList("foo", "bar*"),
                    new Template(Settings.builder().put("setting", "value").build(),
                        new CompressedXContent("{\"baz\":\"eggplant\"}"),
                        Collections.singletonMap("alias", AliasMetaData.builder("alias").build())),
                    Collections.singletonList("component_template"),
                    5L,
                    4L,
                    Collections.singletonMap("my_meta", Collections.singletonMap("potato", "chicken"))))
                .put(IndexMetaData.builder("test12")
                        .settings(settings(Version.CURRENT)
                                .put("setting1", "value1")
                                .put("setting2", "value2"))
                        .creationDate(2L)
                        .numberOfShards(1)
                        .numberOfReplicas(2)
                        .putMapping("mapping1", MAPPING_SOURCE1)
                        .putMapping("mapping2", MAPPING_SOURCE2)
                        .putAlias(newAliasMetaDataBuilder("alias1").filter(ALIAS_FILTER1))
                        .putAlias(newAliasMetaDataBuilder("alias3").writeIndex(randomBoolean() ? null : randomBoolean()))
                        .putAlias(newAliasMetaDataBuilder("alias4").filter(ALIAS_FILTER2)))
                .put(IndexTemplateMetaData.builder("foo")
                        .patterns(Collections.singletonList("bar"))
                        .order(1)
                        .settings(Settings.builder()
                                .put("setting1", "value1")
                                .put("setting2", "value2"))
                        .putAlias(newAliasMetaDataBuilder("alias-bar1"))
                        .putAlias(newAliasMetaDataBuilder("alias-bar2").filter("{\"term\":{\"user\":\"kimchy\"}}"))
                        .putAlias(newAliasMetaDataBuilder("alias-bar3").routing("routing-bar")))
                .put(new DataStream("data-stream1", "@timestamp", Collections.emptyList()))
                .put(new DataStream("data-stream2", "@timestamp2", Collections.emptyList()))
                .build();

        XContentBuilder builder = JsonXContent.contentBuilder();
        builder.startObject();
        MetaData.FORMAT.toXContent(builder, metaData);
        builder.endObject();

        MetaData parsedMetaData = MetaData.Builder.fromXContent(createParser(builder));

        // templates
        assertThat(parsedMetaData.templates().get("foo").name(), is("foo"));
        assertThat(parsedMetaData.templates().get("foo").patterns(), is(Collections.singletonList("bar")));
        assertThat(parsedMetaData.templates().get("foo").settings().get("index.setting1"), is("value1"));
        assertThat(parsedMetaData.templates().get("foo").settings().getByPrefix("index.").get("setting2"), is("value2"));
        assertThat(parsedMetaData.templates().get("foo").aliases().size(), equalTo(3));
        assertThat(parsedMetaData.templates().get("foo").aliases().get("alias-bar1").alias(), equalTo("alias-bar1"));
        assertThat(parsedMetaData.templates().get("foo").aliases().get("alias-bar2").alias(), equalTo("alias-bar2"));
        assertThat(parsedMetaData.templates().get("foo").aliases().get("alias-bar2").filter().string(),
            equalTo("{\"term\":{\"user\":\"kimchy\"}}"));
        assertThat(parsedMetaData.templates().get("foo").aliases().get("alias-bar3").alias(), equalTo("alias-bar3"));
        assertThat(parsedMetaData.templates().get("foo").aliases().get("alias-bar3").indexRouting(), equalTo("routing-bar"));
        assertThat(parsedMetaData.templates().get("foo").aliases().get("alias-bar3").searchRouting(), equalTo("routing-bar"));

        // component template
        assertNotNull(parsedMetaData.componentTemplates().get("component_template"));
        assertThat(parsedMetaData.componentTemplates().get("component_template").version(), is(5L));
        assertThat(parsedMetaData.componentTemplates().get("component_template").metadata(),
            equalTo(Collections.singletonMap("my_meta", Collections.singletonMap("foo", "bar"))));
        assertThat(parsedMetaData.componentTemplates().get("component_template").template(),
            equalTo(new Template(Settings.builder().put("setting", "value").build(),
                new CompressedXContent("{\"baz\":\"eggplant\"}"),
                Collections.singletonMap("alias", AliasMetaData.builder("alias").build()))));

        // index template v2
        assertNotNull(parsedMetaData.templatesV2().get("index_templatev2"));
        assertThat(parsedMetaData.templatesV2().get("index_templatev2").priority(), is(5L));
        assertThat(parsedMetaData.templatesV2().get("index_templatev2").version(), is(4L));
        assertThat(parsedMetaData.templatesV2().get("index_templatev2").indexPatterns(), is(Arrays.asList("foo", "bar*")));
        assertThat(parsedMetaData.templatesV2().get("index_templatev2").composedOf(), is(Collections.singletonList("component_template")));
        assertThat(parsedMetaData.templatesV2().get("index_templatev2").metadata(),
            equalTo(Collections.singletonMap("my_meta", Collections.singletonMap("potato", "chicken"))));
        assertThat(parsedMetaData.templatesV2().get("index_templatev2").template(),
            equalTo(new Template(Settings.builder().put("setting", "value").build(),
                new CompressedXContent("{\"baz\":\"eggplant\"}"),
                Collections.singletonMap("alias", AliasMetaData.builder("alias").build()))));

        // data streams
        assertNotNull(parsedMetaData.dataStreams().get("data-stream1"));
        assertThat(parsedMetaData.dataStreams().get("data-stream1").getName(), is("data-stream1"));
        assertThat(parsedMetaData.dataStreams().get("data-stream1").getTimeStampField(), is("@timestamp"));
        assertThat(parsedMetaData.dataStreams().get("data-stream1").getIndices(), is(Collections.emptyList()));
        assertNotNull(parsedMetaData.dataStreams().get("data-stream2"));
        assertThat(parsedMetaData.dataStreams().get("data-stream2").getName(), is("data-stream2"));
        assertThat(parsedMetaData.dataStreams().get("data-stream2").getTimeStampField(), is("@timestamp2"));
        assertThat(parsedMetaData.dataStreams().get("data-stream2").getIndices(), is(Collections.emptyList()));
    }

    private static final String MAPPING_SOURCE1 = "{\"mapping1\":{\"text1\":{\"type\":\"string\"}}}";
    private static final String MAPPING_SOURCE2 = "{\"mapping2\":{\"text2\":{\"type\":\"string\"}}}";
    private static final String ALIAS_FILTER1 = "{\"field1\":\"value1\"}";
    private static final String ALIAS_FILTER2 = "{\"field2\":\"value2\"}";

    public void testToXContentGateway_FlatSettingTrue_ReduceMappingFalse() throws IOException {
        Map<String, String> mapParams = new HashMap<String,String>(){{
            put(MetaData.CONTEXT_MODE_PARAM, CONTEXT_MODE_GATEWAY);
            put("flat_settings", "true");
            put("reduce_mappings", "false");
        }};

        MetaData metaData = buildMetaData();
        XContentBuilder builder = JsonXContent.contentBuilder().prettyPrint();
        builder.startObject();
        metaData.toXContent(builder, new ToXContent.MapParams(mapParams));
        builder.endObject();

        assertEquals("{\n" +
            "  \"meta-data\" : {\n" +
            "    \"version\" : 0,\n" +
            "    \"cluster_uuid\" : \"clusterUUID\",\n" +
            "    \"cluster_uuid_committed\" : false,\n" +
            "    \"cluster_coordination\" : {\n" +
            "      \"term\" : 1,\n" +
            "      \"last_committed_config\" : [\n" +
            "        \"commitedConfigurationNodeId\"\n" +
            "      ],\n" +
            "      \"last_accepted_config\" : [\n" +
            "        \"acceptedConfigurationNodeId\"\n" +
            "      ],\n" +
            "      \"voting_config_exclusions\" : [\n" +
            "        {\n" +
            "          \"node_id\" : \"exlucdedNodeId\",\n" +
            "          \"node_name\" : \"excludedNodeName\"\n" +
            "        }\n" +
            "      ]\n" +
            "    },\n" +
            "    \"settings\" : {\n" +
            "      \"index.version.created\" : \"" + Version.CURRENT.id + "\"\n" +
            "    },\n" +
            "    \"templates\" : {\n" +
            "      \"template\" : {\n" +
            "        \"order\" : 0,\n" +
            "        \"index_patterns\" : [\n" +
            "          \"pattern1\",\n" +
            "          \"pattern2\"\n" +
            "        ],\n" +
            "        \"settings\" : {\n" +
            "          \"index.version.created\" : \"" + Version.CURRENT.id + "\"\n" +
            "        },\n" +
            "        \"mappings\" : [\n" +
            "          {\n" +
            "            \"key1\" : { }\n" +
            "          }\n" +
            "        ],\n" +
            "        \"aliases\" : { }\n" +
            "      }\n" +
            "    },\n" +
            "    \"index-graveyard\" : {\n" +
            "      \"tombstones\" : [ ]\n" +
            "    }\n" +
            "  }\n" +
            "}", Strings.toString(builder));
    }

    public void testToXContentAPI_SameTypeName() throws IOException {
        Map<String, String> mapParams = new HashMap<String,String>(){{
            put(MetaData.CONTEXT_MODE_PARAM, CONTEXT_MODE_API);
        }};

        MetaData metaData = MetaData.builder()
            .clusterUUID("clusterUUID")
            .coordinationMetaData(CoordinationMetaData.builder()
                .build())
            .put(IndexMetaData.builder("index")
                .state(IndexMetaData.State.OPEN)
                .settings(Settings.builder()
                    .put(SETTING_VERSION_CREATED, Version.CURRENT.id))
                .putMapping(new MappingMetaData("type",
                    // the type name is the root value,
                    // the original logic in ClusterState.toXContent will reduce
                    new HashMap<String,Object>(){{
                        put("type", new HashMap<String, Object>(){{
                            put("key", "value");
                        }});
                    }}))
                .numberOfShards(1)
                .primaryTerm(0, 1L)
                .numberOfReplicas(2))
            .build();
        XContentBuilder builder = JsonXContent.contentBuilder().prettyPrint();
        builder.startObject();
        metaData.toXContent(builder, new ToXContent.MapParams(mapParams));
        builder.endObject();

        assertEquals("{\n" +
            "  \"metadata\" : {\n" +
            "    \"cluster_uuid\" : \"clusterUUID\",\n" +
            "    \"cluster_uuid_committed\" : false,\n" +
            "    \"cluster_coordination\" : {\n" +
            "      \"term\" : 0,\n" +
            "      \"last_committed_config\" : [ ],\n" +
            "      \"last_accepted_config\" : [ ],\n" +
            "      \"voting_config_exclusions\" : [ ]\n" +
            "    },\n" +
            "    \"templates\" : { },\n" +
            "    \"indices\" : {\n" +
            "      \"index\" : {\n" +
            "        \"version\" : 2,\n" +
            "        \"mapping_version\" : 1,\n" +
            "        \"settings_version\" : 1,\n" +
            "        \"aliases_version\" : 1,\n" +
            "        \"routing_num_shards\" : 1,\n" +
            "        \"state\" : \"open\",\n" +
            "        \"settings\" : {\n" +
            "          \"index\" : {\n" +
            "            \"number_of_shards\" : \"1\",\n" +
            "            \"number_of_replicas\" : \"2\",\n" +
            "            \"version\" : {\n" +
            "              \"created\" : \"" + Version.CURRENT.id + "\"\n" +
            "            }\n" +
            "          }\n" +
            "        },\n" +
            "        \"mappings\" : {\n" +
            "          \"type\" : {\n" +
            "            \"key\" : \"value\"\n" +
            "          }\n" +
            "        },\n" +
            "        \"aliases\" : [ ],\n" +
            "        \"primary_terms\" : {\n" +
            "          \"0\" : 1\n" +
            "        },\n" +
            "        \"in_sync_allocations\" : {\n" +
            "          \"0\" : [ ]\n" +
            "        },\n" +
            "        \"rollover_info\" : { }\n" +
            "      }\n" +
            "    },\n" +
            "    \"index-graveyard\" : {\n" +
            "      \"tombstones\" : [ ]\n" +
            "    }\n" +
            "  }\n" +
            "}", Strings.toString(builder));
    }

    public void testToXContentGateway_FlatSettingFalse_ReduceMappingTrue() throws IOException {
        Map<String, String> mapParams = new HashMap<String,String>(){{
            put(MetaData.CONTEXT_MODE_PARAM, CONTEXT_MODE_GATEWAY);
            put("flat_settings", "false");
            put("reduce_mappings", "true");
        }};

        MetaData metaData = buildMetaData();
        XContentBuilder builder = JsonXContent.contentBuilder().prettyPrint();
        builder.startObject();
        metaData.toXContent(builder, new ToXContent.MapParams(mapParams));
        builder.endObject();

        assertEquals("{\n" +
            "  \"meta-data\" : {\n" +
            "    \"version\" : 0,\n" +
            "    \"cluster_uuid\" : \"clusterUUID\",\n" +
            "    \"cluster_uuid_committed\" : false,\n" +
            "    \"cluster_coordination\" : {\n" +
            "      \"term\" : 1,\n" +
            "      \"last_committed_config\" : [\n" +
            "        \"commitedConfigurationNodeId\"\n" +
            "      ],\n" +
            "      \"last_accepted_config\" : [\n" +
            "        \"acceptedConfigurationNodeId\"\n" +
            "      ],\n" +
            "      \"voting_config_exclusions\" : [\n" +
            "        {\n" +
            "          \"node_id\" : \"exlucdedNodeId\",\n" +
            "          \"node_name\" : \"excludedNodeName\"\n" +
            "        }\n" +
            "      ]\n" +
            "    },\n" +
            "    \"settings\" : {\n" +
            "      \"index.version.created\" : \"" + Version.CURRENT.id + "\"\n" +
            "    },\n" +
            "    \"templates\" : {\n" +
            "      \"template\" : {\n" +
            "        \"order\" : 0,\n" +
            "        \"index_patterns\" : [\n" +
            "          \"pattern1\",\n" +
            "          \"pattern2\"\n" +
            "        ],\n" +
            "        \"settings\" : {\n" +
            "          \"index\" : {\n" +
            "            \"version\" : {\n" +
            "              \"created\" : \"" + Version.CURRENT.id + "\"\n" +
            "            }\n" +
            "          }\n" +
            "        },\n" +
            "        \"mappings\" : {\n" +
            "          \"type\" : {\n" +
            "            \"key1\" : { }\n" +
            "          }\n" +
            "        },\n" +
            "        \"aliases\" : { }\n" +
            "      }\n" +
            "    },\n" +
            "    \"index-graveyard\" : {\n" +
            "      \"tombstones\" : [ ]\n" +
            "    }\n" +
            "  }\n" +
            "}", Strings.toString(builder));
    }

    public void testToXContentAPI_FlatSettingTrue_ReduceMappingFalse() throws IOException {
        Map<String, String> mapParams = new HashMap<String,String>(){{
            put(MetaData.CONTEXT_MODE_PARAM, CONTEXT_MODE_API);
            put("flat_settings", "true");
            put("reduce_mappings", "false");
        }};

        final MetaData metaData = buildMetaData();

        XContentBuilder builder = JsonXContent.contentBuilder().prettyPrint();
        builder.startObject();
        metaData.toXContent(builder, new ToXContent.MapParams(mapParams));
        builder.endObject();

        assertEquals("{\n" +
            "  \"metadata\" : {\n" +
            "    \"cluster_uuid\" : \"clusterUUID\",\n" +
            "    \"cluster_uuid_committed\" : false,\n" +
            "    \"cluster_coordination\" : {\n" +
            "      \"term\" : 1,\n" +
            "      \"last_committed_config\" : [\n" +
            "        \"commitedConfigurationNodeId\"\n" +
            "      ],\n" +
            "      \"last_accepted_config\" : [\n" +
            "        \"acceptedConfigurationNodeId\"\n" +
            "      ],\n" +
            "      \"voting_config_exclusions\" : [\n" +
            "        {\n" +
            "          \"node_id\" : \"exlucdedNodeId\",\n" +
            "          \"node_name\" : \"excludedNodeName\"\n" +
            "        }\n" +
            "      ]\n" +
            "    },\n" +
            "    \"templates\" : {\n" +
            "      \"template\" : {\n" +
            "        \"order\" : 0,\n" +
            "        \"index_patterns\" : [\n" +
            "          \"pattern1\",\n" +
            "          \"pattern2\"\n" +
            "        ],\n" +
            "        \"settings\" : {\n" +
            "          \"index.version.created\" : \"" + Version.CURRENT.id + "\"\n" +
            "        },\n" +
            "        \"mappings\" : {\n" +
            "          \"type\" : {\n" +
            "            \"key1\" : { }\n" +
            "          }\n" +
            "        },\n" +
            "        \"aliases\" : { }\n" +
            "      }\n" +
            "    },\n" +
            "    \"indices\" : {\n" +
            "      \"index\" : {\n" +
            "        \"version\" : 2,\n" +
            "        \"mapping_version\" : 1,\n" +
            "        \"settings_version\" : 1,\n" +
            "        \"aliases_version\" : 1,\n" +
            "        \"routing_num_shards\" : 1,\n" +
            "        \"state\" : \"open\",\n" +
            "        \"settings\" : {\n" +
            "          \"index.number_of_replicas\" : \"2\",\n" +
            "          \"index.number_of_shards\" : \"1\",\n" +
            "          \"index.version.created\" : \"" + Version.CURRENT.id + "\"\n" +
            "        },\n" +
            "        \"mappings\" : {\n" +
            "          \"type\" : {\n" +
            "            \"type1\" : {\n" +
            "              \"key\" : \"value\"\n" +
            "            }\n" +
            "          }\n" +
            "        },\n" +
            "        \"aliases\" : [\n" +
            "          \"alias\"\n" +
            "        ],\n" +
            "        \"primary_terms\" : {\n" +
            "          \"0\" : 1\n" +
            "        },\n" +
            "        \"in_sync_allocations\" : {\n" +
            "          \"0\" : [\n" +
            "            \"allocationId\"\n" +
            "          ]\n" +
            "        },\n" +
            "        \"rollover_info\" : {\n" +
            "          \"rolloveAlias\" : {\n" +
            "            \"met_conditions\" : { },\n" +
            "            \"time\" : 1\n" +
            "          }\n" +
            "        }\n" +
            "      }\n" +
            "    },\n" +
            "    \"index-graveyard\" : {\n" +
            "      \"tombstones\" : [ ]\n" +
            "    }\n" +
            "  }\n" +
            "}", Strings.toString(builder));
    }

    public void testToXContentAPI_FlatSettingFalse_ReduceMappingTrue() throws IOException {
        Map<String, String> mapParams = new HashMap<String,String>(){{
            put(MetaData.CONTEXT_MODE_PARAM, CONTEXT_MODE_API);
            put("flat_settings", "false");
            put("reduce_mappings", "true");
        }};

        final MetaData metaData = buildMetaData();

        XContentBuilder builder = JsonXContent.contentBuilder().prettyPrint();
        builder.startObject();
        metaData.toXContent(builder, new ToXContent.MapParams(mapParams));
        builder.endObject();

        assertEquals("{\n" +
            "  \"metadata\" : {\n" +
            "    \"cluster_uuid\" : \"clusterUUID\",\n" +
            "    \"cluster_uuid_committed\" : false,\n" +
            "    \"cluster_coordination\" : {\n" +
            "      \"term\" : 1,\n" +
            "      \"last_committed_config\" : [\n" +
            "        \"commitedConfigurationNodeId\"\n" +
            "      ],\n" +
            "      \"last_accepted_config\" : [\n" +
            "        \"acceptedConfigurationNodeId\"\n" +
            "      ],\n" +
            "      \"voting_config_exclusions\" : [\n" +
            "        {\n" +
            "          \"node_id\" : \"exlucdedNodeId\",\n" +
            "          \"node_name\" : \"excludedNodeName\"\n" +
            "        }\n" +
            "      ]\n" +
            "    },\n" +
            "    \"templates\" : {\n" +
            "      \"template\" : {\n" +
            "        \"order\" : 0,\n" +
            "        \"index_patterns\" : [\n" +
            "          \"pattern1\",\n" +
            "          \"pattern2\"\n" +
            "        ],\n" +
            "        \"settings\" : {\n" +
            "          \"index\" : {\n" +
            "            \"version\" : {\n" +
            "              \"created\" : \"" + Version.CURRENT.id + "\"\n" +
            "            }\n" +
            "          }\n" +
            "        },\n" +
            "        \"mappings\" : {\n" +
            "          \"type\" : {\n" +
            "            \"key1\" : { }\n" +
            "          }\n" +
            "        },\n" +
            "        \"aliases\" : { }\n" +
            "      }\n" +
            "    },\n" +
            "    \"indices\" : {\n" +
            "      \"index\" : {\n" +
            "        \"version\" : 2,\n" +
            "        \"mapping_version\" : 1,\n" +
            "        \"settings_version\" : 1,\n" +
            "        \"aliases_version\" : 1,\n" +
            "        \"routing_num_shards\" : 1,\n" +
            "        \"state\" : \"open\",\n" +
            "        \"settings\" : {\n" +
            "          \"index\" : {\n" +
            "            \"number_of_shards\" : \"1\",\n" +
            "            \"number_of_replicas\" : \"2\",\n" +
            "            \"version\" : {\n" +
            "              \"created\" : \"" + Version.CURRENT.id + "\"\n" +
            "            }\n" +
            "          }\n" +
            "        },\n" +
            "        \"mappings\" : {\n" +
            "          \"type\" : {\n" +
            "            \"type1\" : {\n" +
            "              \"key\" : \"value\"\n" +
            "            }\n" +
            "          }\n" +
            "        },\n" +
            "        \"aliases\" : [\n" +
            "          \"alias\"\n" +
            "        ],\n" +
            "        \"primary_terms\" : {\n" +
            "          \"0\" : 1\n" +
            "        },\n" +
            "        \"in_sync_allocations\" : {\n" +
            "          \"0\" : [\n" +
            "            \"allocationId\"\n" +
            "          ]\n" +
            "        },\n" +
            "        \"rollover_info\" : {\n" +
            "          \"rolloveAlias\" : {\n" +
            "            \"met_conditions\" : { },\n" +
            "            \"time\" : 1\n" +
            "          }\n" +
            "        }\n" +
            "      }\n" +
            "    },\n" +
            "    \"index-graveyard\" : {\n" +
            "      \"tombstones\" : [ ]\n" +
            "    }\n" +
            "  }\n" +
            "}", Strings.toString(builder));
    }


    private MetaData buildMetaData() throws IOException {
        return MetaData.builder()
            .clusterUUID("clusterUUID")
            .coordinationMetaData(CoordinationMetaData.builder()
                .term(1)
                .lastCommittedConfiguration(new CoordinationMetaData.VotingConfiguration(new HashSet<String>(){{
                    add("commitedConfigurationNodeId");
                }}))
                .lastAcceptedConfiguration(new CoordinationMetaData.VotingConfiguration(new HashSet<String>(){{
                    add("acceptedConfigurationNodeId");
                }}))
                .addVotingConfigExclusion(new CoordinationMetaData.VotingConfigExclusion("exlucdedNodeId", "excludedNodeName"))
                .build())
            .persistentSettings(Settings.builder()
                .put(SETTING_VERSION_CREATED, Version.CURRENT.id).build())
            .transientSettings(Settings.builder()
                .put(SETTING_VERSION_CREATED, Version.CURRENT.id).build())
            .put(IndexMetaData.builder("index")
                .state(IndexMetaData.State.OPEN)
                .settings(Settings.builder()
                    .put(SETTING_VERSION_CREATED, Version.CURRENT.id))
                .putMapping(new MappingMetaData("type",
                    new HashMap<String,Object>(){{
                        put("type1", new HashMap<String, Object>(){{
                            put("key", "value");
                        }});
                    }}))
                .putAlias(AliasMetaData.builder("alias")
                    .indexRouting("indexRouting")
                    .build())
                .numberOfShards(1)
                .primaryTerm(0, 1L)
                .putInSyncAllocationIds(0, new HashSet<String>(){{
                    add("allocationId");
                }})
                .numberOfReplicas(2)
                .putRolloverInfo(new RolloverInfo("rolloveAlias", new ArrayList<>(), 1L)))
            .put(IndexTemplateMetaData.builder("template")
                .patterns(Arrays.asList("pattern1", "pattern2"))
                .order(0)
                .settings(Settings.builder().put(SETTING_VERSION_CREATED, Version.CURRENT.id))
                .putMapping("type", "{ \"key1\": {} }")
                .build())
            .build();
    }

    public static class CustomMetaData extends TestCustomMetaData {
        public static final String TYPE = "custom_md";

        CustomMetaData(String data) {
            super(data);
        }

        @Override
        public String getWriteableName() {
            return TYPE;
        }

        @Override
        public Version getMinimalSupportedVersion() {
            return Version.CURRENT;
        }

        @Override
        public EnumSet<MetaData.XContentContext> context() {
            return EnumSet.of(MetaData.XContentContext.GATEWAY, MetaData.XContentContext.SNAPSHOT);
        }
    }
}
