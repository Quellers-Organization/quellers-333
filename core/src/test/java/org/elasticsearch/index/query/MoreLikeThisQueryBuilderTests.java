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

package org.elasticsearch.index.query;

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.termvectors.*;
import org.elasticsearch.common.ParseFieldMatcher;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.lucene.search.MoreLikeThisQuery;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.VersionType;
import org.elasticsearch.index.query.MoreLikeThisQueryBuilder.Item;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.*;

public class MoreLikeThisQueryBuilderTests extends AbstractQueryTestCase<MoreLikeThisQueryBuilder> {

    private static String[] randomFields;
    private static Item[] randomLikeItems;
    private static Item[] randomUnlikeItems;

    @Before
    public void setup() {
        // MLT only supports string fields, unsupported fields are tested below
        randomFields = randomStringFields();
        // we also preset the item requests
        randomLikeItems = new Item[randomIntBetween(1, 3)];
        for (int i = 0; i < randomLikeItems.length; i++) {
            randomLikeItems[i] = generateRandomItem();
        }
        // and for the unlike items too
        randomUnlikeItems = new Item[randomIntBetween(1, 3)];
        for (int i = 0; i < randomUnlikeItems.length; i++) {
            randomUnlikeItems[i] = generateRandomItem();
        }
    }

    private static String[] randomStringFields() {
        String[] mappedStringFields = new String[]{STRING_FIELD_NAME, STRING_FIELD_NAME_2};
        String[] unmappedStringFields = generateRandomStringArray(2, 5, false, false);
        return Stream.concat(Arrays.stream(mappedStringFields), Arrays.stream(unmappedStringFields)).toArray(String[]::new);
    }

    private Item generateRandomItem() {
        Item item = new Item(
                randomBoolean() ? getIndex().getName() : null,
                getRandomType(),  // set to one type to avoid ambiguous types
                randomAsciiOfLength(5))
                .routing(randomAsciiOfLength(10))
                .version(randomInt(5))
                .versionType(randomFrom(VersionType.values()));
        // if no field is specified MLT uses all mapped fields for this item
        if (randomBoolean()) {
            item.fields(randomFrom(randomFields));
        }
        return item;
        // delegate artificial documents and per field analyzers to the tests further below
    }

    @Override
    protected MoreLikeThisQueryBuilder doCreateTestQueryBuilder() {
        MoreLikeThisQueryBuilder queryBuilder;
        if (randomBoolean()) { // for the default field
            queryBuilder = new MoreLikeThisQueryBuilder();
        } else {
            queryBuilder = new MoreLikeThisQueryBuilder(randomFields);
        }
        // like field is required
        if (randomBoolean()) {
            queryBuilder.like(generateRandomStringArray(5, 5, false, false));
        } else {
            queryBuilder.like(randomLikeItems);
        }
        if (randomBoolean()) {
            queryBuilder.unlike(generateRandomStringArray(5, 5, false, false));
        }
        if (randomBoolean()) {
            queryBuilder.unlike(randomUnlikeItems);
        }
        if (randomBoolean()) {
            queryBuilder.maxQueryTerms(randomInt(25));
        }
        if (randomBoolean()) {
            queryBuilder.minTermFreq(randomInt(5));
        }
        if (randomBoolean()) {
            queryBuilder.minDocFreq(randomInt(5));
        }
        if (randomBoolean()) {
            queryBuilder.maxDocFreq(randomInt(100));
        }
        if (randomBoolean()) {
            queryBuilder.minWordLength(randomInt(5));
        }
        if (randomBoolean()) {
            queryBuilder.maxWordLength(randomInt(25));
        }
        if (randomBoolean()) {
            queryBuilder.stopWords(generateRandomStringArray(5, 5, false));
        }
        if (randomBoolean()) {
            queryBuilder.analyzer(randomFrom("simple", "keyword", "whitespace"));  // fix the analyzer?
        }
        if (randomBoolean()) {
            queryBuilder.minimumShouldMatch(randomMinimumShouldMatch());
        }
        if (randomBoolean()) {
            queryBuilder.boostTerms(randomFloat() * 10);
        }
        if (randomBoolean()) {
            queryBuilder.include(true);
        }
        if (randomBoolean()) {
            queryBuilder.failOnUnsupportedField(false);
        }
        return queryBuilder;
    }

    @Override
    protected MultiTermVectorsResponse executeMultiTermVectors(MultiTermVectorsRequest mtvRequest) {
        try {
            MultiTermVectorsItemResponse[] responses = new MultiTermVectorsItemResponse[mtvRequest.size()];
            int i = 0;
            for (TermVectorsRequest request : mtvRequest) {
                TermVectorsResponse response = new TermVectorsResponse(request.index(), request.type(), request.id());
                response.setExists(true);
                Fields generatedFields = generateFields(request.selectedFields().toArray(new String[0]), request.id());
                EnumSet<TermVectorsRequest.Flag> flags = EnumSet.of(TermVectorsRequest.Flag.Positions, TermVectorsRequest.Flag.Offsets);
                response.setFields(generatedFields, request.selectedFields(), flags, generatedFields);
                responses[i++] = new MultiTermVectorsItemResponse(response, null);
            }
            return new MultiTermVectorsResponse(responses);
        } catch (IOException ex) {
            throw new ElasticsearchException("boom", ex);
        }
    }

    /**
     * Here we could go overboard and use a pre-generated indexed random document for a given Item,
     * but for now we'd prefer to simply return the id as the content of the document and that for
     * every field.
     */
    private static Fields generateFields(String[] fieldNames, String text) throws IOException {
        MemoryIndex index = new MemoryIndex();
        for (String fieldName : fieldNames) {
            index.addField(fieldName, text, new WhitespaceAnalyzer());
        }
        return MultiFields.getFields(index.createSearcher().getIndexReader());
    }

    @Override
    protected void doAssertLuceneQuery(MoreLikeThisQueryBuilder queryBuilder, Query query, QueryShardContext context) throws IOException {
        assertThat(query, anyOf(instanceOf(BooleanQuery.class), instanceOf(MoreLikeThisQuery.class)));
    }

    @Test
    public void testValidate() {
        MoreLikeThisQueryBuilder queryBuilder = new MoreLikeThisQueryBuilder(Strings.EMPTY_ARRAY);
        assertThat(queryBuilder.validate().validationErrors().size(), is(2));

        queryBuilder = new MoreLikeThisQueryBuilder(Strings.EMPTY_ARRAY).like("some text");
        assertThat(queryBuilder.validate().validationErrors().size(), is(1));

        queryBuilder = new MoreLikeThisQueryBuilder("field").like(Strings.EMPTY_ARRAY);
        assertThat(queryBuilder.validate().validationErrors().size(), is(1));

        queryBuilder = new MoreLikeThisQueryBuilder("field").like(Item.EMPTY_ARRAY);
        assertThat(queryBuilder.validate().validationErrors().size(), is(1));

        queryBuilder = new MoreLikeThisQueryBuilder("field").like("some text");
        assertNull(queryBuilder.validate());
    }

    @Test
    public void testValidateItems() {
        MoreLikeThisQueryBuilder queryBuilder = new MoreLikeThisQueryBuilder("field").like("some text");
        int totalExpectedErrors = 0;
        if (randomBoolean()) {
            queryBuilder.addLikeItem(generateRandomItem().id(null));
            totalExpectedErrors++;
        }
        if (randomBoolean()) {
            queryBuilder.addLikeItem(generateRandomItem().id(null).doc((XContentBuilder) null));
            totalExpectedErrors++;
        }
        if (randomBoolean()) {
            queryBuilder.addUnlikeItem(generateRandomItem().id(null));
            totalExpectedErrors++;
        }
        assertValidate(queryBuilder, totalExpectedErrors);
    }

    @Test
    public void testArtificialDocument() {

    }

    @Test
    public void testUnsupportedFields() throws IOException {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        String unsupportedField = randomFrom(INT_FIELD_NAME, DOUBLE_FIELD_NAME, DATE_FIELD_NAME);
        MoreLikeThisQueryBuilder queryBuilder = new MoreLikeThisQueryBuilder(unsupportedField)
                .like("some text")
                .failOnUnsupportedField(true);
        try {
            queryBuilder.toQuery(createShardContext());
            fail("should have failed with IllegalArgumentException for field: " + unsupportedField);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), Matchers.containsString("more_like_this doesn't support binary/numeric fields"));
        }
    }

    @Test
    public void testItemSerialization() throws IOException {
        Item expectedItem = generateRandomItem();
        BytesStreamOutput output = new BytesStreamOutput();
        expectedItem.writeTo(output);
        Item newItem = Item.readItemFrom(StreamInput.wrap(output.bytes()));
        assertEquals(expectedItem, newItem);
    }

    @Test
    public void testItemFromXContent() throws IOException {
        Item expectedItem = generateRandomItem();
        String json = expectedItem.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS).string();
        XContentParser parser = XContentFactory.xContent(json).createParser(json);
        Item newItem = Item.parse(parser, ParseFieldMatcher.STRICT, new Item());
        assertEquals(expectedItem, newItem);
    }

    @Test
    public void testPerFieldAnalyzer() {

    }
}
