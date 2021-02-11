/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.elasticsearch.gradle.fixtures.AbstractRestResourcesFuncTest
import org.elasticsearch.gradle.internal.rest.compat.YamlRestCompatTestPlugin
import org.gradle.testkit.runner.TaskOutcome

class YamlRestCompatTestPluginFuncTest extends AbstractRestResourcesFuncTest {

    private static final String intermediateDir = YamlRestCompatTestPlugin.TEST_INTERMEDIATE_DIR_NAME
    private static final String transformTask  = ":" + YamlRestCompatTestPlugin.TRANSFORM_TASK_NAME
    private static final YAMLFactory YAML_FACTORY = new YAMLFactory()
    private static final ObjectMapper MAPPER = new ObjectMapper(YAML_FACTORY)
    private static final ObjectReader READER = MAPPER.readerFor(ObjectNode.class)
    private static final ObjectWriter WRITER = MAPPER.writerFor(ObjectNode.class)


    def "yamlRestCompatTest does nothing when there are no tests"() {
        given:

        addSubProject(":distribution:bwc:minor") << """
        configurations { checkout }
        artifacts {
            checkout(new File(projectDir, "checkoutDir"))
        }
        """

        buildFile << """
        plugins {
          id 'elasticsearch.yaml-rest-compat-test'
        }
        """

        when:
        def result = gradleRunner("yamlRestCompatTest").build()

        then:
        result.task(':yamlRestCompatTest').outcome == TaskOutcome.NO_SOURCE
        result.task(':copyRestCompatApiTask').outcome == TaskOutcome.NO_SOURCE
        result.task(':copyRestCompatTestTask').outcome == TaskOutcome.NO_SOURCE
        result.task(transformTask).outcome == TaskOutcome.NO_SOURCE
    }

    def "yamlRestCompatTest executes and copies api and transforms tests from :bwc:minor"() {
        given:
        internalBuild()

        addSubProject(":distribution:bwc:minor") << """
        configurations { checkout }
        artifacts {
            checkout(new File(projectDir, "checkoutDir"))
        }
        """

        buildFile << """
            apply plugin: 'elasticsearch.yaml-rest-compat-test'

            // avoids a dependency problem in this test, the distribution in use here is inconsequential to the test
            import org.elasticsearch.gradle.testclusters.TestDistribution;
            testClusters {
              yamlRestCompatTest.setTestDistribution(TestDistribution.INTEG_TEST)
            }

            dependencies {
               yamlRestTestImplementation "junit:junit:4.12"
            }

            // can't actually spin up test cluster from this test
           tasks.withType(Test).configureEach{ enabled = false }
        """

        String wrongApi = "wrong_version.json"
        String wrongTest = "wrong_version.yml"
        String additionalTest = "additional_test.yml"
        setupRestResources([wrongApi], [wrongTest]) //setups up resources for current version, which should not be used for this test
        addRestTestsToProject([additionalTest], "yamlRestCompatTest")
        //intentionally adding to yamlRestTest source set since the .classes are copied from there
        file("src/yamlRestTest/java/MockIT.java") << "import org.junit.Test;class MockIT { @Test public void doNothing() { }}"

        String api = "foo.json"
        String test = "10_basic.yml"
        //add the compatible test and api files, these are the prior version's normal yaml rest tests
        file("distribution/bwc/minor/checkoutDir/rest-api-spec/src/main/resources/rest-api-spec/api/" + api) << ""
        file("distribution/bwc/minor/checkoutDir/src/yamlRestTest/resources/rest-api-spec/test/" + test) << ""

        when:
        def result = gradleRunner("yamlRestCompatTest").build()

        then:
        result.task(':yamlRestCompatTest').outcome == TaskOutcome.SKIPPED
        result.task(':copyRestCompatApiTask').outcome == TaskOutcome.SUCCESS
        result.task(':copyRestCompatTestTask').outcome == TaskOutcome.SUCCESS
        result.task(transformTask).outcome == TaskOutcome.SUCCESS

        file("/build/resources/yamlRestCompatTest/rest-api-spec/api/" + api).exists()
        file("/build/resources/yamlRestCompatTest/rest-api-spec/test/" + test).exists()
        file("/build/resources/yamlRestCompatTest/rest-api-spec/test/" + test).text.contains("headers") //transformation adds this
        file("/build/resources/yamlRestCompatTest/" + intermediateDir + "/rest-api-spec/test/" + test).exists()
        file("/build/resources/yamlRestCompatTest/rest-api-spec/test/" + additionalTest).exists()

        //additionalTest is not copied from the prior version, and thus not in the intermediate directory, nor transformed
        file("/build/resources/yamlRestCompatTest/" + intermediateDir + "/rest-api-spec/test/" + additionalTest).exists() == false
        file("/build/resources/yamlRestCompatTest/rest-api-spec/test/" + additionalTest).text.contains("headers") == false

        file("/build/classes/java/yamlRestTest/MockIT.class").exists() //The "standard" runner is used to execute the compat test

        file("/build/resources/yamlRestCompatTest/rest-api-spec/api/" + wrongApi).exists() == false
        file("/build/resources/yamlRestCompatTest/" + intermediateDir + "/rest-api-spec/test/" + wrongTest).exists() == false
        file("/build/resources/yamlRestCompatTest/rest-api-spec/test/" + wrongTest).exists() == false

        result.task(':copyRestApiSpecsTask').outcome == TaskOutcome.NO_SOURCE
        result.task(':copyYamlTestsTask').outcome == TaskOutcome.NO_SOURCE

        when:
        result = gradleRunner("yamlRestCompatTest").build()

        then:
        result.task(':yamlRestCompatTest').outcome == TaskOutcome.SKIPPED
        result.task(':copyRestCompatApiTask').outcome == TaskOutcome.UP_TO_DATE
        result.task(':copyRestCompatTestTask').outcome == TaskOutcome.UP_TO_DATE
        result.task(transformTask).outcome == TaskOutcome.UP_TO_DATE
    }

    def "yamlRestCompatTest is wired into check and checkRestCompat"() {
        given:

        addSubProject(":distribution:bwc:minor") << """
        configurations { checkout }
        artifacts {
            checkout(new File(projectDir, "checkoutDir"))
        }
        """

        buildFile << """
        plugins {
          id 'elasticsearch.yaml-rest-compat-test'
        }

        """

        when:
        def result = gradleRunner("check").build()

        then:
        result.task(':check').outcome == TaskOutcome.UP_TO_DATE
        result.task(':checkRestCompat').outcome == TaskOutcome.UP_TO_DATE
        result.task(':yamlRestCompatTest').outcome == TaskOutcome.NO_SOURCE
        result.task(':copyRestCompatApiTask').outcome == TaskOutcome.NO_SOURCE
        result.task(':copyRestCompatTestTask').outcome == TaskOutcome.NO_SOURCE
        result.task(transformTask).outcome == TaskOutcome.NO_SOURCE

        when:
        buildFile << """
         ext.bwc_tests_enabled = false
        """
        result = gradleRunner("check").build()

        then:
        result.task(':check').outcome == TaskOutcome.UP_TO_DATE
        result.task(':checkRestCompat').outcome == TaskOutcome.UP_TO_DATE
        result.task(':yamlRestCompatTest').outcome == TaskOutcome.SKIPPED
        result.task(':copyRestCompatApiTask').outcome == TaskOutcome.SKIPPED
        result.task(':copyRestCompatTestTask').outcome == TaskOutcome.SKIPPED
        result.task(transformTask).outcome == TaskOutcome.SKIPPED
    }

    def "transform task executes and works as configured"() {
        given:
        internalBuild()

        addSubProject(":distribution:bwc:minor") << """
        configurations { checkout }
        artifacts {
            checkout(new File(projectDir, "checkoutDir"))
        }
        """

        buildFile << """
            apply plugin: 'elasticsearch.yaml-rest-compat-test'

            // avoids a dependency problem in this test, the distribution in use here is inconsequential to the test
            import org.elasticsearch.gradle.testclusters.TestDistribution;
            testClusters {
              yamlRestCompatTest.setTestDistribution(TestDistribution.INTEG_TEST)
            }

            dependencies {
               yamlRestTestImplementation "junit:junit:4.12"
            }
            tasks.named("transformV7RestTests").configure({ task ->
              task.replaceMatch("_type", "_doc")
              task.replaceMatch("_source.values", ["z", "x", "y"], "one")
              task.removeMatch("_source.blah")
              task.removeMatch("_source.junk", "two")
              task.addMatch("_source.added", [name: 'jake', likes: 'cheese'], "one")
            })
            // can't actually spin up test cluster from this test
           tasks.withType(Test).configureEach{ enabled = false }
        """

        setupRestResources([], [])

        file("distribution/bwc/minor/checkoutDir/src/yamlRestTest/resources/rest-api-spec/test/test.yml" ) << """
        "one":
          - do:
              get:
                index: test
                id: 1
          - match: { _source.values: ["foo"] }
          - match: { _type: "_foo" }
          - match: { _source.blah: 1234 }
          - match: { _source.junk: true }
        ---
        "two":
          - do:
              get:
                index: test
                id: 1
          - match: { _source.values: ["foo"] }
          - match: { _type: "_foo" }
          - match: { _source.blah: 1234 }
          - match: { _source.junk: true }

        """.stripIndent()
        when:
        def result = gradleRunner("yamlRestCompatTest").build()

        then:

        result.task(transformTask).outcome == TaskOutcome.SUCCESS


        file("/build/resources/yamlRestCompatTest/rest-api-spec/test/test.yml" ).exists()
        List<ObjectNode> actual = READER.readValues(file("/build/resources/yamlRestCompatTest/rest-api-spec/test/test.yml")).readAll()
        List<ObjectNode> expectedAll = READER.readValues(
        """
        ---
        setup:
        - skip:
            features: "headers"
        ---
        one:
        - do:
            get:
              index: "test"
              id: 1
            headers:
              Content-Type: "application/vnd.elasticsearch+json;compatible-with=7"
              Accept: "application/vnd.elasticsearch+json;compatible-with=7"
        - match:
            _source.values:
            - "z"
            - "x"
            - "y"
        - match:
            _type: "_doc"
        - match: {}
        - match:
            _source.junk: true
        - match:
            _source.added:
              name: "jake"
              likes: "cheese"
        ---
        two:
        - do:
            get:
              index: "test"
              id: 1
            headers:
              Content-Type: "application/vnd.elasticsearch+json;compatible-with=7"
              Accept: "application/vnd.elasticsearch+json;compatible-with=7"
        - match:
            _source.values:
            - "foo"
        - match:
            _type: "_doc"
        - match: {}
        - match: {}
        """.stripIndent()).readAll()

        expectedAll.eachWithIndex{ ObjectNode expected, int i ->
           assert expected == actual.get(i)
        }

        when:
        result = gradleRunner(transformTask).build()

        then:
        result.task(transformTask).outcome == TaskOutcome.UP_TO_DATE

        when:
        buildFile.write(buildFile.text.replace("blah", "baz"))
        result = gradleRunner(transformTask).build()

        then:
        result.task(transformTask).outcome == TaskOutcome.SUCCESS
    }

}
