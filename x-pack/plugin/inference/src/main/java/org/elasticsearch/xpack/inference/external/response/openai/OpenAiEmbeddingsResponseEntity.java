/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.external.response.openai;

import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.common.xcontent.XContentParserUtils;
import org.elasticsearch.inference.InferenceResults;
import org.elasticsearch.xcontent.ToXContentObject;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.XContentParserConfiguration;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xpack.inference.external.http.HttpResult;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.core.Strings.format;

public record OpenAiEmbeddingsResponseEntity(List<Embedding> embeddings) implements InferenceResults {

    public static final String TEXT_EMBEDDING = "text_embedding";
    public static final String NAME = "openai_embeddings_response_entity";

    /**
     * Parses the OpenAI json response.
     * For a request like:
     *
     * <pre>
     *     <code>
     *        {
     *            "inputs": ["hello this is my name", "I wish I was there!"]
     *        }
     *     </code>
     * </pre>
     *
     * The response would look like:
     *
     * <pre>
     * <code>
     * {
     *  "object": "list",
     *  "data": [
     *      {
     *          "object": "embedding",
     *          "embedding": [
     *              -0.009327292,
     *              .... (1536 floats total for ada-002)
     *              -0.0028842222,
     *          ],
     *          "index": 0
     *      },
     *      {
     *          "object": "embedding",
     *          "embedding": [ ... ],
     *          "index": 1
     *      }
     *  ],
     *  "model": "text-embedding-ada-002",
     *  "usage": {
     *      "prompt_tokens": 8,
     *      "total_tokens": 8
     *  }
     * }
     * </code>
     * </pre>
     */
    public static OpenAiEmbeddingsResponseEntity fromResponse(HttpResult response) throws IOException {
        var parserConfig = XContentParserConfiguration.EMPTY.withDeprecationHandler(LoggingDeprecationHandler.INSTANCE);

        try (XContentParser jsonParser = XContentFactory.xContent(XContentType.JSON).createParser(parserConfig, response.body())) {
            if (jsonParser.currentToken() == null) {
                jsonParser.nextToken();
            }

            XContentParser.Token token = jsonParser.currentToken();
            XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, token, jsonParser);

            positionParserAtTokenAfterField(jsonParser, "data");

            List<Embedding> embeddingList = XContentParserUtils.parseList(jsonParser, OpenAiEmbeddingsResponseEntity::parseEmbeddingObject);

            return new OpenAiEmbeddingsResponseEntity(embeddingList);
        }
    }

    /**
     * Iterates over the tokens until it finds a field name token with the text matching the field requested.
     *
     * @throws IllegalStateException if the field cannot be found
     */
    private static void positionParserAtTokenAfterField(XContentParser parser, String field) throws IOException {
        XContentParser.Token token;

        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME && parser.currentName().equals(field)) {
                parser.nextToken();
                return;
            }
        }

        throw new IllegalStateException(format("Failed to find required field [%s] in OpenAI embeddings response", field));
    }

    private static Embedding parseEmbeddingObject(XContentParser parser) throws IOException {
        XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);

        positionParserAtTokenAfterField(parser, "embedding");

        List<Float> embeddingValues = XContentParserUtils.parseList(parser, OpenAiEmbeddingsResponseEntity::parseEmbeddingList);

        // the parser is currently sitting at an ARRAY_END so go to the next token
        parser.nextToken();
        // if there are additional fields within this object, lets skip them, so we can begin parsing the next embedding array
        parser.skipChildren();

        return new Embedding(embeddingValues);
    }

    private static float parseEmbeddingList(XContentParser parser) throws IOException {
        XContentParser.Token token = parser.currentToken();
        XContentParserUtils.ensureExpectedToken(XContentParser.Token.VALUE_NUMBER, token, parser);
        return parser.floatValue();
    }

    public record Embedding(List<Float> values) implements Writeable, ToXContentObject {
        public static final String EMBEDDING = "embedding";

        public Embedding(StreamInput in) throws IOException {
            this(in.readCollectionAsImmutableList(StreamInput::readFloat));
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeCollection(values, StreamOutput::writeFloat);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject();
            builder.field(EMBEDDING, values);
            builder.endObject();
            return builder;
        }

        @Override
        public String toString() {
            return Strings.toString(this);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.field(TEXT_EMBEDDING, embeddings);
        return builder;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeCollection(embeddings);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    @Override
    public String getResultsField() {
        return TEXT_EMBEDDING;
    }

    @Override
    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(getResultsField(), embeddings);

        return map;
    }

    @Override
    public Map<String, Object> asMap(String outputField) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(outputField, embeddings);

        return map;
    }

    @Override
    public Object predictedValue() {
        throw new UnsupportedOperationException("[" + NAME + "] does not support a single predicted value");
    }
}
