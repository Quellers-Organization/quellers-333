/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.common.time;

import org.elasticsearch.core.Nullable;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Parses datetimes in ISO8601 format (and subsequences thereof)
 */
class Iso8601Parser {

    record Result(@Nullable DateTime result, int errorIndex) {
        Result(DateTime result) {
            this(result, -1);
        }

        static Result error(int errorIndex) {
            return new Result(null, errorIndex);
        }
    }

    private static final Set<ChronoField> VALID_MANDATORY_FIELDS = EnumSet.of(
        ChronoField.YEAR,
        ChronoField.MONTH_OF_YEAR,
        ChronoField.DAY_OF_MONTH,
        ChronoField.HOUR_OF_DAY,
        ChronoField.MINUTE_OF_HOUR,
        ChronoField.SECOND_OF_MINUTE
    );

    private static final Set<ChronoField> VALID_DEFAULT_FIELDS = EnumSet.of(
        ChronoField.MONTH_OF_YEAR,
        ChronoField.DAY_OF_MONTH,
        ChronoField.HOUR_OF_DAY,
        ChronoField.MINUTE_OF_HOUR,
        ChronoField.SECOND_OF_MINUTE,
        ChronoField.NANO_OF_SECOND
    );

    private final Set<ChronoField> mandatoryFields;
    private final boolean optionalTime;
    private final Map<ChronoField, Integer> defaults;

    /**
     * Constructs a new {@code Iso8601Parser} object
     *
     * @param mandatoryFields
     *          The set of fields that must be present for a valid parse. These should be specified in field order
     *          (eg if {@link ChronoField#DAY_OF_MONTH} is specified, {@link ChronoField#MONTH_OF_YEAR} should also be specified).
     *          {@link ChronoField#YEAR} is always mandatory.
     * @param optionalTime
     *          {@code false} if the presence of time fields follows {@code mandatoryFields},
     *          {@code true} if a time component is always optional, despite the presence of time fields in {@code mandatoryFields}.
     *          This makes it possible to specify 'time is optional, but if it is present, it must have these fields'
     *          by settings {@code optionalTime = true} and putting time fields such as {@link ChronoField#HOUR_OF_DAY}
     *          and {@link ChronoField#MINUTE_OF_HOUR} in {@code mandatoryFields}.
     * @param defaults
     *          Map of default field values, if they are not present in the parsed string.
     */
    Iso8601Parser(Set<ChronoField> mandatoryFields, boolean optionalTime, Map<ChronoField, Integer> defaults) {
        checkChronoFields(mandatoryFields, VALID_MANDATORY_FIELDS);
        checkChronoFields(defaults.keySet(), VALID_DEFAULT_FIELDS);

        this.mandatoryFields = EnumSet.of(ChronoField.YEAR); // year is always mandatory
        this.mandatoryFields.addAll(mandatoryFields);
        this.optionalTime = optionalTime;
        this.defaults = defaults.isEmpty() ? Map.of() : new EnumMap<>(defaults);
    }

    private static void checkChronoFields(Set<ChronoField> fields, Set<ChronoField> validFields) {
        if (fields.isEmpty()) return;   // nothing to check

        fields = EnumSet.copyOf(fields);
        fields.removeAll(validFields);
        if (fields.isEmpty() == false) {
            throw new IllegalArgumentException("Invalid chrono fields specified " + fields);
        }
    }

    boolean optionalTime() {
        return optionalTime;
    }

    Set<ChronoField> mandatoryFields() {
        return mandatoryFields;
    }

    private boolean isOptional(ChronoField field) {
        return mandatoryFields.contains(field) == false;
    }

    private Integer defaultZero(ChronoField field) {
        return defaults.getOrDefault(field, 0);
    }

    Result tryParse(CharSequence str, @Nullable ZoneId defaultTimezone) {
        if (str.charAt(0) == '-') {
            // the year is negative. This is most unusual.
            // Instead of always adding offsets and dynamically calculating position in the main parser code below,
            // just in case it starts with a -, just parse the substring, then adjust the output appropriately
            Result result = parse(new CharSubSequence(str, 1, str.length()), defaultTimezone);

            if (result.errorIndex() >= 0) {
                return Result.error(result.errorIndex() + 1);
            } else {
                DateTime dt = result.result();
                return new Result(
                    new DateTime(
                        -dt.years(),
                        dt.months(),
                        dt.days(),
                        dt.hours(),
                        dt.minutes(),
                        dt.seconds(),
                        dt.nanos(),
                        dt.zoneId(),
                        dt.offset()
                    )
                );
            }
        } else {
            return parse(str, defaultTimezone);
        }
    }

    private Result parse(CharSequence str, @Nullable ZoneId defaultTimezone) {
        int len = str.length();

        // YEARS
        Integer years = parseInt(str, 0, 4);
        if (years == null) return Result.error(0);
        if (len == 4) {
            return isOptional(ChronoField.MONTH_OF_YEAR)
                ? new Result(
                    withZoneOffset(
                        years,
                        defaults.get(ChronoField.MONTH_OF_YEAR),
                        defaults.get(ChronoField.DAY_OF_MONTH),
                        defaults.get(ChronoField.HOUR_OF_DAY),
                        defaults.get(ChronoField.MINUTE_OF_HOUR),
                        defaults.get(ChronoField.SECOND_OF_MINUTE),
                        defaults.get(ChronoField.NANO_OF_SECOND),
                        defaultTimezone
                    )
                )
                : Result.error(4);
        }

        if (str.charAt(4) != '-') return Result.error(4);

        // MONTHS
        Integer months = parseInt(str, 5, 7);
        if (months == null || months > 12) return Result.error(5);
        if (len == 7) {
            return isOptional(ChronoField.DAY_OF_MONTH)
                ? new Result(
                    withZoneOffset(
                        years,
                        months,
                        defaults.get(ChronoField.DAY_OF_MONTH),
                        defaults.get(ChronoField.HOUR_OF_DAY),
                        defaults.get(ChronoField.MINUTE_OF_HOUR),
                        defaults.get(ChronoField.SECOND_OF_MINUTE),
                        defaults.get(ChronoField.NANO_OF_SECOND),
                        defaultTimezone
                    )
                )
                : Result.error(7);
        }

        if (str.charAt(7) != '-') return Result.error(7);

        // DAYS
        Integer days = parseInt(str, 8, 10);
        if (days == null || days > 31) return Result.error(8);
        if (len == 10) {
            return optionalTime || isOptional(ChronoField.HOUR_OF_DAY)
                ? new Result(
                    withZoneOffset(
                        years,
                        months,
                        days,
                        defaults.get(ChronoField.HOUR_OF_DAY),
                        defaults.get(ChronoField.MINUTE_OF_HOUR),
                        defaults.get(ChronoField.SECOND_OF_MINUTE),
                        defaults.get(ChronoField.NANO_OF_SECOND),
                        defaultTimezone
                    )
                )
                : Result.error(10);
        }

        if (str.charAt(10) != 'T') return Result.error(10);
        if (len == 11) {
            return isOptional(ChronoField.HOUR_OF_DAY)
                ? new Result(
                    withZoneOffset(
                        years,
                        months,
                        days,
                        defaults.get(ChronoField.HOUR_OF_DAY),
                        defaults.get(ChronoField.MINUTE_OF_HOUR),
                        defaults.get(ChronoField.SECOND_OF_MINUTE),
                        defaults.get(ChronoField.NANO_OF_SECOND),
                        defaultTimezone
                    )
                )
                : Result.error(11);
        }

        // HOURS + timezone
        Integer hours = parseInt(str, 11, 13);
        if (hours == null || hours > 23) return Result.error(11);
        if (len == 13) {
            return isOptional(ChronoField.MINUTE_OF_HOUR)
                ? new Result(
                    withZoneOffset(
                        years,
                        months,
                        days,
                        hours,
                        defaultZero(ChronoField.MINUTE_OF_HOUR),
                        defaultZero(ChronoField.SECOND_OF_MINUTE),
                        defaultZero(ChronoField.NANO_OF_SECOND),
                        defaultTimezone
                    )
                )
                : Result.error(13);
        }
        if (isZoneId(str, 13)) {
            ZoneId timezone = parseZoneId(str, 13);
            return timezone != null && isOptional(ChronoField.MINUTE_OF_HOUR)
                ? new Result(
                    withZoneOffset(
                        years,
                        months,
                        days,
                        hours,
                        defaultZero(ChronoField.MINUTE_OF_HOUR),
                        defaultZero(ChronoField.SECOND_OF_MINUTE),
                        defaultZero(ChronoField.NANO_OF_SECOND),
                        timezone
                    )
                )
                : Result.error(13);
        }

        if (str.charAt(13) != ':') return Result.error(13);

        // MINUTES + timezone
        Integer minutes = parseInt(str, 14, 16);
        if (minutes == null || minutes > 59) return Result.error(14);
        if (len == 16) {
            return isOptional(ChronoField.SECOND_OF_MINUTE)
                ? new Result(
                    withZoneOffset(
                        years,
                        months,
                        days,
                        hours,
                        minutes,
                        defaultZero(ChronoField.SECOND_OF_MINUTE),
                        defaultZero(ChronoField.NANO_OF_SECOND),
                        defaultTimezone
                    )
                )
                : Result.error(16);
        }
        if (isZoneId(str, 16)) {
            ZoneId timezone = parseZoneId(str, 16);
            return timezone != null && isOptional(ChronoField.SECOND_OF_MINUTE)
                ? new Result(
                    withZoneOffset(
                        years,
                        months,
                        days,
                        hours,
                        minutes,
                        defaultZero(ChronoField.SECOND_OF_MINUTE),
                        defaultZero(ChronoField.NANO_OF_SECOND),
                        timezone
                    )
                )
                : Result.error(16);
        }

        if (str.charAt(16) != ':') return Result.error(16);

        // SECONDS + timezone
        Integer seconds = parseInt(str, 17, 19);
        if (seconds == null || seconds > 59) return Result.error(17);
        if (len == 19) {
            return new Result(
                withZoneOffset(years, months, days, hours, minutes, seconds, defaultZero(ChronoField.NANO_OF_SECOND), defaultTimezone)
            );
        }
        if (isZoneId(str, 19)) {
            ZoneId timezone = parseZoneId(str, 19);
            return timezone != null
                ? new Result(
                    withZoneOffset(years, months, days, hours, minutes, seconds, defaultZero(ChronoField.NANO_OF_SECOND), timezone)
                )
                : Result.error(19);
        }

        char decSeparator = str.charAt(19);
        if (decSeparator != '.' && decSeparator != ',') return Result.error(19);

        // NANOS + timezone
        // nanos are always optional
        // the last number could be millis or nanos, or any combination in the middle
        // so we keep parsing numbers until we get to not a number
        int nanos = 0;
        int pos;
        for (pos = 20; pos < len && pos < 29; pos++) {
            char c = str.charAt(pos);
            if (c < ZERO || c > NINE) break;
            nanos = nanos * 10 + (c - ZERO);
        }

        if (pos == 20) return Result.error(20);   // didn't find a number at all

        // multiply it by the remainder of the nano digits missed off the end
        for (int pow10 = 29 - pos; pow10 > 0; pow10--) {
            nanos *= 10;
        }

        if (len == pos) {
            return new Result(withZoneOffset(years, months, days, hours, minutes, seconds, nanos, defaultTimezone));
        }
        if (isZoneId(str, pos)) {
            ZoneId timezone = parseZoneId(str, pos);
            return timezone != null
                ? new Result(withZoneOffset(years, months, days, hours, minutes, seconds, nanos, timezone))
                : Result.error(pos);
        }

        // still chars left at the end - string is not valid
        return Result.error(pos);
    }

    private static boolean isZoneId(CharSequence str, int pos) {
        // all region zoneIds must start with [A-Za-z] (see ZoneId#of)
        // this also covers Z and UT/UTC/GMT zone variants
        char c = str.charAt(pos);
        return c == '+' || c == '-' || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    /**
     * This parses the zone offset, which is of the format accepted by {@link java.time.ZoneId#of(String)}.
     * It has fast paths for numerical offsets, but falls back on {@code ZoneId.of} for non-trivial zone ids.
     */
    private ZoneId parseZoneId(CharSequence str, int pos) {
        int len = str.length();
        char first = str.charAt(pos);

        if (first == 'Z' && len == pos + 1) {
            return ZoneOffset.UTC;
        }

        boolean positive;
        switch (first) {
            case '+' -> positive = true;
            case '-' -> positive = false;
            default -> {
                // non-trivial zone offset, fallback on the built-in java zoneid parser
                try {
                    return ZoneId.of(str.subSequence(pos, str.length()).toString());
                } catch (DateTimeException e) {
                    return null;
                }
            }
        }
        pos++;  // read the + or -

        Integer hours = parseInt(str, pos, pos += 2);
        if (hours == null) return null;
        if (len == pos) return ofHoursMinutesSeconds(hours, 0, 0, positive);

        boolean hasColon = false;
        if (str.charAt(pos) == ':') {
            pos++;
            hasColon = true;
        }

        Integer minutes = parseInt(str, pos, pos += 2);
        if (minutes == null) return null;
        if (len == pos) return ofHoursMinutesSeconds(hours, minutes, 0, positive);

        // either both dividers have a colon, or neither do
        if ((str.charAt(pos) == ':') != hasColon) return null;
        if (hasColon) {
            pos++;
        }

        Integer seconds = parseInt(str, pos, pos += 2);
        if (seconds == null) return null;
        if (len == pos) return ofHoursMinutesSeconds(hours, minutes, seconds, positive);

        // there's some text left over...
        return null;
    }

    /*
     * ZoneOffset.ofTotalSeconds has a ConcurrentHashMap cache of offsets. This is fine,
     * but it does mean there's an expensive map lookup every time we call ofTotalSeconds.
     * There's no way to get round that, but we can at least have a very quick last-value cache here
     * to avoid doing a full map lookup when there's lots of timestamps with the same offset being parsed
     */
    private final ThreadLocal<ZoneOffset> lastOffset = ThreadLocal.withInitial(() -> ZoneOffset.UTC);

    private ZoneOffset ofHoursMinutesSeconds(int hours, int minutes, int seconds, boolean positive) {
        int totalSeconds = hours * 3600 + minutes * 60 + seconds;
        if (positive == false) {
            totalSeconds = -totalSeconds;
        }

        // check the lastOffset value
        ZoneOffset lastOffset = this.lastOffset.get();
        if (totalSeconds == lastOffset.getTotalSeconds()) {
            return lastOffset;
        }

        try {
            ZoneOffset offset = ZoneOffset.ofTotalSeconds(totalSeconds);
            this.lastOffset.set(lastOffset);
            return offset;
        } catch (DateTimeException e) {
            // zoneoffset is out of range
            return null;
        }
    }

    /**
     * Specify ZoneOffset when we can
     */
    private static DateTime withZoneOffset(
        int years,
        Integer months,
        Integer days,
        Integer hours,
        Integer minutes,
        Integer seconds,
        Integer nanos,
        ZoneId zoneId
    ) {
        if (zoneId instanceof ZoneOffset zo) {
            return new DateTime(years, months, days, hours, minutes, seconds, nanos, zoneId, zo);
        } else {
            return new DateTime(years, months, days, hours, minutes, seconds, nanos, zoneId, null);
        }
    }

    private static final char ZERO = '0';
    private static final char NINE = '9';

    private static Integer parseInt(CharSequence str, int startInclusive, int endExclusive) {
        if (str.length() < endExclusive) return null;

        int result = 0;
        for (int i = startInclusive; i < endExclusive; i++) {
            char c = str.charAt(i);
            if (c < ZERO || c > NINE) return null;
            result = result * 10 + (c - ZERO);
        }
        return result;
    }
}
