/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.ingest.geoip;

import com.maxmind.db.NoCache;
import com.maxmind.db.NodeCache;
import com.maxmind.db.Reader;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.AbstractResponse;
import org.elasticsearch.Version;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.Booleans;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.SuppressForbidden;
import org.elasticsearch.common.cache.Cache;
import org.elasticsearch.common.cache.CacheBuilder;
import org.elasticsearch.common.io.PathUtils;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsModule;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.core.internal.io.IOUtils;
import org.elasticsearch.indices.SystemIndexDescriptor;
import org.elasticsearch.ingest.Processor;
import org.elasticsearch.persistent.PersistentTaskParams;
import org.elasticsearch.persistent.PersistentTaskState;
import org.elasticsearch.persistent.PersistentTasksExecutor;
import org.elasticsearch.plugins.IngestPlugin;
import org.elasticsearch.plugins.PersistentTaskPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.SystemIndexPlugin;
import org.elasticsearch.threadpool.ThreadPool;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.mapper.MapperService.SINGLE_MAPPING_NAME;
import static org.elasticsearch.ingest.geoip.GeoIpDownloader.DATABASES_INDEX;
import static org.elasticsearch.ingest.geoip.GeoIpDownloader.GEOIP_DOWNLOADER;

public class IngestGeoIpPlugin extends Plugin implements IngestPlugin, SystemIndexPlugin, Closeable, PersistentTaskPlugin {
    public static final Setting<Long> CACHE_SIZE =
        Setting.longSetting("ingest.geoip.cache_size", 1000, 0, Setting.Property.NodeScope);

    static String[] DEFAULT_DATABASE_FILENAMES = new String[]{"GeoLite2-ASN.mmdb", "GeoLite2-City.mmdb", "GeoLite2-Country.mmdb"};

    private Map<String, DatabaseReaderLazyLoader> databaseReaders;

    @Override
    public List<Setting<?>> getSettings() {
        List<Setting<?>> settings = Arrays.asList(CACHE_SIZE,
            GeoIpDownloader.ENDPOINT_SETTING,
            GeoIpDownloader.POLL_INTERVAL_SETTING);
        if (GeoIpDownloader.GEOIP_V2_FEATURE_FLAG_ENABLED) {
            settings.add(GeoIpDownloader.ENABLED_SETTING);
        }
        return settings;
    }

    @Override
    public Map<String, Processor.Factory> getProcessors(Processor.Parameters parameters) {
        if (databaseReaders != null) {
            throw new IllegalStateException("getProcessors called twice for geoip plugin!!");
        }
        final Path geoIpDirectory = getGeoIpDirectory(parameters);
        final Path geoIpConfigDirectory = parameters.env.configFile().resolve("ingest-geoip");
        long cacheSize = CACHE_SIZE.get(parameters.env.settings());
        try {
            databaseReaders = loadDatabaseReaders(geoIpDirectory, geoIpConfigDirectory);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Collections.singletonMap(GeoIpProcessor.TYPE, new GeoIpProcessor.Factory(databaseReaders, new GeoIpCache(cacheSize)));
    }

    /*
     * In GeoIpProcessorNonIngestNodeTests, ingest-geoip is loaded on the classpath. This means that the plugin is never unbundled into a
     * directory where the database files would live. Therefore, we have to copy these database files ourselves. To do this, we need the
     * ability to specify where those database files would go. We do this by adding a plugin that registers ingest.geoip.database_path as
     * an actual setting. Otherwise, in production code, this setting is not registered and the database path is not configurable.
     */
    @SuppressForbidden(reason = "PathUtils#get")
    private Path getGeoIpDirectory(Processor.Parameters parameters) {
        final Path geoIpDirectory;
        if (parameters.env.settings().get("ingest.geoip.database_path") == null) {
            geoIpDirectory = parameters.env.modulesFile().resolve("ingest-geoip");
        } else {
            geoIpDirectory = PathUtils.get(parameters.env.settings().get("ingest.geoip.database_path"));
        }
        return geoIpDirectory;
    }

    static Map<String, DatabaseReaderLazyLoader> loadDatabaseReaders(Path geoIpDirectory, Path geoIpConfigDirectory) throws IOException {
        assertDatabaseExistence(geoIpDirectory, true);
        assertDatabaseExistence(geoIpConfigDirectory, false);
        final boolean loadDatabaseOnHeap = Booleans.parseBoolean(System.getProperty("es.geoip.load_db_on_heap", "false"));
        final Map<String, DatabaseReaderLazyLoader> databaseReaders = new HashMap<>();

        // load the default databases
        for (final String databaseFilename : DEFAULT_DATABASE_FILENAMES) {
            final Path databasePath = geoIpDirectory.resolve(databaseFilename);
            final DatabaseReaderLazyLoader loader = createLoader(databasePath, loadDatabaseOnHeap);
            databaseReaders.put(databaseFilename, loader);
        }

        // load any custom databases
        if (Files.exists(geoIpConfigDirectory)) {
            try (Stream<Path> databaseFiles = Files.list(geoIpConfigDirectory)) {
                PathMatcher pathMatcher = geoIpConfigDirectory.getFileSystem().getPathMatcher("glob:**.mmdb");
                // Use iterator instead of forEach otherwise IOException needs to be caught twice...
                Iterator<Path> iterator = databaseFiles.iterator();
                while (iterator.hasNext()) {
                    Path databasePath = iterator.next();
                    if (Files.isRegularFile(databasePath) && pathMatcher.matches(databasePath)) {
                        String databaseFileName = databasePath.getFileName().toString();
                        final DatabaseReaderLazyLoader loader = createLoader(databasePath, loadDatabaseOnHeap);
                        databaseReaders.put(databaseFileName, loader);
                    }
                }
            }
        }
        return Collections.unmodifiableMap(databaseReaders);
    }

    private static DatabaseReaderLazyLoader createLoader(Path databasePath, boolean loadDatabaseOnHeap) {
        return new DatabaseReaderLazyLoader(
                databasePath,
                () -> {
                    DatabaseReader.Builder builder = createDatabaseBuilder(databasePath).withCache(NoCache.getInstance());
                    if (loadDatabaseOnHeap) {
                        builder.fileMode(Reader.FileMode.MEMORY);
                    } else {
                        builder.fileMode(Reader.FileMode.MEMORY_MAPPED);
                    }
                    return builder.build();
                });
    }

    private static void assertDatabaseExistence(final Path path, final boolean exists) throws IOException {
        for (final String database : DEFAULT_DATABASE_FILENAMES) {
            if (Files.exists(path.resolve(database)) != exists) {
                final String message = "expected database [" + database + "] to " + (exists ? "" : "not ") + "exist in [" + path + "]";
                throw new IOException(message);
            }
        }
    }

    @SuppressForbidden(reason = "Maxmind API requires java.io.File")
    private static DatabaseReader.Builder createDatabaseBuilder(Path databasePath) {
        return new DatabaseReader.Builder(databasePath.toFile());
    }

    @Override
    public void close() throws IOException {
        if (databaseReaders != null) {
            IOUtils.close(databaseReaders.values());
        }
    }

    /**
     * The in-memory cache for the geoip data. There should only be 1 instance of this class..
     * This cache differs from the maxmind's {@link NodeCache} such that this cache stores the deserialized Json objects to avoid the
     * cost of deserialization for each lookup (cached or not). This comes at slight expense of higher memory usage, but significant
     * reduction of CPU usage.
     */
    static class GeoIpCache {
        private final Cache<CacheKey<?>, AbstractResponse> cache;

        //package private for testing
        GeoIpCache(long maxSize) {
            if (maxSize < 0) {
                throw new IllegalArgumentException("geoip max cache size must be 0 or greater");
            }
            this.cache = CacheBuilder.<CacheKey<?>, AbstractResponse>builder().setMaximumWeight(maxSize).build();
        }

        <T extends AbstractResponse> T putIfAbsent(InetAddress ip, Class<T> responseType,
                                                   Function<InetAddress, AbstractResponse> retrieveFunction) {

            //can't use cache.computeIfAbsent due to the elevated permissions for the jackson (run via the cache loader)
            CacheKey<T> cacheKey = new CacheKey<>(ip, responseType);
            //intentionally non-locking for simplicity...it's OK if we re-put the same key/value in the cache during a race condition.
            AbstractResponse response = cache.get(cacheKey);
            if (response == null) {
                response = retrieveFunction.apply(ip);
                cache.put(cacheKey, response);
            }
            return responseType.cast(response);
        }

        //only useful for testing
        <T extends AbstractResponse> T get(InetAddress ip, Class<T> responseType) {
            CacheKey<T> cacheKey = new CacheKey<>(ip, responseType);
            return responseType.cast(cache.get(cacheKey));
        }

         /**
         * The key to use for the cache. Since this cache can span multiple geoip processors that all use different databases, the response
         * type is needed to be included in the cache key. For example, if we only used the IP address as the key the City and ASN the same
         * IP may be in both with different values and we need to cache both. The response type scopes the IP to the correct database
         * provides a means to safely cast the return objects.
         * @param <T> The AbstractResponse type used to scope the key and cast the result.
         */
        private static class CacheKey<T extends AbstractResponse> {

            private final InetAddress ip;
            private final Class<T> responseType;

            private CacheKey(InetAddress ip, Class<T> responseType) {
                this.ip = ip;
                this.responseType = responseType;
            }

            //generated
            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                CacheKey<?> cacheKey = (CacheKey<?>) o;
                return Objects.equals(ip, cacheKey.ip) &&
                    Objects.equals(responseType, cacheKey.responseType);
            }

            //generated
            @Override
            public int hashCode() {
                return Objects.hash(ip, responseType);
            }
        }
    }

    @Override
    public List<PersistentTasksExecutor<?>> getPersistentTasksExecutor(ClusterService clusterService, ThreadPool threadPool,
                                                                       Client client, SettingsModule settingsModule,
                                                                       IndexNameExpressionResolver expressionResolver) {
        return List.of(new GeoIpDownloader(client, new HttpClient(), clusterService, threadPool, settingsModule.getSettings()));
    }

    @Override
    public List<NamedXContentRegistry.Entry> getNamedXContent() {
        return List.of(new NamedXContentRegistry.Entry(PersistentTaskParams.class, new ParseField(GEOIP_DOWNLOADER),
                GeoIpTaskParams::fromXContent),
            new NamedXContentRegistry.Entry(PersistentTaskState.class, new ParseField(GEOIP_DOWNLOADER), GeoIpTaskState::fromXContent));
    }

    @Override
    public List<NamedWriteableRegistry.Entry> getNamedWriteables() {
        return List.of(new NamedWriteableRegistry.Entry(PersistentTaskState.class, GEOIP_DOWNLOADER, GeoIpTaskState::new),
            new NamedWriteableRegistry.Entry(PersistentTaskParams.class, GEOIP_DOWNLOADER, GeoIpTaskParams::new));
    }

    @Override
    public Collection<SystemIndexDescriptor> getSystemIndexDescriptors(Settings settings) {
        SystemIndexDescriptor geoipDatabasesIndex = SystemIndexDescriptor.builder()
            .setIndexPattern(DATABASES_INDEX)
            .setDescription("GeoIP databases")
            .setMappings(mappings())
            .setSettings(Settings.builder()
                .put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, 1)
                .put(IndexMetadata.SETTING_AUTO_EXPAND_REPLICAS, "0-1")
                .build())
            .setOrigin("geoip")
            .setVersionMetaKey("version")
            .setPrimaryIndex(DATABASES_INDEX)
            .build();
        return Collections.singleton(geoipDatabasesIndex);
    }

    private static XContentBuilder mappings() {
        try {
            return jsonBuilder()
                .startObject()
                    .startObject(SINGLE_MAPPING_NAME)
                        .startObject("_meta")
                            .field("version", Version.CURRENT)
                        .endObject()
                        .field("dynamic", "strict")
                        .startObject("properties")
                            .startObject("name")
                                .field("type", "keyword")
                            .endObject()
                            .startObject("chunk")
                                .field("type", "integer")
                            .endObject()
                            .startObject("data")
                                .field("type", "binary")
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build mappings for " + DATABASES_INDEX, e);
        }
    }
}
