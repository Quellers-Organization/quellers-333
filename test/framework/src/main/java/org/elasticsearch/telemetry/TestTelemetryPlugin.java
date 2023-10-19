/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.telemetry;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.TelemetryPlugin;
import org.elasticsearch.telemetry.metric.Instrument;
import org.elasticsearch.telemetry.metric.MeterRegistry;
import org.elasticsearch.telemetry.metric.MeterService;
import org.elasticsearch.telemetry.tracing.Tracer;

import java.util.List;

public class TestTelemetryPlugin extends Plugin implements TelemetryPlugin {

    protected final RecordingMeterRegistry meter = new RecordingMeterRegistry();

    Registration getRegistration(Instrument instrument) {
        return meter.getRecorder().getRegistration(instrument);
    }

    public List<Measurement> getMetrics(Instrument instrument) {
        return meter.getRecorder().getMetrics(instrument);
    }

    public List<Measurement> getDoubleCounterMeasurement(String name) {
        return meter.getRecorder().getMetrics(InstrumentType.DOUBLE_COUNTER, name);
    }

    public List<Measurement> getLongCounterMeasurement(String name) {
        return meter.getRecorder().getMetrics(InstrumentType.LONG_COUNTER, name);
    }

    public List<Measurement> getDoubleUpDownCounterMeasurement(String name) {
        return meter.getRecorder().getMetrics(InstrumentType.DOUBLE_UP_DOWN_COUNTER, name);
    }

    public List<Measurement> getLongUpDownCounterMeasurement(String name) {
        return meter.getRecorder().getMetrics(InstrumentType.LONG_UP_DOWN_COUNTER, name);
    }

    public List<Measurement> getDoubleGaugeMeasurement(String name) {
        return meter.getRecorder().getMetrics(InstrumentType.DOUBLE_GAUGE, name);
    }

    public List<Measurement> getLongGaugeMeasurement(String name) {
        return meter.getRecorder().getMetrics(InstrumentType.LONG_GAUGE, name);
    }

    public List<Measurement> getDoubleHistogramMeasurement(String name) {
        return meter.getRecorder().getMetrics(InstrumentType.DOUBLE_HISTOGRAM, name);
    }

    public List<Measurement> getLongHistogramMeasurement(String name) {
        return meter.getRecorder().getMetrics(InstrumentType.LONG_HISTOGRAM, name);
    }

    @Override
    public TelemetryProvider getTelemetryProvider(Settings settings) {
        return new TelemetryProvider() {
            @Override
            public Tracer getTracer() {
                return Tracer.NOOP;
            }

            @Override
            public MeterRegistry getMeterRegistry() {
                return meter;
            }

            @Override
            public MeterService getMeterService() {
                return null;
            }
        };
    }
}
