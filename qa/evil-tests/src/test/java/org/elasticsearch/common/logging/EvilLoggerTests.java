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

package org.elasticsearch.common.logging;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.CountingNoOpAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.elasticsearch.cli.UserException;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.common.io.PathUtils;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.node.Node;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.hamcrest.RegexMatcher;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.startsWith;

public class EvilLoggerTests extends ESTestCase {

    @Override
    public void setUp() throws Exception {
        super.setUp();
        LogConfigurator.registerErrorListener();
    }

    @Override
    public void tearDown() throws Exception {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configurator.shutdown(context);
        super.tearDown();
    }

    public void testLocationInfoTest() throws IOException, UserException {
        setupLogging("location_info");

        final Logger testLogger = ESLoggerFactory.getLogger("test");

        testLogger.error("This is an error message");
        testLogger.warn("This is a warning message");
        testLogger.info("This is an info message");
        testLogger.debug("This is a debug message");
        testLogger.trace("This is a trace message");
        final String path =
            System.getProperty("es.logs.base_path") +
                System.getProperty("file.separator") +
                System.getProperty("es.logs.cluster_name") +
                ".log";
        final List<String> events = Files.readAllLines(PathUtils.get(path));
        assertThat(events.size(), equalTo(5));
        final String location = "org.elasticsearch.common.logging.EvilLoggerTests.testLocationInfoTest";
        // the first message is a warning for unsupported configuration files
        assertLogLine(events.get(0), Level.ERROR, location, "This is an error message");
        assertLogLine(events.get(1), Level.WARN, location, "This is a warning message");
        assertLogLine(events.get(2), Level.INFO, location, "This is an info message");
        assertLogLine(events.get(3), Level.DEBUG, location, "This is a debug message");
        assertLogLine(events.get(4), Level.TRACE, location, "This is a trace message");
    }

    public void testDeprecationLogger() throws IOException, UserException {
        setupLogging("deprecation");

        final DeprecationLogger deprecationLogger =
                new DeprecationLogger(ESLoggerFactory.getLogger("deprecation"));

        final int deprecatedIterations = randomIntBetween(0, 256);
        for (int i = 0; i < deprecatedIterations; i++) {
            deprecationLogger.deprecated("This is a deprecation message");
            assertWarnings("This is a deprecation message");
        }

        final String deprecationPath =
            System.getProperty("es.logs.base_path") +
                System.getProperty("file.separator") +
                System.getProperty("es.logs.cluster_name") +
                "_deprecation.log";
        final List<String> deprecationEvents = Files.readAllLines(PathUtils.get(deprecationPath));
        assertThat(deprecationEvents.size(), equalTo(deprecatedIterations));
        for (int i = 0; i < deprecatedIterations; i++) {
            assertLogLine(
                    deprecationEvents.get(i),
                    Level.WARN,
                    "org.elasticsearch.common.logging.DeprecationLogger.deprecated",
                    "This is a deprecation message");
        }
    }

    public void testDeprecationLoggerMaybeLog() throws IOException, UserException {
        setupLogging("deprecation");

        final DeprecationLogger deprecationLogger =
                new DeprecationLogger(ESLoggerFactory.getLogger("deprecation"));

        final int iterations = randomIntBetween(1, 16);

        for (int i = 0; i < iterations; i++) {
            deprecationLogger.deprecatedAndMaybeLog("key", "This is a maybe logged deprecation message");
            assertWarnings("This is a maybe logged deprecation message");
        }
        for (int k = 0; k < 128; k++) {
            for (int i = 0; i < iterations; i++) {
                deprecationLogger.deprecatedAndMaybeLog("key" + k, "This is a maybe logged deprecation message" + k);
                assertWarnings("This is a maybe logged deprecation message" + k);
            }
        }
        for (int i = 0; i < iterations; i++) {
            deprecationLogger.deprecatedAndMaybeLog("key", "This is a maybe logged deprecation message");
            assertWarnings("This is a maybe logged deprecation message");
        }

        final String deprecationPath =
                System.getProperty("es.logs.base_path") +
                        System.getProperty("file.separator") +
                        System.getProperty("es.logs.cluster_name") +
                        "_deprecation.log";
        final List<String> deprecationEvents = Files.readAllLines(PathUtils.get(deprecationPath));
        assertThat(deprecationEvents.size(), equalTo(1 + 128 + 1));
        assertLogLine(
                deprecationEvents.get(0),
                Level.WARN,
                "org.elasticsearch.common.logging.DeprecationLogger.deprecated",
                "This is a maybe logged deprecation message");
        for (int k = 0; k < 128; k++) {
            assertLogLine(
                    deprecationEvents.get(1 + k),
                    Level.WARN,
                    "org.elasticsearch.common.logging.DeprecationLogger.deprecated",
                    "This is a maybe logged deprecation message" + k);
        }
    }

    public void testDeprecatedSettings() throws IOException, UserException {
        setupLogging("settings");

        final Setting setting = Setting.boolSetting("deprecated.foo", false, Setting.Property.Deprecated);
        final Settings settings = Settings.builder().put("deprecated.foo", true).build();

        final int iterations = randomIntBetween(0, 128);
        for (int i = 0; i < iterations; i++) {
            setting.get(settings);
            assertSettingDeprecationsAndWarnings(new Setting[]{setting});
        }

        final String deprecationPath =
                System.getProperty("es.logs.base_path") +
                        System.getProperty("file.separator") +
                        System.getProperty("es.logs.cluster_name") +
                        "_deprecation.log";
        final List<String> deprecationEvents = Files.readAllLines(PathUtils.get(deprecationPath));
        assertThat(deprecationEvents.size(), equalTo(1));
        assertLogLine(
                deprecationEvents.get(0),
                Level.WARN,
                "org.elasticsearch.common.logging.DeprecationLogger.deprecated",
                "\\[deprecated.foo\\] setting was deprecated in Elasticsearch and will be removed in a future release! " +
                        "See the breaking changes documentation for the next major version.");
    }

    public void testFindAppender() throws IOException, UserException {
        setupLogging("find_appender");

        final Logger hasConsoleAppender = ESLoggerFactory.getLogger("has_console_appender");

        final Appender testLoggerConsoleAppender = Loggers.findAppender(hasConsoleAppender, ConsoleAppender.class);
        assertNotNull(testLoggerConsoleAppender);
        assertThat(testLoggerConsoleAppender.getName(), equalTo("console"));
        final Logger hasCountingNoOpAppender = ESLoggerFactory.getLogger("has_counting_no_op_appender");
        assertNull(Loggers.findAppender(hasCountingNoOpAppender, ConsoleAppender.class));
        final Appender countingNoOpAppender = Loggers.findAppender(hasCountingNoOpAppender, CountingNoOpAppender.class);
        assertThat(countingNoOpAppender.getName(), equalTo("counting_no_op"));
    }

    public void testPrefixLogger() throws IOException, IllegalAccessException, UserException {
        setupLogging("prefix");

        final String prefix = randomBoolean() ? null : randomAlphaOfLength(16);
        final Logger logger = Loggers.getLogger("prefix", prefix);
        logger.info("test");
        logger.info("{}", "test");
        final Exception e = new Exception("exception");
        logger.info(new ParameterizedMessage("{}", "test"), e);

        final String path =
            System.getProperty("es.logs.base_path") +
                System.getProperty("file.separator") +
                System.getProperty("es.logs.cluster_name") +
                ".log";
        final List<String> events = Files.readAllLines(PathUtils.get(path));

        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        final int stackTraceLength = sw.toString().split(System.getProperty("line.separator")).length;
        final int expectedLogLines = 3;
        assertThat(events.size(), equalTo(expectedLogLines + stackTraceLength));
        for (int i = 0; i < expectedLogLines; i++) {
            if (prefix == null) {
                assertThat(events.get(i), startsWith("test"));
            } else {
                assertThat(events.get(i), startsWith("[" + prefix + "] test"));
            }
        }
    }

    public void testPrefixLoggerMarkersCanBeCollected() throws IOException, UserException {
        setupLogging("prefix");

        final int prefixes = 1 << 19; // to ensure enough markers that the GC should collect some when we force a GC below
        for (int i = 0; i < prefixes; i++) {
            Loggers.getLogger("prefix" + i, "prefix" + i); // this has the side effect of caching a marker with this prefix

        }

        System.gc(); // this will free the weakly referenced keys in the marker cache
        assertThat(PrefixLogger.markersSize(), lessThan(prefixes));
    }

    public void testProperties() throws IOException, UserException {
        final Settings.Builder builder = Settings.builder().put("cluster.name", randomAlphaOfLength(16));
        if (randomBoolean()) {
            builder.put("node.name", randomAlphaOfLength(16));
        }
        final Settings settings = builder.build();
        setupLogging("minimal", settings);

        assertNotNull(System.getProperty("es.logs.base_path"));

        assertThat(System.getProperty("es.logs.cluster_name"), equalTo(ClusterName.CLUSTER_NAME_SETTING.get(settings).value()));
        if (Node.NODE_NAME_SETTING.exists(settings)) {
            assertThat(System.getProperty("es.logs.node_name"), equalTo(Node.NODE_NAME_SETTING.get(settings)));
        } else {
            assertNull(System.getProperty("es.logs.node_name"));
        }
    }

    private void setupLogging(final String config) throws IOException, UserException {
        setupLogging(config, Settings.EMPTY);
    }

    private void setupLogging(final String config, final Settings settings) throws IOException, UserException {
        assert !Environment.PATH_HOME_SETTING.exists(settings);
        final Path configDir = getDataPath(config);
        final Settings mergedSettings = Settings.builder()
            .put(settings)
            .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toString())
            .build();
        // need to use custom config path so we can use a custom log4j2.properties file for the test
        final Environment environment = new Environment(mergedSettings, configDir);
        LogConfigurator.configure(environment);
    }

    private void assertLogLine(final String logLine, final Level level, final String location, final String message) {
        final Matcher matcher = Pattern.compile("\\[(.*)\\]\\[(.*)\\(.*\\)\\] (.*)").matcher(logLine);
        assertTrue(logLine, matcher.matches());
        assertThat(matcher.group(1), equalTo(level.toString()));
        assertThat(matcher.group(2), RegexMatcher.matches(location));
        assertThat(matcher.group(3), RegexMatcher.matches(message));
    }

}
