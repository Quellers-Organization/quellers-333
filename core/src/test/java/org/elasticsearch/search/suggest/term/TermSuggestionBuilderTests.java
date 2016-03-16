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

package org.elasticsearch.search.suggest.term;

import com.carrotsearch.randomizedtesting.generators.RandomStrings;
import org.elasticsearch.search.suggest.AbstractSuggestionBuilderTestCase;
import org.elasticsearch.search.suggest.DirectSpellcheckerSettings;
import org.elasticsearch.search.suggest.SortBy;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.SuggestionSearchContext.SuggestionContext;
import org.elasticsearch.search.suggest.term.TermSuggestionBuilder.StringDistanceImpl;
import org.elasticsearch.search.suggest.term.TermSuggestionBuilder.SuggestMode;

import java.io.IOException;
import java.util.Locale;

import static org.elasticsearch.search.suggest.DirectSpellcheckerSettings.DEFAULT_ACCURACY;
import static org.elasticsearch.search.suggest.DirectSpellcheckerSettings.DEFAULT_MAX_EDITS;
import static org.elasticsearch.search.suggest.DirectSpellcheckerSettings.DEFAULT_MAX_INSPECTIONS;
import static org.elasticsearch.search.suggest.DirectSpellcheckerSettings.DEFAULT_MAX_TERM_FREQ;
import static org.elasticsearch.search.suggest.DirectSpellcheckerSettings.DEFAULT_MIN_DOC_FREQ;
import static org.elasticsearch.search.suggest.DirectSpellcheckerSettings.DEFAULT_MIN_WORD_LENGTH;
import static org.elasticsearch.search.suggest.DirectSpellcheckerSettings.DEFAULT_PREFIX_LENGTH;
import static org.hamcrest.Matchers.containsString;

/**
 * Test the {@link TermSuggestionBuilder} class.
 */
public class TermSuggestionBuilderTests extends AbstractSuggestionBuilderTestCase<TermSuggestionBuilder> {

    /**
     *  creates random suggestion builder, renders it to xContent and back to new instance that should be equal to original
     */
    @Override
    protected TermSuggestionBuilder randomSuggestionBuilder() {
        return randomTermSuggestionBuilder();
    }

    /**
     * Creates a random TermSuggestionBuilder
     */
    public static TermSuggestionBuilder randomTermSuggestionBuilder() {
        TermSuggestionBuilder testBuilder = new TermSuggestionBuilder(randomAsciiOfLengthBetween(2, 20));
        setCommonPropertiesOnRandomBuilder(testBuilder);
        maybeSet(testBuilder::suggestMode, randomSuggestMode());
        maybeSet(testBuilder::accuracy, randomFloat());
        maybeSet(testBuilder::sort, randomSort());
        maybeSet(testBuilder::stringDistance, randomStringDistance());
        maybeSet(testBuilder::maxEdits, randomIntBetween(1, 2));
        maybeSet(testBuilder::maxInspections, randomInt(Integer.MAX_VALUE));
        maybeSet(testBuilder::maxTermFreq, randomFloat());
        maybeSet(testBuilder::prefixLength, randomInt(Integer.MAX_VALUE));
        maybeSet(testBuilder::minWordLength, randomInt(Integer.MAX_VALUE));
        maybeSet(testBuilder::minDocFreq, randomFloat());
        return testBuilder;
    }

    private static SuggestMode randomSuggestMode() {
        final int randomVal = randomIntBetween(0, 2);
        switch (randomVal) {
            case 0: return SuggestMode.MISSING;
            case 1: return SuggestMode.POPULAR;
            case 2: return SuggestMode.ALWAYS;
            default: throw new IllegalArgumentException("No suggest mode with an ordinal of " + randomVal);
        }
    }

    private static SortBy randomSort() {
        int randomVal = randomIntBetween(0, 1);
        switch (randomVal) {
            case 0: return SortBy.SCORE;
            case 1: return SortBy.FREQUENCY;
            default: throw new IllegalArgumentException("No sort mode with an ordinal of " + randomVal);
        }
    }

    private static StringDistanceImpl randomStringDistance() {
        int randomVal = randomIntBetween(0, 4);
        switch (randomVal) {
            case 0: return StringDistanceImpl.INTERNAL;
            case 1: return StringDistanceImpl.DAMERAU_LEVENSHTEIN;
            case 2: return StringDistanceImpl.LEVENSTEIN;
            case 3: return StringDistanceImpl.JAROWINKLER;
            case 4: return StringDistanceImpl.NGRAM;
            default: throw new IllegalArgumentException("No string distance algorithm with an ordinal of " + randomVal);
        }
    }

    @Override
    protected void mutateSpecificParameters(TermSuggestionBuilder builder) throws IOException {
        switch (randomIntBetween(0, 9)) {
            case 0:
                builder.suggestMode(randomValueOtherThan(builder.suggestMode(), () -> randomSuggestMode()));
                break;
            case 1:
                builder.accuracy(randomValueOtherThan(builder.accuracy(), () -> randomFloat()));
                break;
            case 2:
                builder.sort(randomValueOtherThan(builder.sort(), () -> randomSort()));
                break;
            case 3:
                builder.stringDistance(randomValueOtherThan(builder.stringDistance(), () -> randomStringDistance()));
                break;
            case 4:
                builder.maxEdits(randomValueOtherThan(builder.maxEdits(), () -> randomIntBetween(1, 2)));
                break;
            case 5:
                builder.maxInspections(randomValueOtherThan(builder.maxInspections(), () -> randomInt(Integer.MAX_VALUE)));
                break;
            case 6:
                builder.maxTermFreq(randomValueOtherThan(builder.maxTermFreq(), () -> randomFloat()));
                break;
            case 7:
                builder.prefixLength(randomValueOtherThan(builder.prefixLength(), () -> randomInt(Integer.MAX_VALUE)));
                break;
            case 8:
                builder.minWordLength(randomValueOtherThan(builder.minWordLength(), () -> randomInt(Integer.MAX_VALUE)));
                break;
            case 9:
                builder.minDocFreq(randomValueOtherThan(builder.minDocFreq(), () -> randomFloat()));
                break;
            default:
                break; // do nothing
        }
    }

    public void testInvalidParameters() throws IOException {
        // test missing field name
        try {
            new TermSuggestionBuilder(null);
            fail("Should not allow null as field name");
        } catch (NullPointerException e) {
            assertEquals("suggestion requires a field name", e.getMessage());
        }

        // test emtpy field name
        try {
            new TermSuggestionBuilder("");
            fail("Should not allow empty string as field name");
        } catch (IllegalArgumentException e) {
            assertEquals("suggestion field name is empty", e.getMessage());
        }

        TermSuggestionBuilder builder = new TermSuggestionBuilder(randomAsciiOfLengthBetween(2, 20));
        // test invalid accuracy values
        try {
            builder.accuracy(-0.5f);
            fail("Should not allow accuracy to be set to a negative value.");
        } catch (IllegalArgumentException e) {
        }
        try {
            builder.accuracy(1.1f);
            fail("Should not allow accuracy to be greater than 1.0.");
        } catch (IllegalArgumentException e) {
        }
        // test invalid max edit distance values
        try {
            builder.maxEdits(0);
            fail("Should not allow maxEdits to be less than 1.");
        } catch (IllegalArgumentException e) {
        }
        try {
            builder.maxEdits(-1);
            fail("Should not allow maxEdits to be a negative value.");
        } catch (IllegalArgumentException e) {
        }
        try {
            builder.maxEdits(3);
            fail("Should not allow maxEdits to be greater than 2.");
        } catch (IllegalArgumentException e) {
        }
        // test invalid max inspections values
        try {
            builder.maxInspections(-1);
            fail("Should not allow maxInspections to be a negative value.");
        } catch (IllegalArgumentException e) {
        }
        // test invalid max term freq values
        try {
            builder.maxTermFreq(-0.5f);
            fail("Should not allow max term freq to be a negative value.");
        } catch (IllegalArgumentException e) {
        }
        try {
            builder.maxTermFreq(1.5f);
            fail("If max term freq is greater than 1, it must be a whole number.");
        } catch (IllegalArgumentException e) {
        }
        try {
            builder.maxTermFreq(2.0f); // this should be allowed
        } catch (IllegalArgumentException e) {
            fail("A max term freq greater than 1 that is a whole number should be allowed.");
        }
        // test invalid min doc freq values
        try {
            builder.minDocFreq(-0.5f);
            fail("Should not allow min doc freq to be a negative value.");
        } catch (IllegalArgumentException e) {
        }
        try {
            builder.minDocFreq(1.5f);
            fail("If min doc freq is greater than 1, it must be a whole number.");
        } catch (IllegalArgumentException e) {
        }
        try {
            builder.minDocFreq(2.0f); // this should be allowed
        } catch (IllegalArgumentException e) {
            fail("A min doc freq greater than 1 that is a whole number should be allowed.");
        }
        // test invalid min word length values
        try {
            builder.minWordLength(0);
            fail("A min word length < 1 should not be allowed.");
        } catch (IllegalArgumentException e) {
        }
        try {
            builder.minWordLength(-1);
            fail("Should not allow min word length to be a negative value.");
        } catch (IllegalArgumentException e) {
        }
        // test invalid prefix length values
        try {
            builder.prefixLength(-1);
            fail("Should not allow prefix length to be a negative value.");
        } catch (IllegalArgumentException e) {
        }
        // test invalid size values
        try {
            builder.size(0);
            fail("Size must be a positive value.");
        } catch (IllegalArgumentException e) {
        }
        try {
            builder.size(-1);
            fail("Size must be a positive value.");
        } catch (IllegalArgumentException e) {
        }
        // null values not allowed for enums
        try {
            builder.sort(null);
            fail("Should not allow setting a null sort value.");
        } catch (NullPointerException e) {
        }
        try {
            builder.stringDistance(null);
            fail("Should not allow setting a null string distance value.");
        } catch (NullPointerException e) {
        }
        try {
            builder.suggestMode(null);
            fail("Should not allow setting a null suggest mode value.");
        } catch (NullPointerException e) {
        }
    }

    public void testDefaultValuesSet() {
        TermSuggestionBuilder builder = new TermSuggestionBuilder(randomAsciiOfLengthBetween(2, 20));
        assertEquals(DEFAULT_ACCURACY, builder.accuracy(), Float.MIN_VALUE);
        assertEquals(DEFAULT_MAX_EDITS, builder.maxEdits());
        assertEquals(DEFAULT_MAX_INSPECTIONS, builder.maxInspections());
        assertEquals(DEFAULT_MAX_TERM_FREQ, builder.maxTermFreq(), Float.MIN_VALUE);
        assertEquals(DEFAULT_MIN_DOC_FREQ, builder.minDocFreq(), Float.MIN_VALUE);
        assertEquals(DEFAULT_MIN_WORD_LENGTH, builder.minWordLength());
        assertEquals(DEFAULT_PREFIX_LENGTH, builder.prefixLength());
        assertEquals(SortBy.SCORE, builder.sort());
        assertEquals(StringDistanceImpl.INTERNAL, builder.stringDistance());
        assertEquals(SuggestMode.MISSING, builder.suggestMode());
    }

    public void testMalformedJson() {
        final String field = RandomStrings.randomAsciiOfLength(getRandom(), 10).toLowerCase(Locale.ROOT);
        String suggest = "{\n" +
                         "  \"bad-payload\" : {\n" +
                         "    \"text\" : \"the amsterdma meetpu\",\n" +
                         "    \"term\" : {\n" +
                         "      \"field\" : { \"" + field + "\" : \"bad-object\" }\n" +
                         "    }\n" +
                         "  }\n" +
                         "}";
        try {
            final SuggestBuilder suggestBuilder = SuggestBuilder.fromXContent(newParseContext(suggest), suggesters);
            fail("Should not have been able to create SuggestBuilder from malformed JSON: " + suggestBuilder);
        } catch (Exception e) {
            assertThat(e.getMessage(), containsString("parsing failed"));
        }
    }

    private void assertSpellcheckerSettings(DirectSpellcheckerSettings oldSettings, DirectSpellcheckerSettings newSettings) {
        final double delta = 0.0d;
        // make sure the objects aren't the same
        assertNotSame(oldSettings, newSettings);
        // make sure the objects aren't null
        assertNotNull(oldSettings);
        assertNotNull(newSettings);
        // and now, make sure they are equal..
        assertEquals(oldSettings.accuracy(), newSettings.accuracy(), delta);
        assertEquals(oldSettings.maxEdits(), newSettings.maxEdits());
        assertEquals(oldSettings.maxInspections(), newSettings.maxInspections());
        assertEquals(oldSettings.maxTermFreq(), newSettings.maxTermFreq(), delta);
        assertEquals(oldSettings.minDocFreq(), newSettings.minDocFreq(), delta);
        assertEquals(oldSettings.minWordLength(), newSettings.minWordLength());
        assertEquals(oldSettings.prefixLength(), newSettings.prefixLength());
        assertEquals(oldSettings.sort(), newSettings.sort());
        assertEquals(oldSettings.stringDistance().getClass(), newSettings.stringDistance().getClass());
        assertEquals(oldSettings.suggestMode().getClass(), newSettings.suggestMode().getClass());
    }

}
