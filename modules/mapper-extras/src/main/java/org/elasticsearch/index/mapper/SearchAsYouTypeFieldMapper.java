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

package org.elasticsearch.index.mapper;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.AnalyzerWrapper;
import org.apache.lucene.analysis.CachingTokenFilter;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ngram.EdgeNGramTokenFilter;
import org.apache.lucene.analysis.shingle.FixedShingleFilter;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.AutomatonQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.NormsFieldExistsQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.spans.FieldMaskingSpanQuery;
import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.util.automaton.Automata;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.Operations;
import org.elasticsearch.common.collect.Iterators;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.analysis.AnalyzerScope;
import org.elasticsearch.index.analysis.NamedAnalyzer;
import org.elasticsearch.index.query.QueryShardContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.elasticsearch.common.xcontent.support.XContentMapValues.nodeIntegerValue;
import static org.elasticsearch.index.mapper.TextFieldMapper.TextFieldType.hasGaps;
import static org.elasticsearch.index.mapper.TypeParsers.parseTextField;

public class SearchAsYouTypeFieldMapper extends FieldMapper {

    public static final String CONTENT_TYPE = "search_as_you_type";
    private static final int MAX_SHINGLE_SIZE_LOWER_BOUND = 2;
    private static final int MAX_SHINGLE_SIZE_UPPER_BOUND = 4;
    private static final String PREFIX_FIELD_SUFFIX = "._index_prefix";

    public static class Defaults {

        public static final int MIN_GRAM = 1;
        public static final int MAX_GRAM = 20;
        public static final int MAX_SHINGLE_SIZE = 3;

        public static final MappedFieldType FIELD_TYPE = new SearchAsYouTypeFieldType();

        static {
            FIELD_TYPE.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
            FIELD_TYPE.freeze();
        }
    }

    public static class TypeParser implements Mapper.TypeParser {

        @Override
        public Mapper.Builder<?, ?> parse(String name,
                                          Map<String, Object> node,
                                          ParserContext parserContext) throws MapperParsingException {

            final Builder builder = new Builder(name);

            builder.fieldType().setIndexAnalyzer(parserContext.getIndexAnalyzers().getDefaultIndexAnalyzer());
            builder.fieldType().setSearchAnalyzer(parserContext.getIndexAnalyzers().getDefaultSearchAnalyzer());
            builder.fieldType().setSearchQuoteAnalyzer(parserContext.getIndexAnalyzers().getDefaultSearchQuoteAnalyzer());
            parseTextField(builder, name, node, parserContext);
            for (Iterator<Map.Entry<String, Object>> iterator = node.entrySet().iterator(); iterator.hasNext();) {
                final Map.Entry<String, Object> entry = iterator.next();
                final String fieldName = entry.getKey();
                final Object fieldNode = entry.getValue();

                if (fieldName.equals("max_shingle_size")) {
                    builder.maxShingleSize(nodeIntegerValue(fieldNode));
                    iterator.remove();
                }
                // TODO should we allow to configure the prefix field
            }
            return builder;
        }
    }

    public static class Builder extends FieldMapper.Builder<Builder, SearchAsYouTypeFieldMapper> {
        private int maxShingleSize = Defaults.MAX_SHINGLE_SIZE;

        public Builder(String name) {
            super(name, Defaults.FIELD_TYPE, Defaults.FIELD_TYPE);
            this.builder = this;
        }

        public Builder maxShingleSize(int maxShingleSize) {
            if (maxShingleSize < MAX_SHINGLE_SIZE_LOWER_BOUND || maxShingleSize > MAX_SHINGLE_SIZE_UPPER_BOUND) {
                throw new MapperParsingException("[max_shingle_size] must be at least [" + MAX_SHINGLE_SIZE_LOWER_BOUND + "] and at most " +
                    "[" + MAX_SHINGLE_SIZE_UPPER_BOUND + "], got [" + maxShingleSize + "]");
            }
            this.maxShingleSize = maxShingleSize;
            return builder;
        }

        @Override
        public SearchAsYouTypeFieldType fieldType() {
            return (SearchAsYouTypeFieldType) this.fieldType;
        }

        @Override
        public SearchAsYouTypeFieldMapper build(Mapper.BuilderContext context) {
            setupFieldType(context);

            final NamedAnalyzer analyzer = fieldType().indexAnalyzer();
            final NamedAnalyzer searchAnalyzer = fieldType().searchAnalyzer() == null ? analyzer : fieldType().searchAnalyzer();

            // Setup the prefix field
            String prefixFieldName = name() + PREFIX_FIELD_SUFFIX;
            PrefixFieldType prefixFieldType = new PrefixFieldType(name(), prefixFieldName, Defaults.MIN_GRAM, Defaults.MAX_GRAM);
            prefixFieldType.setIndexOptions(fieldType().indexOptions());
            prefixFieldType.setStored(fieldType().stored());
            // we wrap the index analyzer with shingle and edge-ngram
            SearchAsYouTypeAnalyzer indexWrapper = SearchAsYouTypeAnalyzer.withShingleAndPrefix(analyzer.analyzer(), maxShingleSize);
            // the search analyzer is wrapped with shingle only
            SearchAsYouTypeAnalyzer searchWrapper = SearchAsYouTypeAnalyzer.withShingle(searchAnalyzer.analyzer(), maxShingleSize);
            prefixFieldType.setIndexAnalyzer(new NamedAnalyzer(analyzer.name(), AnalyzerScope.INDEX, indexWrapper));
            prefixFieldType.setSearchAnalyzer(new NamedAnalyzer(searchAnalyzer.name(), AnalyzerScope.INDEX, searchWrapper));
            PrefixFieldMapper prefixFieldMapper = new PrefixFieldMapper(prefixFieldType, context.indexSettings());

            // Setup the shingle fields
            ShingleFieldMapper[] shingleFieldMappers = new ShingleFieldMapper[maxShingleSize-1];
            ShingleFieldType[] shingleFieldTypes = new ShingleFieldType[maxShingleSize-1];
            for (int i = 0; i < shingleFieldMappers.length; i++) {
                int shingleSize = i + 2;
                ShingleFieldType shingleFieldType = new ShingleFieldType(fieldType(), shingleSize);
                shingleFieldType.setName(getShingleFieldName(name(), shingleSize));
                // we wrap the search and index analyzer with a shingle filter
                indexWrapper = SearchAsYouTypeAnalyzer.withShingle(analyzer.analyzer(), shingleSize);
                searchWrapper = SearchAsYouTypeAnalyzer.withShingle(searchAnalyzer.analyzer(), shingleSize);
                shingleFieldType.setIndexAnalyzer(new NamedAnalyzer(analyzer.name(), AnalyzerScope.INDEX, indexWrapper));
                shingleFieldType.setSearchAnalyzer(new NamedAnalyzer(searchAnalyzer.name(), AnalyzerScope.INDEX, searchWrapper));
                shingleFieldType.setSearchQuoteAnalyzer(shingleFieldType.searchAnalyzer());
                shingleFieldType.setPrefixFieldType(prefixFieldType);
                shingleFieldTypes[i] = shingleFieldType;
                shingleFieldMappers[i] = new ShingleFieldMapper(shingleFieldType, context.indexSettings());
            }
            fieldType().setPrefixField(prefixFieldType);
            fieldType().setShingleFields(shingleFieldTypes);
            return new SearchAsYouTypeFieldMapper(name, fieldType(), context.indexSettings(), copyTo,
                maxShingleSize, prefixFieldMapper, shingleFieldMappers);
        }
    }

    private static int countPosition(TokenStream stream) throws IOException {
        assert stream instanceof CachingTokenFilter;
        PositionIncrementAttribute posIncAtt = stream.getAttribute(PositionIncrementAttribute.class);
        stream.reset();
        int positionCount = 0;
        while (stream.incrementToken()) {
            if (posIncAtt.getPositionIncrement() != 0) {
                positionCount += posIncAtt.getPositionIncrement();
            }
        }
        return positionCount;
    }

    static class SearchAsYouTypeFieldType extends StringFieldType {

        PrefixFieldType prefixField;
        ShingleFieldType[] shingleFields = new ShingleFieldType[0];

        SearchAsYouTypeFieldType() {
            setTokenized(true);
        }

        SearchAsYouTypeFieldType(SearchAsYouTypeFieldType other) {
            super(other);

            if (other.prefixField != null) {
                this.prefixField = other.prefixField.clone();
            }
            if (other.shingleFields != null) {
                this.shingleFields = new ShingleFieldType[other.shingleFields.length];
                for (int i = 0; i < this.shingleFields.length; i++) {
                    if (other.shingleFields[i] != null) {
                        this.shingleFields[i] = other.shingleFields[i].clone();
                    }
                }
            }
        }

        public void setPrefixField(PrefixFieldType prefixField) {
            this.prefixField = prefixField;
        }

        public void setShingleFields(ShingleFieldType[] shingleFields) {
            this.shingleFields = shingleFields;
        }

        @Override
        public MappedFieldType clone() {
            return new SearchAsYouTypeFieldType(this);
        }

        @Override
        public String typeName() {
            return CONTENT_TYPE;
        }

        @Override
        public Query existsQuery(QueryShardContext context) {
            if (omitNorms()) {
                return new TermQuery(new Term(FieldNamesFieldMapper.NAME, name()));
            } else {
                return new NormsFieldExistsQuery(name());
            }
        }

        @Override
        public Query prefixQuery(String value, MultiTermQuery.RewriteMethod method, QueryShardContext context) {
            if (prefixField == null || prefixField.termLengthWithinBounds(value.length()) == false) {
                return super.prefixQuery(value, method, context);
            } else {
                final Query query = prefixField.prefixQuery(value, method, context);
                if (method == null
                    || method == MultiTermQuery.CONSTANT_SCORE_REWRITE
                    || method == MultiTermQuery.CONSTANT_SCORE_BOOLEAN_REWRITE) {
                    return new ConstantScoreQuery(query);
                } else {
                    return query;
                }
            }
        }

        @Override
        public Query phraseQuery(TokenStream stream, int slop, boolean enablePositionIncrements) throws IOException {
            int numPos = countPosition(stream);
            if (shingleFields.length == 0 || slop > 0 || hasGaps(stream) || numPos <= 1) {
                return TextFieldMapper.createPhraseQuery(stream, name(), slop, enablePositionIncrements);
            }
            int shingleSize = Math.min(numPos-2, shingleFields.length);
            stream = new FixedShingleFilter(stream, shingleSize);
            return shingleFields[numPos-1].phraseQuery(stream, 0, true);
        }

        @Override
        public Query multiPhraseQuery(TokenStream stream, int slop, boolean enablePositionIncrements) throws IOException {
            int numPos = countPosition(stream);
            if (shingleFields.length == 0 || slop > 0 || hasGaps(stream) || numPos <= 1) {
                return TextFieldMapper.createPhraseQuery(stream, name(), slop, enablePositionIncrements);
            }
            int shingleSize = Math.min(numPos-2, shingleFields.length);
            stream = new FixedShingleFilter(stream, shingleSize);
            return shingleFields[numPos-2].multiPhraseQuery(stream, 0, true);
        }

        @Override
        public Query phrasePrefixQuery(TokenStream stream, int slop, int maxExpansions) throws IOException {
            int numPos = countPosition(stream);
            if (shingleFields.length == 0 || slop > 0 || hasGaps(stream) || numPos <= 1) {
                return TextFieldMapper.createPhrasePrefixQuery(stream, name(), slop, maxExpansions,
                    null, null);
            }
            ShingleFieldType shingleField = shingleFields[Math.min(numPos-2, shingleFields.length-1)];
            stream = new FixedShingleFilter(stream, shingleField.shingleSize);
            return shingleField.phrasePrefixQuery(stream, 0, maxExpansions);
        }

        @Override
        public SpanQuery spanPrefixQuery(String value, SpanMultiTermQueryWrapper.SpanRewriteMethod method, QueryShardContext context) {
            if (prefixField != null && prefixField.termLengthWithinBounds(value.length())) {
                return new FieldMaskingSpanQuery(new SpanTermQuery(new Term(prefixField.name(), indexedValueForSearch(value))), name());
            } else {
                SpanMultiTermQueryWrapper<?> spanMulti =
                    new SpanMultiTermQueryWrapper<>(new PrefixQuery(new Term(name(), indexedValueForSearch(value))));
                spanMulti.setRewriteMethod(method);
                return spanMulti;
            }
        }

        @Override
        public boolean equals(Object otherObject) {
            if (this == otherObject) {
                return true;
            }
            if (otherObject == null || getClass() != otherObject.getClass()) {
                return false;
            }
            if (!super.equals(otherObject)) {
                return false;
            }
            final SearchAsYouTypeFieldType other = (SearchAsYouTypeFieldType) otherObject;
            return Objects.equals(prefixField, other.prefixField) &&
                Arrays.equals(shingleFields, other.shingleFields);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), prefixField, Arrays.hashCode(shingleFields));
        }
    }

    static final class PrefixFieldType extends StringFieldType {

        final int minChars;
        final int maxChars;
        final String parentField;

        PrefixFieldType(String parentField, String name, int minChars, int maxChars) {
            setTokenized(true);
            setOmitNorms(true);
            setIndexOptions(IndexOptions.DOCS);
            setName(name);
            this.minChars = minChars;
            this.maxChars = maxChars;
            this.parentField = parentField;
        }

        PrefixFieldType(PrefixFieldType other) {
            super(other);
            this.minChars = other.minChars;
            this.maxChars = other.maxChars;
            this.parentField = other.parentField;
        }

        boolean termLengthWithinBounds(int length) {
            return length >= minChars - 1 && length <= maxChars;
        }

        @Override
        public Query prefixQuery(String value, MultiTermQuery.RewriteMethod method, QueryShardContext context) {
            if (value.length() >= minChars) {
                return super.termQuery(value, context);
            }
            List<Automaton> automata = new ArrayList<>();
            automata.add(Automata.makeString(value));
            for (int i = value.length(); i < minChars; i++) {
                automata.add(Automata.makeAnyChar());
            }
            Automaton automaton = Operations.concatenate(automata);
            AutomatonQuery query = new AutomatonQuery(new Term(name(), value + "*"), automaton);
            query.setRewriteMethod(method);
            return new BooleanQuery.Builder()
                .add(query, BooleanClause.Occur.SHOULD)
                .add(new TermQuery(new Term(parentField, value)), BooleanClause.Occur.SHOULD)
                .build();
        }

        @Override
        public PrefixFieldType clone() {
            return new PrefixFieldType(this);
        }

        @Override
        public String typeName() {
            return "prefix";
        }

        @Override
        public String toString() {
            return super.toString() + ",prefixChars=" + minChars + ":" + maxChars;
        }

        @Override
        public Query existsQuery(QueryShardContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            PrefixFieldType that = (PrefixFieldType) o;
            return minChars == that.minChars &&
                maxChars == that.maxChars;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), minChars, maxChars);
        }
    }

    static final class PrefixFieldMapper extends FieldMapper {

        PrefixFieldMapper(PrefixFieldType fieldType, Settings indexSettings) {
            super(fieldType.name(), fieldType, fieldType, indexSettings, MultiFields.empty(), CopyTo.empty());
        }

        @Override
        public PrefixFieldType fieldType() {
            return (PrefixFieldType) super.fieldType();
        }

        @Override
        protected void parseCreateField(ParseContext context, List<IndexableField> fields) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected String contentType() {
            return "prefix";
        }

        @Override
        public String toString() {
            return fieldType().toString();
        }
    }

    static final class ShingleFieldMapper extends FieldMapper {

        ShingleFieldMapper(ShingleFieldType fieldType, Settings indexSettings) {
            super(fieldType.name(), fieldType, fieldType, indexSettings, MultiFields.empty(), CopyTo.empty());
        }

        @Override
        public ShingleFieldType fieldType() {
            return (ShingleFieldType) super.fieldType();
        }

        @Override
        protected void parseCreateField(ParseContext context, List<IndexableField> fields) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected String contentType() {
            return CONTENT_TYPE;
        }
    }

    static class ShingleFieldType extends StringFieldType {
        final int shingleSize;
        PrefixFieldType prefixFieldType;

        ShingleFieldType(MappedFieldType other, int shingleSize) {
            super(other);
            this.shingleSize = shingleSize;
        }

        ShingleFieldType(ShingleFieldType other) {
            super(other);
            this.shingleSize = other.shingleSize;
            if (other.prefixFieldType != null) {
                this.prefixFieldType = other.prefixFieldType.clone();
            }
        }

        void setPrefixFieldType(PrefixFieldType prefixFieldType) {
            checkIfFrozen();
            this.prefixFieldType = prefixFieldType;
        }

        @Override
        public ShingleFieldType clone() {
            return new ShingleFieldType(this);
        }

        @Override
        public String typeName() {
            return CONTENT_TYPE;
        }

        @Override
        public Query existsQuery(QueryShardContext context) {
            if (omitNorms()) {
                return new TermQuery(new Term(FieldNamesFieldMapper.NAME, name()));
            } else {
                return new NormsFieldExistsQuery(name());
            }
        }

        @Override
        public Query prefixQuery(String value, MultiTermQuery.RewriteMethod method, QueryShardContext context) {
            if (prefixFieldType == null || prefixFieldType.termLengthWithinBounds(value.length()) == false) {
                return super.prefixQuery(value, method, context);
            } else {
                final Query query = prefixFieldType.prefixQuery(value, method, context);
                if (method == null
                    || method == MultiTermQuery.CONSTANT_SCORE_REWRITE
                    || method == MultiTermQuery.CONSTANT_SCORE_BOOLEAN_REWRITE) {
                    return new ConstantScoreQuery(query);
                } else {
                    return query;
                }
            }
        }

        @Override
        public Query phraseQuery(TokenStream stream, int slop, boolean enablePositionIncrements) throws IOException {
            return TextFieldMapper.createPhraseQuery(stream, name(), slop, enablePositionIncrements);
        }

        @Override
        public Query multiPhraseQuery(TokenStream stream, int slop, boolean enablePositionIncrements) throws IOException {
            return TextFieldMapper.createPhraseQuery(stream, name(), slop, enablePositionIncrements);
        }

        @Override
        public Query phrasePrefixQuery(TokenStream stream, int slop, int maxExpansions) throws IOException {
            return TextFieldMapper.createPhrasePrefixQuery(stream, name(), slop, maxExpansions,
                prefixFieldType.name(), prefixFieldType::termLengthWithinBounds);
        }

        @Override
        public SpanQuery spanPrefixQuery(String value, SpanMultiTermQueryWrapper.SpanRewriteMethod method, QueryShardContext context) {
            if (prefixFieldType != null && prefixFieldType.termLengthWithinBounds(value.length())) {
                return new FieldMaskingSpanQuery(new SpanTermQuery(new Term(prefixFieldType.name(), indexedValueForSearch(value))), name());
            } else {
                SpanMultiTermQueryWrapper<?> spanMulti =
                    new SpanMultiTermQueryWrapper<>(new PrefixQuery(new Term(name(), indexedValueForSearch(value))));
                spanMulti.setRewriteMethod(method);
                return spanMulti;
            }
        }

        @Override
        public void checkCompatibility(MappedFieldType other, List<String> conflicts) {
            super.checkCompatibility(other, conflicts);
            ShingleFieldType ft = (ShingleFieldType) other;
            if (ft.shingleSize != this.shingleSize) {
                conflicts.add("mapper [" + name() + "] has different [shingle_size] values");
            }
            if (Objects.equals(this.prefixFieldType, ft.prefixFieldType) == false) {
                conflicts.add("mapper [" + name() + "] has different [index_prefixes] settings");
            }
        }

        @Override
        public boolean equals(Object otherObject) {
            if (this == otherObject) {
                return true;
            }
            if (otherObject == null || getClass() != otherObject.getClass()) {
                return false;
            }
            if (!super.equals(otherObject)) {
                return false;
            }
            final ShingleFieldType other = (ShingleFieldType) otherObject;
            return shingleSize == other.shingleSize
                && Objects.equals(prefixFieldType, other.prefixFieldType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), shingleSize, prefixFieldType);
        }
    }

    private final int maxShingleSize;
    private PrefixFieldMapper prefixField;
    private final ShingleFieldMapper[] shingleFields;

    public SearchAsYouTypeFieldMapper(String simpleName,
                                      SearchAsYouTypeFieldType fieldType,
                                      Settings indexSettings,
                                      CopyTo copyTo,
                                      int maxShingleSize,
                                      PrefixFieldMapper prefixField,
                                      ShingleFieldMapper[] shingleFields) {
        super(simpleName, fieldType, Defaults.FIELD_TYPE, indexSettings, MultiFields.empty(), copyTo);
        this.prefixField = prefixField;
        this.shingleFields = shingleFields;
        this.maxShingleSize = maxShingleSize;
    }

    @Override
    protected void parseCreateField(ParseContext context, List<IndexableField> fields) throws IOException {
        final String value = context.externalValueSet() ? context.externalValue().toString() : context.parser().textOrNull();
        if (value == null) {
            return;
        }

        List<IndexableField> newFields = new ArrayList<>();
        newFields.add(new Field(fieldType().name(), value, fieldType()));
        for (ShingleFieldMapper subFieldMapper : shingleFields) {
            fields.add(new Field(subFieldMapper.fieldType().name(), value, subFieldMapper.fieldType()));
        }
        newFields.add(new Field(prefixField.fieldType().name(), value, prefixField.fieldType()));
        if (fieldType().omitNorms()) {
            createFieldNamesField(context, newFields);
        }
        fields.addAll(newFields);
    }

    @Override
    protected String contentType() {
        return CONTENT_TYPE;
    }

    @Override
    protected void doMerge(Mapper mergeWith) {
        super.doMerge(mergeWith);
        SearchAsYouTypeFieldMapper mw = (SearchAsYouTypeFieldMapper) mergeWith;
        if (mw.maxShingleSize != maxShingleSize) {
            throw new IllegalArgumentException("mapper [" + name() + "] has different maxShingleSize setting, current ["
                + this.maxShingleSize + "], merged [" + mw.maxShingleSize + "]");
        }
        this.prefixField = (PrefixFieldMapper) this.prefixField.merge(mw);

        ShingleFieldMapper[] shingleFieldMappers = new ShingleFieldMapper[mw.shingleFields.length];
        for (int i = 0; i < shingleFieldMappers.length; i++) {
            this.shingleFields[i] = (ShingleFieldMapper) this.shingleFields[i].merge(mw.shingleFields[i]);
        }
    }

    public static String getShingleFieldName(String parentField, int shingleSize) {
        return parentField + "._" + shingleSize + "gram";
    }

    @Override
    public SearchAsYouTypeFieldType fieldType() {
        return (SearchAsYouTypeFieldType) super.fieldType();
    }

    public int maxShingleSize() {
        return maxShingleSize;
    }

    public PrefixFieldMapper prefixField() {
        return prefixField;
    }

    public ShingleFieldMapper[] shingleFields() {
        return shingleFields;
    }

    @Override
    protected void doXContentBody(XContentBuilder builder, boolean includeDefaults, Params params) throws IOException {
        super.doXContentBody(builder, includeDefaults, params);
        doXContentAnalyzers(builder, includeDefaults);
        builder.field("max_shingle_size", maxShingleSize);
    }

    @Override
    public Iterator<Mapper> iterator() {
        List<Mapper> subIterators = new ArrayList<>();
        subIterators.add(prefixField);
        subIterators.addAll(Arrays.asList(shingleFields));
        @SuppressWarnings("unchecked") Iterator<Mapper> concat = Iterators.concat(super.iterator(), subIterators.iterator());
        return concat;
    }

    static class SearchAsYouTypeAnalyzer extends AnalyzerWrapper {

        private final Analyzer delegate;
        private final int shingleSize;
        private final boolean indexPrefixes;

        private SearchAsYouTypeAnalyzer(Analyzer delegate,
                                        int shingleSize,
                                        boolean indexPrefixes) {

            super(delegate.getReuseStrategy());
            this.delegate = delegate;
            this.shingleSize = shingleSize;
            this.indexPrefixes = indexPrefixes;
        }

        static SearchAsYouTypeAnalyzer withShingle(Analyzer delegate, int shingleSize) {
            return new SearchAsYouTypeAnalyzer(delegate, shingleSize, false);
        }

        static SearchAsYouTypeAnalyzer withShingleAndPrefix(Analyzer delegate, int shingleSize) {
            return new SearchAsYouTypeAnalyzer(delegate, shingleSize, true);
        }

        @Override
        protected Analyzer getWrappedAnalyzer(String fieldName) {
            return delegate;
        }

        @Override
        protected TokenStreamComponents wrapComponents(String fieldName, TokenStreamComponents components) {
            TokenStream tokenStream = components.getTokenStream();
            if (indexPrefixes) {
                tokenStream = new TrailingShingleTokenFilter(tokenStream, shingleSize - 1);
            }
            tokenStream = new FixedShingleFilter(tokenStream, shingleSize, " ", "");
            if (indexPrefixes) {
                tokenStream = new EdgeNGramTokenFilter(tokenStream, Defaults.MIN_GRAM, Defaults.MAX_GRAM, true);
            }
            return new TokenStreamComponents(components.getSource(), tokenStream);
        }

        public int shingleSize() {
            return shingleSize;
        }

        public boolean indexPrefixes() {
            return indexPrefixes;
        }

        @Override
        public String toString() {
            return "<" + getClass().getCanonicalName() + " shingleSize=[" + shingleSize + "] indexPrefixes=[" + indexPrefixes + "]>";
        }

        private static class TrailingShingleTokenFilter extends TokenFilter {

            private final int numberOfExtraTrailingPositions;
            private final PositionIncrementAttribute positionIncrementAttribute;

            TrailingShingleTokenFilter(TokenStream input, int numberOfExtraTrailingPositions) {
                super(input);
                this.numberOfExtraTrailingPositions = numberOfExtraTrailingPositions;
                this.positionIncrementAttribute = addAttribute(PositionIncrementAttribute.class);
            }

            @Override
            public boolean incrementToken() throws IOException {
                return input.incrementToken();
            }

            @Override
            public void end() throws IOException {
                super.end();
                positionIncrementAttribute.setPositionIncrement(numberOfExtraTrailingPositions);
            }
        }
    }
}
