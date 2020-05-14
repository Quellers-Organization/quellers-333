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

package org.elasticsearch.gradle;

import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin;
import com.github.jengelman.gradle.plugins.shadow.ShadowExtension;
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import groovy.util.Node;
import groovy.util.NodeList;
import nebula.plugin.info.InfoBrokerPlugin;
import org.elasticsearch.gradle.info.BuildParams;
import org.elasticsearch.gradle.util.Util;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.BasePluginConvention;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.tasks.GenerateMavenPom;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.external.javadoc.CoreJavadocOptions;
import org.gradle.internal.jvm.Jvm;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.elasticsearch.gradle.util.GradleUtils.maybeConfigure;

public class PublishPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("nebula.maven-base-publish");

        configureJavadoc(project);
        configureJavadocJar(project);
        configureSourcesJar(project);
        configurePomGeneration(project);

    }

    private static String getArchivesBaseName(Project project) {
        return project.getConvention().getPlugin(BasePluginConvention.class).getArchivesBaseName();
    }

    /**Configuration generation of maven poms. */
    private static void configurePomGeneration(Project project) {

        TaskProvider<Task> generatePomTask = project.getTasks().register("generatePom");
        // generatePomTask.configure(t -> t.dependsOn("generatePomFileForNebulaPublication"));

        maybeConfigure(project.getTasks(), LifecycleBasePlugin.ASSEMBLE_TASK_NAME, assemble -> assemble.dependsOn(generatePomTask));

        project.getTasks().withType(GenerateMavenPom.class).configureEach(pomTask -> pomTask.setDestination(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return String.format(
                    "%s/distributions/%s-%s.pom",
                    project.getBuildDir(),
                    getArchivesBaseName(project),
                    project.getVersion()
                );
            }
        }));

        PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);

        project.getPluginManager().withPlugin("com.github.johnrengelman.shadow", plugin -> {
            MavenPublication publication = publishing.getPublications().maybeCreate("shadow", MavenPublication.class);
            ShadowExtension shadow = project.getExtensions().getByType(ShadowExtension.class);
            shadow.component(publication);
            // Workaround for https://github.com/johnrengelman/shadow/issues/334
            // Here we manually add any project dependencies in the "shadow" configuration to our generated POM
            publication.getPom().withXml(xml -> {
                Node root = xml.asNode();
                root.appendNode("name", project.getName());
                root.appendNode("description", project.getDescription());
                Node dependenciesNode = (Node) ((NodeList) root.get("dependencies")).get(0);
                project.getConfigurations().getByName(ShadowBasePlugin.getCONFIGURATION_NAME()).getAllDependencies().all(dependency -> {
                    if (dependency instanceof ProjectDependency) {
                        Node dependencyNode = dependenciesNode.appendNode("dependency");
                        dependencyNode.appendNode("groupId", dependency.getGroup());
                        ProjectDependency projectDependency = (ProjectDependency) dependency;
                        String artifactId = getArchivesBaseName(projectDependency.getDependencyProject());
                        dependencyNode.appendNode("artifactId", artifactId);
                        dependencyNode.appendNode("version", dependency.getVersion());
                        dependencyNode.appendNode("scope", "compile");
                    }
                });
            });
        });

        // Add git origin info to generated POM files
        publishing.getPublications().withType(MavenPublication.class, publication -> {
            publication.getPom().withXml(PublishPlugin::addScmInfo);

            // have to defer this until archivesBaseName is set
            project.afterEvaluate(p -> publication.setArtifactId(getArchivesBaseName(project)));

            generatePomTask.configure(
                t -> t.dependsOn(String.format("generatePomFileFor%sPublication", Util.capitalize(publication.getName())))
            );
        });

    }

    private static void addScmInfo(XmlProvider xml) {
        Node root = xml.asNode();
        root.appendNode("url", Util.urlFromOrigin(BuildParams.getGitOrigin()));
        Node scmNode = root.appendNode("scm");
        scmNode.appendNode("url", BuildParams.getGitOrigin());
    }

    private static void configureJavadoc(Project project) {
        // remove compiled classes from the Javadoc classpath: http://mail.openjdk.java.net/pipermail/javadoc-dev/2018-January/000400.html
        final List<File> classes = new ArrayList<>();
        project.getTasks().withType(JavaCompile.class).configureEach(javaCompile -> classes.add(javaCompile.getDestinationDir()));
        project.getTasks().withType(Javadoc.class).configureEach(javadoc -> {
            // only explicitly set javadoc executable if compiler JDK is different from Gradle
            // this ensures better cacheability as setting ths input to an absolute path breaks portability
            Path compilerJvm = BuildParams.getCompilerJavaHome().toPath();
            Path gradleJvm = Jvm.current().getJavaHome().toPath();
            try {
                if (Files.isSameFile(compilerJvm, gradleJvm) == false) {
                    javadoc.setExecutable(compilerJvm.resolve("bin/javadoc").toString());
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            javadoc.setClasspath(javadoc.getClasspath().filter(f -> classes.contains(f) == false));
            /*
             * Generate docs using html5 to suppress a warning from `javadoc`
             * that the default will change to html5 in the future.
             */
            CoreJavadocOptions javadocOptions = (CoreJavadocOptions) javadoc.getOptions();
            javadocOptions.addBooleanOption("html5", true);
        });
        // ensure javadoc task is run with 'check'
        project.getTasks()
            .named(LifecycleBasePlugin.CHECK_TASK_NAME)
            .configure(t -> t.dependsOn(project.getTasks().withType(Javadoc.class)));
    }

    /** Adds a javadocJar task to generate a jar containing javadocs. */
    private static void configureJavadocJar(Project project) {
        project.getPlugins().withId("elasticsearch.java", p -> {
            TaskProvider<Jar> javadocJarTask = project.getTasks().register("javadocJar", Jar.class);
            javadocJarTask.configure(jar -> {
                jar.getArchiveClassifier().set("javadoc");
                jar.setGroup("build");
                jar.setDescription("Assembles a jar containing javadocs.");
                jar.from(project.getTasks().named(JavaPlugin.JAVADOC_TASK_NAME));
            });
            maybeConfigure(project.getTasks(), BasePlugin.ASSEMBLE_TASK_NAME, t -> t.dependsOn(javadocJarTask));
        });
    }

    static void configureSourcesJar(Project project) {
        project.getPlugins().withId("elasticsearch.java", p -> {
            TaskProvider<Jar> sourcesJarTask = project.getTasks().register("sourcesJar", Jar.class);
            sourcesJarTask.configure(jar -> {
                jar.getArchiveClassifier().set("sources");
                jar.setGroup("build");
                jar.setDescription("Assembles a jar containing source files.");
                SourceSet mainSourceSet = Util.getJavaMainSourceSet(project).get();
                jar.from(mainSourceSet.getAllSource());
            });
            maybeConfigure(project.getTasks(), BasePlugin.ASSEMBLE_TASK_NAME, t -> t.dependsOn(sourcesJarTask));
        });
    }


}
