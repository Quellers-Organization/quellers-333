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

package org.elasticsearch.monitor.os;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public class OsStats implements Writeable, ToXContent {

    private final long timestamp;
    private final Cpu cpu;
    private final Mem mem;
    private final Swap swap;
    private final Cgroup cgroup;

    public OsStats(final long timestamp, final Cpu cpu, final Mem mem, final Swap swap, final Cgroup cgroup) {
        this.timestamp = timestamp;
        this.cpu = Objects.requireNonNull(cpu);
        this.mem = Objects.requireNonNull(mem);
        this.swap = Objects.requireNonNull(swap);
        this.cgroup = cgroup;
    }

    public OsStats(StreamInput in) throws IOException {
        this.timestamp = in.readVLong();
        this.cpu = new Cpu(in);
        this.mem = new Mem(in);
        this.swap = new Swap(in);
        this.cgroup = new Cgroup(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeVLong(timestamp);
        cpu.writeTo(out);
        mem.writeTo(out);
        swap.writeTo(out);
        cgroup.writeTo(out);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Cpu getCpu() { return cpu; }

    public Mem getMem() {
        return mem;
    }

    public Swap getSwap() {
        return swap;
    }

    public Cgroup getCgroup() {
        return cgroup;
    }

    static final class Fields {
        static final String OS = "os";
        static final String TIMESTAMP = "timestamp";
        static final String CPU = "cpu";
        static final String PERCENT = "percent";
        static final String LOAD_AVERAGE = "load_average";
        static final String LOAD_AVERAGE_1M = "1m";
        static final String LOAD_AVERAGE_5M = "5m";
        static final String LOAD_AVERAGE_15M = "15m";

        static final String MEM = "mem";
        static final String SWAP = "swap";
        static final String FREE = "free";
        static final String FREE_IN_BYTES = "free_in_bytes";
        static final String USED = "used";
        static final String USED_IN_BYTES = "used_in_bytes";
        static final String TOTAL = "total";
        static final String TOTAL_IN_BYTES = "total_in_bytes";

        static final String FREE_PERCENT = "free_percent";
        static final String USED_PERCENT = "used_percent";
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(Fields.OS);
        builder.field(Fields.TIMESTAMP, getTimestamp());
        cpu.toXContent(builder, params);
        mem.toXContent(builder, params);
        swap.toXContent(builder, params);
        cgroup.toXContent(builder, params);
        builder.endObject();
        return builder;
    }

    public static class Cpu implements Writeable, ToXContent {

        private final short percent;
        private final double[] loadAverage;

        public Cpu(short systemCpuPercent, double[] systemLoadAverage) {
            this.percent = systemCpuPercent;
            this.loadAverage = systemLoadAverage;
        }

        public Cpu(StreamInput in) throws IOException {
            this.percent = in.readShort();
            if (in.readBoolean()) {
                this.loadAverage = in.readDoubleArray();
            } else {
                this.loadAverage = null;
            }
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeShort(percent);
            if (loadAverage == null) {
                out.writeBoolean(false);
            } else {
                out.writeBoolean(true);
                out.writeDoubleArray(loadAverage);
            }
        }

        public short getPercent() {
            return percent;
        }

        public double[] getLoadAverage() {
            return loadAverage;
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject(Fields.CPU);
            builder.field(Fields.PERCENT, getPercent());
            if (getLoadAverage() != null && Arrays.stream(getLoadAverage()).anyMatch(load -> load != -1)) {
                builder.startObject(Fields.LOAD_AVERAGE);
                if (getLoadAverage()[0] != -1) {
                    builder.field(Fields.LOAD_AVERAGE_1M, getLoadAverage()[0]);
                }
                if (getLoadAverage()[1] != -1) {
                    builder.field(Fields.LOAD_AVERAGE_5M, getLoadAverage()[1]);
                }
                if (getLoadAverage()[2] != -1) {
                    builder.field(Fields.LOAD_AVERAGE_15M, getLoadAverage()[2]);
                }
                builder.endObject();
            }
            builder.endObject();
            return builder;
        }
    }

    public static class Swap implements Writeable, ToXContent {

        private final long total;
        private final long free;

        public Swap(long total, long free) {
            this.total = total;
            this.free = free;
        }

        public Swap(StreamInput in) throws IOException {
            this.total = in.readLong();
            this.free = in.readLong();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeLong(total);
            out.writeLong(free);
        }

        public ByteSizeValue getFree() {
            return new ByteSizeValue(free);
        }

        public ByteSizeValue getUsed() {
            return new ByteSizeValue(total - free);
        }

        public ByteSizeValue getTotal() {
            return new ByteSizeValue(total);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject(Fields.SWAP);
            builder.byteSizeField(Fields.TOTAL_IN_BYTES, Fields.TOTAL, getTotal());
            builder.byteSizeField(Fields.FREE_IN_BYTES, Fields.FREE, getFree());
            builder.byteSizeField(Fields.USED_IN_BYTES, Fields.USED, getUsed());
            builder.endObject();
            return builder;
        }
    }

    public static class Mem implements Writeable, ToXContent {

        private final long total;
        private final long free;

        public Mem(long total, long free) {
            this.total = total;
            this.free = free;
        }

        public Mem(StreamInput in) throws IOException {
            this.total = in.readLong();
            this.free = in.readLong();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeLong(total);
            out.writeLong(free);
        }

        public ByteSizeValue getTotal() {
            return new ByteSizeValue(total);
        }

        public ByteSizeValue getUsed() {
            return new ByteSizeValue(total - free);
        }

        public short getUsedPercent() {
            return calculatePercentage(getUsed().getBytes(), total);
        }

        public ByteSizeValue getFree() {
            return new ByteSizeValue(free);
        }

        public short getFreePercent() {
            return calculatePercentage(free, total);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject(Fields.MEM);
            builder.byteSizeField(Fields.TOTAL_IN_BYTES, Fields.TOTAL, getTotal());
            builder.byteSizeField(Fields.FREE_IN_BYTES, Fields.FREE, getFree());
            builder.byteSizeField(Fields.USED_IN_BYTES, Fields.USED, getUsed());
            builder.field(Fields.FREE_PERCENT, getFreePercent());
            builder.field(Fields.USED_PERCENT, getUsedPercent());
            builder.endObject();
            return builder;
        }
    }

    public static class Cgroup implements Writeable, ToXContent {

        private final String cpuAcctControlGroup;
        private final long cpuAcctUsageNanos;
        private final String cpuControlGroup;
        private final long cpuCfsPeriodMicros; // completely fair scheduler enforcement period
        private final long cpuCfsQuotaMicros; // completely fair scheduler quota
        private final CpuStat cpuStat;

        public String getCpuAcctControlGroup() {
            return cpuAcctControlGroup;
        }

        public long getCpuAcctUsageNanos() {
            return cpuAcctUsageNanos;
        }

        public String getCpuControlGroup() {
            return cpuControlGroup;
        }

        public long getCpuCfsPeriodMicros() {
            return cpuCfsPeriodMicros;
        }

        public long getCpuCfsQuotaMicros() {
            return cpuCfsQuotaMicros;
        }

        public CpuStat getCpuStat() {
            return cpuStat;
        }

        public Cgroup(
            final String cpuAcctControlGroup,
            final long cpuAcctUsageNanos,
            final String cpuControlGroup,
            final long cpuCfsPeriodMicros,
            final long cpuCfsQuotaMicros,
            final CpuStat cpuStat) {
            this.cpuAcctControlGroup = cpuAcctControlGroup;
            this.cpuAcctUsageNanos = cpuAcctUsageNanos;
            this.cpuControlGroup = cpuControlGroup;
            this.cpuCfsPeriodMicros = cpuCfsPeriodMicros;
            this.cpuCfsQuotaMicros = cpuCfsQuotaMicros;
            this.cpuStat = cpuStat;
        }

        Cgroup(final StreamInput in) throws IOException {
            cpuAcctControlGroup = in.readString();
            cpuAcctUsageNanos = in.readLong();
            cpuControlGroup = in.readString();
            cpuCfsPeriodMicros = in.readLong();
            cpuCfsQuotaMicros = in.readLong();
            if (!in.readBoolean()) {
                cpuStat = null;
            } else {
                cpuStat = new CpuStat(in);
            }
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeString(cpuAcctControlGroup);
            out.writeLong(cpuAcctUsageNanos);
            out.writeString(cpuControlGroup);
            out.writeLong(cpuCfsPeriodMicros);
            out.writeLong(cpuCfsQuotaMicros);
            if (cpuStat == null) {
                out.writeBoolean(false);
            } else {
                out.writeBoolean(true);
                cpuStat.writeTo(out);
            }
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject("cgroup");
            {
                builder.startObject("cpuacct");
                {
                    builder.field("control_group", cpuAcctControlGroup);
                    builder.field("usage_nanos", cpuAcctUsageNanos);
                }
                builder.endObject();
                builder.startObject("cpu");
                {
                    builder.field("control_group", cpuControlGroup);
                    builder.field("cfs_period_micros", cpuCfsPeriodMicros);
                    builder.field("cfs_quota_micros", cpuCfsQuotaMicros);
                    cpuStat.toXContent(builder, params);
                }
                builder.endObject();
            }
            builder.endObject();
            return builder;
        }

        public static class CpuStat implements Writeable, ToXContent {

            private final long numberOfElapsedPeriods;
            private final long numberOfTimesThrottled;
            private final long timeThrottledNanos;

            public long getNumberOfElapsedPeriods() {
                return numberOfElapsedPeriods;
            }

            public long getNumberOfTimesThrottled() {
                return numberOfTimesThrottled;
            }

            public long getTimeThrottledNanos() {
                return timeThrottledNanos;
            }

            public CpuStat(final long numberOfElapsedPeriods, final long numberOfTimesThrottled, final long timeThrottledNanos) {
                this.numberOfElapsedPeriods = numberOfElapsedPeriods;
                this.numberOfTimesThrottled = numberOfTimesThrottled;
                this.timeThrottledNanos = timeThrottledNanos;
            }

            CpuStat(final StreamInput in) throws IOException {
                numberOfElapsedPeriods = in.readLong();
                numberOfTimesThrottled = in.readLong();
                timeThrottledNanos = in.readLong();
            }

            @Override
            public void writeTo(final StreamOutput out) throws IOException {
                out.writeLong(numberOfElapsedPeriods);
                out.writeLong(numberOfTimesThrottled);
                out.writeLong(timeThrottledNanos);
            }

            @Override
            public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
                builder.startObject("stat");
                {
                    builder.field("number_of_elapsed_periods", numberOfElapsedPeriods);
                    builder.field("number_of_times_throttled", numberOfTimesThrottled);
                    builder.field("time_throttled_nanos", timeThrottledNanos);
                }
                builder.endObject();
                return builder;
            }

        }

    }

    public static short calculatePercentage(long used, long max) {
        return max <= 0 ? 0 : (short) (Math.round((100d * used) / max));
    }

}
