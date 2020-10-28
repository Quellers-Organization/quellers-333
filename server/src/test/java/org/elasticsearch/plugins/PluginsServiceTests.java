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

package org.elasticsearch.plugins;

import org.apache.log4j.Level;
import org.apache.lucene.util.Constants;
import org.apache.lucene.util.LuceneTestCase;
import org.elasticsearch.Version;
import org.elasticsearch.bootstrap.JarHell;
import org.elasticsearch.common.io.PathUtils;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.TestEnvironment;
import org.elasticsearch.index.IndexModule;
import org.elasticsearch.test.ESTestCase;
import org.hamcrest.Matchers;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasToString;
import static org.hamcrest.Matchers.instanceOf;

@LuceneTestCase.SuppressFileSystems(value = "ExtrasFS")
public class PluginsServiceTests extends ESTestCase {
    public static class AdditionalSettingsPlugin1 extends Plugin {
        @Override
        public Settings additionalSettings() {
            return Settings.builder()
                .put("foo.bar", "1")
                .put(IndexModule.INDEX_STORE_TYPE_SETTING.getKey(), IndexModule.Type.MMAPFS.getSettingsKey())
                .build();
        }
    }
    public static class AdditionalSettingsPlugin2 extends Plugin {
        @Override
        public Settings additionalSettings() {
            return Settings.builder().put("foo.bar", "2").build();
        }
    }

    public static class FilterablePlugin extends Plugin implements ScriptPlugin {}

    static PluginsService newPluginsService(Settings settings, Class<? extends Plugin>... classpathPlugins) {
        return new PluginsService(
            settings, null, null,
            TestEnvironment.newEnvironment(settings).pluginsFile(), Arrays.asList(classpathPlugins)
        );
    }

    public void testAdditionalSettings() {
        Settings settings = Settings.builder()
            .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir())
            .put("my.setting", "test")
            .put(IndexModule.INDEX_STORE_TYPE_SETTING.getKey(), IndexModule.Type.SIMPLEFS.getSettingsKey()).build();
        PluginsService service = newPluginsService(settings, AdditionalSettingsPlugin1.class);
        Settings newSettings = service.updatedSettings();
        assertEquals("test", newSettings.get("my.setting")); // previous settings still exist
        assertEquals("1", newSettings.get("foo.bar")); // added setting exists
        assertEquals(
            IndexModule.Type.SIMPLEFS.getSettingsKey(),
            newSettings.get(IndexModule.INDEX_STORE_TYPE_SETTING.getKey())
        ); // does not override pre existing settings
    }

    public void testAdditionalSettingsClash() {
        Settings settings = Settings.builder()
            .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir()).build();
        PluginsService service = newPluginsService(settings, AdditionalSettingsPlugin1.class, AdditionalSettingsPlugin2.class);
        try {
            service.updatedSettings();
            fail("Expected exception when building updated settings");
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            assertTrue(msg, msg.contains("Cannot have additional setting [foo.bar]"));
            assertTrue(msg, msg.contains("plugin [" + AdditionalSettingsPlugin1.class.getName()));
            assertTrue(msg, msg.contains("plugin [" + AdditionalSettingsPlugin2.class.getName()));
        }
    }

    public void testExistingPluginMissingDescriptor() throws Exception {
        Path pluginsDir = createTempDir();
        Files.createDirectory(pluginsDir.resolve("plugin-missing-descriptor"));
        IllegalStateException e = expectThrows(IllegalStateException.class, () -> PluginsService.getPluginBundles(pluginsDir));
        assertThat(e.getMessage(),
                   containsString("Could not load plugin descriptor for plugin directory [plugin-missing-descriptor]"));
    }

    public void testFilterPlugins() {
        Settings settings = Settings.builder()
            .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir())
            .put("my.setting", "test")
            .put(IndexModule.INDEX_STORE_TYPE_SETTING.getKey(), IndexModule.Type.SIMPLEFS.getSettingsKey()).build();
        PluginsService service = newPluginsService(settings, AdditionalSettingsPlugin1.class, FilterablePlugin.class);
        List<ScriptPlugin> scriptPlugins = service.filterPlugins(ScriptPlugin.class);
        assertEquals(1, scriptPlugins.size());
        assertEquals(FilterablePlugin.class, scriptPlugins.get(0).getClass());
    }

    public void testHiddenFiles() throws IOException {
        final Path home = createTempDir();
        final Settings settings =
                Settings.builder()
                        .put(Environment.PATH_HOME_SETTING.getKey(), home)
                        .build();
        final Path hidden = home.resolve("plugins").resolve(".hidden");
        Files.createDirectories(hidden);
        @SuppressWarnings("unchecked")
        final IllegalStateException e = expectThrows(
                IllegalStateException.class,
                () -> newPluginsService(settings));

        final String expected = "Could not load plugin descriptor for plugin directory [.hidden]";
        assertThat(e, hasToString(containsString(expected)));
    }

    public void testDesktopServicesStoreFiles() throws IOException {
        final Path home = createTempDir();
        final Settings settings =
                Settings.builder()
                        .put(Environment.PATH_HOME_SETTING.getKey(), home)
                        .build();
        final Path plugins = home.resolve("plugins");
        Files.createDirectories(plugins);
        final Path desktopServicesStore = plugins.resolve(".DS_Store");
        Files.createFile(desktopServicesStore);
        if (Constants.MAC_OS_X) {
            @SuppressWarnings("unchecked") final PluginsService pluginsService = newPluginsService(settings);
            assertNotNull(pluginsService);
        } else {
            final IllegalStateException e = expectThrows(IllegalStateException.class, () -> newPluginsService(settings));
            assertThat(e.getMessage(), containsString("Could not load plugin descriptor for plugin directory [.DS_Store]"));
            assertNotNull(e.getCause());
            assertThat(e.getCause(), instanceOf(FileSystemException.class));
            if (Constants.WINDOWS) {
                assertThat(e.getCause(), instanceOf(NoSuchFileException.class));
            } else {
                assertThat(e.getCause(), hasToString(containsString("Not a directory")));
            }
        }
    }

    public void testStartupWithRemovingMarker() throws IOException {
        final Path home = createTempDir();
        final Settings settings =
                Settings.builder()
                        .put(Environment.PATH_HOME_SETTING.getKey(), home)
                        .build();
        final Path fake = home.resolve("plugins").resolve("fake");
        Files.createDirectories(fake);
        Files.createFile(fake.resolve("plugin.jar"));
        final Path removing = home.resolve("plugins").resolve(".removing-fake");
        Files.createFile(removing);
        PluginTestUtil.writePluginProperties(
                fake,
                "description", "fake",
                "name", "fake",
                "version", "1.0.0",
                "elasticsearch.version", Version.CURRENT.toString(),
                "java.version", System.getProperty("java.specification.version"),
                "classname", "Fake",
                "has.native.controller", "false");
        final IllegalStateException e = expectThrows(IllegalStateException.class, () -> newPluginsService(settings));
        final String expected = String.format(
                Locale.ROOT,
                "found file [%s] from a failed attempt to remove the plugin [fake]; execute [elasticsearch-plugin remove fake]",
                removing);
        assertThat(e, hasToString(containsString(expected)));
    }

    public void testLoadPluginWithNoPublicConstructor() {
        class NoPublicConstructorPlugin extends Plugin {

            private NoPublicConstructorPlugin() {

            }

        }

        final Path home = createTempDir();
        final Settings settings = Settings.builder().put(Environment.PATH_HOME_SETTING.getKey(), home).build();
        final IllegalStateException e =
                expectThrows(IllegalStateException.class, () -> newPluginsService(settings, NoPublicConstructorPlugin.class));
        assertThat(e, hasToString(containsString("no public constructor")));
    }

    public void testLoadPluginWithMultiplePublicConstructors() {
        class MultiplePublicConstructorsPlugin extends Plugin {

            @SuppressWarnings("unused")
            public MultiplePublicConstructorsPlugin() {

            }

            @SuppressWarnings("unused")
            public MultiplePublicConstructorsPlugin(final Settings settings) {

            }

        }

        final Path home = createTempDir();
        final Settings settings = Settings.builder().put(Environment.PATH_HOME_SETTING.getKey(), home).build();
        final IllegalStateException e = expectThrows(
            IllegalStateException.class,
            () -> newPluginsService(settings, MultiplePublicConstructorsPlugin.class)
        );
        assertThat(e, hasToString(containsString("no unique public constructor")));
    }

    public void testLoadPluginWithNoPublicConstructorOfCorrectSignature() {
        class TooManyParametersPlugin extends Plugin {

            @SuppressWarnings("unused")
            public TooManyParametersPlugin(Settings settings, Path configPath, Object object) {

            }

        }

        class TwoParametersFirstIncorrectType extends Plugin {

            @SuppressWarnings("unused")
            public TwoParametersFirstIncorrectType(Object object, Path configPath) {

            }
        }

        class TwoParametersSecondIncorrectType extends Plugin {

            @SuppressWarnings("unused")
            public TwoParametersSecondIncorrectType(Settings settings, Object object) {

            }

        }

        class OneParameterIncorrectType extends Plugin {

            @SuppressWarnings("unused")
            public OneParameterIncorrectType(Object object) {

            }
        }

        final Collection<Class<? extends Plugin>> classes = Arrays.asList(
                TooManyParametersPlugin.class,
                TwoParametersFirstIncorrectType.class,
                TwoParametersSecondIncorrectType.class,
                OneParameterIncorrectType.class);
        for (Class<? extends Plugin> pluginClass : classes) {
            final Path home = createTempDir();
            final Settings settings = Settings.builder().put(Environment.PATH_HOME_SETTING.getKey(), home).build();
            final IllegalStateException e = expectThrows(IllegalStateException.class, () -> newPluginsService(settings, pluginClass));
            assertThat(e, hasToString(containsString("no public constructor of correct signature")));
        }
    }

    public void testSortBundlesCycleSelfReference() throws Exception {
        Path pluginDir = createTempDir();
        PluginInfo info = new PluginInfo("foo", "desc", "1.0", Version.CURRENT, "1.8",
            "MyPlugin", Collections.singletonList("foo"), false, PluginType.ISOLATED, "");
        PluginsService.Bundle bundle = new PluginsService.Bundle(info, pluginDir);
        IllegalStateException e = expectThrows(IllegalStateException.class, () ->
            PluginsService.sortBundles(Collections.singleton(bundle))
        );
        assertEquals("Cycle found in plugin dependencies: foo -> foo", e.getMessage());
    }

    public void testSortBundlesCycle() throws Exception {
        Path pluginDir = createTempDir();
        Set<PluginsService.Bundle> bundles = new LinkedHashSet<>(); // control iteration order, so we get know the beginning of the cycle
        PluginInfo info = new PluginInfo("foo", "desc", "1.0", Version.CURRENT, "1.8",
            "MyPlugin", Arrays.asList("bar", "other"), false, PluginType.ISOLATED, "");
        bundles.add(new PluginsService.Bundle(info, pluginDir));
        PluginInfo info2 = new PluginInfo("bar", "desc", "1.0", Version.CURRENT, "1.8",
            "MyPlugin", Collections.singletonList("baz"), false, PluginType.ISOLATED, "");
        bundles.add(new PluginsService.Bundle(info2, pluginDir));
        PluginInfo info3 = new PluginInfo("baz", "desc", "1.0", Version.CURRENT, "1.8",
            "MyPlugin", Collections.singletonList("foo"), false, PluginType.ISOLATED, "");
        bundles.add(new PluginsService.Bundle(info3, pluginDir));
        PluginInfo info4 = new PluginInfo("other", "desc", "1.0", Version.CURRENT, "1.8",
            "MyPlugin", Collections.emptyList(), false, PluginType.ISOLATED, "");
        bundles.add(new PluginsService.Bundle(info4, pluginDir));

        IllegalStateException e = expectThrows(IllegalStateException.class, () -> PluginsService.sortBundles(bundles));
        assertEquals("Cycle found in plugin dependencies: foo -> bar -> baz -> foo", e.getMessage());
    }

    public void testSortBundlesSingle() throws Exception {
        Path pluginDir = createTempDir();
        PluginInfo info = new PluginInfo("foo", "desc", "1.0", Version.CURRENT, "1.8",
            "MyPlugin", Collections.emptyList(), false, PluginType.ISOLATED, "");
        PluginsService.Bundle bundle = new PluginsService.Bundle(info, pluginDir);
        List<PluginsService.Bundle> sortedBundles = PluginsService.sortBundles(Collections.singleton(bundle));
        assertThat(sortedBundles, Matchers.contains(bundle));
    }

    public void testSortBundlesNoDeps() throws Exception {
        Path pluginDir = createTempDir();
        Set<PluginsService.Bundle> bundles = new LinkedHashSet<>(); // control iteration order
        PluginInfo info1 = new PluginInfo("foo", "desc", "1.0", Version.CURRENT, "1.8",
            "MyPlugin", Collections.emptyList(), false, PluginType.ISOLATED, "");
        PluginsService.Bundle bundle1 = new PluginsService.Bundle(info1, pluginDir);
        bundles.add(bundle1);
        PluginInfo info2 = new PluginInfo("bar", "desc", "1.0", Version.CURRENT, "1.8",
            "MyPlugin", Collections.emptyList(), false, PluginType.ISOLATED, "");
        PluginsService.Bundle bundle2 = new PluginsService.Bundle(info2, pluginDir);
        bundles.add(bundle2);
        PluginInfo info3 = new PluginInfo("baz", "desc", "1.0", Version.CURRENT, "1.8",
            "MyPlugin", Collections.emptyList(), false, PluginType.ISOLATED, "");
        PluginsService.Bundle bundle3 = new PluginsService.Bundle(info3, pluginDir);
        bundles.add(bundle3);
        List<PluginsService.Bundle> sortedBundles = PluginsService.sortBundles(bundles);
        assertThat(sortedBundles, Matchers.contains(bundle1, bundle2, bundle3));
    }

    public void testSortBundlesMissingDep() throws Exception {
        Path pluginDir = createTempDir();
        PluginInfo info = new PluginInfo("foo", "desc", "1.0", Version.CURRENT, "1.8",
            "MyPlugin", Collections.singletonList("dne"), false, PluginType.ISOLATED, "");
        PluginsService.Bundle bundle = new PluginsService.Bundle(info, pluginDir);
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () ->
            PluginsService.sortBundles(Collections.singleton(bundle))
        );
        assertEquals("Missing plugin [dne], dependency of [foo]", e.getMessage());
    }

    public void testSortBundlesCommonDep() throws Exception {
        Path pluginDir = createTempDir();
        Set<PluginsService.Bundle> bundles = new LinkedHashSet<>(); // control iteration order
        PluginInfo info1 = new PluginInfo("grandparent", "desc", "1.0",Version.CURRENT, "1.8",
            "MyPlugin", Collections.emptyList(), false, PluginType.ISOLATED, "");
        PluginsService.Bundle bundle1 = new PluginsService.Bundle(info1, pluginDir);
        bundles.add(bundle1);
        PluginInfo info2 = new PluginInfo("foo", "desc", "1.0", Version.CURRENT, "1.8",
            "MyPlugin", Collections.singletonList("common"), false, PluginType.ISOLATED, "");
        PluginsService.Bundle bundle2 = new PluginsService.Bundle(info2, pluginDir);
        bundles.add(bundle2);
        PluginInfo info3 = new PluginInfo("bar", "desc", "1.0", Version.CURRENT, "1.8",
            "MyPlugin", Collections.singletonList("common"), false, PluginType.ISOLATED, "");
        PluginsService.Bundle bundle3 = new PluginsService.Bundle(info3, pluginDir);
        bundles.add(bundle3);
        PluginInfo info4 = new PluginInfo("common", "desc", "1.0", Version.CURRENT, "1.8",
            "MyPlugin", Collections.singletonList("grandparent"), false, PluginType.ISOLATED, "");
        PluginsService.Bundle bundle4 = new PluginsService.Bundle(info4, pluginDir);
        bundles.add(bundle4);
        List<PluginsService.Bundle> sortedBundles = PluginsService.sortBundles(bundles);
        assertThat(sortedBundles, Matchers.contains(bundle1, bundle4, bundle2, bundle3));
    }

    public void testSortBundlesAlreadyOrdered() throws Exception {
        Path pluginDir = createTempDir();
        Set<PluginsService.Bundle> bundles = new LinkedHashSet<>(); // control iteration order
        PluginInfo info1 = new PluginInfo("dep", "desc", "1.0", Version.CURRENT, "1.8",
            "MyPlugin", Collections.emptyList(), false, PluginType.ISOLATED, "");
        PluginsService.Bundle bundle1 = new PluginsService.Bundle(info1, pluginDir);
        bundles.add(bundle1);
        PluginInfo info2 = new PluginInfo("myplugin", "desc", "1.0", Version.CURRENT, "1.8",
            "MyPlugin", Collections.singletonList("dep"), false, PluginType.ISOLATED, "");
        PluginsService.Bundle bundle2 = new PluginsService.Bundle(info2, pluginDir);
        bundles.add(bundle2);
        List<PluginsService.Bundle> sortedBundles = PluginsService.sortBundles(bundles);
        assertThat(sortedBundles, Matchers.contains(bundle1, bundle2));
    }

    public static class DummyClass1 {}

    public static class DummyClass2 {}

    public static class DummyClass3 {}

    void makeJar(Path jarFile, Class... classes) throws Exception {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jarFile))) {
            for (Class clazz : classes) {
                String relativePath = clazz.getCanonicalName().replaceAll("\\.", "/") + ".class";
                if (relativePath.contains(PluginsServiceTests.class.getSimpleName())) {
                    // static inner class of this test
                    relativePath = relativePath.replace("/" + clazz.getSimpleName(), "$" + clazz.getSimpleName());
                }

                Path codebase = PathUtils.get(clazz.getProtectionDomain().getCodeSource().getLocation().toURI());
                if (codebase.toString().endsWith(".jar")) {
                    // copy from jar, exactly as is
                    out.putNextEntry(new ZipEntry(relativePath));
                    try (ZipInputStream in = new ZipInputStream(Files.newInputStream(codebase))) {
                        ZipEntry entry = in.getNextEntry();
                        while (entry != null) {
                            if (entry.getName().equals(relativePath)) {
                                byte[] buffer = new byte[10*1024];
                                int read = in.read(buffer);
                                while (read != -1) {
                                    out.write(buffer, 0, read);
                                    read = in.read(buffer);
                                }
                                break;
                            }
                            in.closeEntry();
                            entry = in.getNextEntry();
                        }
                    }
                } else {
                    // copy from dir, and use a different canonical path to not conflict with test classpath
                    out.putNextEntry(new ZipEntry("test/" + clazz.getSimpleName() + ".class"));
                    Files.copy(codebase.resolve(relativePath), out);
                }
                out.closeEntry();
            }
        }
    }

    public void testJarHellDuplicateCodebaseWithDep() throws Exception {
        Path pluginDir = createTempDir();
        Path dupJar = pluginDir.resolve("dup.jar");
        makeJar(dupJar);
        Map<String, Set<URL>> transitiveDeps = new HashMap<>();
        transitiveDeps.put("dep", Collections.singleton(dupJar.toUri().toURL()));
        PluginInfo info1 = new PluginInfo("myplugin", "desc", "1.0", Version.CURRENT, "1.8",
            "MyPlugin", Collections.singletonList("dep"), false, PluginType.ISOLATED, "");
        PluginsService.Bundle bundle = new PluginsService.Bundle(info1, pluginDir);
        IllegalStateException e = expectThrows(IllegalStateException.class, () ->
            PluginsService.checkBundleJarHell(JarHell.parseClassPath(), bundle, transitiveDeps));
        assertEquals("failed to load plugin myplugin due to jar hell", e.getMessage());
        assertThat(e.getCause().getMessage(), containsString("jar hell! duplicate codebases with extended plugin"));
    }

    public void testJarHellDuplicateCodebaseAcrossDeps() throws Exception {
        Path pluginDir = createTempDir();
        Path pluginJar = pluginDir.resolve("plugin.jar");
        makeJar(pluginJar, DummyClass1.class);
        Path otherDir = createTempDir();
        Path dupJar = otherDir.resolve("dup.jar");
        makeJar(dupJar, DummyClass2.class);
        Map<String, Set<URL>> transitiveDeps = new HashMap<>();
        transitiveDeps.put("dep1", Collections.singleton(dupJar.toUri().toURL()));
        transitiveDeps.put("dep2", Collections.singleton(dupJar.toUri().toURL()));
        PluginInfo info1 = new PluginInfo("myplugin", "desc", "1.0", Version.CURRENT, "1.8",
            "MyPlugin", Arrays.asList("dep1", "dep2"), false, PluginType.ISOLATED, "");
        PluginsService.Bundle bundle = new PluginsService.Bundle(info1, pluginDir);
        IllegalStateException e = expectThrows(IllegalStateException.class, () ->
            PluginsService.checkBundleJarHell(JarHell.parseClassPath(), bundle, transitiveDeps));
        assertEquals("failed to load plugin myplugin due to jar hell", e.getMessage());
        assertThat(e.getCause().getMessage(), containsString("jar hell!"));
        assertThat(e.getCause().getMessage(), containsString("duplicate codebases"));
    }

    // Note: testing dup codebase with core is difficult because it requires a symlink, but we have mock filesystems and security manager

    public void testJarHellDuplicateClassWithCore() throws Exception {
        // need a jar file of core dep, use log4j here
        Path pluginDir = createTempDir();
        Path pluginJar = pluginDir.resolve("plugin.jar");
        makeJar(pluginJar, Level.class);
        PluginInfo info1 = new PluginInfo("myplugin", "desc", "1.0", Version.CURRENT, "1.8",
            "MyPlugin", Collections.emptyList(), false, PluginType.ISOLATED, "");
        PluginsService.Bundle bundle = new PluginsService.Bundle(info1, pluginDir);
        IllegalStateException e = expectThrows(IllegalStateException.class, () ->
            PluginsService.checkBundleJarHell(JarHell.parseClassPath(), bundle, new HashMap<>()));
        assertEquals("failed to load plugin myplugin due to jar hell", e.getMessage());
        assertThat(e.getCause().getMessage(), containsString("jar hell!"));
        assertThat(e.getCause().getMessage(), containsString("Level"));
    }

    public void testJarHellDuplicateClassWithDep() throws Exception {
        Path pluginDir = createTempDir();
        Path pluginJar = pluginDir.resolve("plugin.jar");
        makeJar(pluginJar, DummyClass1.class);
        Path depDir = createTempDir();
        Path depJar = depDir.resolve("dep.jar");
        makeJar(depJar, DummyClass1.class);
        Map<String, Set<URL>> transitiveDeps = new HashMap<>();
        transitiveDeps.put("dep", Collections.singleton(depJar.toUri().toURL()));
        PluginInfo info1 = new PluginInfo("myplugin", "desc", "1.0", Version.CURRENT, "1.8",
            "MyPlugin", Collections.singletonList("dep"), false, PluginType.ISOLATED, "");
        PluginsService.Bundle bundle = new PluginsService.Bundle(info1, pluginDir);
        IllegalStateException e = expectThrows(IllegalStateException.class, () ->
            PluginsService.checkBundleJarHell(JarHell.parseClassPath(), bundle, transitiveDeps));
        assertEquals("failed to load plugin myplugin due to jar hell", e.getMessage());
        assertThat(e.getCause().getMessage(), containsString("jar hell!"));
        assertThat(e.getCause().getMessage(), containsString("DummyClass1"));
    }

    public void testJarHellDuplicateClassAcrossDeps() throws Exception {
        Path pluginDir = createTempDir();
        Path pluginJar = pluginDir.resolve("plugin.jar");
        makeJar(pluginJar, DummyClass1.class);
        Path dep1Dir = createTempDir();
        Path dep1Jar = dep1Dir.resolve("dep1.jar");
        makeJar(dep1Jar, DummyClass2.class);
        Path dep2Dir = createTempDir();
        Path dep2Jar = dep2Dir.resolve("dep2.jar");
        makeJar(dep2Jar, DummyClass2.class);
        Map<String, Set<URL>> transitiveDeps = new HashMap<>();
        transitiveDeps.put("dep1", Collections.singleton(dep1Jar.toUri().toURL()));
        transitiveDeps.put("dep2", Collections.singleton(dep2Jar.toUri().toURL()));
        PluginInfo info1 = new PluginInfo("myplugin", "desc", "1.0", Version.CURRENT, "1.8",
            "MyPlugin", Arrays.asList("dep1", "dep2"), false, PluginType.ISOLATED, "");
        PluginsService.Bundle bundle = new PluginsService.Bundle(info1, pluginDir);
        IllegalStateException e = expectThrows(IllegalStateException.class, () ->
            PluginsService.checkBundleJarHell(JarHell.parseClassPath(), bundle, transitiveDeps));
        assertEquals("failed to load plugin myplugin due to jar hell", e.getMessage());
        assertThat(e.getCause().getMessage(), containsString("jar hell!"));
        assertThat(e.getCause().getMessage(), containsString("DummyClass2"));
    }

    public void testJarHellTransitiveMap() throws Exception {
        Path pluginDir = createTempDir();
        Path pluginJar = pluginDir.resolve("plugin.jar");
        makeJar(pluginJar, DummyClass1.class);
        Path dep1Dir = createTempDir();
        Path dep1Jar = dep1Dir.resolve("dep1.jar");
        makeJar(dep1Jar, DummyClass2.class);
        Path dep2Dir = createTempDir();
        Path dep2Jar = dep2Dir.resolve("dep2.jar");
        makeJar(dep2Jar, DummyClass3.class);
        Map<String, Set<URL>> transitiveDeps = new HashMap<>();
        transitiveDeps.put("dep1", Collections.singleton(dep1Jar.toUri().toURL()));
        transitiveDeps.put("dep2", Collections.singleton(dep2Jar.toUri().toURL()));
        PluginInfo info1 = new PluginInfo("myplugin", "desc", "1.0", Version.CURRENT, "1.8",
            "MyPlugin", Arrays.asList("dep1", "dep2"), false, PluginType.ISOLATED, "");
        PluginsService.Bundle bundle = new PluginsService.Bundle(info1, pluginDir);
        PluginsService.checkBundleJarHell(JarHell.parseClassPath(), bundle, transitiveDeps);
        Set<URL> deps = transitiveDeps.get("myplugin");
        assertNotNull(deps);
        assertThat(deps, containsInAnyOrder(pluginJar.toUri().toURL(), dep1Jar.toUri().toURL(), dep2Jar.toUri().toURL()));
    }

    public void testNonExtensibleDep() throws Exception {
        // This test opens a child classloader, reading a jar under the test temp
        // dir (a dummy plugin). Classloaders are closed by GC, so when test teardown
        // occurs the jar is deleted while the classloader is still open. However, on
        // windows, files cannot be deleted when they are still open by a process.
        assumeFalse("windows deletion behavior is asinine", Constants.WINDOWS);

        Path homeDir = createTempDir();
        Settings settings = Settings.builder().put(Environment.PATH_HOME_SETTING.getKey(), homeDir).build();
        Path pluginsDir = homeDir.resolve("plugins");
        Path mypluginDir = pluginsDir.resolve("myplugin");
        PluginTestUtil.writePluginProperties(
            mypluginDir,
            "description", "whatever",
            "name", "myplugin",
            "version", "1.0.0",
            "elasticsearch.version", Version.CURRENT.toString(),
            "java.version", System.getProperty("java.specification.version"),
            "extended.plugins", "nonextensible",
            "classname", "test.DummyPlugin");
        try (InputStream jar = PluginsServiceTests.class.getResourceAsStream("dummy-plugin.jar")) {
            Files.copy(jar, mypluginDir.resolve("plugin.jar"));
        }
        Path nonextensibleDir = pluginsDir.resolve("nonextensible");
        PluginTestUtil.writePluginProperties(
            nonextensibleDir,
            "description", "whatever",
            "name", "nonextensible",
            "version", "1.0.0",
            "elasticsearch.version", Version.CURRENT.toString(),
            "java.version", System.getProperty("java.specification.version"),
            "classname", "test.NonExtensiblePlugin");
        try (InputStream jar = PluginsServiceTests.class.getResourceAsStream("non-extensible-plugin.jar")) {
            Files.copy(jar, nonextensibleDir.resolve("plugin.jar"));
        }
        IllegalStateException e = expectThrows(IllegalStateException.class, () -> newPluginsService(settings));
        assertEquals("Plugin [myplugin] cannot extend non-extensible plugin [nonextensible]", e.getMessage());
    }

    public void testIncompatibleElasticsearchVersion() throws Exception {
        PluginInfo info = new PluginInfo("my_plugin", "desc", "1.0", Version.V_5_0_0,
            "1.8", "FakePlugin", Collections.emptyList(), false, PluginType.ISOLATED, "");
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> PluginsService.verifyCompatibility(info));
        assertThat(e.getMessage(), containsString("was built for Elasticsearch version 5.0.0"));
    }

    public void testIncompatibleJavaVersion() throws Exception {
        PluginInfo info = new PluginInfo("my_plugin", "desc", "1.0", Version.CURRENT,
            "1000000.0", "FakePlugin", Collections.emptyList(), false, PluginType.ISOLATED, "");
        IllegalStateException e = expectThrows(IllegalStateException.class, () -> PluginsService.verifyCompatibility(info));
        assertThat(e.getMessage(), containsString("my_plugin requires Java"));
    }

    public void testFindPluginDirs() throws IOException {
        final Path plugins = createTempDir();

        final Path fake = plugins.resolve("fake");

        PluginTestUtil.writePluginProperties(
                fake,
                "description", "description",
                "name", "fake",
                "version", "1.0.0",
                "elasticsearch.version", Version.CURRENT.toString(),
                "java.version", System.getProperty("java.specification.version"),
                "classname", "test.DummyPlugin");

        try (InputStream jar = PluginsServiceTests.class.getResourceAsStream("dummy-plugin.jar")) {
            Files.copy(jar, fake.resolve("plugin.jar"));
        }

        assertThat(PluginsService.findPluginDirs(plugins), containsInAnyOrder(fake));
    }

    public void testExistingMandatoryClasspathPlugin() {
        final Settings settings =
                Settings.builder()
                        .put("path.home", createTempDir())
                        .put("plugin.mandatory", "org.elasticsearch.plugins.PluginsServiceTests$FakePlugin")
                        .build();
        newPluginsService(settings, FakePlugin.class);
    }

    public static class FakePlugin extends Plugin {

        public FakePlugin() {

        }

    }

    public void testExistingMandatoryInstalledPlugin() throws IOException {
        // This test opens a child classloader, reading a jar under the test temp
        // dir (a dummy plugin). Classloaders are closed by GC, so when test teardown
        // occurs the jar is deleted while the classloader is still open. However, on
        // windows, files cannot be deleted when they are still open by a process.
        assumeFalse("windows deletion behavior is asinine", Constants.WINDOWS);
        final Path pathHome = createTempDir();
        final Path plugins = pathHome.resolve("plugins");
        final Path fake = plugins.resolve("fake");

        PluginTestUtil.writePluginProperties(
                fake,
                "description", "description",
                "name", "fake",
                "version", "1.0.0",
                "elasticsearch.version", Version.CURRENT.toString(),
                "java.version", System.getProperty("java.specification.version"),
                "classname", "test.DummyPlugin");
        try (InputStream jar = PluginsServiceTests.class.getResourceAsStream("dummy-plugin.jar")) {
            Files.copy(jar, fake.resolve("plugin.jar"));
        }

        final Settings settings =
                Settings.builder()
                        .put("path.home", pathHome)
                        .put("plugin.mandatory", "fake")
                        .build();
        newPluginsService(settings);
    }
}
