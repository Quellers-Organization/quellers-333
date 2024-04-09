/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.core.ilm;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.action.support.PlainActionFuture;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.index.IndexVersion;
import org.elasticsearch.xpack.core.ilm.Step.StepKey;
import org.mockito.Mockito;

import static org.hamcrest.Matchers.equalTo;

public class AllowWriteStepTests extends AbstractStepTestCase<AllowWriteStep> {

    @Override
    public AllowWriteStep createRandomInstance() {
        StepKey stepKey = randomStepKey();
        StepKey nextStepKey = randomStepKey();
        return new AllowWriteStep(stepKey, nextStepKey, client, randomBoolean());
    }

    @Override
    public AllowWriteStep mutateInstance(AllowWriteStep instance) {
        StepKey key = instance.getKey();
        StepKey nextKey = instance.getNextStepKey();
        boolean allowWriteAfterShrink = instance.isAllowWriteAfterShrink();

        switch (between(0, 2)) {
            case 0 -> key = new StepKey(key.phase(), key.action(), key.name() + randomAlphaOfLength(5));
            case 1 -> nextKey = new StepKey(nextKey.phase(), nextKey.action(), nextKey.name() + randomAlphaOfLength(5));
            case 2 -> allowWriteAfterShrink = allowWriteAfterShrink == false;
            default -> throw new AssertionError("Illegal randomisation branch");
        }

        return new AllowWriteStep(key, nextKey, client, allowWriteAfterShrink);
    }

    @Override
    public AllowWriteStep copyInstance(AllowWriteStep instance) {
        return new AllowWriteStep(instance.getKey(), instance.getNextStepKey(), instance.getClient(), instance.isAllowWriteAfterShrink());
    }

    private static IndexMetadata getIndexMetadata() {
        return IndexMetadata.builder(randomAlphaOfLength(10))
            .settings(settings(IndexVersion.current()))
            .numberOfShards(randomIntBetween(1, 5))
            .numberOfReplicas(randomIntBetween(0, 5))
            .build();
    }

    public void testPerformAction() {
        IndexMetadata indexMetadata = getIndexMetadata();

        AllowWriteStep step = createRandomInstance();

        Mockito.doAnswer(invocation -> {
            UpdateSettingsRequest request = (UpdateSettingsRequest) invocation.getArguments()[0];
            @SuppressWarnings("unchecked")
            ActionListener<AcknowledgedResponse> listener = (ActionListener<AcknowledgedResponse>) invocation.getArguments()[1];
            assertThat(request.settings(), equalTo(AllowWriteStep.CLEAR_BLOCKS_WRITE_SETTING));
            assertThat(request.indices(), equalTo(new String[] { indexMetadata.getIndex().getName() }));
            listener.onResponse(AcknowledgedResponse.TRUE);
            return null;
        }).when(indicesClient).updateSettings(Mockito.any(), Mockito.any());

        assertNull(PlainActionFuture.<Void, RuntimeException>get(f -> step.performAction(indexMetadata, emptyClusterState(), null, f)));

        Mockito.verify(client, Mockito.only()).admin();
        Mockito.verify(adminClient, Mockito.only()).indices();
        Mockito.verify(indicesClient, Mockito.only()).updateSettings(Mockito.any(), Mockito.any());
    }

    public void testPerformActionFailure() {
        IndexMetadata indexMetadata = getIndexMetadata();
        Exception exception = new RuntimeException();
        UpdateSettingsStep step = createRandomInstance();

        Mockito.doAnswer(invocation -> {
            UpdateSettingsRequest request = (UpdateSettingsRequest) invocation.getArguments()[0];
            @SuppressWarnings("unchecked")
            ActionListener<AcknowledgedResponse> listener = (ActionListener<AcknowledgedResponse>) invocation.getArguments()[1];
            assertThat(request.settings(), equalTo(AllowWriteStep.CLEAR_BLOCKS_WRITE_SETTING));
            assertThat(request.indices(), equalTo(new String[] { indexMetadata.getIndex().getName() }));
            listener.onFailure(exception);
            return null;
        }).when(indicesClient).updateSettings(Mockito.any(), Mockito.any());

        assertSame(
            exception,
            expectThrows(
                Exception.class,
                () -> PlainActionFuture.<Void, Exception>get(f -> step.performAction(indexMetadata, emptyClusterState(), null, f))
            )
        );

        Mockito.verify(client, Mockito.only()).admin();
        Mockito.verify(adminClient, Mockito.only()).indices();
        Mockito.verify(indicesClient, Mockito.only()).updateSettings(Mockito.any(), Mockito.any());
    }
}
