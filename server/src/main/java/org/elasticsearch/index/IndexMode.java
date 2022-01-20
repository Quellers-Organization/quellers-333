/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index;

import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.metadata.MetadataCreateDataStreamService;
import org.elasticsearch.common.compress.CompressedXContent;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.index.mapper.DataStreamTimestampFieldMapper;
import org.elasticsearch.index.mapper.DateFieldMapper;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.mapper.MappingLookup;
import org.elasticsearch.index.mapper.MetadataFieldMapper;
import org.elasticsearch.index.mapper.RoutingFieldMapper;
import org.elasticsearch.index.mapper.TimeSeriesIdFieldMapper;

import java.io.IOException;
import java.util.List;

/**
 * "Mode" that controls which behaviors and settings an index supports.
 * <p>
 * For the most part this class concentrates on validating settings and
 * mappings. Most different behavior is controlled by forcing settings
 * to be set or not set and by enabling extra fields in the mapping.
 */
public enum IndexMode {
    STANDARD {
        @Override
        void validateWithOtherSettings(Settings settings) {
            settingRequiresTimeSeries(settings, IndexMetadata.INDEX_ROUTING_PATH);
            settingRequiresTimeSeries(settings, IndexSettings.TIME_SERIES_START_TIME);
            settingRequiresTimeSeries(settings, IndexSettings.TIME_SERIES_END_TIME);
        }

        private void settingRequiresTimeSeries(Settings settings, Setting<?> setting) {
            if (settings.hasValue(setting.getKey())) {
                throw new IllegalArgumentException("[" + setting.getKey() + "] requires [" + IndexSettings.MODE.getKey() + "=time_series]");
            }
        }

        @Override
        public void validateMapping(MappingLookup lookup) {};

        @Override
        public void validateAlias(@Nullable String indexRouting, @Nullable String searchRouting) {}

        @Override
        public void validateTimestampFieldMapping(boolean isDataStream, MappingLookup mappingLookup) throws IOException {
            if (isDataStream) {
                MetadataCreateDataStreamService.validateTimestampFieldMapping(mappingLookup);
            }
        }

        @Override
        public CompressedXContent getDefaultMapping() {
            return null;
        }

        @Override
        public MetadataFieldMapper buildTimeSeriesIdFieldMapper() {
            // non time-series indices must not have a TimeSeriesIdFieldMapper
            return null;
        }
    },
    TIME_SERIES {
        @Override
        void validateWithOtherSettings(Settings settings) {
            if (IndexMetadata.INDEX_ROUTING_PARTITION_SIZE_SETTING.get(settings) != Integer.valueOf(1)) {
                throw new IllegalArgumentException(error(IndexMetadata.INDEX_ROUTING_PARTITION_SIZE_SETTING));
            }
            for (Setting<?> unsupported : TIME_SERIES_UNSUPPORTED) {
                if (settings.hasValue(unsupported.getKey())) {
                    throw new IllegalArgumentException(error(unsupported));
                }
            }
            settingRequiresTimeSeries(settings, IndexMetadata.INDEX_ROUTING_PATH);
            settingRequiresTimeSeries(settings, IndexSettings.TIME_SERIES_START_TIME);
            settingRequiresTimeSeries(settings, IndexSettings.TIME_SERIES_END_TIME);

            if (IndexMetadata.INDEX_ROUTING_PATH.get(settings).isEmpty()) {
                throw new IllegalArgumentException(tsdbMode() + " " + IndexMetadata.INDEX_ROUTING_PATH.getKey() + " can not be empty");
            }
        }

        private void settingRequiresTimeSeries(Settings settings, Setting<?> setting) {
            if (false == settings.hasValue(setting.getKey())) {
                throw new IllegalArgumentException("[" + IndexSettings.MODE.getKey() + "=time_series] requires [" + setting.getKey() + "]");
            }
        }

        private String error(Setting<?> unsupported) {
            return tsdbMode() + " is incompatible with [" + unsupported.getKey() + "]";
        }

        @Override
        public void validateMapping(MappingLookup lookup) {
            if (((RoutingFieldMapper) lookup.getMapper(RoutingFieldMapper.NAME)).required()) {
                throw new IllegalArgumentException(routingRequiredBad());
            }
        }

        @Override
        public void validateAlias(@Nullable String indexRouting, @Nullable String searchRouting) {
            if (indexRouting != null || searchRouting != null) {
                throw new IllegalArgumentException(routingRequiredBad());
            }
        }

        @Override
        public void validateTimestampFieldMapping(boolean isDataStream, MappingLookup mappingLookup) throws IOException {
            MetadataCreateDataStreamService.validateTimestampFieldMapping(mappingLookup);
        }

        @Override
        public CompressedXContent getDefaultMapping() {
            return DEFAULT_TIME_SERIES_TIMESTAMP_MAPPING;
        }

        private String routingRequiredBad() {
            return "routing is forbidden on CRUD operations that target indices in " + tsdbMode();
        }

        private String tsdbMode() {
            return "[" + IndexSettings.MODE.getKey() + "=time_series]";
        }

        @Override
        public MetadataFieldMapper buildTimeSeriesIdFieldMapper() {
            return TimeSeriesIdFieldMapper.INSTANCE;
        }
    };

    public static final CompressedXContent DEFAULT_TIME_SERIES_TIMESTAMP_MAPPING;

    static {
        try {
            DEFAULT_TIME_SERIES_TIMESTAMP_MAPPING = new CompressedXContent(
                ((builder, params) -> builder.startObject(MapperService.SINGLE_MAPPING_NAME)
                    .startObject(DataStreamTimestampFieldMapper.NAME)
                    .field("enabled", true)
                    .endObject()
                    .startObject("properties")
                    .startObject(DataStreamTimestampFieldMapper.DEFAULT_PATH)
                    .field("type", DateFieldMapper.CONTENT_TYPE)
                    .endObject()
                    .endObject()
                    .endObject())
            );
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static final List<Setting<?>> TIME_SERIES_UNSUPPORTED = List.of(
        IndexSortConfig.INDEX_SORT_FIELD_SETTING,
        IndexSortConfig.INDEX_SORT_ORDER_SETTING,
        IndexSortConfig.INDEX_SORT_MODE_SETTING,
        IndexSortConfig.INDEX_SORT_MISSING_SETTING
    );

    abstract void validateWithOtherSettings(Settings settings);

    /**
     * Validate the mapping for this index.
     */
    public abstract void validateMapping(MappingLookup lookup);

    /**
     * Validate aliases targeting this index.
     */
    public abstract void validateAlias(@Nullable String indexRouting, @Nullable String searchRouting);

    /**
     * validate timestamp mapping for this index.
     */
    public abstract void validateTimestampFieldMapping(boolean isDataStream, MappingLookup mappingLookup) throws IOException;

    /**
     * Get default mapping for this index or {@code null} if there is none.
     */
    @Nullable
    public abstract CompressedXContent getDefaultMapping();

    /**
     * Return an instance of the {@link TimeSeriesIdFieldMapper} that generates
     * the _tsid field. The field mapper will be added to the list of the metadata
     * field mappers for the index.
     */
    public abstract MetadataFieldMapper buildTimeSeriesIdFieldMapper();
}
