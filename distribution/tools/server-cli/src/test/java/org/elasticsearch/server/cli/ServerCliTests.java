/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.server.cli;

import joptsimple.OptionSet;

import org.elasticsearch.Build;
import org.elasticsearch.bootstrap.ServerArgs;
import org.elasticsearch.cli.Command;
import org.elasticsearch.cli.CommandTestCase;
import org.elasticsearch.cli.ExitCodes;
import org.elasticsearch.cli.ProcessInfo;
import org.elasticsearch.cli.Terminal;
import org.elasticsearch.cli.UserException;
import org.elasticsearch.common.cli.EnvironmentAwareCommand;
import org.elasticsearch.common.io.stream.InputStreamStreamInput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.IOUtils;
import org.elasticsearch.env.Environment;
import org.elasticsearch.monitor.jvm.JvmInfo;
import org.hamcrest.Matcher;
import org.junit.Before;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.hasItem;

public class ServerCliTests extends CommandTestCase {

    Path esConfigDir;

    @Before
    public void setupDummyInstallation() throws IOException {
        sysprops.put("java.home", "/javahome");
        esConfigDir = esHomeDir.resolve("config");
        Files.createDirectories(esConfigDir);
        Files.writeString(esConfigDir.resolve("jvm.options"), "");
    }

    private void assertOk(String... args) throws Exception {
        assertOkWithOutput(emptyString(), args);
    }

    private void assertOkWithOutput(Matcher<String> matcher, String... args) throws Exception {
        terminal.reset();
        int status = executeMain(args);
        assertThat(status, equalTo(ExitCodes.OK));
        assertThat(terminal.getErrorOutput(), emptyString());
        assertThat(terminal.getOutput(), matcher);
    }

    private void assertUsage(Matcher<String> matcher, String... args) throws Exception {
        terminal.reset();
        mainCallback = FAIL_MAIN;
        int status = executeMain(args);
        assertThat(status, equalTo(ExitCodes.USAGE));
        assertThat(terminal.getErrorOutput(), matcher);
    }

    private void assertMutuallyExclusiveOptions(String... args) throws Exception {
        assertUsage(allOf(containsString("ERROR:"), containsString("are unavailable given other options on the command line")), args);
    }

    public void testVersion() throws Exception {
        assertMutuallyExclusiveOptions("-V", "-d");
        assertMutuallyExclusiveOptions("-V", "--daemonize");
        assertMutuallyExclusiveOptions("-V", "-p", "/tmp/pid");
        assertMutuallyExclusiveOptions("-V", "--pidfile", "/tmp/pid");
        assertMutuallyExclusiveOptions("--version", "-d");
        assertMutuallyExclusiveOptions("--version", "--daemonize");
        assertMutuallyExclusiveOptions("--version", "-p", "/tmp/pid");
        assertMutuallyExclusiveOptions("--version", "--pidfile", "/tmp/pid");
        assertMutuallyExclusiveOptions("--version", "-q");
        assertMutuallyExclusiveOptions("--version", "--quiet");

        final String expectedBuildOutput = String.format(
            Locale.ROOT,
            "Build: %s/%s/%s",
            Build.CURRENT.type().displayName(),
            Build.CURRENT.hash(),
            Build.CURRENT.date()
        );
        Matcher<String> versionOutput = allOf(
            containsString("Version: " + Build.CURRENT.qualifiedVersion()),
            containsString(expectedBuildOutput),
            containsString("JVM: " + JvmInfo.jvmInfo().version())
        );
        assertOkWithOutput(versionOutput, "-V");
        assertOkWithOutput(versionOutput, "--version");
    }

    public void testPositionalArgs() throws Exception {
        String prefix = "Positional arguments not allowed, found ";
        assertUsage(containsString(prefix + "[foo]"), "foo");
        assertUsage(containsString(prefix + "[foo, bar]"), "foo", "bar");
        assertUsage(containsString(prefix + "[foo]"), "-E", "foo=bar", "foo", "-E", "baz=qux");
    }

    public void testPidFile() throws Exception {
        Path tmpDir = createTempDir();
        Path pidFileArg = tmpDir.resolve("pid");
        assertUsage(containsString("Option p/pidfile requires an argument"), "-p");
        mainCallback = (args, stdout, stderr, exitCode) -> { assertThat(args.pidFile().toString(), equalTo(pidFileArg.toString())); };
        terminal.reset();
        assertOk("-p", pidFileArg.toString());
        terminal.reset();
        assertOk("--pidfile", pidFileArg.toString());
    }

    public void testDaemonize() throws Exception {
        AtomicBoolean expectDaemonize = new AtomicBoolean(true);
        mainCallback = (args, stdout, stderr, exitCode) -> assertThat(args.daemonize(), equalTo(expectDaemonize.get()));
        assertOk("-d");
        assertOk("--daemonize");
        expectDaemonize.set(false);
        assertOk();
    }

    public void testQuiet() throws Exception {
        AtomicBoolean expectQuiet = new AtomicBoolean(true);
        mainCallback = (args, stdout, stderr, exitCode) -> assertThat(args.quiet(), equalTo(expectQuiet.get()));
        assertOk("-q");
        assertOk("--quiet");
        expectQuiet.set(false);
        assertOk();
    }

    public void testElasticsearchSettings() throws Exception {
        mainCallback = (args, stdout, stderr, exitCode) -> {
            Settings settings = args.nodeSettings();
            assertThat(settings.get("foo"), equalTo("bar"));
            assertThat(settings.get("baz"), equalTo("qux"));
        };
        assertOk("-Efoo=bar", "-E", "baz=qux");
    }

    public void testElasticsearchSettingCanNotBeEmpty() throws Exception {
        assertUsage(containsString("setting [foo] must not be empty"), "-E", "foo=");
    }

    public void testElasticsearchSettingCanNotBeDuplicated() throws Exception {
        assertUsage(containsString("setting [foo] already set, saw [bar] and [baz]"), "-E", "foo=bar", "-E", "foo=baz");
    }

    public void testUnknownOption() throws Exception {
        assertUsage(containsString("network.host is not a recognized option"), "--network.host");
    }

    public void testPathHome() throws Exception {
        AtomicReference<String> expectedHomeDir = new AtomicReference<>();
        expectedHomeDir.set(esHomeDir.toString());
        mainCallback = (args, stdout, stderr, exitCode) -> {
            Settings settings = args.nodeSettings();
            assertThat(settings.get("path.home"), equalTo(expectedHomeDir.get()));
            assertThat(settings.keySet(), hasItem("path.logs")); // added by env initialization
        };
        assertOk();
        sysprops.remove("es.path.home");
        final String commandLineValue = createTempDir().toString();
        expectedHomeDir.set(commandLineValue);
        assertOk("-Epath.home=" + commandLineValue);
    }

    public void testMissingEnrollmentToken() throws Exception {
        assertUsage(containsString("Option enrollment-token requires an argument"), "--enrollment-token");
    }

    public void testMultipleEnrollmentTokens() throws Exception {
        assertUsage(
            containsString("Multiple --enrollment-token parameters are not allowed"),
            "--enrollment-token",
            "some-token",
            "--enrollment-token",
            "some-other-token"
        );
    }

    public void testAutoConfigEnrollment() throws Exception {
        autoConfigCallback = (t, options, env, processInfo) -> {
            assertThat(options.valueOf("enrollment-token"), equalTo("mydummytoken"));
        };
        assertOk("--enrollment-token", "mydummytoken");
    }

    public void testAutoConfig() throws Exception {
        autoConfigCallback = (t, options, env, processInfo) -> {
            t.println("message from auto config");
        };
        assertOkWithOutput(containsString("message from auto config"));
    }

    public void testAutoConfigErrorPropagated() throws Exception {
        autoConfigCallback = (t, options, env, processInfo) -> {
            throw new UserException(ExitCodes.IO_ERROR, "error from auto config");
        };
        var e = expectThrows(UserException.class, () -> execute());
        assertThat(e.exitCode, equalTo(ExitCodes.IO_ERROR));
        assertThat(e.getMessage(), equalTo("error from auto config"));
    }

    public void assertAutoConfigOkError(int exitCode) throws Exception {
        autoConfigCallback = (t, options, env, processInfo) -> {
            throw new UserException(exitCode, "ok error from auto config");
        };
        int mainExitCode = executeMain();
        assertThat(mainExitCode, equalTo(ExitCodes.OK));
        assertThat(terminal.getErrorOutput(), containsString("ok error from auto config"));
    }

    public void testAutoConfigOkErrors() throws Exception {
        assertAutoConfigOkError(ExitCodes.CANT_CREATE);
        assertAutoConfigOkError(ExitCodes.CONFIG);
        assertAutoConfigOkError(ExitCodes.NOOP);
    }

    interface MainMethod {
        void main(ServerArgs args, OutputStream stdout, OutputStream stderr, AtomicInteger exitCode);
    }

    interface AutoConfigMethod {
        void autoconfig(Terminal terminal, OptionSet options, Environment env, ProcessInfo processInfo) throws UserException;
    }

    MainMethod mainCallback;
    final MainMethod FAIL_MAIN = (args, stdout, stderr, exitCode) -> fail("Did not expect to run init");

    AutoConfigMethod autoConfigCallback;
    private final MockAutoConfigCli AUTO_CONFIG_CLI = new MockAutoConfigCli();

    @Before
    public void resetCommand() {
        mainCallback = null;
        autoConfigCallback = null;
    }

    private class MockAutoConfigCli extends EnvironmentAwareCommand {
        MockAutoConfigCli() {
            super("mock auto config tool");
        }

        @Override
        protected void execute(Terminal terminal, OptionSet options, ProcessInfo processInfo) throws Exception {
            fail("Called wrong execute method, must call the one that takes already parsed env");
        }

        @Override
        public void execute(Terminal terminal, OptionSet options, Environment env, ProcessInfo processInfo) throws Exception {
            // TODO: fake errors, check password from terminal, allow tests to make elasticsearch.yml change
            if (autoConfigCallback != null) {
                autoConfigCallback.autoconfig(terminal, options, env, processInfo);
            }
        }
    }

    // a "process" that just exits
    private class NoopProcess extends Process {
        private final OutputStream processStdin = OutputStream.nullOutputStream();
        private final InputStream processStdout = InputStream.nullInputStream();
        private final InputStream processStderr = InputStream.nullInputStream();

        @Override
        public OutputStream getOutputStream() {
            return processStdin;
        }

        @Override
        public InputStream getInputStream() {
            return processStdout;
        }

        @Override
        public InputStream getErrorStream() {
            return processStderr;
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            fail("Tried to kill ES process");
        }
    }

    // a "process" that is really another thread
    private class MockElasticsearchProcess extends Process {
        private final PipedOutputStream processStdin = new PipedOutputStream();
        private final PipedInputStream processStdout = new PipedInputStream();
        private final PipedInputStream processStderr = new PipedInputStream();
        private final PipedInputStream stdin = new PipedInputStream();
        private final PipedOutputStream stdout = new PipedOutputStream();
        private final PipedOutputStream stderr = new PipedOutputStream();

        private final AtomicInteger exitCode = new AtomicInteger();
        private final AtomicReference<IOException> argsParsingException = new AtomicReference<>();
        private final Thread thread = new Thread(() -> {
            try (var in = new InputStreamStreamInput(stdin)) {
                final ServerArgs serverArgs = new ServerArgs(in);

                mainCallback.main(serverArgs, stdout, stderr, exitCode);
            } catch (IOException e) {
                argsParsingException.set(e);
            }
            IOUtils.closeWhileHandlingException(stdin, stdout, stderr);
        }, "mock Elasticsearch process");

        MockElasticsearchProcess() throws IOException {
            stdin.connect(processStdin);
            stdout.connect(processStdout);
            stderr.connect(processStderr);
            thread.start();
        }

        @Override
        public OutputStream getOutputStream() {
            return processStdin;
        }

        @Override
        public InputStream getInputStream() {
            return processStdout;
        }

        @Override
        public InputStream getErrorStream() {
            return processStderr;
        }

        @Override
        public int waitFor() throws InterruptedException {
            thread.join();
            if (argsParsingException.get() != null) {
                throw new AssertionError("Reading server args failed", argsParsingException.get());
            }
            return exitCode.get();
        }

        @Override
        public int exitValue() {
            if (thread.isAlive()) {
                throw new IllegalThreadStateException(); // match spec
            }
            return exitCode.get();
        }

        @Override
        public void destroy() {
            fail("Tried to kill ES process");
        }
    }

    @Override
    protected Command newCommand() {
        return new ServerCli() {

            @Override
            protected List<String> getJvmOptions(Path configDir, Path pluginsDir, Path tmpDir, String envOptions) throws Exception {
                return new ArrayList<>();
            }

            @Override
            protected Process startProcess(ProcessBuilder processBuilder) throws IOException {
                // TODO: validate processbuilder stuff
                if (mainCallback == null) {
                    return new NoopProcess();
                }
                return new MockElasticsearchProcess();
            }

            @Override
            protected Command loadTool(String toolname, String libs) {
                assertThat(toolname, equalTo("auto-configure-node"));
                assertThat(libs, equalTo("modules/x-pack-core,modules/x-pack-security,lib/tools/security-cli"));
                return AUTO_CONFIG_CLI;
            }

            @Override
            public boolean addShutdownHook() {
                return false;
            }
        };
    }

}
