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

package org.elasticsearch.script;

import com.google.common.base.Charsets;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ElasticsearchIllegalArgumentException;

import org.elasticsearch.ElasticsearchIllegalStateException;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.collect.HppcMaps;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.Streams;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.ConcurrentCollections;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.fielddata.IndexFieldDataService;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.search.lookup.SearchLookup;
import org.elasticsearch.watcher.FileChangesListener;
import org.elasticsearch.watcher.FileWatcher;
import org.elasticsearch.watcher.ResourceWatcherService;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class ScriptService extends AbstractComponent {

    public static final String DEFAULT_SCRIPTING_LANGUAGE_SETTING = "script.default_lang";
    public static final String DISABLE_DYNAMIC_SCRIPTING_SETTING = "script.disable_dynamic";
    public static final String DISABLE_DYNAMIC_SCRIPTING_DEFAULT = "sandbox";
    public static final String SCRIPT_INDEX = ".scripts";

    private final String defaultLang;

    private final ImmutableMap<String, ScriptEngineService> scriptEngines;

    private final ConcurrentMap<String, CompiledScript> staticCache = ConcurrentCollections.newConcurrentMap();

    private final Cache<CacheKey, CompiledScript> cache;
    private final File scriptsDirectory;

    private final DynamicScriptDisabling dynamicScriptingDisabled;

    private Client client = null;

    /**
     * Enum defining the different dynamic settings for scripting, either
     * ONLY_DISK_ALLOWED (scripts must be placed on disk), EVERYTHING_ALLOWED
     * (all dynamic scripting is enabled), or SANDBOXED_ONLY (only sandboxed
     * scripting languages are allowed)
     */
    enum DynamicScriptDisabling {
        EVERYTHING_ALLOWED,
        ONLY_DISK_ALLOWED,
        SANDBOXED_ONLY;

        public static final DynamicScriptDisabling parse(String s) {
            switch (s.toLowerCase(Locale.ROOT)) {
                // true for "disable_dynamic" means only on-disk scripts are enabled
                case "true":
                case "all":
                    return ONLY_DISK_ALLOWED;
                // false for "disable_dynamic" means all scripts are enabled
                case "false":
                case "none":
                    return EVERYTHING_ALLOWED;
                // only sandboxed scripting is enabled
                case "sandbox":
                case "sandboxed":
                    return SANDBOXED_ONLY;
                default:
                    throw new ElasticsearchIllegalArgumentException("Unrecognized script allowance setting: [" + s + "]");
            }
        }
    }

    public static final ParseField SCRIPT_LANG = new ParseField("lang","script_lang");

    public static final ParseField VALUE_SCRIPT_FILE = new ParseField("value_script_file");
    public static final ParseField VALUE_SCRIPT_ID = new ParseField("value_script_id");
    public static final ParseField VALUE_SCRIPT_INLINE = new ParseField("value_script");

    public static final ParseField KEY_SCRIPT_FILE = new ParseField("key_script_file");
    public static final ParseField KEY_SCRIPT_ID = new ParseField("key_script_id");
    public static final ParseField KEY_SCRIPT_INLINE = new ParseField("key_script");

    public static final ParseField SCRIPT_FILE = new ParseField("script_file","file");
    public static final ParseField SCRIPT_ID = new ParseField("script_id", "id");
    public static final ParseField SCRIPT_INLINE = new ParseField("script","scriptField");


    public static enum ScriptType {
        INLINE,
        INDEXED,
        FILE;

        public static ScriptType readFrom(StreamInput in) throws IOException {
            int scriptTypeOrd = in.read();
            if (scriptTypeOrd != -1) {
                if (scriptTypeOrd == INDEXED.ordinal()) {
                    return INDEXED;
                } else if (scriptTypeOrd == INLINE.ordinal()) {
                    return INLINE;
                } else if (scriptTypeOrd == FILE.ordinal()) {
                    return FILE;
                }
            }
            return null;
        }

        public static void writeTo(ScriptType scriptType, StreamOutput out) throws IOException{
            if (scriptType != null) {
                out.write(scriptType.ordinal());
            } else {
                out.write(INLINE.ordinal()); //Default to inline
            }
        }
    }

    class IndexedScript {
        String lang;
        String id;

        IndexedScript(String lang, String script) {
            this.lang = lang;
            final String[] parts = script.split("/");
            if (parts.length == 1) {
                this.id = script;
                if (this.lang == null){
                    this.lang = defaultLang;
                }
            } else {
                if (parts.length != 3) {
                    throw new ElasticsearchIllegalArgumentException("Illegal index script format [" + script + "]" +
                            " should be /lang/id");
                } else {
                    if (!parts[1].equals(this.lang)) {
                        throw new ElasticsearchIllegalStateException("Conflicting script language, found [" + parts[1] + "] expected + ["+ this.lang + "]");
                    }
                    this.id = parts[2];
                }
            }
        }
    }

    @Inject
    public ScriptService(Settings settings, Environment env, Set<ScriptEngineService> scriptEngines,
                         ResourceWatcherService resourceWatcherService) {
        super(settings);

        int cacheMaxSize = componentSettings.getAsInt("cache.max_size", 500);
        TimeValue cacheExpire = componentSettings.getAsTime("cache.expire", null);
        logger.debug("using script cache with max_size [{}], expire [{}]", cacheMaxSize, cacheExpire);

        this.defaultLang = settings.get(DEFAULT_SCRIPTING_LANGUAGE_SETTING, "groovy");
        this.dynamicScriptingDisabled = DynamicScriptDisabling.parse(settings.get(DISABLE_DYNAMIC_SCRIPTING_SETTING, DISABLE_DYNAMIC_SCRIPTING_DEFAULT));

        CacheBuilder cacheBuilder = CacheBuilder.newBuilder();
        if (cacheMaxSize >= 0) {
            cacheBuilder.maximumSize(cacheMaxSize);
        }
        if (cacheExpire != null) {
            cacheBuilder.expireAfterAccess(cacheExpire.nanos(), TimeUnit.NANOSECONDS);
        }
        this.cache = cacheBuilder.build();

        ImmutableMap.Builder<String, ScriptEngineService> builder = ImmutableMap.builder();
        for (ScriptEngineService scriptEngine : scriptEngines) {
            for (String type : scriptEngine.types()) {
                builder.put(type, scriptEngine);
            }
        }
        this.scriptEngines = builder.build();

        // put some default optimized scripts
        staticCache.put("doc.score", new CompiledScript("native", new DocScoreNativeScriptFactory()));

        // add file watcher for static scripts
        scriptsDirectory = new File(env.configFile(), "scripts");
        logger.debug("ScriptsDir : " + scriptsDirectory.toString());
        FileWatcher fileWatcher = new FileWatcher(scriptsDirectory);
        fileWatcher.addListener(new ScriptChangesListener());

        if (componentSettings.getAsBoolean("auto_reload_enabled", true)) {
            // automatic reload is enabled - register scripts
            resourceWatcherService.add(fileWatcher);
        } else {
            // automatic reload is disable just load scripts once
            fileWatcher.init();
        }
    }

    @Inject(optional=true)
    public void setClient(Client client) {
        this.client = client;
    }

    public void close() {
        for (ScriptEngineService engineService : scriptEngines.values()) {
            engineService.close();
        }
    }

    public CompiledScript compile(String script) {
        return compile(defaultLang, script);
    }

    public CompiledScript compile(String lang, String script) {
        return compile(lang, script, ScriptType.INLINE);
    }

    public CompiledScript compile(String lang,  String script, ScriptType scriptType) {
        if (logger.isTraceEnabled()) {
            logger.trace("Compiling lang: [{}] type: [{}] script: {}",  lang, scriptType, script);
        }

        CacheKey cacheKey;
        CompiledScript compiled;

        if(scriptType == ScriptType.INDEXED) {
            if (client == null) {
                throw new ElasticsearchIllegalArgumentException("Got an indexed script with no Client registered.");
            }

            final IndexedScript indexedScript = new IndexedScript(lang,script);

            if (lang != null && !lang.equals(indexedScript.lang)) {
                logger.trace("Overriding lang to " + indexedScript.lang);
                lang = indexedScript.lang;
            }

            verifyDynamicScripting(indexedScript.lang); //Since anyone can index a script, disable indexed scripting
                                          // if dynamic scripting is disabled, perhaps its own setting ?

            script = getScriptFromIndex(client, SCRIPT_INDEX, indexedScript.lang, indexedScript.id);
        } else if (scriptType == ScriptType.FILE) {

            compiled = staticCache.get(script); //On disk scripts will be loaded into the staticCache by the listener

            if (compiled != null) {
                return compiled;
            } else {
                throw new ElasticsearchIllegalArgumentException("Unable to find on disk script " + script);
            }
        }

        if (scriptType != ScriptType.INDEXED) {
            //For backwards compat attempt to load from disk
            compiled = staticCache.get(script); //On disk scripts will be loaded into the staticCache by the listener

            if (compiled != null) {
                return compiled;
            }
        }

        if (lang == null) {
            lang = defaultLang;
        }

        //This is an inline script check to see if we have it in the cache
        verifyDynamicScripting(lang);

        cacheKey = new CacheKey(lang, script);

        compiled = cache.getIfPresent(cacheKey);
        if (compiled != null) {
            return compiled;
        }

        //Either an un-cached inline script or an indexed script

        if (!dynamicScriptEnabled(lang)) {
            throw new ScriptException("dynamic scripting for [" + lang + "] disabled");
        }

        if (cacheKey == null) {
            cacheKey = new CacheKey(lang, script);
        }
        // not the end of the world if we compile it twice...
        compiled = getCompiledScript(lang, script);
        if (scriptType != ScriptType.INDEXED) {
            cache.put(cacheKey, compiled); //Only cache non indexed scripts
        }

        return compiled;
    }

    private CompiledScript getCompiledScript(String lang, String script) {
        CompiledScript compiled;ScriptEngineService service = scriptEngines.get(lang);
        if (service == null) {
            throw new ElasticsearchIllegalArgumentException("script_lang not supported [" + lang + "]");
        }

        compiled = new CompiledScript(lang, service.compile(script));
        return compiled;
    }

    private void verifyDynamicScripting(String lang) {
        if (!dynamicScriptEnabled(lang)) {
            throw new ScriptException("dynamic scripting disabled for [" + lang + "]");
        }
    }

    public static String getScriptFromIndex(Client client, String index, String scriptLang, String id) {
        GetResponse responseFields = client.prepareGet(index, scriptLang, id).get();
        if (responseFields.isExists()) {
            Map<String, Object> source = responseFields.getSourceAsMap();
            if (source.containsKey("template")) {
                try {
                    XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
                    Object template = source.get("template");
                    builder.map((Map<String, Object>)template);
                    return builder.string();
                } catch (IOException|ClassCastException e) {
                    throw new ElasticsearchIllegalStateException("Unable to parse "  + responseFields.getSourceAsString() + " as json", e);
                }
            } else  if (source.containsKey("script")) {
                return source.get("script").toString();
            } else {
                try {
                    XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
                    builder.map(responseFields.getSource());
                    return builder.string();
                } catch (IOException|ClassCastException e) {
                    throw new ElasticsearchIllegalStateException("Unable to parse "  + responseFields.getSourceAsString() + " as json", e);
                }
            }
        }
        throw new ElasticsearchIllegalArgumentException("Unable to find script [" + index + "/" + scriptLang + "/" + id + "]");

    }


    public ExecutableScript executable(String lang, String script, ScriptType scriptType, Map vars) {
        return executable(compile(lang, script, scriptType), vars);
    }

    public ExecutableScript executable(CompiledScript compiledScript, Map vars) {
        return scriptEngines.get(compiledScript.lang()).executable(compiledScript.compiled(), vars);
    }

    public SearchScript search(CompiledScript compiledScript, SearchLookup lookup, @Nullable Map<String, Object> vars) {
        return scriptEngines.get(compiledScript.lang()).search(compiledScript.compiled(), lookup, vars);
    }

    public SearchScript search(SearchLookup lookup, String lang, String script, ScriptType scriptType, @Nullable Map<String, Object> vars) {
        return search(compile(lang, script, scriptType), lookup, vars);
    }

    public SearchScript search(MapperService mapperService, IndexFieldDataService fieldDataService, String lang, String script, ScriptType scriptType, @Nullable Map<String, Object> vars) {
        return search(compile(lang, script), new SearchLookup(mapperService, fieldDataService, null), vars);
    }

    public Object execute(CompiledScript compiledScript, Map vars) {
        return scriptEngines.get(compiledScript.lang()).execute(compiledScript.compiled(), vars);
    }

    public void clear() {
        cache.invalidateAll();
    }

    private boolean dynamicScriptEnabled(String lang) {
        ScriptEngineService service = scriptEngines.get(lang);
        if (service == null) {
            throw new ElasticsearchIllegalArgumentException("script_lang not supported [" + lang + "]");
        }

        // Templating languages (mustache) and native scripts are always
        // allowed, "native" executions are registered through plugins
        if (this.dynamicScriptingDisabled == DynamicScriptDisabling.EVERYTHING_ALLOWED || "native".equals(lang) || "mustache".equals(lang)) {
            return true;
        } else if (this.dynamicScriptingDisabled == DynamicScriptDisabling.ONLY_DISK_ALLOWED) {
            return false;
        } else {
            return service.sandboxed();
        }
    }

    private class ScriptChangesListener extends FileChangesListener {

        private Tuple<String, String> scriptNameExt(File file) {
            String scriptPath = scriptsDirectory.toURI().relativize(file.toURI()).getPath();
            int extIndex = scriptPath.lastIndexOf('.');
            if (extIndex != -1) {
                String ext = scriptPath.substring(extIndex + 1);
                String scriptName = scriptPath.substring(0, extIndex).replace(File.separatorChar, '_');
                return new Tuple<>(scriptName, ext);
            } else {
                return null;
            }
        }

        @Override
        public void onFileInit(File file) {
            logger.trace("Loading script file : [{}]", file.toString());
            Tuple<String, String> scriptNameExt = scriptNameExt(file);
            if (scriptNameExt != null) {
                boolean found = false;
                for (ScriptEngineService engineService : scriptEngines.values()) {
                    for (String s : engineService.extensions()) {
                        if (s.equals(scriptNameExt.v2())) {
                            found = true;
                            try {
                                logger.info("compiling script file [{}]", file.getAbsolutePath());
                                String script = Streams.copyToString(new InputStreamReader(new FileInputStream(file), Charsets.UTF_8));
                                staticCache.put(scriptNameExt.v1(), new CompiledScript(engineService.types()[0], engineService.compile(script)));
                            } catch (Throwable e) {
                                logger.warn("failed to load/compile script [{}]", e, scriptNameExt.v1());
                            }
                            break;
                        }
                    }
                    if (found) {
                        break;
                    }
                }
                if (!found) {
                    logger.warn("no script engine found for [{}]", scriptNameExt.v2());
                }
            }
        }

        @Override
        public void onFileCreated(File file) {
            onFileInit(file);
        }

        @Override
        public void onFileDeleted(File file) {
            Tuple<String, String> scriptNameExt = scriptNameExt(file);
            logger.info("removing script file [{}]", file.getAbsolutePath());
            staticCache.remove(scriptNameExt.v1());
        }

        @Override
        public void onFileChanged(File file) {
            onFileInit(file);
        }

    }

    public static class CacheKey {
        public final String lang;
        public final String script;

        private CacheKey(){
            this.lang = null;
            this.script = null;
        }

        public CacheKey(String lang, String script) {
            this.lang = lang;
            this.script = script;
        }

        @Override
        public boolean equals(Object o) {
            CacheKey other = (CacheKey) o;
            return lang.equals(other.lang) && script.equals(other.script);
        }

        @Override
        public int hashCode() {
            return lang.hashCode() + 31 * script.hashCode();
        }
    }

    public static class DocScoreNativeScriptFactory implements NativeScriptFactory {
        @Override
        public ExecutableScript newScript(@Nullable Map<String, Object> params) {
            return new DocScoreSearchScript();
        }
    }

    public static class DocScoreSearchScript extends AbstractFloatSearchScript {
        @Override
        public float runAsFloat() {
            try {
                return doc().score();
            } catch (IOException e) {
                return 0;
            }
        }
    }
}
