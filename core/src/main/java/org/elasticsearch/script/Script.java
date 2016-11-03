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

package org.elasticsearch.script;

import org.elasticsearch.Version;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParseFieldMatcher;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentParser.Token;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryParseContext;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Script represents used-defined input that can be used to
 * compile and execute a script from the {@link ScriptService}
 * based on the {@link ScriptType}.
 */
public final class Script implements ToXContent, Writeable {

    /**
     * ScriptField is a wrapper for {@link ParseField}s used to parse XContent for {@link Script}s.
     */
    public interface ScriptField {
        ParseField SCRIPT = new ParseField("script");
        ParseField LANG = new ParseField("lang");
        ParseField OPTIONS = new ParseField("options");
        ParseField PARAMS = new ParseField("params");
    }

    /**
     * ScriptOptions is a wrapper for the names of options that can be provided to the compiler during compilation.
     */
    public interface ScriptOptions {
        String CONTENT_TYPE = "content_type";
    }

    /**
     * The name of the of the default scripting language.
     */
    public static final String DEFAULT_SCRIPT_LANG = "painless";

    /**
     * The name of the default template language.
     */
    public static final String DEFAULT_TEMPLATE_LANG = "mustache";

    /**
     * The default {@link ScriptType}.
     */
    public static final ScriptType DEFAULT_SCRIPT_TYPE = ScriptType.INLINE;

    /**
     * Convenience method to call {@link Script#parse(XContentParser, ParseFieldMatcher, String)}
     * using the default scripting language.
     */
    public static Script parse(XContentParser parser, ParseFieldMatcher matcher) throws IOException {
        return parse(parser, matcher, DEFAULT_SCRIPT_LANG);
    }

    /**
     * Convenience method to call {@link Script#parse(XContentParser, ParseFieldMatcher, String)} using the
     * {@link ParseFieldMatcher} and scripting language provided by the {@link QueryParseContext}.
     */
    public static Script parse(XContentParser parser, QueryParseContext context) throws IOException {
        return parse(parser, context.getParseFieldMatcher(), context.getDefaultScriptLanguage());
    }

    /**
     * This will parse XContent into a {@link Script}.  The following formats can be parsed:
     *
     * The simple format defaults to an {@link ScriptType#INLINE} with no compiler options or user-defined params:
     *
     * {@code
     * "<idOrCode>"
     *
     * "return Math.log(doc.popularity) * 100;"
     * }
     *
     * The complex format where {@link ScriptType} and idOrCode are required while lang, options and params are not required.
     *
     * {@code
     * {
     *     "<type (inline, stored, file)>" : "<idOrCode>",
     *     "lang" : "<lang>",
     *     "options" : {
     *         "option0" : "<option0>",
     *         "option1" : "<option1>",
     *         ...
     *     }
     *     "params" : {
     *         "param0" : "<param0>",
     *         "param1" : "<param1>",
     *         ...
     *     }
     * }
     *
     * {
     *     "inline" : "return Math.log(doc.popularity) * params.multiplier",
     *     "lang" : "painless",
     *     "params" : {
     *         "multiplier" : 100.0
     *     }
     * }
     * }
     *
     * This also handles templates in a special way.  If a complexly formatted query is specified as another complex
     * JSON object the query is assumed to be a template, and the format will be preserved.
     *
     * {@code
     * {
     *     "inline" : { "query" : ... },
     *     "lang" : "<lang>",
     *     "options" : {
     *         "option0" : "<option0>",
     *         "option1" : "<option1>",
     *         ...
     *     }
     *     "params" : {
     *         "param0" : "<param0>",
     *         "param1" : "<param1>",
     *         ...
     *     }
     * }
     * }
     *
     * @param parser  The {@link XContentParser} to be used.
     * @param matcher The {@link ParseFieldMatcher} to be used.
     * @param lang    The default language to use if no language is specified.  The default language isn't necessarily
     *                the one defined by {@link Script#DEFAULT_SCRIPT_LANG} due to backwards compatiblity requirements
     *                related to stored queries using previously default languauges.
     * @return        The parsed {@link Script}.
     */
    public static Script parse(XContentParser parser, ParseFieldMatcher matcher, String lang) throws IOException {
        Objects.requireNonNull(lang);

        Token token = parser.currentToken();

        if (token == null) {
            token = parser.nextToken();
        }

        if (token == Token.VALUE_STRING) {
            return new Script(ScriptType.INLINE, lang, parser.text(), Collections.emptyMap());
        } else if (token != Token.START_OBJECT) {
            throw new ParsingException(parser.getTokenLocation(),
                "unexpected value [" + (token.isValue() ? parser.text() : token) + "], expected [{, <code>]");
        }

        ScriptType type = null;
        String idOrCode = null;
        Map<String, String> options = new HashMap<>();
        Map<String, Object> params = null;

        String name = null;

        while ((token = parser.nextToken()) != Token.END_OBJECT) {
            if (token == Token.FIELD_NAME) {
                name = parser.currentName();
            } else if (matcher.match(name, ScriptType.INLINE.getParseField())) {
                if (type != null) {
                    throw new ParsingException(parser.getTokenLocation(),
                        "unexpected script type [" + ScriptType.INLINE.getParseField().getPreferredName() + "], " +
                        "when type has already been specified [" + type.getName() + "]");
                }

                type = ScriptType.INLINE;

                options = new HashMap<>();

                if (parser.currentToken() == Token.START_OBJECT) {
                    options.put(ScriptOptions.CONTENT_TYPE, parser.contentType().mediaType());
                    XContentBuilder builder = XContentFactory.contentBuilder(parser.contentType());
                    idOrCode = builder.copyCurrentStructure(parser).bytes().utf8ToString();
                } else {
                    idOrCode = parser.text();
                }
            } else if (matcher.match(name, ScriptType.STORED.getParseField())) {
                if (type != null) {
                    throw new ParsingException(parser.getTokenLocation(),
                        "unexpected script type [" + ScriptType.STORED.getParseField().getPreferredName() + "], " +
                            "when type has already been specified [" + type.getName() + "]");
                }

                type = ScriptType.STORED;

                if (token == Token.VALUE_STRING) {
                    idOrCode = parser.text();
                } else {
                    throw new ParsingException(parser.getTokenLocation(),
                        "unexpected value [" + (token.isValue() ? parser.text() : token) + "], expected [<id>]");
                }
            } else if (matcher.match(name, ScriptType.FILE.getParseField())) {
                if (type != null) {
                    throw new ParsingException(parser.getTokenLocation(),
                        "unexpected script type [" + ScriptType.FILE.getParseField().getPreferredName() + "], " +
                            "when type has already been specified [" + type.getName() + "]");
                }

                type = ScriptType.FILE;

                if (token == Token.VALUE_STRING) {
                    idOrCode = parser.text();
                } else {
                    throw new ParsingException(parser.getTokenLocation(),
                        "unexpected value [" + (token.isValue() ? parser.text() : token) + "], expected [<id>]");
                }
            } else if (matcher.match(name, ScriptField.LANG)) {
                if (token == Token.VALUE_STRING) {
                    lang = parser.text();
                } else {
                    throw new ParsingException(parser.getTokenLocation(),
                        "unexpected value [" + (token.isValue() ? parser.text() : token) + "], expected [<lang>]");
                }
            } else if (matcher.match(name, ScriptField.OPTIONS)) {
                if (token == Token.START_OBJECT) {
                    while ((token = parser.nextToken()) != Token.END_OBJECT) {
                        if (token == Token.FIELD_NAME) {
                            String optionName = parser.currentName();
                            token = parser.nextToken();

                            if (token == Token.VALUE_STRING) {
                                options.put(optionName, parser.text());
                            } else {
                                throw new ParsingException(parser.getTokenLocation(),
                                    "unexpected value [" + (token.isValue() ? parser.text() : token) + "], expected [<option value>]");
                            }
                        } else {
                            throw new ParsingException(parser.getTokenLocation(),
                                "unexpected value [" + (token.isValue() ? parser.text() : token) + "], expected [<option name>]");
                        }
                    }
                } else {
                    throw new ParsingException(parser.getTokenLocation(),
                        "unexpected value [" + (token.isValue() ? parser.text() : token) + "], expected [<options>]");
                }
            } else if (matcher.match(name, ScriptField.PARAMS)) {
                if (token == Token.START_OBJECT) {
                    params = parser.map();
                } else {
                    throw new ParsingException(parser.getTokenLocation(),
                        "unexpected value [" + (token.isValue() ? parser.text() : token) + "], expected [<params>]");
                }
            } else {
                throw new ParsingException(parser.getTokenLocation(),
                    "unexpected field [" + (name == null ? parser.currentToken() : name) + "], " + "expected [" +
                        ScriptType.INLINE.getParseField().getPreferredName() + ", " +
                        ScriptType.STORED.getParseField().getPreferredName() + ", " +
                        ScriptType.FILE.getParseField().getPreferredName() + ", " +
                        ScriptField.LANG.getPreferredName() + ", " +
                        ScriptField.PARAMS.getPreferredName() +
                        "]");
            }
        }

        if (type == null) {
            throw new IllegalArgumentException("missing required parameter for parsing script, expected valid script type [" +
                ScriptType.INLINE.getName() + "] or [" + ScriptType.STORED.getName() + "] or [" + ScriptType.FILE.getName() + "]");
        }

        if (idOrCode == null) {
            throw new IllegalArgumentException("missing required parameter for parsing script, expected [<id>] or [<code>]");
        }

        if (params == null) {
            params = new HashMap<>();
        }

        return new Script(type, lang, idOrCode, options, params);
    }

    @SuppressWarnings("unchecked")
    public static Script readFrom(StreamInput in) throws IOException {
        // Version 6.0 requires all Script members to be non-null and supports the potential
        // for more options than just XContentType.  Reorders the read in contents to be in
        // same order as the constructor.
        if (Version.smallest(in.getVersion(), Version.V_6_0_0_alpha1) == Version.V_6_0_0_alpha1) {
            ScriptType type = ScriptType.readFrom(in);
            String lang = in.readString();
            String idOrCode = in.readString();
            Map<String, String> options = (Map)in.readMap();
            Map<String, Object> params = in.readMap();

            return new Script(type, lang, idOrCode, options, params);
        // Prior to version 6.0 the script members are read in certain cases as optional and given
        // default values when necessary.  Also the only option supported is for XContentType.
        } else {
            String idOrCode = in.readString();
            ScriptType type;

            if (in.readBoolean()) {
                type = ScriptType.readFrom(in);
            } else {
                type = DEFAULT_SCRIPT_TYPE;
            }

            String lang = in.readOptionalString();

            if (lang == null) {
                lang = DEFAULT_SCRIPT_LANG;
            }

            Map<String, Object> params = in.readMap();

            if (params == null) {
                params = new HashMap<>();
            }

            Map<String, String> options = new HashMap<>();

            if (in.readBoolean()) {
                XContentType contentType = XContentType.readFrom(in);
                options.put(ScriptOptions.CONTENT_TYPE, contentType.mediaType());
            }

            return new Script(type, lang, idOrCode, options, params);
        }
    }

    private ScriptType type;
    private String lang;
    private String idOrCode;
    private Map<String, String> options;
    private Map<String, Object> params;

    /**
     * Constructor for simple script using the default language and default type.
     * @param idOrCode The id or code to use dependent on the default script type.
     */
    public Script(String idOrCode) {
        this(DEFAULT_SCRIPT_TYPE, DEFAULT_SCRIPT_LANG, idOrCode, Collections.emptyMap(), Collections.emptyMap());
    }

    /**
     * Constructor for a script that does not need to use compiler options.
     * @param type     The {@link ScriptType}.
     * @param lang     The lang for this {@link Script}.
     * @param idOrCode The id for this {@link Script} if the {@link ScriptType} is {@link ScriptType#FILE} or {@link ScriptType#STORED}.
     *                 The code for this {@link Script} if the {@link ScriptType} is {@link ScriptType#INLINE}.
     * @param params   The user-defined params to be bound for script execution.
     */
    public Script(ScriptType type, String lang, String idOrCode, Map<String, Object> params) {
        this(type, lang, idOrCode, Collections.emptyMap(), params);
    }

    /**
     * Constructor for a script that requires the use of compiler options.
     * @param type     The {@link ScriptType}.
     * @param lang     The lang for this {@link Script}.
     * @param idOrCode The id for this {@link Script} if the {@link ScriptType} is {@link ScriptType#FILE} or {@link ScriptType#STORED}.
     *                 The code for this {@link Script} if the {@link ScriptType} is {@link ScriptType#INLINE}.
     * @param options  The options to be passed to the compiler for use at compile-time.
     * @param params   The user-defined params to be bound for script execution.
     */
    public Script(ScriptType type, String lang, String idOrCode, Map<String, String> options, Map<String, Object> params) {
        this.idOrCode = Objects.requireNonNull(idOrCode);
        this.type = Objects.requireNonNull(type);
        this.lang = Objects.requireNonNull(lang);
        this.options = Collections.unmodifiableMap(Objects.requireNonNull(options));
        this.params = Collections.unmodifiableMap(Objects.requireNonNull(params));
    }

    /**
     * This will build scripts into the following XContent structure:
     *
     * {@code
     * {
     *     "<type (inline, stored, file)>" : "<idOrCode>",
     *     "lang" : "<lang>",
     *     "options" : {
     *         "option0" : "<option0>",
     *         "option1" : "<option1>",
     *         ...
     *     }
     *     "params" : {
     *         "param0" : "<param0>",
     *         "param1" : "<param1>",
     *         ...
     *     }
     * }
     *
     * {
     *     "inline" : "return Math.log(doc.popularity) * params.multiplier;",
     *     "lang" : "painless",
     *     "params" : {
     *         "multiplier" : 100.0
     *     }
     * }
     * }
     *
     * Note that options and params will only be included if there have been any specified respectively.
     *
     * This also handles templates in a special way.  If the {@link ScriptOptions#CONTENT_TYPE} option
     * is provided and the {@link ScriptType#INLINE} is specified then the template will be preserved as a raw field.
     *
     * {@code
     * {
     *     "inline" : { "query" : ... },
     *     "lang" : "<lang>",
     *     "options" : {
     *         "option0" : "<option0>",
     *         "option1" : "<option1>",
     *         ...
     *     }
     *     "params" : {
     *         "param0" : "<param0>",
     *         "param1" : "<param1>",
     *         ...
     *     }
     * }
     * }
     */
    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params builderParams) throws IOException {
        builder.startObject();

        String contentType = options.get(ScriptOptions.CONTENT_TYPE);

        if (type == ScriptType.INLINE && contentType != null && builder.contentType().mediaType().equals(contentType)) {
            builder.rawField(type.getParseField().getPreferredName(), new BytesArray(idOrCode));
        } else {
            builder.field(type.getParseField().getPreferredName(), idOrCode);
        }

        builder.field(ScriptField.LANG.getPreferredName(), lang);

        if (!options.isEmpty()) {
            builder.field(ScriptField.OPTIONS.getPreferredName(), options);
        }

        if (!params.isEmpty()) {
            builder.field(ScriptField.PARAMS.getPreferredName(), params);
        }

        builder.endObject();

        return builder;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void writeTo(StreamOutput out) throws IOException {
        // Version 6.0 requires all Script members to be non-null and supports the potential
        // for more options than just XContentType.  Reorders the written out contents to be in
        // same order as the constructor.
        if (Version.smallest(out.getVersion(), Version.V_6_0_0_alpha1) == Version.V_6_0_0_alpha1) {
            type.writeTo(out);
            out.writeString(lang);
            out.writeString(idOrCode);
            out.writeMap((Map)options);
            out.writeMap(params);
        // Prior to version 6.0 the Script members were possibly written as optional or null, though this is no longer
        // necessary since Script members cannot be null anymore, and there is no case where a null value wasn't equivalent
        // to it's default value when actually compiling/executing a script.  Meaning, there are no backwards compatibility issues,
        // and now there's enforced consistency.  Also the only supported compiler option was XContentType.
        } else {
            out.writeString(idOrCode);
            out.writeBoolean(true);
            type.writeTo(out);
            out.writeBoolean(true);
            out.writeString(lang);
            out.writeMap(params.isEmpty() ? null : params);

            if (options.containsKey(ScriptOptions.CONTENT_TYPE)) {
                XContentType contentType = XContentType.fromMediaTypeOrFormat(options.get(ScriptOptions.CONTENT_TYPE));
                out.writeBoolean(true);
                contentType.writeTo(out);
            } else {
                out.writeBoolean(false);
            }
        }
    }

    /**
     * @return The id for this {@link Script} if the {@link ScriptType} is {@link ScriptType#FILE} or {@link ScriptType#STORED}.
     *         The code for this {@link Script} if the {@link ScriptType} is {@link ScriptType#INLINE}.
     */
    public String getIdOrCode() {
        return idOrCode;
    }

    /**
     * @return The {@link ScriptType} for this {@link Script}.
     */
    public ScriptType getType() {
        return type;
    }

    /**
     * @return The language for this {@link Script}.
     */
    public String getLang() {
        return lang;
    }

    /**
     * @return The map of user-defined params for this {@link Script}.
     */
    public Map<String, Object> getParams() {
        return params;
    }

    /**
     * @return The map of compiler options for this {@link Script}.
     */
    public Map<String, String> getOptions() {
        return options;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Script script = (Script)o;

        if (type != script.type) return false;
        if (!lang.equals(script.lang)) return false;
        if (!idOrCode.equals(script.idOrCode)) return false;
        if (!options.equals(script.options)) return false;
        return params.equals(script.params);

    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + lang.hashCode();
        result = 31 * result + idOrCode.hashCode();
        result = 31 * result + options.hashCode();
        result = 31 * result + params.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Script{" +
            "type=" + type +
            ", lang='" + lang + '\'' +
            ", idOrCode='" + idOrCode + '\'' +
            ", options=" + options +
            ", params=" + params +
            '}';
    }
}
