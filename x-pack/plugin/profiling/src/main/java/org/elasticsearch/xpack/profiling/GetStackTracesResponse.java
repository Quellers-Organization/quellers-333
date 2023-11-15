/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.profiling;

import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.common.collect.Iterators;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ChunkedToXContentHelper;
import org.elasticsearch.common.xcontent.ChunkedToXContentObject;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.xcontent.ToXContent;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

public class GetStackTracesResponse extends ActionResponse implements ChunkedToXContentObject {
    @Nullable
    private final Map<String, StackTrace> stackTraces;
    @Nullable
    private final Map<String, StackFrame> stackFrames;
    @Nullable
    private final Map<String, String> executables;
    @Nullable
    private final Map<String, Long> stackTraceEvents;
    private final int totalFrames;
    private final double samplingRate;
    private final long totalSamples;

    public GetStackTracesResponse(StreamInput in) throws IOException {
        this.stackTraces = in.readBoolean()
            ? in.readMap(
                i -> new StackTrace(
                    i.readCollectionAsList(StreamInput::readInt),
                    i.readCollectionAsList(StreamInput::readString),
                    i.readCollectionAsList(StreamInput::readString),
                    i.readCollectionAsList(StreamInput::readInt),
                    i.readDouble(),
                    i.readDouble(),
                    i.readLong()
                )
            )
            : null;
        this.stackFrames = in.readBoolean()
            ? in.readMap(
                i -> new StackFrame(
                    i.readCollectionAsList(StreamInput::readString),
                    i.readCollectionAsList(StreamInput::readString),
                    i.readCollectionAsList(StreamInput::readInt),
                    i.readCollectionAsList(StreamInput::readInt)
                )
            )
            : null;
        this.executables = in.readBoolean() ? in.readMap(StreamInput::readString) : null;
        this.stackTraceEvents = in.readBoolean() ? in.readMap(StreamInput::readLong) : null;
        this.totalFrames = in.readInt();
        this.samplingRate = in.readDouble();
        this.totalSamples = in.readLong();
    }

    public GetStackTracesResponse(
        Map<String, StackTrace> stackTraces,
        Map<String, StackFrame> stackFrames,
        Map<String, String> executables,
        Map<String, TraceEvent> stackTraceEvents,
        int totalFrames,
        double samplingRate,
        long totalSamples
    ) {
        this.stackTraces = stackTraces;
        this.stackFrames = stackFrames;
        this.executables = executables;
        this.totalFrames = totalFrames;
        this.samplingRate = samplingRate;
        this.totalSamples = totalSamples;
        if (stackTraceEvents != null) {
            this.stackTraceEvents = new HashMap<>(stackTraceEvents.size());
            stackTraceEvents.forEach((id, event) -> this.stackTraceEvents.put(id, event.count));
        } else {
            this.stackTraceEvents = null;
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        if (stackTraces != null) {
            out.writeBoolean(true);
            out.writeMap(stackTraces, (o, v) -> {
                o.writeCollection(v.addressOrLines, StreamOutput::writeInt);
                o.writeStringCollection(v.fileIds);
                o.writeStringCollection(v.frameIds);
                o.writeCollection(v.typeIds, StreamOutput::writeInt);
                o.writeDouble(v.annualCO2Tons);
                o.writeDouble(v.annualCostsUSD);
                o.writeLong(v.count);
            });
        } else {
            out.writeBoolean(false);
        }
        if (stackFrames != null) {
            out.writeBoolean(true);
            out.writeMap(stackFrames, (o, v) -> {
                o.writeStringCollection(v.fileName);
                o.writeStringCollection(v.functionName);
                o.writeCollection(v.functionOffset, StreamOutput::writeInt);
                o.writeCollection(v.lineNumber, StreamOutput::writeInt);
            });
        } else {
            out.writeBoolean(false);
        }
        if (executables != null) {
            out.writeBoolean(true);
            out.writeMap(executables, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
        if (stackTraceEvents != null) {
            out.writeBoolean(true);
            out.writeMap(stackTraceEvents, StreamOutput::writeLong);
        } else {
            out.writeBoolean(false);
        }
        out.writeInt(totalFrames);
        out.writeDouble(samplingRate);
        out.writeLong(totalSamples);
    }

    public Map<String, StackTrace> getStackTraces() {
        return stackTraces;
    }

    public Map<String, StackFrame> getStackFrames() {
        return stackFrames;
    }

    public Map<String, String> getExecutables() {
        return executables;
    }

    public Map<String, Long> getStackTraceEvents() {
        return stackTraceEvents;
    }

    public int getTotalFrames() {
        return totalFrames;
    }

    public double getSamplingRate() {
        return samplingRate;
    }

    public long getTotalSamples() {
        return totalSamples;
    }

    @Override
    public Iterator<? extends ToXContent> toXContentChunked(ToXContent.Params params) {
        return Iterators.concat(
            ChunkedToXContentHelper.startObject(),
            optional("stack_traces", stackTraces, ChunkedToXContentHelper::xContentValuesMap),
            optional("stack_frames", stackFrames, ChunkedToXContentHelper::xContentValuesMap),
            optional("executables", executables, ChunkedToXContentHelper::map),
            optional("stack_trace_events", stackTraceEvents, ChunkedToXContentHelper::map),
            Iterators.single((b, p) -> b.field("total_frames", totalFrames)),
            Iterators.single((b, p) -> b.field("sampling_rate", samplingRate)),
            // the following fields are intentionally not written to the XContent representation (only needed on the transport layer):
            //
            // * start
            // * end
            // * totalSamples
            ChunkedToXContentHelper.endObject()
        );
    }

    private static <T> Iterator<? extends ToXContent> optional(
        String name,
        Map<String, T> values,
        BiFunction<String, Map<String, T>, Iterator<? extends ToXContent>> supplier
    ) {
        return (values != null) ? supplier.apply(name, values) : Collections.emptyIterator();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GetStackTracesResponse response = (GetStackTracesResponse) o;
        return totalFrames == response.totalFrames
            && samplingRate == response.samplingRate
            && Objects.equals(stackTraces, response.stackTraces)
            && Objects.equals(stackFrames, response.stackFrames)
            && Objects.equals(executables, response.executables)
            && Objects.equals(stackTraceEvents, response.stackTraceEvents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stackTraces, stackFrames, executables, stackTraceEvents, totalFrames, samplingRate);
    }
}
