/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.parser;

import org.antlr.v4.runtime.tree.TerminalNode;
import org.elasticsearch.common.Strings;
import org.elasticsearch.xpack.esql.core.expression.Expression;
import org.elasticsearch.xpack.esql.core.expression.Literal;
import org.elasticsearch.xpack.esql.core.expression.UnresolvedAttribute;
import org.elasticsearch.xpack.esql.expression.UnresolvedNamePattern;
import org.elasticsearch.xpack.esql.parser.EsqlBaseParser.IdentifierContext;
import org.elasticsearch.xpack.esql.parser.EsqlBaseParser.IndexStringContext;

import java.util.ArrayList;
import java.util.List;

import static org.elasticsearch.transport.RemoteClusterAware.REMOTE_CLUSTER_INDEX_SEPARATOR;
import static org.elasticsearch.xpack.esql.parser.ParserUtils.source;
import static org.elasticsearch.xpack.esql.parser.ParserUtils.typedParsing;

abstract class IdentifierBuilder extends AbstractBuilder {

    @Override
    public String visitIdentifier(IdentifierContext ctx) {
        String errorUnsupported = "Unsupported query parameter [{}][{}]";
        String errorUnsupportedOrUnknown = "Unsupported or unknown query parameter [{}]";
        if (ctx == null) {
            return null;
        } else if (ctx.QUOTED_IDENTIFIER() != null || ctx.UNQUOTED_IDENTIFIER() != null) {
            return unquoteIdentifier(ctx.QUOTED_IDENTIFIER(), ctx.UNQUOTED_IDENTIFIER());
        } else {
            Expression exp = typedParsing(this, ctx.params(), Expression.class);
            switch (exp) {
                case Literal lit -> throw new ParsingException(source(ctx), errorUnsupportedOrUnknown, ctx.getText());
                case UnresolvedNamePattern up -> throw new ParsingException(source(ctx), errorUnsupported, ctx.getText(), up.name());
                case UnresolvedAttribute ua -> {
                    if (ua.name() != null) {
                        return ua.name();
                    } else {
                        throw new ParsingException(source(ctx), errorUnsupported, ctx.getText());
                    }
                }
                default -> {
                    return null;
                }
            }
        }
    }

    protected static String unquoteIdentifier(TerminalNode quotedNode, TerminalNode unquotedNode) {
        String result;
        if (quotedNode != null) {
            result = unquoteIdString(quotedNode.getText());
        } else {
            result = unquotedNode.getText();
        }
        return result;
    }

    protected static String unquoteIdString(String quotedString) {
        return quotedString.substring(1, quotedString.length() - 1).replace("``", "`");
    }

    @Override
    public String visitIndexString(IndexStringContext ctx) {
        TerminalNode n = ctx.UNQUOTED_SOURCE();
        return n != null ? n.getText() : unquote(ctx.QUOTED_STRING().getText());
    }

    public String visitIndexPattern(List<EsqlBaseParser.IndexPatternContext> ctx) {
        List<String> patterns = new ArrayList<>(ctx.size());
        ctx.forEach(c -> {
            String indexPattern = visitIndexString(c.indexString());
            patterns.add(
                c.clusterString() != null ? c.clusterString().getText() + REMOTE_CLUSTER_INDEX_SEPARATOR + indexPattern : indexPattern
            );
        });
        return Strings.collectionToDelimitedString(patterns, ",");
    }
}
