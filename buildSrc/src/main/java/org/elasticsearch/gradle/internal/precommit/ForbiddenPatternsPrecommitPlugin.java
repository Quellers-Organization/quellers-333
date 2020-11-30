/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.gradle.internal.precommit;

import org.elasticsearch.gradle.internal.InternalPlugin;
import org.elasticsearch.gradle.precommit.PrecommitPlugin;
import org.elasticsearch.gradle.util.GradleUtils;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.util.stream.Collectors;

public class ForbiddenPatternsPrecommitPlugin extends PrecommitPlugin implements InternalPlugin {

    private final ProviderFactory providerFactory;

    @Inject
    public ForbiddenPatternsPrecommitPlugin(ProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
    }

    @Override
    public TaskProvider<? extends Task> createTask(Project project) {
        return project.getTasks().register("forbiddenPatterns", ForbiddenPatternsTask.class, forbiddenPatternsTask -> {
            forbiddenPatternsTask.getSourceFolders()
                .addAll(
                    providerFactory.provider(
                        () -> GradleUtils.getJavaSourceSets(project).stream().map(s -> s.getAllSource()).collect(Collectors.toList())
                    )
                );
            forbiddenPatternsTask.getRootDir().set(project.getRootDir());
        });
    }
}
