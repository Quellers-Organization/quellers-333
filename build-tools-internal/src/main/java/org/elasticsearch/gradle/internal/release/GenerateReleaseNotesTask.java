/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.gradle.internal.release;

import com.google.common.annotations.VisibleForTesting;

import org.elasticsearch.gradle.VersionProperties;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * Orchestrates the steps required to generate or update various release notes files.
 */
public class GenerateReleaseNotesTask extends DefaultTask {
    private static final Logger LOGGER = Logging.getLogger(GenerateReleaseNotesTask.class);

    private final ConfigurableFileCollection changelogs;

    private final RegularFileProperty releaseNotesIndexTemplate;
    private final RegularFileProperty releaseNotesTemplate;
    private final RegularFileProperty releaseHighlightsTemplate;
    private final RegularFileProperty breakingChangesTemplate;

    private final RegularFileProperty releaseNotesIndexFile;
    private final RegularFileProperty releaseNotesFile;
    private final RegularFileProperty releaseHighlightsFile;
    private final RegularFileProperty breakingChangesFile;

    private final GitWrapper gitWrapper;

    @Inject
    public GenerateReleaseNotesTask(ObjectFactory objectFactory, ExecOperations execOperations) {
        changelogs = objectFactory.fileCollection();

        releaseNotesIndexTemplate = objectFactory.fileProperty();
        releaseNotesTemplate = objectFactory.fileProperty();
        releaseHighlightsTemplate = objectFactory.fileProperty();
        breakingChangesTemplate = objectFactory.fileProperty();

        releaseNotesIndexFile = objectFactory.fileProperty();
        releaseNotesFile = objectFactory.fileProperty();
        releaseHighlightsFile = objectFactory.fileProperty();
        breakingChangesFile = objectFactory.fileProperty();

        gitWrapper = new GitWrapper(execOperations);
    }

    @TaskAction
    public void executeTask() throws IOException {
        if (needsGitTags(VersionProperties.getElasticsearch())) {
            findAndUpdateUpstreamRemote(gitWrapper);
        }

        LOGGER.info("Finding changelog files...");

        final Map<QualifiedVersion, Set<File>> filesByVersion = partitionFiles(
            gitWrapper,
            VersionProperties.getElasticsearch(),
            this.changelogs.getFiles()
        );

        final List<ChangelogEntry> entries = new ArrayList<>();
        final Map<QualifiedVersion, Set<ChangelogEntry>> changelogsByVersion = new HashMap<>();

        filesByVersion.forEach((version, files) -> {
            Set<ChangelogEntry> entriesForVersion = files.stream().map(ChangelogEntry::parse).collect(toSet());
            entries.addAll(entriesForVersion);
            changelogsByVersion.put(version, entriesForVersion);
        });

        final Set<QualifiedVersion> versions = getVersions(gitWrapper, VersionProperties.getElasticsearch());

        LOGGER.info("Updating release notes index...");
        ReleaseNotesIndexGenerator.update(
            versions,
            this.releaseNotesIndexTemplate.get().getAsFile(),
            this.releaseNotesIndexFile.get().getAsFile()
        );

        LOGGER.info("Generating release notes...");
        ReleaseNotesGenerator.update(
            this.releaseNotesTemplate.get().getAsFile(),
            this.releaseNotesFile.get().getAsFile(),
            changelogsByVersion
        );

        LOGGER.info("Generating release highlights...");
        ReleaseHighlightsGenerator.update(
            this.releaseHighlightsTemplate.get().getAsFile(),
            this.releaseHighlightsFile.get().getAsFile(),
            entries
        );

        LOGGER.info("Generating breaking changes / deprecations notes...");
        BreakingChangesGenerator.update(
            this.breakingChangesTemplate.get().getAsFile(),
            this.breakingChangesFile.get().getAsFile(),
            entries
        );
    }

    @VisibleForTesting
    static Set<QualifiedVersion> getVersions(GitWrapper gitWrapper, String currentVersion) {
        QualifiedVersion v = QualifiedVersion.of(currentVersion);
        Set<QualifiedVersion> versions = gitWrapper.listVersions("v" + v.getMajor() + '.' + v.getMinor() + ".*").collect(toSet());
        versions.add(v);
        return versions;
    }

    @VisibleForTesting
    static Map<QualifiedVersion, Set<File>> partitionFiles(GitWrapper gitWrapper, String versionString, Set<File> allFilesInCheckout) {
        if (needsGitTags(versionString) == false) {
            return Map.of(QualifiedVersion.of(versionString), allFilesInCheckout);
        }

        QualifiedVersion currentVersion = QualifiedVersion.of(versionString);

        // Find all tags for this minor series, using a wildcard tag pattern.
        String tagWildcard = "v%d.%d*".formatted(currentVersion.getMajor(), currentVersion.getMinor());

        final List<QualifiedVersion> earlierVersions = gitWrapper.listVersions(tagWildcard)
            // Only keep earlier versions, and if `currentVersion` is a prerelease, then only prereleases too.
            .filter(each -> each.isBefore(currentVersion) && (currentVersion.hasQualifier() == each.hasQualifier()))
            .sorted(naturalOrder())
            .collect(toList());

        if (earlierVersions.isEmpty()) {
            throw new GradleException("Failed to find git tags prior to [v" + currentVersion + "]");
        }

        Map<QualifiedVersion, Set<File>> partitionedFiles = new HashMap<>();

        Set<File> mutableAllFilesInCheckout = new HashSet<>(allFilesInCheckout);

        // 1. For each earlier version
        earlierVersions.forEach(earlierVersion -> {
            // 2. Find all the changelog files it contained
            Set<String> filesInTreeForVersion = gitWrapper.listFiles("v" + earlierVersion, "docs/changelog")
                .map(line -> Path.of(line).getFileName().toString())
                .collect(toSet());

            Set<File> filesForVersion = new HashSet<>();
            partitionedFiles.put(earlierVersion, filesForVersion);

            // 3. Find the `File` object for each one
            final Iterator<File> filesIterator = mutableAllFilesInCheckout.iterator();
            while (filesIterator.hasNext()) {
                File nextFile = filesIterator.next();
                if (filesInTreeForVersion.contains(nextFile.getName())) {
                    // 4. And remove it so that it is associated with the earlier version
                    filesForVersion.add(nextFile);
                    filesIterator.remove();
                }
            }
        });

        // 5. Associate whatever is left with the current version.
        partitionedFiles.put(currentVersion, mutableAllFilesInCheckout);

        return partitionedFiles;
    }

    private static void findAndUpdateUpstreamRemote(GitWrapper gitWrapper) {
        LOGGER.info("Finding upstream git remote");
        // We need to ensure the tags are up-to-date. Find the correct remote to use
        String upstream = gitWrapper.listRemotes()
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue().contains("elastic/elasticsearch"))
            .findFirst()
            .map(Map.Entry::getKey)
            .orElseThrow(
                () -> new GradleException(
                    "I need to ensure the git tags are up-to-date, but I couldn't find a git remote for [elastic/elasticsearch]"
                )
            );

        LOGGER.info("Updating remote [{}]", upstream);
        // Now update the remote, and make sure we update the tags too
        gitWrapper.updateRemote(upstream);

        LOGGER.info("Updating tags from [{}]", upstream);
        gitWrapper.updateTags(upstream);
    }

    @VisibleForTesting
    static boolean needsGitTags(String versionString) {
        if (versionString.endsWith(".0") || versionString.endsWith(".0-SNAPSHOT") || versionString.endsWith("-alpha1")) {
            return false;
        }

        return true;
    }

    @InputFiles
    public FileCollection getChangelogs() {
        return changelogs;
    }

    public void setChangelogs(FileCollection files) {
        this.changelogs.setFrom(files);
    }

    @InputFile
    public RegularFileProperty getReleaseNotesIndexTemplate() {
        return releaseNotesIndexTemplate;
    }

    public void setReleaseNotesIndexTemplate(RegularFile file) {
        this.releaseNotesIndexTemplate.set(file);
    }

    @InputFile
    public RegularFileProperty getReleaseNotesTemplate() {
        return releaseNotesTemplate;
    }

    public void setReleaseNotesTemplate(RegularFile file) {
        this.releaseNotesTemplate.set(file);
    }

    @InputFile
    public RegularFileProperty getReleaseHighlightsTemplate() {
        return releaseHighlightsTemplate;
    }

    public void setReleaseHighlightsTemplate(RegularFile file) {
        this.releaseHighlightsTemplate.set(file);
    }

    @InputFile
    public RegularFileProperty getBreakingChangesTemplate() {
        return breakingChangesTemplate;
    }

    public void setBreakingChangesTemplate(RegularFile file) {
        this.breakingChangesTemplate.set(file);
    }

    @OutputFile
    public RegularFileProperty getReleaseNotesIndexFile() {
        return releaseNotesIndexFile;
    }

    public void setReleaseNotesIndexFile(RegularFile file) {
        this.releaseNotesIndexFile.set(file);
    }

    @OutputFile
    public RegularFileProperty getReleaseNotesFile() {
        return releaseNotesFile;
    }

    public void setReleaseNotesFile(RegularFile file) {
        this.releaseNotesFile.set(file);
    }

    @OutputFile
    public RegularFileProperty getReleaseHighlightsFile() {
        return releaseHighlightsFile;
    }

    public void setReleaseHighlightsFile(RegularFile file) {
        this.releaseHighlightsFile.set(file);
    }

    @OutputFile
    public RegularFileProperty getBreakingChangesFile() {
        return breakingChangesFile;
    }

    public void setBreakingChangesFile(RegularFile file) {
        this.breakingChangesFile.set(file);
    }
}
