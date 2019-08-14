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

package org.elasticsearch.packaging.util;

import org.elasticsearch.core.internal.io.IOUtils;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;

import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Wrappers and convenience methods for common filesystem operations
 */
public class FileUtils {

    public static List<Path> lsGlob(Path directory, String glob) {
        List<Path> paths = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, glob)) {

            for (Path path : stream) {
                paths.add(path);
            }
            return paths;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void rm(Path... paths) {
        try {
            IOUtils.rm(paths);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Path mktempDir(Path path) {
        try {
            return Files.createTempDirectory(path,"tmp");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static Path mkdir(Path path) {
        try {
            return Files.createDirectories(path);
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
     }

     public static Path cp(Path source, Path target) {
        try {
            return Files.copy(source, target);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Path mv(Path source, Path target) {
        try {
            return Files.move(source, target);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void append(Path file, String text) {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

            writer.write(text);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String slurp(Path file) {
        try {
            return String.join("\n", Files.readAllLines(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the content a {@link java.nio.file.Path} file. The file can be in plain text or GZIP format.
     * @param file The {@link java.nio.file.Path} to the file.
     * @return The content of {@code file}.
     */
    public static String slurpTxtorGz(Path file) {
        ByteArrayOutputStream fileBuffer = new ByteArrayOutputStream();
        try (GZIPInputStream in = new GZIPInputStream(Channels.newInputStream(FileChannel.open(file)))) {
            byte[] buffer = new byte[1024];
            int len;

            while ((len = in.read(buffer)) != -1) {
                fileBuffer.write(buffer, 0, len);
            }

            return (new String(fileBuffer.toByteArray(), StandardCharsets.UTF_8));
        } catch (ZipException e) {
            if (e.toString().contains("Not in GZIP format")) {
                return slurp(file);
            }
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns combined content of a text log file and rotated log files matching a pattern. Order of rotated log files is
     * not guaranteed.
     * @param logPath Base directory where log files reside.
     * @param activeLogFile The currently active log file. This file needs to be plain text under {@code logPath}.
     * @param rotatedLogFilesGlob A glob pattern to match rotated log files under {@code logPath}.
     *                            See {@link java.nio.file.FileSystem#getPathMatcher(String)} for glob examples.
     * @return Merges contents of {@code activeLogFile} and contents of filenames matching {@code rotatedLogFilesGlob}.
     * File contents are separated by a newline. The order of rotated log files matched by {@code rotatedLogFilesGlob} is not guaranteed.
     */
    public static String slurpAllLogs(Path logPath, String activeLogFile, String rotatedLogFilesGlob) {
        StringJoiner logFileJoiner = new StringJoiner("\n");
        try {
            logFileJoiner.add(new String(Files.readAllBytes(logPath.resolve(activeLogFile)), StandardCharsets.UTF_8));

            for (Path rotatedLogFile : FileUtils.lsGlob(logPath, rotatedLogFilesGlob)) {
                logFileJoiner.add(FileUtils.slurpTxtorGz(rotatedLogFile));
            }
            return(logFileJoiner.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets the owner of a file in a way that should be supported by all filesystems that have a concept of file owner
     */
    public static String getFileOwner(Path path) {
        try {
            FileOwnerAttributeView view = Files.getFileAttributeView(path, FileOwnerAttributeView.class);
            return view.getOwner().getName();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets attributes that are supported by all filesystems
     */
    public static BasicFileAttributes getBasicFileAttributes(Path path) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets attributes that are supported by posix filesystems
     */
    public static PosixFileAttributes getPosixFileAttributes(Path path) {
        try {
            return Files.readAttributes(path, PosixFileAttributes.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // vagrant creates /tmp for us in windows so we use that to avoid long paths
    public static Path getTempDir() {
        return Paths.get("/tmp");
    }

    public static Path getDefaultArchiveInstallPath() {
        return getTempDir().resolve("elasticsearch");
    }

    private static final Pattern VERSION_REGEX = Pattern.compile("(\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?)");
    public static String getCurrentVersion() {
        // TODO: just load this once
        String distroFile = System.getProperty("tests.distribution");
        java.util.regex.Matcher matcher = VERSION_REGEX.matcher(distroFile);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalStateException("Could not find version in filename: " + distroFile);
    }

    public static Path getPackagingArchivesDir() {
        return Paths.get(""); // tests are started in the packaging archives dir, ie the empty relative path
    }

    public static Path getDistributionFile(Distribution distribution) {
        return distribution.path;
    }

    public static void assertPathsExist(Path... paths) {
        Arrays.stream(paths).forEach(path -> assertTrue(path + " should exist", Files.exists(path)));
    }

    public static Matcher<Path> fileWithGlobExist(String glob) throws IOException {
        return new FeatureMatcher<Path,Iterable<Path>>(not(emptyIterable()),"File with pattern exist", "file with pattern"){

            @Override
            protected Iterable<Path> featureValueOf(Path actual) {
                try {
                    return Files.newDirectoryStream(actual,glob);
                } catch (IOException e) {
                    return Collections.emptyList();
                }
            }
        };
    }

    public static void assertPathsDontExist(Path... paths) {
        Arrays.stream(paths).forEach(path -> assertFalse(path + " should not exist", Files.exists(path)));
    }
}
