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

import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.IndexScopedSettings;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsFilter;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.grok.Grok;
import org.elasticsearch.grok.ThreadInterrupter;
import org.elasticsearch.ingest.Processor;
import org.elasticsearch.plugins.ActionPlugin;
import org.elasticsearch.plugins.IngestPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestHandler;
import org.elasticsearch.threadpool.ThreadPool;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class IngestCommonPlugin extends Plugin implements ActionPlugin, IngestPlugin {

    static final Map<String, String> GROK_PATTERNS = Grok.getBuiltinPatterns();
    static final Setting<TimeValue> GUARD_INTERVAL =
        Setting.timeSetting("ingest.grok.guard.interval", TimeValue.timeValueSeconds(3), Setting.Property.NodeScope);
    static final Setting<TimeValue> GUARD_MAX_EXECUTION_TIME =
        Setting.timeSetting("ingest.grok.guard.max_execution_time", TimeValue.timeValueSeconds(5), Setting.Property.NodeScope);

    public IngestCommonPlugin() {
    }

    @Override
    public Map<String, Processor.Factory> getProcessors(Processor.Parameters parameters) {
        Map<String, Processor.Factory> processors = new HashMap<>();
        processors.put(DateProcessor.TYPE, new DateProcessor.Factory(parameters.scriptService));
        processors.put(SetProcessor.TYPE, new SetProcessor.Factory(parameters.scriptService));
        processors.put(AppendProcessor.TYPE, new AppendProcessor.Factory(parameters.scriptService));
        processors.put(RenameProcessor.TYPE, new RenameProcessor.Factory());
        processors.put(RemoveProcessor.TYPE, new RemoveProcessor.Factory(parameters.scriptService));
        processors.put(SplitProcessor.TYPE, new SplitProcessor.Factory());
        processors.put(JoinProcessor.TYPE, new JoinProcessor.Factory());
        processors.put(UppercaseProcessor.TYPE, new UppercaseProcessor.Factory());
        processors.put(LowercaseProcessor.TYPE, new LowercaseProcessor.Factory());
        processors.put(TrimProcessor.TYPE, new TrimProcessor.Factory());
        processors.put(ConvertProcessor.TYPE, new ConvertProcessor.Factory());
        processors.put(GsubProcessor.TYPE, new GsubProcessor.Factory());
        processors.put(FailProcessor.TYPE, new FailProcessor.Factory(parameters.scriptService));
        processors.put(ForEachProcessor.TYPE, new ForEachProcessor.Factory());
        processors.put(DateIndexNameProcessor.TYPE, new DateIndexNameProcessor.Factory());
        processors.put(SortProcessor.TYPE, new SortProcessor.Factory());
        ThreadInterrupter threadInterrupter = createGrokThreadInterrupter(parameters);
        processors.put(GrokProcessor.TYPE, new GrokProcessor.Factory(GROK_PATTERNS, threadInterrupter));
        processors.put(ScriptProcessor.TYPE, new ScriptProcessor.Factory(parameters.scriptService));
        processors.put(DotExpanderProcessor.TYPE, new DotExpanderProcessor.Factory());
        processors.put(JsonProcessor.TYPE, new JsonProcessor.Factory());
        processors.put(KeyValueProcessor.TYPE, new KeyValueProcessor.Factory());
        processors.put(URLDecodeProcessor.TYPE, new URLDecodeProcessor.Factory());
        return Collections.unmodifiableMap(processors);
    }

    @Override
    public List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> getActions() {
        return Arrays.asList(new ActionHandler<>(GrokProcessorGetAction.INSTANCE, GrokProcessorGetAction.TransportAction.class));
    }

    @Override
    public List<RestHandler> getRestHandlers(Settings settings, RestController restController, ClusterSettings clusterSettings,
                                             IndexScopedSettings indexScopedSettings, SettingsFilter settingsFilter,
                                             IndexNameExpressionResolver indexNameExpressionResolver,
                                             Supplier<DiscoveryNodes> nodesInCluster) {
        return Arrays.asList(new GrokProcessorGetAction.RestAction(settings, restController));
    }
    
    @Override
    public List<Setting<?>> getSettings() {
        return Arrays.asList(GUARD_INTERVAL, GUARD_MAX_EXECUTION_TIME);
    }
    
    private static ThreadInterrupter createGrokThreadInterrupter(Processor.Parameters parameters) {
        long intervalMillis = GUARD_INTERVAL.get(parameters.env.settings()).getMillis();
        long maxExecutionTimeMillis = GUARD_MAX_EXECUTION_TIME.get(parameters.env.settings()).getMillis();
        ThreadPool threadPool = parameters.threadPool;
        return ThreadInterrupter.newInstance(intervalMillis, maxExecutionTimeMillis, threadPool::relativeTimeInMillis,
            (delay, command) -> threadPool.schedule(TimeValue.timeValueMillis(delay), ThreadPool.Names.GENERIC, command));
    }

}
