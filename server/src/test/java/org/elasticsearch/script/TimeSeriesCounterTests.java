/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.script;

import org.elasticsearch.test.ESTestCase;
import org.hamcrest.Matcher;
import org.junit.Before;

import java.util.ArrayList;
import java.util.List;

import static org.elasticsearch.script.TimeSeriesCounter.Counter;
import static org.elasticsearch.script.TimeSeriesCounter.HOUR;
import static org.hamcrest.Matchers.lessThan;

public class TimeSeriesCounterTests extends ESTestCase {
    protected long now;
    protected long customCounterResolution;
    protected long customCounterDuration;
    protected TimeSeriesCounter tsc = new TimeSeriesCounter();
    protected final Matcher<Long> fiveDelta = lessThan(tsc.fiveMinutes.resolution);
    protected final Matcher<Long> fifteenDelta = lessThan(tsc.fifteenMinutes.resolution);
    protected final Matcher<Long> twentyFourDelta = lessThan(tsc.twentyFourHours.resolution);
    protected List<Long> events;
    protected Counter counter;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        now = 1635182590;
        customCounterResolution = 45;
        customCounterDuration = 900;
        reset();
    }

    protected void reset() {
        tsc = new TimeSeriesCounter();
        events = new ArrayList<>();
        counter = new Counter(customCounterResolution, customCounterDuration);
    }

    public void testCounterNegativeResolution() {
        IllegalArgumentException iae = expectThrows(IllegalArgumentException.class, () -> new Counter(-20, 200));
        assertEquals("resolution [-20] must be greater than zero", iae.getMessage());
    }

    public void testCounterNegativeDuration() {
        IllegalArgumentException iae = expectThrows(IllegalArgumentException.class, () -> new Counter(20, -200));
        assertEquals("duration [-200] must be greater than zero", iae.getMessage());
    }

    public void testCounterIndivisibleResolution() {
        IllegalArgumentException iae = expectThrows(IllegalArgumentException.class, () -> new Counter(3, 101));
        assertEquals("duration [101] must divisible by resolution [3]", iae.getMessage());
    }

    public void testOnePerSecond() {
        long time = now;
        long t;
        long next = randomLongBetween(1, HOUR);
        long twentyFive = 25 * HOUR;
        for (int i=0; i < twentyFive; i++) {
            t = time + i;
            inc(t);

            if (i == next) {
                TimeSeries ts = tsc.timeSeries(t);
                assertThat(five(t) - ts.fiveMinutes, fiveDelta);
                assertThat(fifteen(t) - ts.fifteenMinutes, fifteenDelta);
                assertThat(twentyFour(t) - ts.twentyFourHours, twentyFourDelta);
                assertEquals(i + 1, tsc.count());

                next = Math.min(twentyFive, next + randomLongBetween(HOUR, 3 * HOUR));
            }
        }
    }

    public void testCounterIncrementSameBucket() {
        long resolution = 45;
        long duration = 900;
        counter.inc(now);
        long count = randomLongBetween(resolution / 2, resolution * 2);
        long start = (now / resolution) * resolution;
        for (int i = 1; i < count; i++) {
            counter.inc(start + randomLongBetween(0, resolution - 1));
        }

        assertEquals(count, counter.sum(start));
        assertEquals(count, counter.sum(now));

        long t = 0;
        for (; t <= duration; t += resolution) {
            assertEquals(count, counter.sum(start + t));
        }
        assertEquals(0, counter.sum(start + t));
        assertEquals(0, counter.sum(start + duration + resolution));
        assertEquals(count, counter.sum(start + duration + resolution - 1));
    }


    public void testFiveMinuteSameBucket() {
        inc(now);
        long resolution = tsc.fiveMinutes.resolution;
        long duration = tsc.fiveMinutes.duration;
        long count = randomLongBetween(1, resolution);
        long start = (now / resolution) * resolution;
        for (int i = 1; i < count; i++) {
            inc(start + i);
        }
        assertEquals(count, tsc.count());
        assertEquals(count, tsc.timeSeries(now).fiveMinutes);

        long t = 0;
        for (; t <= duration; t += resolution) {
            assertEquals(count, tsc.timeSeries(start + t).fiveMinutes);
        }

        TimeSeries series = tsc.timeSeries(start + t);
        assertEquals(0, series.fiveMinutes);
        assertEquals(count, series.fifteenMinutes);
        assertEquals(count, series.twentyFourHours);

        series = tsc.timeSeries(start + duration + resolution);
        assertEquals(0, series.fiveMinutes);
        assertEquals(count, series.fifteenMinutes);
        assertEquals(count, series.twentyFourHours);
        assertEquals(count, tsc.timeSeries(start + duration + resolution - 1).fiveMinutes);
    }

    public void testFifteenMinuteSameBucket() {
        inc(now);
        long resolution = tsc.fifteenMinutes.resolution;
        long duration = tsc.fifteenMinutes.duration;
        long start = (now / resolution) * resolution;
        long count = randomLongBetween(1, resolution);
        for (int i = 1; i < count; i++) {
            inc(start + i);
        }
        assertEquals(count, tsc.count());
        assertEquals(count, tsc.timeSeries(now).fifteenMinutes);

        long t = 0;
        for (; t <= duration; t += resolution) {
            assertEquals(count, tsc.timeSeries(start + t).fifteenMinutes);
        }

        TimeSeries series = tsc.timeSeries(start + t);
        assertEquals(0, series.fiveMinutes);
        assertEquals(0, series.fifteenMinutes);
        assertEquals(count, series.twentyFourHours);

        series = tsc.timeSeries(start + duration + resolution);
        assertEquals(0, series.fiveMinutes);
        assertEquals(0, series.fifteenMinutes);
        assertEquals(count, series.twentyFourHours);
        assertEquals(count, tsc.timeSeries(start + duration + resolution - 1).fifteenMinutes);
    }

    public void testCounterIncrementBucket() {
        long count = customCounterDuration / customCounterResolution;
        for (int i = 0; i < count; i++) {
            counter.inc(now + i * customCounterResolution);
        }
        assertEquals(count, counter.sum(now + customCounterDuration));
        assertEquals(count - 1, counter.sum(now + customCounterDuration + customCounterResolution));
        assertEquals(count - 2, counter.sum(now + customCounterDuration + (2 * customCounterResolution)));
        counter.inc(now + customCounterDuration);
        assertEquals(count, counter.sum(now + customCounterDuration + customCounterResolution));
    }

    public void testFiveMinuteIncrementBucket() {
        int count = tsc.fiveMinutes.buckets.length;
        long resolution = tsc.fiveMinutes.resolution;
        long duration = tsc.fiveMinutes.duration;
        for (int i = 0; i < count; i++) {
            inc(now + i * resolution);
        }

        TimeSeries ts = tsc.timeSeries(now + duration);
        assertEquals(count, ts.fiveMinutes);
        assertEquals(count, ts.fifteenMinutes);
        assertEquals(count, ts.twentyFourHours);
        assertEquals(count, tsc.count());

        ts = tsc.timeSeries(now + duration + resolution);
        assertEquals(count - 1, ts.fiveMinutes);
        assertEquals(count, ts.fifteenMinutes);
        assertEquals(count, ts.twentyFourHours);

        ts = tsc.timeSeries(now + duration + (2 * resolution));
        assertEquals(count - 2, ts.fiveMinutes);
        assertEquals(count, ts.fifteenMinutes);
        assertEquals(count, ts.twentyFourHours);

        inc(now + duration);
        ts = tsc.timeSeries(now + duration + resolution);
        assertEquals(count, ts.fiveMinutes);
        assertEquals(count + 1, ts.fifteenMinutes);
        assertEquals(count + 1, ts.twentyFourHours);
        assertEquals(count + 1, tsc.count());
    }

    public void testFifteenMinuteIncrementBucket() {
        int count = tsc.fifteenMinutes.buckets.length;
        long resolution = tsc.fifteenMinutes.resolution;
        long duration = tsc.fifteenMinutes.duration;
        for (int i = 0; i < count; i++) {
            long t = now + i * resolution;
            inc(t);
        }
        long t = now + duration;
        TimeSeries ts = tsc.timeSeries(t);
        assertEquals(five(t), ts.fiveMinutes);
        assertEquals(count, ts.fifteenMinutes);
        assertEquals(count, ts.twentyFourHours);

        t = now + duration + resolution;
        ts = tsc.timeSeries(t);
        assertEquals(five(t), ts.fiveMinutes);
        assertEquals(count - 1, ts.fifteenMinutes);
        assertEquals(count, ts.twentyFourHours);

        t = now + duration + (2 * resolution);
        ts = tsc.timeSeries(t);
        assertEquals(five(t), ts.fiveMinutes);
        assertEquals(count - 2, ts.fifteenMinutes);
        assertEquals(count, ts.twentyFourHours);

        inc(now + duration);
        t = now + duration + resolution;
        ts = tsc.timeSeries(t);
        assertEquals(five(t), ts.fiveMinutes);
        assertEquals(count, ts.fifteenMinutes);
        assertEquals(count + 1, ts.twentyFourHours);
        assertEquals(count + 1, tsc.count());
    }

    public void testCounterSkipBuckets() {
        int count = (int) (customCounterDuration / customCounterResolution);
        for (int skip = 1; skip <= count; skip++) {
            reset();
            int increments = 0;
            for (int i = 0; (i * skip * customCounterResolution) < customCounterDuration; i++) {
                counter.inc(now + (i * skip * customCounterResolution));
                increments++;
            }
            assertEquals(increments, counter.sum(now + customCounterDuration));
        }
    }

    public void testFiveMinuteSkipBucket() {
        int count = tsc.fiveMinutes.buckets.length;
        long resolution = tsc.fiveMinutes.resolution;
        long duration = tsc.fiveMinutes.duration;
        for (int skip = 1; skip <= count; skip++) {
            tsc = new TimeSeriesCounter();
            long increments = 0;
            for (int i = 0; (i * skip * resolution) < duration; i++) {
                inc(now + (i * skip * resolution));
                increments++;
            }

            TimeSeries series = tsc.timeSeries(now + duration);
            assertEquals(increments, series.fiveMinutes);
            assertEquals(increments, series.fifteenMinutes);
            assertEquals(increments, series.twentyFourHours);
            assertEquals(increments, tsc.count());
        }
    }

    public void testFifteenMinuteSkipBuckets() {
        int count = tsc.fifteenMinutes.buckets.length;
        long resolution = tsc.fifteenMinutes.resolution;
        long duration = tsc.fifteenMinutes.duration;
        for (int skip = 1; skip <= count; skip++) {
            reset();
            for (int i = 0; (i * skip * resolution) < duration; i++) {
                inc(now + (i * skip * resolution));
            }
            TimeSeries ts = tsc.timeSeries(now + duration);
            assertEquals(five(now + duration), ts.fiveMinutes);
            assertEquals(events.size(), ts.fifteenMinutes);
            assertEquals(events.size(), ts.twentyFourHours);
            assertEquals(events.size(), tsc.count());
        }
    }

    public void testCounterReset() {
        long time = now;
        for (int i=0; i < 20; i++) {
            long count = 0;
            long withinBucket = randomIntBetween(1, (int) (customCounterResolution / 2));
            time += customCounterResolution + (i * customCounterDuration);
            long last = time;
            for (int j=0; j < withinBucket; j++) {
                long bucketTime = (time / customCounterResolution) * customCounterResolution;
                last = bucketTime + randomLongBetween(0, customCounterResolution - 1);
                counter.inc(last);
                count++;
            }
            assertEquals(count, counter.sum(last));
        }
    }

    public void testFiveMinuteReset() {
        long time = now;
        long resolution = tsc.fiveMinutes.resolution;
        long duration = tsc.fiveMinutes.duration;
        for (int i=0; i < 20; i++) {
            long withinBucket = randomLongBetween(1, resolution);
            time += resolution + (i * duration);
            for (int j=0; j < withinBucket; j++) {
                inc(time + j);
            }
            TimeSeries ts = tsc.timeSeries(time);
            assertThat(five(time) - ts.fiveMinutes, fiveDelta);
            assertThat(fifteen(time) - ts.fifteenMinutes, fifteenDelta);
            assertThat(twentyFour(time) - ts.twentyFourHours, twentyFourDelta);
            assertEquals(events.size(), tsc.count());
        }
    }

    public void testFifteenMinuteReset() {
        long time = now;
        long resolution = tsc.fifteenMinutes.resolution;
        long duration = tsc.fifteenMinutes.duration;
        for (int i=0; i < 20; i++) {
            long withinBucket = randomLongBetween(1, resolution);
            time += resolution + (i * duration);
            for (int j=0; j < withinBucket; j++) {
                inc(time + j);
            }
            TimeSeries ts = tsc.timeSeries(time);
            assertThat(five(time) - ts.fiveMinutes, fiveDelta);
            assertThat(fifteen(time) - ts.fifteenMinutes, fifteenDelta);
            assertThat(twentyFour(time) - ts.twentyFourHours, twentyFourDelta);
            assertEquals(events.size(), tsc.count());
        }
    }

    // Count the last five minutes of events before t
    public long five(long t) {
        return countLast(t, tsc.fiveMinutes, events);
    }

    // Count the last fifteen minutes of events before t
    public long fifteen(long t) {
        return countLast(t, tsc.fifteenMinutes, events);
    }

    // Count the last twenty-four hours of events before t
    public long twentyFour(long t) {
        return countLast(t, tsc.twentyFourHours, events);
    }

    // Count the last set of events that would be recorded by counter
    public long countLast(long t, Counter counter, List<Long> events) {
        long count = 0;
        long after = ((t - counter.duration) / counter.resolution) * counter.resolution;
        for (long event : events) {
            if (event > after) {
                count++;
            }
        }
        return count;
    }

    private void inc(long t) {
        tsc.inc(t);
        events.add(t);
    }
}
