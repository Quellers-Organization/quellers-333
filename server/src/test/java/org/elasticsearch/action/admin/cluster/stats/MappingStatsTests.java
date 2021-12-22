/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.action.admin.cluster.stats;

import org.elasticsearch.Version;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.Writeable.Reader;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.script.Script;
import org.elasticsearch.tasks.TaskCancelledException;
import org.elasticsearch.test.AbstractWireSerializingTestCase;
import org.elasticsearch.test.VersionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class MappingStatsTests extends AbstractWireSerializingTestCase<MappingStats> {

    public void testToXContent() {
        Settings settings = Settings.builder()
            .put("index.number_of_replicas", 0)
            .put("index.number_of_shards", 1)
            .put("index.version.created", Version.CURRENT)
            .build();
        Script script1 = new Script("doc['field'] + doc.field + params._source.field");
        Script script2 = new Script("doc['field']");
        Script script3 = new Script("params._source.field + params._source.field \n + params._source.field");
        Script script4 = new Script("params._source.field");
        String mapping = """
            {
                "runtime": {
                    "keyword1": {
                        "type": "keyword",
                        "script": %s
                    },
                    "keyword2": {
                        "type": "keyword"
                    },
                    "object.keyword3": {
                        "type": "keyword",
                        "script": %s
                    },
                    "long": {
                        "type": "long",
                        "script": %s
                    },
                    "long2": {
                        "type": "long",
                        "script": %s
                    }
                },
                "properties": {
                    "object": {
                        "type": "object",
                        "properties": {
                            "keyword3": {
                                "type": "keyword"
                            }
                        }
                    },
                    "long3": {
                        "type": "long",
                        "script": %s
                    },
                    "long4": {
                        "type": "long",
                        "script": %s
                    },
                    "keyword3": {
                        "type": "keyword",
                        "script": %s
                    }
                }
            }""".formatted(
            Strings.toString(script1),
            Strings.toString(script2),
            Strings.toString(script3),
            Strings.toString(script4),
            Strings.toString(script3),
            Strings.toString(script4),
            Strings.toString(script1)
        );
        IndexMetadata meta = IndexMetadata.builder("index").settings(settings).putMapping(mapping).build();
        IndexMetadata meta2 = IndexMetadata.builder("index2").settings(settings).putMapping(mapping).build();
        Metadata metadata = Metadata.builder().put(meta, false).put(meta2, false).build();
        MappingStats mappingStats = MappingStats.of(metadata, () -> {});
        assertEquals("""
            {
              "mappings" : {
                "field_types" : [
                  {
                    "name" : "keyword",
                    "count" : 4,
                    "index_count" : 2,
                    "script_count" : 2,
                    "lang" : [
                      "painless"
                    ],
                    "lines_max" : 1,
                    "lines_total" : 2,
                    "chars_max" : 47,
                    "chars_total" : 94,
                    "source_max" : 1,
                    "source_total" : 2,
                    "doc_max" : 2,
                    "doc_total" : 4
                  },
                  {
                    "name" : "long",
                    "count" : 4,
                    "index_count" : 2,
                    "script_count" : 4,
                    "lang" : [
                      "painless"
                    ],
                    "lines_max" : 2,
                    "lines_total" : 6,
                    "chars_max" : 68,
                    "chars_total" : 176,
                    "source_max" : 3,
                    "source_total" : 8,
                    "doc_max" : 0,
                    "doc_total" : 0
                  },
                  {
                    "name" : "object",
                    "count" : 2,
                    "index_count" : 2,
                    "script_count" : 0
                  }
                ],
                "runtime_field_types" : [
                  {
                    "name" : "keyword",
                    "count" : 6,
                    "index_count" : 2,
                    "scriptless_count" : 2,
                    "shadowed_count" : 2,
                    "lang" : [
                      "painless"
                    ],
                    "lines_max" : 1,
                    "lines_total" : 4,
                    "chars_max" : 47,
                    "chars_total" : 118,
                    "source_max" : 1,
                    "source_total" : 2,
                    "doc_max" : 2,
                    "doc_total" : 6
                  },
                  {
                    "name" : "long",
                    "count" : 4,
                    "index_count" : 2,
                    "scriptless_count" : 0,
                    "shadowed_count" : 0,
                    "lang" : [
                      "painless"
                    ],
                    "lines_max" : 2,
                    "lines_total" : 6,
                    "chars_max" : 68,
                    "chars_total" : 176,
                    "source_max" : 3,
                    "source_total" : 8,
                    "doc_max" : 0,
                    "doc_total" : 0
                  }
                ]
              }
            }""", Strings.toString(mappingStats, true, true));
    }

    @Override
    protected Reader<MappingStats> instanceReader() {
        return MappingStats::new;
    }

    @Override
    protected MappingStats createTestInstance() {
        Collection<FieldStats> stats = new ArrayList<>();
        Collection<RuntimeFieldStats> runtimeFieldStats = new ArrayList<>();
        if (randomBoolean()) {
            stats.add(randomFieldStats("keyword"));
        }
        if (randomBoolean()) {
            stats.add(randomFieldStats("double"));
        }
        if (randomBoolean()) {
            runtimeFieldStats.add(randomRuntimeFieldStats("keyword"));
        }
        if (randomBoolean()) {
            runtimeFieldStats.add(randomRuntimeFieldStats("long"));
        }
        return new MappingStats(stats, runtimeFieldStats);
    }

    private static FieldStats randomFieldStats(String type) {
        FieldStats stats = new FieldStats(type);
        stats.count = randomIntBetween(0, Integer.MAX_VALUE);
        stats.indexCount = randomIntBetween(0, Integer.MAX_VALUE);
        if (randomBoolean()) {
            stats.scriptCount = randomIntBetween(0, Integer.MAX_VALUE);
            stats.scriptLangs.add(randomAlphaOfLengthBetween(3, 10));
            stats.fieldScriptStats.update(
                randomIntBetween(1, 100),
                randomLongBetween(100, 1000),
                randomIntBetween(1, 10),
                randomIntBetween(1, 10)
            );
        }
        return stats;
    }

    private static RuntimeFieldStats randomRuntimeFieldStats(String type) {
        RuntimeFieldStats stats = new RuntimeFieldStats(type);
        stats.count = randomIntBetween(0, Integer.MAX_VALUE);
        stats.indexCount = randomIntBetween(0, Integer.MAX_VALUE);
        stats.scriptLessCount = randomIntBetween(0, Integer.MAX_VALUE);
        stats.shadowedCount = randomIntBetween(0, Integer.MAX_VALUE);
        stats.scriptLangs.add(randomAlphaOfLengthBetween(3, 10));
        if (randomBoolean()) {
            stats.fieldScriptStats.update(
                randomIntBetween(1, 100),
                randomLongBetween(100, 1000),
                randomIntBetween(1, 10),
                randomIntBetween(1, 10)
            );
        }
        return stats;
    }

    @Override
    protected MappingStats mutateInstance(MappingStats instance) throws IOException {
        List<FieldStats> fieldTypes = new ArrayList<>(instance.getFieldTypeStats());
        List<RuntimeFieldStats> runtimeFieldTypes = new ArrayList<>(instance.getRuntimeFieldStats());
        if (randomBoolean()) {
            boolean remove = fieldTypes.size() > 0 && randomBoolean();
            if (remove) {
                fieldTypes.remove(randomInt(fieldTypes.size() - 1));
            }
            if (remove == false || randomBoolean()) {
                FieldStats s = new FieldStats("float");
                s.count = 13;
                s.indexCount = 2;
                fieldTypes.add(s);
            }
        } else {
            boolean remove = runtimeFieldTypes.size() > 0 && randomBoolean();
            if (remove) {
                runtimeFieldTypes.remove(randomInt(runtimeFieldTypes.size() - 1));
            }
            if (remove == false || randomBoolean()) {
                runtimeFieldTypes.add(randomRuntimeFieldStats("double"));
            }
        }

        return new MappingStats(fieldTypes, runtimeFieldTypes);
    }

    public void testAccountsRegularIndices() {
        String mapping = """
            {"properties":{"bar":{"type":"long"}}}""";
        Settings settings = Settings.builder()
            .put(IndexMetadata.SETTING_VERSION_CREATED, Version.CURRENT)
            .put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, 4)
            .put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 1)
            .build();
        IndexMetadata.Builder indexMetadata = new IndexMetadata.Builder("foo").settings(settings).putMapping(mapping);
        Metadata metadata = new Metadata.Builder().put(indexMetadata).build();
        MappingStats mappingStats = MappingStats.of(metadata, () -> {});
        FieldStats expectedStats = new FieldStats("long");
        expectedStats.count = 1;
        expectedStats.indexCount = 1;
        assertEquals(Collections.singleton(expectedStats), mappingStats.getFieldTypeStats());
    }

    public void testIgnoreSystemIndices() {
        String mapping = """
            {"properties":{"bar":{"type":"long"}}}""";
        Settings settings = Settings.builder()
            .put(IndexMetadata.SETTING_VERSION_CREATED, Version.CURRENT)
            .put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, 4)
            .put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 1)
            .build();
        IndexMetadata.Builder indexMetadata = new IndexMetadata.Builder("foo").settings(settings).putMapping(mapping).system(true);
        Metadata metadata = new Metadata.Builder().put(indexMetadata).build();
        MappingStats mappingStats = MappingStats.of(metadata, () -> {});
        assertEquals(Collections.emptySet(), mappingStats.getFieldTypeStats());
    }

    public void testChecksForCancellation() {
        Settings settings = Settings.builder()
            .put(IndexMetadata.SETTING_VERSION_CREATED, Version.CURRENT)
            .put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, 4)
            .put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 1)
            .build();
        IndexMetadata.Builder indexMetadata = new IndexMetadata.Builder("foo").settings(settings);
        Metadata metadata = new Metadata.Builder().put(indexMetadata).build();
        expectThrows(
            TaskCancelledException.class,
            () -> MappingStats.of(metadata, () -> { throw new TaskCancelledException("task cancelled"); })
        );
    }

    public void testWriteTo() throws IOException {
        MappingStats instance = createTestInstance();
        BytesStreamOutput out = new BytesStreamOutput();
        Version version = VersionUtils.randomCompatibleVersion(random(), Version.CURRENT);
        out.setVersion(version);
        instance.writeTo(out);
        StreamInput in = StreamInput.wrap(out.bytes().toBytesRef().bytes);
        in.setVersion(version);
        MappingStats deserialized = new MappingStats(in);
        assertEquals(instance.getFieldTypeStats(), deserialized.getFieldTypeStats());
        assertEquals(instance.getRuntimeFieldStats(), deserialized.getRuntimeFieldStats());
    }
}
