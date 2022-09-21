/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.gradle.stableplugin;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkerExecutor;

import java.io.File;
import java.util.Set;
import javax.inject.Inject;

//asm here?
public abstract class GenerateNamedComponentsTask extends DefaultTask {
    private static final Logger LOGGER = Logging.getLogger(GenerateNamedComponentsTask.class);
    private static final String NAMED_COMPONENTS_FILE = "named_components.json";

    private final WorkerExecutor workerExecutor;
    private FileCollection classpath;
    private FileCollection pluginClasses;

    @Inject
    public GenerateNamedComponentsTask(WorkerExecutor workerExecutor, ObjectFactory objectFactory, ProjectLayout projectLayout) {
        this.workerExecutor = workerExecutor;
        getOutputFile().convention(projectLayout.getBuildDirectory().file("generated-named-components/" + NAMED_COMPONENTS_FILE));

    }
    @TaskAction
    public void scanPluginClasses() {
        workerExecutor.noIsolation().submit(GenerateNamedComponentsAction.class, params -> {
            params.getClasspath().from(classpath);
            params.getPluginClasses().setFrom(pluginClasses);
        });
    }

    @OutputFile
    public abstract RegularFileProperty getOutputFile();


    @CompileClasspath
    public FileCollection getClasspath() {
        return classpath.filter(File::exists);
    }

    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    @InputFiles
    public FileCollection getPluginClasses() {
        return pluginClasses;
    }

    public void setPluginClasses(FileCollection pluginClasses) {
        this.pluginClasses = pluginClasses;
    }

    public abstract static class GenerateNamedComponentsAction implements WorkAction<Parameters> {
        @Override
        public void execute() {
            final Parameters parameters = getParameters();
            Set<File> files = getParameters().getClasspath().getFiles();
            Set<File> pluginFiles = getParameters().getPluginClasses().getFiles();
            LOGGER.info(files.toString());
            LOGGER.info(pluginFiles.toString());
        }
    }

    interface Parameters extends WorkParameters {
        Property<String> getProjectPath();

        ConfigurableFileCollection getClasspath();

        SetProperty<File> getSrcDirs();

        ConfigurableFileCollection getPluginClasses();
    }
}
