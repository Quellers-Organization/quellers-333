/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.profiling;

import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;

import java.util.List;

public class GetStackTracesActionIT extends ProfilingTestCase {
    public void testGetStackTracesUnfiltered() throws Exception {
        GetStackTracesRequest request = new GetStackTracesRequest(10, null, null, null);
        request.setAdjustSampleCount(true);
        GetStackTracesResponse response = client().execute(GetStackTracesAction.INSTANCE, request).get();
        assertEquals(40, response.getTotalSamples());
        assertEquals(473, response.getTotalFrames());

        assertNotNull(response.getStackTraceEvents());
        assertEquals(4L, (long) response.getStackTraceEvents().get("L7kj7UvlKbT-vN73el4faQ"));

        assertNotNull(response.getStackTraces());
        // just do a high-level spot check. Decoding is tested in unit-tests
        StackTrace stackTrace = response.getStackTraces().get("L7kj7UvlKbT-vN73el4faQ");
        assertEquals(18, stackTrace.addressOrLines.size());
        assertEquals(18, stackTrace.fileIds.size());
        assertEquals(18, stackTrace.frameIds.size());
        assertEquals(18, stackTrace.typeIds.size());

        assertNotNull(response.getStackFrames());
        StackFrame stackFrame = response.getStackFrames().get("8NlMClggx8jaziUTJXlmWAAAAAAAAIYI");
        assertEquals(List.of("start_thread"), stackFrame.functionName);

        assertNotNull(response.getExecutables());
        assertEquals("vmlinux", response.getExecutables().get("lHp5_WAgpLy2alrUVab6HA"));
    }

    public void testGetStackTracesFromAPMWithMatch() throws Exception {
        TermQueryBuilder query = QueryBuilders.termQuery("transaction.name", "encodeSha1");

        GetStackTracesRequest request = new GetStackTracesRequest(null, query, "apm-test-*", "transaction.profiler_stack_trace_ids");
        GetStackTracesResponse response = client().execute(GetStackTracesAction.INSTANCE, request).get();
        assertEquals(39, response.getTotalFrames());

        assertNotNull(response.getStackTraceEvents());
        assertEquals(1L, (long) response.getStackTraceEvents().get("Ce77w10WeIDow3kd1jowlA"));

        assertNotNull(response.getStackTraces());
        // just do a high-level spot check. Decoding is tested in unit-tests
        StackTrace stackTrace = response.getStackTraces().get("Ce77w10WeIDow3kd1jowlA");
        assertEquals(39, stackTrace.addressOrLines.size());
        assertEquals(39, stackTrace.fileIds.size());
        assertEquals(39, stackTrace.frameIds.size());
        assertEquals(39, stackTrace.typeIds.size());

        assertNotNull(response.getStackFrames());
        StackFrame stackFrame = response.getStackFrames().get("fhsEKXDuxJ-jIJrZpdRuSAAAAAAAAFtj");
        assertEquals(List.of("deflate", "deflate"), stackFrame.functionName);

        assertNotNull(response.getExecutables());
        assertEquals("libzip.so", response.getExecutables().get("GXH6S9Nv2Lf0omTz4cH4RA"));
    }

    public void testGetStackTracesFromAPMNoMatch() throws Exception {
        TermQueryBuilder query = QueryBuilders.termQuery("transaction.name", "nonExistingTransaction");

        GetStackTracesRequest request = new GetStackTracesRequest(null, query, "apm-test-*", "transaction.profiler_stack_trace_ids");
        GetStackTracesResponse response = client().execute(GetStackTracesAction.INSTANCE, request).get();
        assertEquals(0, response.getTotalFrames());
    }
}
