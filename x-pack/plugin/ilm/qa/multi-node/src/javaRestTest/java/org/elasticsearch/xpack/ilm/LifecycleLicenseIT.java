/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.ilm;

import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.cluster.metadata.DataStream;
import org.elasticsearch.cluster.metadata.Template;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.license.License;
import org.elasticsearch.license.TestUtils;
import org.elasticsearch.test.rest.ESRestTestCase;
import org.elasticsearch.xpack.core.ilm.LifecycleSettings;
import org.elasticsearch.xpack.core.ilm.PhaseCompleteStep;
import org.elasticsearch.xpack.core.ilm.SearchableSnapshotAction;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.elasticsearch.xpack.TimeSeriesRestDriver.createComposableTemplate;
import static org.elasticsearch.xpack.TimeSeriesRestDriver.createNewSingletonPolicy;
import static org.elasticsearch.xpack.TimeSeriesRestDriver.createSnapshotRepo;
import static org.elasticsearch.xpack.TimeSeriesRestDriver.explainIndex;
import static org.elasticsearch.xpack.TimeSeriesRestDriver.indexDocument;
import static org.elasticsearch.xpack.TimeSeriesRestDriver.rolloverMaxOneDocCondition;
import static org.hamcrest.CoreMatchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;

public class LifecycleLicenseIT extends ESRestTestCase {

    private String policy;
    private String dataStream;

    @Before
    public void refreshDatastream() {
        dataStream = "logs-" + randomAlphaOfLength(10).toLowerCase(Locale.ROOT);
        policy = "policy-" + randomAlphaOfLength(5);
    }

    @After
    public void resetLicenseToTrial() throws Exception {
        putTrialLicense();
        checkCurrentLicenseIs("trial");
    }

    public void testCreatePolicyUsingActionAndNonCompliantLicense() throws Exception {
        String snapshotRepo = randomAlphaOfLengthBetween(4, 10);
        createSnapshotRepo(client(), snapshotRepo, randomBoolean());

        assertOK(client().performRequest(new Request("DELETE", "/_license")));
        checkCurrentLicenseIs("basic");

        ResponseException exception = expectThrows(ResponseException.class,
            () -> createNewSingletonPolicy(client(), policy, "cold", new SearchableSnapshotAction(snapshotRepo, true)));
        assertThat(EntityUtils.toString(exception.getResponse().getEntity()),
            containsStringIgnoringCase("policy [" + policy + "] defines the [" + SearchableSnapshotAction.NAME + "] action but the " +
                "current license is non-compliant for [searchable-snapshots]"));
    }

    public void testSearchableSnapshotActionErrorsOnInvalidLicense() throws Exception {
        String snapshotRepo = randomAlphaOfLengthBetween(4, 10);
        createSnapshotRepo(client(), snapshotRepo, randomBoolean());
        createNewSingletonPolicy(client(), policy, "cold", new SearchableSnapshotAction(snapshotRepo, true));

        createComposableTemplate(client(), "template-name", dataStream,
            new Template(Settings.builder().put(LifecycleSettings.LIFECYCLE_NAME, policy).build(), null, null));

        assertOK(client().performRequest(new Request("DELETE", "/_license")));
        checkCurrentLicenseIs("basic");

        indexDocument(client(), dataStream, true);

        // rolling over the data stream so we can apply the searchable snapshot policy to a backing index that's not the write index
        rolloverMaxOneDocCondition(client(), dataStream);

        String backingIndexName = DataStream.getDefaultBackingIndexName(dataStream, 1L);
        // the searchable_snapshot action should start failing (and retrying) due to invalid license
        assertBusy(() -> {
            Map<String, Object> explainIndex = explainIndex(client(), backingIndexName);
            assertThat(explainIndex.get("action"), is(SearchableSnapshotAction.NAME));
            assertThat((Integer) explainIndex.get("failed_step_retry_count"), greaterThanOrEqualTo(1));

        }, 30, TimeUnit.SECONDS);

        // switching back to trial so searchable_snapshot is permitted
        putTrialLicense();
        checkCurrentLicenseIs("trial");

        String restoredIndexName = SearchableSnapshotAction.RESTORED_INDEX_PREFIX + backingIndexName;
        assertTrue(waitUntil(() -> {
            try {
                return indexExists(restoredIndexName);
            } catch (IOException e) {
                return false;
            }
        }, 30, TimeUnit.SECONDS));

        assertBusy(() -> assertThat(explainIndex(client(), restoredIndexName).get("step"), is(PhaseCompleteStep.NAME)), 30,
            TimeUnit.SECONDS);
    }

    private void putTrialLicense() throws Exception {
        License signedLicense = TestUtils.generateSignedLicense("trial", License.VERSION_CURRENT, -1, TimeValue.timeValueDays(7));
        Request putTrialRequest = new Request("PUT", "/_license");
        XContentBuilder builder = JsonXContent.contentBuilder();
        builder = signedLicense.toXContent(builder, ToXContent.EMPTY_PARAMS);
        putTrialRequest.setJsonEntity("{\"licenses\":[\n " + Strings.toString(builder) + "\n]}");
        client().performRequest(putTrialRequest);
    }

    private void checkCurrentLicenseIs(String type) throws Exception {
        assertBusy(() -> assertThat(EntityUtils.toString(client().performRequest(new Request("GET", "/_license")).getEntity()),
            containsStringIgnoringCase("\"type\" : \"" + type + "\"")));
    }
}
