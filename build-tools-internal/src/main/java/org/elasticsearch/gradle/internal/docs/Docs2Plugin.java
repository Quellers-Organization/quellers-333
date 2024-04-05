/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.gradle.internal.docs;

import org.elasticsearch.gradle.OS;
import org.elasticsearch.gradle.Version;
import org.elasticsearch.gradle.VersionProperties;
import org.elasticsearch.gradle.testclusters.ElasticsearchCluster;
import org.elasticsearch.gradle.testclusters.TestClustersPlugin;
import org.elasticsearch.gradle.testclusters.TestDistribution;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.file.FileOperations;

import java.util.Map;
import javax.inject.Inject;

public class Docs2Plugin implements Plugin<Project> {
    private FileOperations fileOperations;
    private ProjectLayout projectLayout;

    @Inject
    Docs2Plugin(FileOperations fileOperations, ProjectLayout projectLayout) {
        this.projectLayout = projectLayout;
        this.fileOperations = fileOperations;
    }

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("elasticsearch.legacy-yaml-rest-test");

        String distribution = System.getProperty("tests.distribution", "default");
        // The distribution can be configured with -Dtests.distribution on the command line
        NamedDomainObjectContainer<ElasticsearchCluster> testClusters = (NamedDomainObjectContainer<ElasticsearchCluster>) project
            .getExtensions()
            .getByName(TestClustersPlugin.EXTENSION_NAME);

        testClusters.matching((c) -> c.getName().equals("yamlRestTest")).configureEach(c -> {
            c.setTestDistribution(TestDistribution.valueOf(distribution.toUpperCase()));
            c.setNameCustomization((name) -> name.replace("yamlRestTest", "node"));
        });

        project.getTasks().named("assemble").configure(task -> { task.setEnabled(false); });

        Map<String, String> commonDefaultSubstitutions = Map.of(
            /* These match up with the asciidoc syntax for substitutions but
             * the values may differ. In particular {version} needs to resolve
             * to the version being built for testing but needs to resolve to
             * the last released version for docs. */
            "\\{version\\}",
            Version.fromString(VersionProperties.getElasticsearch()).toString(),
            "\\{version_qualified\\}",
            VersionProperties.getElasticsearch(),
            "\\{lucene_version\\}",
            VersionProperties.getLucene().replaceAll("-snapshot-\\w+$", ""),
            "\\{build_flavor\\}",
            distribution,
            "\\{build_type\\}",
            OS.conditionalString().onWindows(() -> "zip").onUnix(() -> "tar").supply()
        );

        project.getTasks().register("listSnippets", DocSnippetTask.class, task -> {
            task.setGroup("Docs");
            task.setDescription("List each snippet");
            task.setDefaultSubstitutions(commonDefaultSubstitutions);
            task.setPerSnippet(snippet -> System.out.println(snippet.toString()));
        });
    }
}
