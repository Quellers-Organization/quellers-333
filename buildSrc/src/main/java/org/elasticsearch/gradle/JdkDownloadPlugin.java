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

package org.elasticsearch.gradle;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RelativePath;
import org.gradle.api.tasks.Copy;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.regex.Matcher;

public class JdkDownloadPlugin implements Plugin<Project> {

    private static final String REPO_NAME_PREFIX = "jdk_repo_";
    private static final String CONTAINER_NAME = "jdks";

    @Override
    public void apply(Project project) {
        NamedDomainObjectContainer<Jdk> jdksContainer = project.container(Jdk.class, name ->
            new Jdk(name, project)
        );
        project.getExtensions().add(CONTAINER_NAME, jdksContainer);

        project.afterEvaluate(p -> {
            for (Jdk jdk : jdksContainer) {
                jdk.finalizeValues();
                String version = jdk.getVersion();
                String platform = jdk.getPlatform();

                // depend on the jdk directory "artifact" from the root project
                DependencyHandler dependencies = project.getDependencies();
                Map<String, Object> depConfig = new HashMap<>();
                depConfig.put("path", ":"); // root project
                depConfig.put("configuration", configName("extracted_jdk", version, platform));
                dependencies.add(jdk.getConfiguration().getName(), dependencies.project(depConfig));

                // ensure a root level jdk download task exists
                setupRootJdkDownload(project.getRootProject(), platform, version);
            }
        });

        // all other repos should ignore the special jdk artifacts
        project.getRootProject().getRepositories().all(repo -> {
            if (repo.getName().startsWith(REPO_NAME_PREFIX) == false) {
                repo.content(content -> {
                    content.excludeGroup("adoptopenjdk");
                    content.excludeGroup("openjdk");
                });
            }
        });
    }

    @SuppressWarnings("unchecked")
    public static NamedDomainObjectContainer<Jdk> getContainer(Project project) {
        return (NamedDomainObjectContainer<Jdk>) project.getExtensions().getByName(CONTAINER_NAME);
    }

    private static void setupRootJdkDownload(Project rootProject, String platform, String version) {
        String extractTaskName = "extract" + capitalize(platform) + "Jdk-" + version;
        // NOTE: this is *horrendous*, but seems to be the only way to check for the existence of a registered task
        try {
            rootProject.getTasks().named(extractTaskName);
            // already setup this version
            return;
        } catch (UnknownTaskException e) {
            // fall through: register the task
        }

        // decompose the bundled jdk version, broken into elements as: [feature, interim, update, build]
        // Note the "patch" version is not yet handled here, as it has not yet been used by java.
        Matcher jdkVersionMatcher = Jdk.VERSION_PATTERN.matcher(version);
        if (jdkVersionMatcher.matches() == false) {
            throw new IllegalArgumentException("Malformed jdk version [" + version + "]");
        }
        String jdkDistribution = jdkVersionMatcher.group(1);
        String jdkVersion = jdkVersionMatcher.group(2) + (jdkVersionMatcher.group(3) != null ? (jdkVersionMatcher.group(3)) : "");
        String jdkMajor = jdkVersionMatcher.group(2);
        String jdkBuild = jdkVersionMatcher.group(4);
        String hash = jdkVersionMatcher.group(6);

        // add fake ivy repo for jdk url
        String repoName = REPO_NAME_PREFIX + version;
        RepositoryHandler repositories = rootProject.getRepositories();
        if (rootProject.getRepositories().findByName(repoName) == null) {
            if (jdkDistribution.equals("adoptopenjdk")) {
                if (hash != null) {
                    throw new IllegalArgumentException("adoptopenjdk versions do not have hashes but was [" + version + "]");
                }
                repositories.ivy(ivyRepo -> {
                    ivyRepo.setName(repoName);
                    ivyRepo.setUrl("https://artifactory.elstc.co/");
                    ivyRepo.metadataSources(IvyArtifactRepository.MetadataSources::artifact);
                    final String pattern = String.format(
                        Locale.ROOT,
                        "artifactory/oss-jdk-local/adoptopenjdk/OpenJDK%sU-jdk_x64_[module]_hotspot_[revision]_%s.[ext]",
                        jdkMajor,
                        jdkBuild);
                    ivyRepo.patternLayout(layout -> layout.artifact(pattern));
                    ivyRepo.content(content -> content.includeGroup("adoptopenjdk"));
                });
            } else {
                assert jdkDistribution.equals("openjdk") : version;
                if (hash != null) {
                    // current pattern since 12.0.1
                    repositories.ivy(ivyRepo -> {
                        ivyRepo.setName(repoName);
                        ivyRepo.setUrl("https://download.oracle.com");
                        ivyRepo.metadataSources(IvyArtifactRepository.MetadataSources::artifact);
                        ivyRepo.patternLayout(layout -> layout.artifact(
                            "java/GA/jdk" + jdkVersion + "/" + hash + "/" + jdkBuild + "/GPL/openjdk-[revision]_[module]-x64_bin.[ext]"));
                        ivyRepo.content(content -> content.includeGroup("openjdk"));
                    });
                } else {
                    // simpler legacy pattern from JDK 9 to JDK 12 that we are advocating to Oracle to bring back
                    repositories.ivy(ivyRepo -> {
                        ivyRepo.setName(repoName);
                        ivyRepo.setUrl("https://download.oracle.com");
                        ivyRepo.metadataSources(IvyArtifactRepository.MetadataSources::artifact);
                        ivyRepo.patternLayout(layout ->
                            layout.artifact("java/GA/jdk" + jdkMajor + "/" + jdkBuild + "/GPL/openjdk-[revision]_[module]-x64_bin.[ext]"));
                        ivyRepo.content(content -> content.includeGroup("openjdk"));
                    });
                }
            }
        }

        // add the jdk as a "dependency"
        final ConfigurationContainer configurations = rootProject.getConfigurations();
        String remoteConfigName = configName(version, platform);
        String localConfigName = configName("extracted_jdk", version, platform);
        Configuration jdkConfig = configurations.findByName(remoteConfigName);
        if (jdkConfig == null) {
            jdkConfig = configurations.create(remoteConfigName);
            configurations.create(localConfigName);
        }
        String platformDep = platform.equals("darwin") ? (jdkDistribution.equals("adoptopenjdk") ? "mac" : "osx") : platform;
        String extension = platform.equals("windows") ? "zip" : "tar.gz";
        String jdkDep = jdkDistribution + ":" + platformDep + ":" + jdkVersion + "@" + extension;
        rootProject.getDependencies().add(configName(version, platform), jdkDep);

        // add task for extraction
        final String extractDir =
            rootProject.getBuildDir().toPath().resolve("jdks/" + jdkDistribution + "-" + jdkVersion + "_" + platform).toString();

        // delay resolving jdkConfig until runtime
        Supplier<File> jdkArchiveGetter = jdkConfig::getSingleFile;
        final Object extractTask;
        if (extension.equals("zip")) {
            final Callable<FileTree> fileGetter = () -> rootProject.zipTree(jdkArchiveGetter.get());
            // TODO: look into doing this as an artifact transform, which are cacheable starting in gradle 5.3
            Action<CopySpec> removeRootDir = copy -> {
                // remove extra unnecessary directory levels
                copy.eachFile(details -> {
                    /*
                     * We want to remove up to the and including the jdk-.* relative paths. That is a JDK archive is structured as:
                     *   jdk-12.0.1/
                     *   jdk-12.0.1/Contents
                     *   ...
                     *
                     * and we want to remove the leading jdk-12.0.1. Note however that there could also be a leading ./ as in
                     *   ./
                     *   ./jdk-12.0.1/
                     *   ./jdk-12.0.1/Contents
                     *
                     * so we account for this and search the path components until we find the jdk-12.0.1, and strip the leading components.
                     */
                    String[] pathSegments = details.getRelativePath().getSegments();
                    int index = 0;
                    for (; index < pathSegments.length; index++) {
                        if (pathSegments[index].matches("jdk-.*")) break;
                    }
                    assert index + 1 <= pathSegments.length;
                    String[] newPathSegments = Arrays.copyOfRange(pathSegments, index + 1, pathSegments.length);
                    details.setRelativePath(new RelativePath(true, newPathSegments));
                });
                copy.setIncludeEmptyDirs(false);
            };
            extractTask = rootProject.getTasks().register(extractTaskName, Copy.class, copyTask -> {
                copyTask.doFirst(new Action<Task>() {
                    @Override
                    public void execute(Task t) {
                        rootProject.delete(extractDir);
                    }
                });
                copyTask.into(extractDir);
                copyTask.from(fileGetter, removeRootDir);
            });
        } else {
            /*
             * Gradle TarFileTree does not resolve symlinks, so we have to manually extract and preserve the symlinks.
             * cf. https://github.com/gradle/gradle/issues/3982 and https://discuss.gradle.org/t/tar-and-untar-losing-symbolic-links/2039
             */
            extractTask = rootProject.getTasks().register(extractTaskName, Task.class, task -> {
                task.doLast(new Action<Task>() {
                    @Override
                    public void execute(final Task task) {
                        final Path extractPath = Paths.get(extractDir);
                        try {
                            // ensure the target extraction path is empty
                            rootProject.delete(extractPath);
                            final TarArchiveInputStream tar =
                                new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(jdkArchiveGetter.get())));
                            TarArchiveEntry entry = tar.getNextTarEntry();
                            while (entry != null) {
                                /*
                                 * We want to remove up to the and including the jdk-.* relative paths. That is a JDK archive is structured
                                 * as:
                                 *   jdk-12.0.1/
                                 *   jdk-12.0.1/Contents
                                 *   ...
                                 *
                                 * and we want to remove the leading jdk-12.0.1. Note however that there could also be a leading ./ as in
                                 *   ./
                                 *   ./jdk-12.0.1/
                                 *   ./jdk-12.0.1/Contents
                                 *
                                 * so we account for this and search the path components until we find the jdk-12.0.1, and strip the leading
                                 * components.
                                 */
                                final Path entryName = Paths.get(entry.getName());
                                int index = 0;
                                for (; index < entryName.getNameCount(); index++) {
                                    if (entryName.getName(index).toString().matches("jdk-.*")) break;
                                }
                                if (index + 1 >= entryName.getNameCount()) {
                                    // this happens on the top-level directories in the archive, which we are removing
                                    entry = tar.getNextTarEntry();
                                    continue;
                                }
                                // finally remove the top-level directories from the output path
                                final Path destination = extractPath.resolve(entryName.subpath(index + 1, entryName.getNameCount()));
                                if (entry.isDirectory()) {
                                    Files.createDirectories(destination);
                                } else if (entry.isSymbolicLink()) {
                                    Files.createSymbolicLink(destination, Paths.get(entry.getLinkName()));
                                } else {
                                    // copy the file from the archive using a small buffer to avoid heaping
                                    Files.createFile(destination);
                                    byte[] bytes = new byte[1024];
                                    final BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(destination.toFile()));
                                    int length = 0;
                                    while ((length = tar.read(bytes)) != -1) {
                                        bos.write(bytes, 0, length);
                                    }
                                    bos.close();
                                }
                                entry = tar.getNextTarEntry();
                            }
                            tar.close();
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
            });
        }
        rootProject.getArtifacts().add(localConfigName,
            rootProject.getLayout().getProjectDirectory().dir(extractDir),
            artifact -> artifact.builtBy(extractTask));
    }

    private static String configName(String version, String platform) {
        return version + "_" + platform;
    }

    private static String configName(String prefix, String version, String platform) {
        return prefix + "_" + version + "_" + platform;
    }

    private static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
