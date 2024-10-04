// ANTLR GENERATED CODE: DO NOT EDIT
package org.elasticsearch.xpack.esql.parser;

/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

import org.antlr.v4.runtime.FailedPredicateException;
import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.RuntimeMetaData;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.VocabularyImpl;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNDeserializer;
import org.antlr.v4.runtime.atn.ParserATNSimulator;
import org.antlr.v4.runtime.atn.PredictionContextCache;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
public class EsqlBaseParser extends ParserConfig {
  static { RuntimeMetaData.checkVersion("4.13.1", RuntimeMetaData.VERSION); }

  protected static final DFA[] _decisionToDFA;
  protected static final PredictionContextCache _sharedContextCache =
    new PredictionContextCache();
  public static final int
    DISSECT=1, DROP=2, ENRICH=3, EVAL=4, EXPLAIN=5, FROM=6, GROK=7, KEEP=8,
    LIMIT=9, META=10, MV_EXPAND=11, RENAME=12, ROW=13, SHOW=14, SORT=15, STATS=16,
    WHERE=17, DEV_INLINESTATS=18, DEV_LOOKUP=19, DEV_MATCH=20, DEV_METRICS=21,
    UNKNOWN_CMD=22, LINE_COMMENT=23, MULTILINE_COMMENT=24, WS=25, PIPE=26,
    QUOTED_STRING=27, INTEGER_LITERAL=28, DECIMAL_LITERAL=29, BY=30, AND=31,
    ASC=32, ASSIGN=33, CAST_OP=34, COMMA=35, DESC=36, DOT=37, FALSE=38, FIRST=39,
    IN=40, IS=41, LAST=42, LIKE=43, LP=44, NOT=45, NULL=46, NULLS=47, OR=48,
    PARAM=49, RLIKE=50, RP=51, TRUE=52, EQ=53, CIEQ=54, NEQ=55, LT=56, LTE=57,
    GT=58, GTE=59, PLUS=60, MINUS=61, ASTERISK=62, SLASH=63, PERCENT=64, NAMED_OR_POSITIONAL_PARAM=65,
    OPENING_BRACKET=66, CLOSING_BRACKET=67, UNQUOTED_IDENTIFIER=68, QUOTED_IDENTIFIER=69,
    EXPR_LINE_COMMENT=70, EXPR_MULTILINE_COMMENT=71, EXPR_WS=72, EXPLAIN_WS=73,
    EXPLAIN_LINE_COMMENT=74, EXPLAIN_MULTILINE_COMMENT=75, METADATA=76, UNQUOTED_SOURCE=77,
    FROM_LINE_COMMENT=78, FROM_MULTILINE_COMMENT=79, FROM_WS=80, ID_PATTERN=81,
    PROJECT_LINE_COMMENT=82, PROJECT_MULTILINE_COMMENT=83, PROJECT_WS=84,
    AS=85, RENAME_LINE_COMMENT=86, RENAME_MULTILINE_COMMENT=87, RENAME_WS=88,
    ON=89, WITH=90, ENRICH_POLICY_NAME=91, ENRICH_LINE_COMMENT=92, ENRICH_MULTILINE_COMMENT=93,
    ENRICH_WS=94, ENRICH_FIELD_LINE_COMMENT=95, ENRICH_FIELD_MULTILINE_COMMENT=96,
    ENRICH_FIELD_WS=97, MVEXPAND_LINE_COMMENT=98, MVEXPAND_MULTILINE_COMMENT=99,
    MVEXPAND_WS=100, INFO=101, SHOW_LINE_COMMENT=102, SHOW_MULTILINE_COMMENT=103,
    SHOW_WS=104, FUNCTIONS=105, META_LINE_COMMENT=106, META_MULTILINE_COMMENT=107,
    META_WS=108, COLON=109, SETTING=110, SETTING_LINE_COMMENT=111, SETTTING_MULTILINE_COMMENT=112,
    SETTING_WS=113, LOOKUP_LINE_COMMENT=114, LOOKUP_MULTILINE_COMMENT=115,
    LOOKUP_WS=116, LOOKUP_FIELD_LINE_COMMENT=117, LOOKUP_FIELD_MULTILINE_COMMENT=118,
    LOOKUP_FIELD_WS=119, METRICS_LINE_COMMENT=120, METRICS_MULTILINE_COMMENT=121,
    METRICS_WS=122, CLOSING_METRICS_LINE_COMMENT=123, CLOSING_METRICS_MULTILINE_COMMENT=124,
    CLOSING_METRICS_WS=125;
  public static final int
    RULE_singleStatement = 0, RULE_query = 1, RULE_sourceCommand = 2, RULE_processingCommand = 3,
    RULE_whereCommand = 4, RULE_booleanExpression = 5, RULE_regexBooleanExpression = 6,
    RULE_matchBooleanExpression = 7, RULE_valueExpression = 8, RULE_operatorExpression = 9,
    RULE_primaryExpression = 10, RULE_functionExpression = 11, RULE_functionName = 12,
    RULE_dataType = 13, RULE_rowCommand = 14, RULE_fields = 15, RULE_field = 16,
    RULE_fromCommand = 17, RULE_indexPattern = 18, RULE_clusterString = 19,
    RULE_indexString = 20, RULE_metadata = 21, RULE_metadataOption = 22, RULE_deprecated_metadata = 23,
    RULE_metricsCommand = 24, RULE_evalCommand = 25, RULE_statsCommand = 26,
    RULE_qualifiedName = 27, RULE_qualifiedNamePattern = 28, RULE_qualifiedNamePatterns = 29,
    RULE_identifier = 30, RULE_identifierPattern = 31, RULE_constant = 32,
    RULE_params = 33, RULE_limitCommand = 34, RULE_sortCommand = 35, RULE_orderExpression = 36,
    RULE_keepCommand = 37, RULE_dropCommand = 38, RULE_renameCommand = 39,
    RULE_renameClause = 40, RULE_dissectCommand = 41, RULE_grokCommand = 42,
    RULE_mvExpandCommand = 43, RULE_commandOptions = 44, RULE_commandOption = 45,
    RULE_booleanValue = 46, RULE_numericValue = 47, RULE_decimalValue = 48,
    RULE_integerValue = 49, RULE_string = 50, RULE_comparisonOperator = 51,
    RULE_explainCommand = 52, RULE_subqueryExpression = 53, RULE_showCommand = 54,
    RULE_metaCommand = 55, RULE_enrichCommand = 56, RULE_enrichWithClause = 57,
    RULE_lookupCommand = 58, RULE_inlinestatsCommand = 59;
  private static String[] makeRuleNames() {
    return new String[] {
      "singleStatement", "query", "sourceCommand", "processingCommand", "whereCommand",
      "booleanExpression", "regexBooleanExpression", "matchBooleanExpression",
      "valueExpression", "operatorExpression", "primaryExpression", "functionExpression",
      "functionName", "dataType", "rowCommand", "fields", "field", "fromCommand",
      "indexPattern", "clusterString", "indexString", "metadata", "metadataOption",
      "deprecated_metadata", "metricsCommand", "evalCommand", "statsCommand",
      "qualifiedName", "qualifiedNamePattern", "qualifiedNamePatterns", "identifier",
      "identifierPattern", "constant", "params", "limitCommand", "sortCommand",
      "orderExpression", "keepCommand", "dropCommand", "renameCommand", "renameClause",
      "dissectCommand", "grokCommand", "mvExpandCommand", "commandOptions",
      "commandOption", "booleanValue", "numericValue", "decimalValue", "integerValue",
      "string", "comparisonOperator", "explainCommand", "subqueryExpression",
      "showCommand", "metaCommand", "enrichCommand", "enrichWithClause", "lookupCommand",
      "inlinestatsCommand"
    };
  }
  public static final String[] ruleNames = makeRuleNames();

  private static String[] makeLiteralNames() {
    return new String[] {
      null, "'dissect'", "'drop'", "'enrich'", "'eval'", "'explain'", "'from'",
      "'grok'", "'keep'", "'limit'", "'meta'", "'mv_expand'", "'rename'", "'row'",
      "'show'", "'sort'", "'stats'", "'where'", null, null, null, null, null,
      null, null, null, "'|'", null, null, null, "'by'", "'and'", "'asc'",
      "'='", "'::'", "','", "'desc'", "'.'", "'false'", "'first'", "'in'",
      "'is'", "'last'", "'like'", "'('", "'not'", "'null'", "'nulls'", "'or'",
      "'?'", "'rlike'", "')'", "'true'", "'=='", "'=~'", "'!='", "'<'", "'<='",
      "'>'", "'>='", "'+'", "'-'", "'*'", "'/'", "'%'", null, null, "']'",
      null, null, null, null, null, null, null, null, "'metadata'", null, null,
      null, null, null, null, null, null, "'as'", null, null, null, "'on'",
      "'with'", null, null, null, null, null, null, null, null, null, null,
      "'info'", null, null, null, "'functions'", null, null, null, "':'"
    };
  }
  private static final String[] _LITERAL_NAMES = makeLiteralNames();
  private static String[] makeSymbolicNames() {
    return new String[] {
      null, "DISSECT", "DROP", "ENRICH", "EVAL", "EXPLAIN", "FROM", "GROK",
      "KEEP", "LIMIT", "META", "MV_EXPAND", "RENAME", "ROW", "SHOW", "SORT",
      "STATS", "WHERE", "DEV_INLINESTATS", "DEV_LOOKUP", "DEV_MATCH", "DEV_METRICS",
      "UNKNOWN_CMD", "LINE_COMMENT", "MULTILINE_COMMENT", "WS", "PIPE", "QUOTED_STRING",
      "INTEGER_LITERAL", "DECIMAL_LITERAL", "BY", "AND", "ASC", "ASSIGN", "CAST_OP",
      "COMMA", "DESC", "DOT", "FALSE", "FIRST", "IN", "IS", "LAST", "LIKE",
      "LP", "NOT", "NULL", "NULLS", "OR", "PARAM", "RLIKE", "RP", "TRUE", "EQ",
      "CIEQ", "NEQ", "LT", "LTE", "GT", "GTE", "PLUS", "MINUS", "ASTERISK",
      "SLASH", "PERCENT", "NAMED_OR_POSITIONAL_PARAM", "OPENING_BRACKET", "CLOSING_BRACKET",
      "UNQUOTED_IDENTIFIER", "QUOTED_IDENTIFIER", "EXPR_LINE_COMMENT", "EXPR_MULTILINE_COMMENT",
      "EXPR_WS", "EXPLAIN_WS", "EXPLAIN_LINE_COMMENT", "EXPLAIN_MULTILINE_COMMENT",
      "METADATA", "UNQUOTED_SOURCE", "FROM_LINE_COMMENT", "FROM_MULTILINE_COMMENT",
      "FROM_WS", "ID_PATTERN", "PROJECT_LINE_COMMENT", "PROJECT_MULTILINE_COMMENT",
      "PROJECT_WS", "AS", "RENAME_LINE_COMMENT", "RENAME_MULTILINE_COMMENT",
      "RENAME_WS", "ON", "WITH", "ENRICH_POLICY_NAME", "ENRICH_LINE_COMMENT",
      "ENRICH_MULTILINE_COMMENT", "ENRICH_WS", "ENRICH_FIELD_LINE_COMMENT",
      "ENRICH_FIELD_MULTILINE_COMMENT", "ENRICH_FIELD_WS", "MVEXPAND_LINE_COMMENT",
      "MVEXPAND_MULTILINE_COMMENT", "MVEXPAND_WS", "INFO", "SHOW_LINE_COMMENT",
      "SHOW_MULTILINE_COMMENT", "SHOW_WS", "FUNCTIONS", "META_LINE_COMMENT",
      "META_MULTILINE_COMMENT", "META_WS", "COLON", "SETTING", "SETTING_LINE_COMMENT",
      "SETTTING_MULTILINE_COMMENT", "SETTING_WS", "LOOKUP_LINE_COMMENT", "LOOKUP_MULTILINE_COMMENT",
      "LOOKUP_WS", "LOOKUP_FIELD_LINE_COMMENT", "LOOKUP_FIELD_MULTILINE_COMMENT",
      "LOOKUP_FIELD_WS", "METRICS_LINE_COMMENT", "METRICS_MULTILINE_COMMENT",
      "METRICS_WS", "CLOSING_METRICS_LINE_COMMENT", "CLOSING_METRICS_MULTILINE_COMMENT",
      "CLOSING_METRICS_WS"
    };
  }
  private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
  public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

  /**
   * @deprecated Use {@link #VOCABULARY} instead.
   */
  @Deprecated
  public static final String[] tokenNames;
  static {
    tokenNames = new String[_SYMBOLIC_NAMES.length];
    for (int i = 0; i < tokenNames.length; i++) {
      tokenNames[i] = VOCABULARY.getLiteralName(i);
      if (tokenNames[i] == null) {
        tokenNames[i] = VOCABULARY.getSymbolicName(i);
      }

      if (tokenNames[i] == null) {
        tokenNames[i] = "<INVALID>";
      }
    }
  }

  @Override
  @Deprecated
  public String[] getTokenNames() {
    return tokenNames;
  }

  @Override

  public Vocabulary getVocabulary() {
    return VOCABULARY;
  }

  @Override
  public String getGrammarFileName() { return "EsqlBaseParser.g4"; }

  @Override
  public String[] getRuleNames() { return ruleNames; }

  @Override
  public String getSerializedATN() { return _serializedATN; }

  @Override
  public ATN getATN() { return _ATN; }

  @SuppressWarnings("this-escape")
  public EsqlBaseParser(TokenStream input) {
    super(input);
    _interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
  }

  @SuppressWarnings("CheckReturnValue")
  public static class SingleStatementContext extends ParserRuleContext {
    public QueryContext query() {
      return getRuleContext(QueryContext.class,0);
    }
    public TerminalNode EOF() { return getToken(EsqlBaseParser.EOF, 0); }
    @SuppressWarnings("this-escape")
    public SingleStatementContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_singleStatement; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterSingleStatement(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitSingleStatement(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitSingleStatement(this);
      else return visitor.visitChildren(this);
    }
  }

  public final SingleStatementContext singleStatement() throws RecognitionException {
    SingleStatementContext _localctx = new SingleStatementContext(_ctx, getState());
    enterRule(_localctx, 0, RULE_singleStatement);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(120);
      query(0);
      setState(121);
      match(EOF);
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class QueryContext extends ParserRuleContext {
    @SuppressWarnings("this-escape")
    public QueryContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_query; }

    @SuppressWarnings("this-escape")
    public QueryContext() { }
    public void copyFrom(QueryContext ctx) {
      super.copyFrom(ctx);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class CompositeQueryContext extends QueryContext {
    public QueryContext query() {
      return getRuleContext(QueryContext.class,0);
    }
    public TerminalNode PIPE() { return getToken(EsqlBaseParser.PIPE, 0); }
    public ProcessingCommandContext processingCommand() {
      return getRuleContext(ProcessingCommandContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public CompositeQueryContext(QueryContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterCompositeQuery(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitCompositeQuery(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitCompositeQuery(this);
      else return visitor.visitChildren(this);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class SingleCommandQueryContext extends QueryContext {
    public SourceCommandContext sourceCommand() {
      return getRuleContext(SourceCommandContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public SingleCommandQueryContext(QueryContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterSingleCommandQuery(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitSingleCommandQuery(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitSingleCommandQuery(this);
      else return visitor.visitChildren(this);
    }
  }

  public final QueryContext query() throws RecognitionException {
    return query(0);
  }

  private QueryContext query(int _p) throws RecognitionException {
    ParserRuleContext _parentctx = _ctx;
    int _parentState = getState();
    QueryContext _localctx = new QueryContext(_ctx, _parentState);
    QueryContext _prevctx = _localctx;
    int _startState = 2;
    enterRecursionRule(_localctx, 2, RULE_query, _p);
    try {
      int _alt;
      enterOuterAlt(_localctx, 1);
      {
      {
      _localctx = new SingleCommandQueryContext(_localctx);
      _ctx = _localctx;
      _prevctx = _localctx;

      setState(124);
      sourceCommand();
      }
      _ctx.stop = _input.LT(-1);
      setState(131);
      _errHandler.sync(this);
      _alt = getInterpreter().adaptivePredict(_input,0,_ctx);
      while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
        if ( _alt==1 ) {
          if ( _parseListeners!=null ) triggerExitRuleEvent();
          _prevctx = _localctx;
          {
          {
          _localctx = new CompositeQueryContext(new QueryContext(_parentctx, _parentState));
          pushNewRecursionContext(_localctx, _startState, RULE_query);
          setState(126);
          if (!(precpred(_ctx, 1))) throw new FailedPredicateException(this, "precpred(_ctx, 1)");
          setState(127);
          match(PIPE);
          setState(128);
          processingCommand();
          }
          }
        }
        setState(133);
        _errHandler.sync(this);
        _alt = getInterpreter().adaptivePredict(_input,0,_ctx);
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      unrollRecursionContexts(_parentctx);
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class SourceCommandContext extends ParserRuleContext {
    public ExplainCommandContext explainCommand() {
      return getRuleContext(ExplainCommandContext.class,0);
    }
    public FromCommandContext fromCommand() {
      return getRuleContext(FromCommandContext.class,0);
    }
    public MetaCommandContext metaCommand() {
      return getRuleContext(MetaCommandContext.class,0);
    }
    public RowCommandContext rowCommand() {
      return getRuleContext(RowCommandContext.class,0);
    }
    public ShowCommandContext showCommand() {
      return getRuleContext(ShowCommandContext.class,0);
    }
    public MetricsCommandContext metricsCommand() {
      return getRuleContext(MetricsCommandContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public SourceCommandContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_sourceCommand; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterSourceCommand(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitSourceCommand(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitSourceCommand(this);
      else return visitor.visitChildren(this);
    }
  }

  public final SourceCommandContext sourceCommand() throws RecognitionException {
    SourceCommandContext _localctx = new SourceCommandContext(_ctx, getState());
    enterRule(_localctx, 4, RULE_sourceCommand);
    try {
      setState(141);
      _errHandler.sync(this);
      switch ( getInterpreter().adaptivePredict(_input,1,_ctx) ) {
      case 1:
        enterOuterAlt(_localctx, 1);
        {
        setState(134);
        explainCommand();
        }
        break;
      case 2:
        enterOuterAlt(_localctx, 2);
        {
        setState(135);
        fromCommand();
        }
        break;
      case 3:
        enterOuterAlt(_localctx, 3);
        {
        setState(136);
        metaCommand();
        }
        break;
      case 4:
        enterOuterAlt(_localctx, 4);
        {
        setState(137);
        rowCommand();
        }
        break;
      case 5:
        enterOuterAlt(_localctx, 5);
        {
        setState(138);
        showCommand();
        }
        break;
      case 6:
        enterOuterAlt(_localctx, 6);
        {
        setState(139);
        if (!(this.isDevVersion())) throw new FailedPredicateException(this, "this.isDevVersion()");
        setState(140);
        metricsCommand();
        }
        break;
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class ProcessingCommandContext extends ParserRuleContext {
    public EvalCommandContext evalCommand() {
      return getRuleContext(EvalCommandContext.class,0);
    }
    public WhereCommandContext whereCommand() {
      return getRuleContext(WhereCommandContext.class,0);
    }
    public KeepCommandContext keepCommand() {
      return getRuleContext(KeepCommandContext.class,0);
    }
    public LimitCommandContext limitCommand() {
      return getRuleContext(LimitCommandContext.class,0);
    }
    public StatsCommandContext statsCommand() {
      return getRuleContext(StatsCommandContext.class,0);
    }
    public SortCommandContext sortCommand() {
      return getRuleContext(SortCommandContext.class,0);
    }
    public DropCommandContext dropCommand() {
      return getRuleContext(DropCommandContext.class,0);
    }
    public RenameCommandContext renameCommand() {
      return getRuleContext(RenameCommandContext.class,0);
    }
    public DissectCommandContext dissectCommand() {
      return getRuleContext(DissectCommandContext.class,0);
    }
    public GrokCommandContext grokCommand() {
      return getRuleContext(GrokCommandContext.class,0);
    }
    public EnrichCommandContext enrichCommand() {
      return getRuleContext(EnrichCommandContext.class,0);
    }
    public MvExpandCommandContext mvExpandCommand() {
      return getRuleContext(MvExpandCommandContext.class,0);
    }
    public InlinestatsCommandContext inlinestatsCommand() {
      return getRuleContext(InlinestatsCommandContext.class,0);
    }
    public LookupCommandContext lookupCommand() {
      return getRuleContext(LookupCommandContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public ProcessingCommandContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_processingCommand; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterProcessingCommand(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitProcessingCommand(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitProcessingCommand(this);
      else return visitor.visitChildren(this);
    }
  }

  public final ProcessingCommandContext processingCommand() throws RecognitionException {
    ProcessingCommandContext _localctx = new ProcessingCommandContext(_ctx, getState());
    enterRule(_localctx, 6, RULE_processingCommand);
    try {
      setState(159);
      _errHandler.sync(this);
      switch ( getInterpreter().adaptivePredict(_input,2,_ctx) ) {
      case 1:
        enterOuterAlt(_localctx, 1);
        {
        setState(143);
        evalCommand();
        }
        break;
      case 2:
        enterOuterAlt(_localctx, 2);
        {
        setState(144);
        whereCommand();
        }
        break;
      case 3:
        enterOuterAlt(_localctx, 3);
        {
        setState(145);
        keepCommand();
        }
        break;
      case 4:
        enterOuterAlt(_localctx, 4);
        {
        setState(146);
        limitCommand();
        }
        break;
      case 5:
        enterOuterAlt(_localctx, 5);
        {
        setState(147);
        statsCommand();
        }
        break;
      case 6:
        enterOuterAlt(_localctx, 6);
        {
        setState(148);
        sortCommand();
        }
        break;
      case 7:
        enterOuterAlt(_localctx, 7);
        {
        setState(149);
        dropCommand();
        }
        break;
      case 8:
        enterOuterAlt(_localctx, 8);
        {
        setState(150);
        renameCommand();
        }
        break;
      case 9:
        enterOuterAlt(_localctx, 9);
        {
        setState(151);
        dissectCommand();
        }
        break;
      case 10:
        enterOuterAlt(_localctx, 10);
        {
        setState(152);
        grokCommand();
        }
        break;
      case 11:
        enterOuterAlt(_localctx, 11);
        {
        setState(153);
        enrichCommand();
        }
        break;
      case 12:
        enterOuterAlt(_localctx, 12);
        {
        setState(154);
        mvExpandCommand();
        }
        break;
      case 13:
        enterOuterAlt(_localctx, 13);
        {
        setState(155);
        if (!(this.isDevVersion())) throw new FailedPredicateException(this, "this.isDevVersion()");
        setState(156);
        inlinestatsCommand();
        }
        break;
      case 14:
        enterOuterAlt(_localctx, 14);
        {
        setState(157);
        if (!(this.isDevVersion())) throw new FailedPredicateException(this, "this.isDevVersion()");
        setState(158);
        lookupCommand();
        }
        break;
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class WhereCommandContext extends ParserRuleContext {
    public TerminalNode WHERE() { return getToken(EsqlBaseParser.WHERE, 0); }
    public BooleanExpressionContext booleanExpression() {
      return getRuleContext(BooleanExpressionContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public WhereCommandContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_whereCommand; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterWhereCommand(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitWhereCommand(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitWhereCommand(this);
      else return visitor.visitChildren(this);
    }
  }

  public final WhereCommandContext whereCommand() throws RecognitionException {
    WhereCommandContext _localctx = new WhereCommandContext(_ctx, getState());
    enterRule(_localctx, 8, RULE_whereCommand);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(161);
      match(WHERE);
      setState(162);
      booleanExpression(0);
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class BooleanExpressionContext extends ParserRuleContext {
    @SuppressWarnings("this-escape")
    public BooleanExpressionContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_booleanExpression; }

    @SuppressWarnings("this-escape")
    public BooleanExpressionContext() { }
    public void copyFrom(BooleanExpressionContext ctx) {
      super.copyFrom(ctx);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class MatchExpressionContext extends BooleanExpressionContext {
    public MatchBooleanExpressionContext matchBooleanExpression() {
      return getRuleContext(MatchBooleanExpressionContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public MatchExpressionContext(BooleanExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterMatchExpression(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitMatchExpression(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitMatchExpression(this);
      else return visitor.visitChildren(this);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class LogicalNotContext extends BooleanExpressionContext {
    public TerminalNode NOT() { return getToken(EsqlBaseParser.NOT, 0); }
    public BooleanExpressionContext booleanExpression() {
      return getRuleContext(BooleanExpressionContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public LogicalNotContext(BooleanExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterLogicalNot(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitLogicalNot(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitLogicalNot(this);
      else return visitor.visitChildren(this);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class BooleanDefaultContext extends BooleanExpressionContext {
    public ValueExpressionContext valueExpression() {
      return getRuleContext(ValueExpressionContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public BooleanDefaultContext(BooleanExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterBooleanDefault(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitBooleanDefault(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitBooleanDefault(this);
      else return visitor.visitChildren(this);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class IsNullContext extends BooleanExpressionContext {
    public ValueExpressionContext valueExpression() {
      return getRuleContext(ValueExpressionContext.class,0);
    }
    public TerminalNode IS() { return getToken(EsqlBaseParser.IS, 0); }
    public TerminalNode NULL() { return getToken(EsqlBaseParser.NULL, 0); }
    public TerminalNode NOT() { return getToken(EsqlBaseParser.NOT, 0); }
    @SuppressWarnings("this-escape")
    public IsNullContext(BooleanExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterIsNull(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitIsNull(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitIsNull(this);
      else return visitor.visitChildren(this);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class RegexExpressionContext extends BooleanExpressionContext {
    public RegexBooleanExpressionContext regexBooleanExpression() {
      return getRuleContext(RegexBooleanExpressionContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public RegexExpressionContext(BooleanExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterRegexExpression(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitRegexExpression(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitRegexExpression(this);
      else return visitor.visitChildren(this);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class LogicalInContext extends BooleanExpressionContext {
    public List<ValueExpressionContext> valueExpression() {
      return getRuleContexts(ValueExpressionContext.class);
    }
    public ValueExpressionContext valueExpression(int i) {
      return getRuleContext(ValueExpressionContext.class,i);
    }
    public TerminalNode IN() { return getToken(EsqlBaseParser.IN, 0); }
    public TerminalNode LP() { return getToken(EsqlBaseParser.LP, 0); }
    public TerminalNode RP() { return getToken(EsqlBaseParser.RP, 0); }
    public TerminalNode NOT() { return getToken(EsqlBaseParser.NOT, 0); }
    public List<TerminalNode> COMMA() { return getTokens(EsqlBaseParser.COMMA); }
    public TerminalNode COMMA(int i) {
      return getToken(EsqlBaseParser.COMMA, i);
    }
    @SuppressWarnings("this-escape")
    public LogicalInContext(BooleanExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterLogicalIn(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitLogicalIn(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitLogicalIn(this);
      else return visitor.visitChildren(this);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class LogicalBinaryContext extends BooleanExpressionContext {
    public BooleanExpressionContext left;
    public Token operator;
    public BooleanExpressionContext right;
    public List<BooleanExpressionContext> booleanExpression() {
      return getRuleContexts(BooleanExpressionContext.class);
    }
    public BooleanExpressionContext booleanExpression(int i) {
      return getRuleContext(BooleanExpressionContext.class,i);
    }
    public TerminalNode AND() { return getToken(EsqlBaseParser.AND, 0); }
    public TerminalNode OR() { return getToken(EsqlBaseParser.OR, 0); }
    @SuppressWarnings("this-escape")
    public LogicalBinaryContext(BooleanExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterLogicalBinary(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitLogicalBinary(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitLogicalBinary(this);
      else return visitor.visitChildren(this);
    }
  }

  public final BooleanExpressionContext booleanExpression() throws RecognitionException {
    return booleanExpression(0);
  }

  private BooleanExpressionContext booleanExpression(int _p) throws RecognitionException {
    ParserRuleContext _parentctx = _ctx;
    int _parentState = getState();
    BooleanExpressionContext _localctx = new BooleanExpressionContext(_ctx, _parentState);
    BooleanExpressionContext _prevctx = _localctx;
    int _startState = 10;
    enterRecursionRule(_localctx, 10, RULE_booleanExpression, _p);
    int _la;
    try {
      int _alt;
      enterOuterAlt(_localctx, 1);
      {
      setState(194);
      _errHandler.sync(this);
      switch ( getInterpreter().adaptivePredict(_input,6,_ctx) ) {
      case 1:
        {
        _localctx = new LogicalNotContext(_localctx);
        _ctx = _localctx;
        _prevctx = _localctx;

        setState(165);
        match(NOT);
        setState(166);
        booleanExpression(8);
        }
        break;
      case 2:
        {
        _localctx = new BooleanDefaultContext(_localctx);
        _ctx = _localctx;
        _prevctx = _localctx;
        setState(167);
        valueExpression();
        }
        break;
      case 3:
        {
        _localctx = new RegexExpressionContext(_localctx);
        _ctx = _localctx;
        _prevctx = _localctx;
        setState(168);
        regexBooleanExpression();
        }
        break;
      case 4:
        {
        _localctx = new LogicalInContext(_localctx);
        _ctx = _localctx;
        _prevctx = _localctx;
        setState(169);
        valueExpression();
        setState(171);
        _errHandler.sync(this);
        _la = _input.LA(1);
        if (_la==NOT) {
          {
          setState(170);
          match(NOT);
          }
        }

        setState(173);
        match(IN);
        setState(174);
        match(LP);
        setState(175);
        valueExpression();
        setState(180);
        _errHandler.sync(this);
        _la = _input.LA(1);
        while (_la==COMMA) {
          {
          {
          setState(176);
          match(COMMA);
          setState(177);
          valueExpression();
          }
          }
          setState(182);
          _errHandler.sync(this);
          _la = _input.LA(1);
        }
        setState(183);
        match(RP);
        }
        break;
      case 5:
        {
        _localctx = new IsNullContext(_localctx);
        _ctx = _localctx;
        _prevctx = _localctx;
        setState(185);
        valueExpression();
        setState(186);
        match(IS);
        setState(188);
        _errHandler.sync(this);
        _la = _input.LA(1);
        if (_la==NOT) {
          {
          setState(187);
          match(NOT);
          }
        }

        setState(190);
        match(NULL);
        }
        break;
      case 6:
        {
        _localctx = new MatchExpressionContext(_localctx);
        _ctx = _localctx;
        _prevctx = _localctx;
        setState(192);
        if (!(this.isDevVersion())) throw new FailedPredicateException(this, "this.isDevVersion()");
        setState(193);
        matchBooleanExpression();
        }
        break;
      }
      _ctx.stop = _input.LT(-1);
      setState(204);
      _errHandler.sync(this);
      _alt = getInterpreter().adaptivePredict(_input,8,_ctx);
      while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
        if ( _alt==1 ) {
          if ( _parseListeners!=null ) triggerExitRuleEvent();
          _prevctx = _localctx;
          {
          setState(202);
          _errHandler.sync(this);
          switch ( getInterpreter().adaptivePredict(_input,7,_ctx) ) {
          case 1:
            {
            _localctx = new LogicalBinaryContext(new BooleanExpressionContext(_parentctx, _parentState));
            ((LogicalBinaryContext)_localctx).left = _prevctx;
            pushNewRecursionContext(_localctx, _startState, RULE_booleanExpression);
            setState(196);
            if (!(precpred(_ctx, 5))) throw new FailedPredicateException(this, "precpred(_ctx, 5)");
            setState(197);
            ((LogicalBinaryContext)_localctx).operator = match(AND);
            setState(198);
            ((LogicalBinaryContext)_localctx).right = booleanExpression(6);
            }
            break;
          case 2:
            {
            _localctx = new LogicalBinaryContext(new BooleanExpressionContext(_parentctx, _parentState));
            ((LogicalBinaryContext)_localctx).left = _prevctx;
            pushNewRecursionContext(_localctx, _startState, RULE_booleanExpression);
            setState(199);
            if (!(precpred(_ctx, 4))) throw new FailedPredicateException(this, "precpred(_ctx, 4)");
            setState(200);
            ((LogicalBinaryContext)_localctx).operator = match(OR);
            setState(201);
            ((LogicalBinaryContext)_localctx).right = booleanExpression(5);
            }
            break;
          }
          }
        }
        setState(206);
        _errHandler.sync(this);
        _alt = getInterpreter().adaptivePredict(_input,8,_ctx);
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      unrollRecursionContexts(_parentctx);
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class RegexBooleanExpressionContext extends ParserRuleContext {
    public Token kind;
    public StringContext pattern;
    public ValueExpressionContext valueExpression() {
      return getRuleContext(ValueExpressionContext.class,0);
    }
    public TerminalNode LIKE() { return getToken(EsqlBaseParser.LIKE, 0); }
    public StringContext string() {
      return getRuleContext(StringContext.class,0);
    }
    public TerminalNode NOT() { return getToken(EsqlBaseParser.NOT, 0); }
    public TerminalNode RLIKE() { return getToken(EsqlBaseParser.RLIKE, 0); }
    @SuppressWarnings("this-escape")
    public RegexBooleanExpressionContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_regexBooleanExpression; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterRegexBooleanExpression(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitRegexBooleanExpression(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitRegexBooleanExpression(this);
      else return visitor.visitChildren(this);
    }
  }

  public final RegexBooleanExpressionContext regexBooleanExpression() throws RecognitionException {
    RegexBooleanExpressionContext _localctx = new RegexBooleanExpressionContext(_ctx, getState());
    enterRule(_localctx, 12, RULE_regexBooleanExpression);
    int _la;
    try {
      setState(221);
      _errHandler.sync(this);
      switch ( getInterpreter().adaptivePredict(_input,11,_ctx) ) {
      case 1:
        enterOuterAlt(_localctx, 1);
        {
        setState(207);
        valueExpression();
        setState(209);
        _errHandler.sync(this);
        _la = _input.LA(1);
        if (_la==NOT) {
          {
          setState(208);
          match(NOT);
          }
        }

        setState(211);
        ((RegexBooleanExpressionContext)_localctx).kind = match(LIKE);
        setState(212);
        ((RegexBooleanExpressionContext)_localctx).pattern = string();
        }
        break;
      case 2:
        enterOuterAlt(_localctx, 2);
        {
        setState(214);
        valueExpression();
        setState(216);
        _errHandler.sync(this);
        _la = _input.LA(1);
        if (_la==NOT) {
          {
          setState(215);
          match(NOT);
          }
        }

        setState(218);
        ((RegexBooleanExpressionContext)_localctx).kind = match(RLIKE);
        setState(219);
        ((RegexBooleanExpressionContext)_localctx).pattern = string();
        }
        break;
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class MatchBooleanExpressionContext extends ParserRuleContext {
    public StringContext queryString;
    public ValueExpressionContext valueExpression() {
      return getRuleContext(ValueExpressionContext.class,0);
    }
    public TerminalNode DEV_MATCH() { return getToken(EsqlBaseParser.DEV_MATCH, 0); }
    public StringContext string() {
      return getRuleContext(StringContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public MatchBooleanExpressionContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_matchBooleanExpression; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterMatchBooleanExpression(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitMatchBooleanExpression(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitMatchBooleanExpression(this);
      else return visitor.visitChildren(this);
    }
  }

  public final MatchBooleanExpressionContext matchBooleanExpression() throws RecognitionException {
    MatchBooleanExpressionContext _localctx = new MatchBooleanExpressionContext(_ctx, getState());
    enterRule(_localctx, 14, RULE_matchBooleanExpression);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(223);
      valueExpression();
      setState(224);
      match(DEV_MATCH);
      setState(225);
      ((MatchBooleanExpressionContext)_localctx).queryString = string();
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class ValueExpressionContext extends ParserRuleContext {
    @SuppressWarnings("this-escape")
    public ValueExpressionContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_valueExpression; }

    @SuppressWarnings("this-escape")
    public ValueExpressionContext() { }
    public void copyFrom(ValueExpressionContext ctx) {
      super.copyFrom(ctx);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class ValueExpressionDefaultContext extends ValueExpressionContext {
    public OperatorExpressionContext operatorExpression() {
      return getRuleContext(OperatorExpressionContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public ValueExpressionDefaultContext(ValueExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterValueExpressionDefault(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitValueExpressionDefault(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitValueExpressionDefault(this);
      else return visitor.visitChildren(this);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class ComparisonContext extends ValueExpressionContext {
    public OperatorExpressionContext left;
    public OperatorExpressionContext right;
    public ComparisonOperatorContext comparisonOperator() {
      return getRuleContext(ComparisonOperatorContext.class,0);
    }
    public List<OperatorExpressionContext> operatorExpression() {
      return getRuleContexts(OperatorExpressionContext.class);
    }
    public OperatorExpressionContext operatorExpression(int i) {
      return getRuleContext(OperatorExpressionContext.class,i);
    }
    @SuppressWarnings("this-escape")
    public ComparisonContext(ValueExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterComparison(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitComparison(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitComparison(this);
      else return visitor.visitChildren(this);
    }
  }

  public final ValueExpressionContext valueExpression() throws RecognitionException {
    ValueExpressionContext _localctx = new ValueExpressionContext(_ctx, getState());
    enterRule(_localctx, 16, RULE_valueExpression);
    try {
      setState(232);
      _errHandler.sync(this);
      switch ( getInterpreter().adaptivePredict(_input,12,_ctx) ) {
      case 1:
        _localctx = new ValueExpressionDefaultContext(_localctx);
        enterOuterAlt(_localctx, 1);
        {
        setState(227);
        operatorExpression(0);
        }
        break;
      case 2:
        _localctx = new ComparisonContext(_localctx);
        enterOuterAlt(_localctx, 2);
        {
        setState(228);
        ((ComparisonContext)_localctx).left = operatorExpression(0);
        setState(229);
        comparisonOperator();
        setState(230);
        ((ComparisonContext)_localctx).right = operatorExpression(0);
        }
        break;
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class OperatorExpressionContext extends ParserRuleContext {
    @SuppressWarnings("this-escape")
    public OperatorExpressionContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_operatorExpression; }

    @SuppressWarnings("this-escape")
    public OperatorExpressionContext() { }
    public void copyFrom(OperatorExpressionContext ctx) {
      super.copyFrom(ctx);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class OperatorExpressionDefaultContext extends OperatorExpressionContext {
    public PrimaryExpressionContext primaryExpression() {
      return getRuleContext(PrimaryExpressionContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public OperatorExpressionDefaultContext(OperatorExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterOperatorExpressionDefault(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitOperatorExpressionDefault(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitOperatorExpressionDefault(this);
      else return visitor.visitChildren(this);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class ArithmeticBinaryContext extends OperatorExpressionContext {
    public OperatorExpressionContext left;
    public Token operator;
    public OperatorExpressionContext right;
    public List<OperatorExpressionContext> operatorExpression() {
      return getRuleContexts(OperatorExpressionContext.class);
    }
    public OperatorExpressionContext operatorExpression(int i) {
      return getRuleContext(OperatorExpressionContext.class,i);
    }
    public TerminalNode ASTERISK() { return getToken(EsqlBaseParser.ASTERISK, 0); }
    public TerminalNode SLASH() { return getToken(EsqlBaseParser.SLASH, 0); }
    public TerminalNode PERCENT() { return getToken(EsqlBaseParser.PERCENT, 0); }
    public TerminalNode PLUS() { return getToken(EsqlBaseParser.PLUS, 0); }
    public TerminalNode MINUS() { return getToken(EsqlBaseParser.MINUS, 0); }
    @SuppressWarnings("this-escape")
    public ArithmeticBinaryContext(OperatorExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterArithmeticBinary(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitArithmeticBinary(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitArithmeticBinary(this);
      else return visitor.visitChildren(this);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class ArithmeticUnaryContext extends OperatorExpressionContext {
    public Token operator;
    public OperatorExpressionContext operatorExpression() {
      return getRuleContext(OperatorExpressionContext.class,0);
    }
    public TerminalNode MINUS() { return getToken(EsqlBaseParser.MINUS, 0); }
    public TerminalNode PLUS() { return getToken(EsqlBaseParser.PLUS, 0); }
    @SuppressWarnings("this-escape")
    public ArithmeticUnaryContext(OperatorExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterArithmeticUnary(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitArithmeticUnary(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitArithmeticUnary(this);
      else return visitor.visitChildren(this);
    }
  }

  public final OperatorExpressionContext operatorExpression() throws RecognitionException {
    return operatorExpression(0);
  }

  private OperatorExpressionContext operatorExpression(int _p) throws RecognitionException {
    ParserRuleContext _parentctx = _ctx;
    int _parentState = getState();
    OperatorExpressionContext _localctx = new OperatorExpressionContext(_ctx, _parentState);
    OperatorExpressionContext _prevctx = _localctx;
    int _startState = 18;
    enterRecursionRule(_localctx, 18, RULE_operatorExpression, _p);
    int _la;
    try {
      int _alt;
      enterOuterAlt(_localctx, 1);
      {
      setState(238);
      _errHandler.sync(this);
      switch ( getInterpreter().adaptivePredict(_input,13,_ctx) ) {
      case 1:
        {
        _localctx = new OperatorExpressionDefaultContext(_localctx);
        _ctx = _localctx;
        _prevctx = _localctx;

        setState(235);
        primaryExpression(0);
        }
        break;
      case 2:
        {
        _localctx = new ArithmeticUnaryContext(_localctx);
        _ctx = _localctx;
        _prevctx = _localctx;
        setState(236);
        ((ArithmeticUnaryContext)_localctx).operator = _input.LT(1);
        _la = _input.LA(1);
        if ( !(_la==PLUS || _la==MINUS) ) {
          ((ArithmeticUnaryContext)_localctx).operator = (Token)_errHandler.recoverInline(this);
        }
        else {
          if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
          _errHandler.reportMatch(this);
          consume();
        }
        setState(237);
        operatorExpression(3);
        }
        break;
      }
      _ctx.stop = _input.LT(-1);
      setState(248);
      _errHandler.sync(this);
      _alt = getInterpreter().adaptivePredict(_input,15,_ctx);
      while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
        if ( _alt==1 ) {
          if ( _parseListeners!=null ) triggerExitRuleEvent();
          _prevctx = _localctx;
          {
          setState(246);
          _errHandler.sync(this);
          switch ( getInterpreter().adaptivePredict(_input,14,_ctx) ) {
          case 1:
            {
            _localctx = new ArithmeticBinaryContext(new OperatorExpressionContext(_parentctx, _parentState));
            ((ArithmeticBinaryContext)_localctx).left = _prevctx;
            pushNewRecursionContext(_localctx, _startState, RULE_operatorExpression);
            setState(240);
            if (!(precpred(_ctx, 2))) throw new FailedPredicateException(this, "precpred(_ctx, 2)");
            setState(241);
            ((ArithmeticBinaryContext)_localctx).operator = _input.LT(1);
            _la = _input.LA(1);
            if ( !(((((_la - 62)) & ~0x3f) == 0 && ((1L << (_la - 62)) & 7L) != 0)) ) {
              ((ArithmeticBinaryContext)_localctx).operator = (Token)_errHandler.recoverInline(this);
            }
            else {
              if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
              _errHandler.reportMatch(this);
              consume();
            }
            setState(242);
            ((ArithmeticBinaryContext)_localctx).right = operatorExpression(3);
            }
            break;
          case 2:
            {
            _localctx = new ArithmeticBinaryContext(new OperatorExpressionContext(_parentctx, _parentState));
            ((ArithmeticBinaryContext)_localctx).left = _prevctx;
            pushNewRecursionContext(_localctx, _startState, RULE_operatorExpression);
            setState(243);
            if (!(precpred(_ctx, 1))) throw new FailedPredicateException(this, "precpred(_ctx, 1)");
            setState(244);
            ((ArithmeticBinaryContext)_localctx).operator = _input.LT(1);
            _la = _input.LA(1);
            if ( !(_la==PLUS || _la==MINUS) ) {
              ((ArithmeticBinaryContext)_localctx).operator = (Token)_errHandler.recoverInline(this);
            }
            else {
              if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
              _errHandler.reportMatch(this);
              consume();
            }
            setState(245);
            ((ArithmeticBinaryContext)_localctx).right = operatorExpression(2);
            }
            break;
          }
          }
        }
        setState(250);
        _errHandler.sync(this);
        _alt = getInterpreter().adaptivePredict(_input,15,_ctx);
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      unrollRecursionContexts(_parentctx);
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class PrimaryExpressionContext extends ParserRuleContext {
    @SuppressWarnings("this-escape")
    public PrimaryExpressionContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_primaryExpression; }

    @SuppressWarnings("this-escape")
    public PrimaryExpressionContext() { }
    public void copyFrom(PrimaryExpressionContext ctx) {
      super.copyFrom(ctx);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class DereferenceContext extends PrimaryExpressionContext {
    public QualifiedNameContext qualifiedName() {
      return getRuleContext(QualifiedNameContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public DereferenceContext(PrimaryExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterDereference(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitDereference(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitDereference(this);
      else return visitor.visitChildren(this);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class InlineCastContext extends PrimaryExpressionContext {
    public PrimaryExpressionContext primaryExpression() {
      return getRuleContext(PrimaryExpressionContext.class,0);
    }
    public TerminalNode CAST_OP() { return getToken(EsqlBaseParser.CAST_OP, 0); }
    public DataTypeContext dataType() {
      return getRuleContext(DataTypeContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public InlineCastContext(PrimaryExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterInlineCast(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitInlineCast(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitInlineCast(this);
      else return visitor.visitChildren(this);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class ConstantDefaultContext extends PrimaryExpressionContext {
    public ConstantContext constant() {
      return getRuleContext(ConstantContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public ConstantDefaultContext(PrimaryExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterConstantDefault(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitConstantDefault(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitConstantDefault(this);
      else return visitor.visitChildren(this);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class ParenthesizedExpressionContext extends PrimaryExpressionContext {
    public TerminalNode LP() { return getToken(EsqlBaseParser.LP, 0); }
    public BooleanExpressionContext booleanExpression() {
      return getRuleContext(BooleanExpressionContext.class,0);
    }
    public TerminalNode RP() { return getToken(EsqlBaseParser.RP, 0); }
    @SuppressWarnings("this-escape")
    public ParenthesizedExpressionContext(PrimaryExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterParenthesizedExpression(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitParenthesizedExpression(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitParenthesizedExpression(this);
      else return visitor.visitChildren(this);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class FunctionContext extends PrimaryExpressionContext {
    public FunctionExpressionContext functionExpression() {
      return getRuleContext(FunctionExpressionContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public FunctionContext(PrimaryExpressionContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterFunction(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitFunction(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitFunction(this);
      else return visitor.visitChildren(this);
    }
  }

  public final PrimaryExpressionContext primaryExpression() throws RecognitionException {
    return primaryExpression(0);
  }

  private PrimaryExpressionContext primaryExpression(int _p) throws RecognitionException {
    ParserRuleContext _parentctx = _ctx;
    int _parentState = getState();
    PrimaryExpressionContext _localctx = new PrimaryExpressionContext(_ctx, _parentState);
    PrimaryExpressionContext _prevctx = _localctx;
    int _startState = 20;
    enterRecursionRule(_localctx, 20, RULE_primaryExpression, _p);
    try {
      int _alt;
      enterOuterAlt(_localctx, 1);
      {
      setState(259);
      _errHandler.sync(this);
      switch ( getInterpreter().adaptivePredict(_input,16,_ctx) ) {
      case 1:
        {
        _localctx = new ConstantDefaultContext(_localctx);
        _ctx = _localctx;
        _prevctx = _localctx;

        setState(252);
        constant();
        }
        break;
      case 2:
        {
        _localctx = new DereferenceContext(_localctx);
        _ctx = _localctx;
        _prevctx = _localctx;
        setState(253);
        qualifiedName();
        }
        break;
      case 3:
        {
        _localctx = new FunctionContext(_localctx);
        _ctx = _localctx;
        _prevctx = _localctx;
        setState(254);
        functionExpression();
        }
        break;
      case 4:
        {
        _localctx = new ParenthesizedExpressionContext(_localctx);
        _ctx = _localctx;
        _prevctx = _localctx;
        setState(255);
        match(LP);
        setState(256);
        booleanExpression(0);
        setState(257);
        match(RP);
        }
        break;
      }
      _ctx.stop = _input.LT(-1);
      setState(266);
      _errHandler.sync(this);
      _alt = getInterpreter().adaptivePredict(_input,17,_ctx);
      while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
        if ( _alt==1 ) {
          if ( _parseListeners!=null ) triggerExitRuleEvent();
          _prevctx = _localctx;
          {
          {
          _localctx = new InlineCastContext(new PrimaryExpressionContext(_parentctx, _parentState));
          pushNewRecursionContext(_localctx, _startState, RULE_primaryExpression);
          setState(261);
          if (!(precpred(_ctx, 1))) throw new FailedPredicateException(this, "precpred(_ctx, 1)");
          setState(262);
          match(CAST_OP);
          setState(263);
          dataType();
          }
          }
        }
        setState(268);
        _errHandler.sync(this);
        _alt = getInterpreter().adaptivePredict(_input,17,_ctx);
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      unrollRecursionContexts(_parentctx);
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class FunctionExpressionContext extends ParserRuleContext {
    public FunctionNameContext functionName() {
      return getRuleContext(FunctionNameContext.class,0);
    }
    public TerminalNode LP() { return getToken(EsqlBaseParser.LP, 0); }
    public TerminalNode RP() { return getToken(EsqlBaseParser.RP, 0); }
    public TerminalNode ASTERISK() { return getToken(EsqlBaseParser.ASTERISK, 0); }
    public List<BooleanExpressionContext> booleanExpression() {
      return getRuleContexts(BooleanExpressionContext.class);
    }
    public BooleanExpressionContext booleanExpression(int i) {
      return getRuleContext(BooleanExpressionContext.class,i);
    }
    public List<TerminalNode> COMMA() { return getTokens(EsqlBaseParser.COMMA); }
    public TerminalNode COMMA(int i) {
      return getToken(EsqlBaseParser.COMMA, i);
    }
    @SuppressWarnings("this-escape")
    public FunctionExpressionContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_functionExpression; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterFunctionExpression(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitFunctionExpression(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitFunctionExpression(this);
      else return visitor.visitChildren(this);
    }
  }

  public final FunctionExpressionContext functionExpression() throws RecognitionException {
    FunctionExpressionContext _localctx = new FunctionExpressionContext(_ctx, getState());
    enterRule(_localctx, 22, RULE_functionExpression);
    int _la;
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(269);
      functionName();
      setState(270);
      match(LP);
      setState(280);
      _errHandler.sync(this);
      switch ( getInterpreter().adaptivePredict(_input,19,_ctx) ) {
      case 1:
        {
        setState(271);
        match(ASTERISK);
        }
        break;
      case 2:
        {
        {
        setState(272);
        booleanExpression(0);
        setState(277);
        _errHandler.sync(this);
        _la = _input.LA(1);
        while (_la==COMMA) {
          {
          {
          setState(273);
          match(COMMA);
          setState(274);
          booleanExpression(0);
          }
          }
          setState(279);
          _errHandler.sync(this);
          _la = _input.LA(1);
        }
        }
        }
        break;
      }
      setState(282);
      match(RP);
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class FunctionNameContext extends ParserRuleContext {
    public TerminalNode DEV_MATCH() { return getToken(EsqlBaseParser.DEV_MATCH, 0); }
    public IdentifierContext identifier() {
      return getRuleContext(IdentifierContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public FunctionNameContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_functionName; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterFunctionName(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitFunctionName(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitFunctionName(this);
      else return visitor.visitChildren(this);
    }
  }

  public final FunctionNameContext functionName() throws RecognitionException {
    FunctionNameContext _localctx = new FunctionNameContext(_ctx, getState());
    enterRule(_localctx, 24, RULE_functionName);
    try {
      setState(287);
      _errHandler.sync(this);
      switch ( getInterpreter().adaptivePredict(_input,20,_ctx) ) {
      case 1:
        enterOuterAlt(_localctx, 1);
        {
        setState(284);
        if (!(this.isDevVersion())) throw new FailedPredicateException(this, "this.isDevVersion()");
        setState(285);
        match(DEV_MATCH);
        }
        break;
      case 2:
        enterOuterAlt(_localctx, 2);
        {
        setState(286);
        identifier();
        }
        break;
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class DataTypeContext extends ParserRuleContext {
    @SuppressWarnings("this-escape")
    public DataTypeContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_dataType; }

    @SuppressWarnings("this-escape")
    public DataTypeContext() { }
    public void copyFrom(DataTypeContext ctx) {
      super.copyFrom(ctx);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class ToDataTypeContext extends DataTypeContext {
    public IdentifierContext identifier() {
      return getRuleContext(IdentifierContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public ToDataTypeContext(DataTypeContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterToDataType(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitToDataType(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitToDataType(this);
      else return visitor.visitChildren(this);
    }
  }

  public final DataTypeContext dataType() throws RecognitionException {
    DataTypeContext _localctx = new DataTypeContext(_ctx, getState());
    enterRule(_localctx, 26, RULE_dataType);
    try {
      _localctx = new ToDataTypeContext(_localctx);
      enterOuterAlt(_localctx, 1);
      {
      setState(289);
      identifier();
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class RowCommandContext extends ParserRuleContext {
    public TerminalNode ROW() { return getToken(EsqlBaseParser.ROW, 0); }
    public FieldsContext fields() {
      return getRuleContext(FieldsContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public RowCommandContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_rowCommand; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterRowCommand(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitRowCommand(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitRowCommand(this);
      else return visitor.visitChildren(this);
    }
  }

  public final RowCommandContext rowCommand() throws RecognitionException {
    RowCommandContext _localctx = new RowCommandContext(_ctx, getState());
    enterRule(_localctx, 28, RULE_rowCommand);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(291);
      match(ROW);
      setState(292);
      fields();
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class FieldsContext extends ParserRuleContext {
    public List<FieldContext> field() {
      return getRuleContexts(FieldContext.class);
    }
    public FieldContext field(int i) {
      return getRuleContext(FieldContext.class,i);
    }
    public List<TerminalNode> COMMA() { return getTokens(EsqlBaseParser.COMMA); }
    public TerminalNode COMMA(int i) {
      return getToken(EsqlBaseParser.COMMA, i);
    }
    @SuppressWarnings("this-escape")
    public FieldsContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_fields; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterFields(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitFields(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitFields(this);
      else return visitor.visitChildren(this);
    }
  }

  public final FieldsContext fields() throws RecognitionException {
    FieldsContext _localctx = new FieldsContext(_ctx, getState());
    enterRule(_localctx, 30, RULE_fields);
    try {
      int _alt;
      enterOuterAlt(_localctx, 1);
      {
      setState(294);
      field();
      setState(299);
      _errHandler.sync(this);
      _alt = getInterpreter().adaptivePredict(_input,21,_ctx);
      while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
        if ( _alt==1 ) {
          {
          {
          setState(295);
          match(COMMA);
          setState(296);
          field();
          }
          }
        }
        setState(301);
        _errHandler.sync(this);
        _alt = getInterpreter().adaptivePredict(_input,21,_ctx);
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class FieldContext extends ParserRuleContext {
    public BooleanExpressionContext booleanExpression() {
      return getRuleContext(BooleanExpressionContext.class,0);
    }
    public QualifiedNameContext qualifiedName() {
      return getRuleContext(QualifiedNameContext.class,0);
    }
    public TerminalNode ASSIGN() { return getToken(EsqlBaseParser.ASSIGN, 0); }
    @SuppressWarnings("this-escape")
    public FieldContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_field; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterField(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitField(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitField(this);
      else return visitor.visitChildren(this);
    }
  }

  public final FieldContext field() throws RecognitionException {
    FieldContext _localctx = new FieldContext(_ctx, getState());
    enterRule(_localctx, 32, RULE_field);
    try {
      setState(307);
      _errHandler.sync(this);
      switch ( getInterpreter().adaptivePredict(_input,22,_ctx) ) {
      case 1:
        enterOuterAlt(_localctx, 1);
        {
        setState(302);
        booleanExpression(0);
        }
        break;
      case 2:
        enterOuterAlt(_localctx, 2);
        {
        setState(303);
        qualifiedName();
        setState(304);
        match(ASSIGN);
        setState(305);
        booleanExpression(0);
        }
        break;
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class FromCommandContext extends ParserRuleContext {
    public TerminalNode FROM() { return getToken(EsqlBaseParser.FROM, 0); }
    public List<IndexPatternContext> indexPattern() {
      return getRuleContexts(IndexPatternContext.class);
    }
    public IndexPatternContext indexPattern(int i) {
      return getRuleContext(IndexPatternContext.class,i);
    }
    public List<TerminalNode> COMMA() { return getTokens(EsqlBaseParser.COMMA); }
    public TerminalNode COMMA(int i) {
      return getToken(EsqlBaseParser.COMMA, i);
    }
    public MetadataContext metadata() {
      return getRuleContext(MetadataContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public FromCommandContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_fromCommand; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterFromCommand(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitFromCommand(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitFromCommand(this);
      else return visitor.visitChildren(this);
    }
  }

  public final FromCommandContext fromCommand() throws RecognitionException {
    FromCommandContext _localctx = new FromCommandContext(_ctx, getState());
    enterRule(_localctx, 34, RULE_fromCommand);
    try {
      int _alt;
      enterOuterAlt(_localctx, 1);
      {
      setState(309);
      match(FROM);
      setState(310);
      indexPattern();
      setState(315);
      _errHandler.sync(this);
      _alt = getInterpreter().adaptivePredict(_input,23,_ctx);
      while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
        if ( _alt==1 ) {
          {
          {
          setState(311);
          match(COMMA);
          setState(312);
          indexPattern();
          }
          }
        }
        setState(317);
        _errHandler.sync(this);
        _alt = getInterpreter().adaptivePredict(_input,23,_ctx);
      }
      setState(319);
      _errHandler.sync(this);
      switch ( getInterpreter().adaptivePredict(_input,24,_ctx) ) {
      case 1:
        {
        setState(318);
        metadata();
        }
        break;
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class IndexPatternContext extends ParserRuleContext {
    public ClusterStringContext clusterString() {
      return getRuleContext(ClusterStringContext.class,0);
    }
    public TerminalNode COLON() { return getToken(EsqlBaseParser.COLON, 0); }
    public IndexStringContext indexString() {
      return getRuleContext(IndexStringContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public IndexPatternContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_indexPattern; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterIndexPattern(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitIndexPattern(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitIndexPattern(this);
      else return visitor.visitChildren(this);
    }
  }

  public final IndexPatternContext indexPattern() throws RecognitionException {
    IndexPatternContext _localctx = new IndexPatternContext(_ctx, getState());
    enterRule(_localctx, 36, RULE_indexPattern);
    try {
      setState(326);
      _errHandler.sync(this);
      switch ( getInterpreter().adaptivePredict(_input,25,_ctx) ) {
      case 1:
        enterOuterAlt(_localctx, 1);
        {
        setState(321);
        clusterString();
        setState(322);
        match(COLON);
        setState(323);
        indexString();
        }
        break;
      case 2:
        enterOuterAlt(_localctx, 2);
        {
        setState(325);
        indexString();
        }
        break;
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class ClusterStringContext extends ParserRuleContext {
    public TerminalNode UNQUOTED_SOURCE() { return getToken(EsqlBaseParser.UNQUOTED_SOURCE, 0); }
    @SuppressWarnings("this-escape")
    public ClusterStringContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_clusterString; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterClusterString(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitClusterString(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitClusterString(this);
      else return visitor.visitChildren(this);
    }
  }

  public final ClusterStringContext clusterString() throws RecognitionException {
    ClusterStringContext _localctx = new ClusterStringContext(_ctx, getState());
    enterRule(_localctx, 38, RULE_clusterString);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(328);
      match(UNQUOTED_SOURCE);
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class IndexStringContext extends ParserRuleContext {
    public TerminalNode UNQUOTED_SOURCE() { return getToken(EsqlBaseParser.UNQUOTED_SOURCE, 0); }
    public TerminalNode QUOTED_STRING() { return getToken(EsqlBaseParser.QUOTED_STRING, 0); }
    @SuppressWarnings("this-escape")
    public IndexStringContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_indexString; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterIndexString(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitIndexString(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitIndexString(this);
      else return visitor.visitChildren(this);
    }
  }

  public final IndexStringContext indexString() throws RecognitionException {
    IndexStringContext _localctx = new IndexStringContext(_ctx, getState());
    enterRule(_localctx, 40, RULE_indexString);
    int _la;
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(330);
      _la = _input.LA(1);
      if ( !(_la==QUOTED_STRING || _la==UNQUOTED_SOURCE) ) {
      _errHandler.recoverInline(this);
      }
      else {
        if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
        _errHandler.reportMatch(this);
        consume();
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class MetadataContext extends ParserRuleContext {
    public MetadataOptionContext metadataOption() {
      return getRuleContext(MetadataOptionContext.class,0);
    }
    public Deprecated_metadataContext deprecated_metadata() {
      return getRuleContext(Deprecated_metadataContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public MetadataContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_metadata; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterMetadata(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitMetadata(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitMetadata(this);
      else return visitor.visitChildren(this);
    }
  }

  public final MetadataContext metadata() throws RecognitionException {
    MetadataContext _localctx = new MetadataContext(_ctx, getState());
    enterRule(_localctx, 42, RULE_metadata);
    try {
      setState(334);
      _errHandler.sync(this);
      switch (_input.LA(1)) {
      case METADATA:
        enterOuterAlt(_localctx, 1);
        {
        setState(332);
        metadataOption();
        }
        break;
      case OPENING_BRACKET:
        enterOuterAlt(_localctx, 2);
        {
        setState(333);
        deprecated_metadata();
        }
        break;
      default:
        throw new NoViableAltException(this);
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class MetadataOptionContext extends ParserRuleContext {
    public TerminalNode METADATA() { return getToken(EsqlBaseParser.METADATA, 0); }
    public List<TerminalNode> UNQUOTED_SOURCE() { return getTokens(EsqlBaseParser.UNQUOTED_SOURCE); }
    public TerminalNode UNQUOTED_SOURCE(int i) {
      return getToken(EsqlBaseParser.UNQUOTED_SOURCE, i);
    }
    public List<TerminalNode> COMMA() { return getTokens(EsqlBaseParser.COMMA); }
    public TerminalNode COMMA(int i) {
      return getToken(EsqlBaseParser.COMMA, i);
    }
    @SuppressWarnings("this-escape")
    public MetadataOptionContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_metadataOption; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterMetadataOption(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitMetadataOption(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitMetadataOption(this);
      else return visitor.visitChildren(this);
    }
  }

  public final MetadataOptionContext metadataOption() throws RecognitionException {
    MetadataOptionContext _localctx = new MetadataOptionContext(_ctx, getState());
    enterRule(_localctx, 44, RULE_metadataOption);
    try {
      int _alt;
      enterOuterAlt(_localctx, 1);
      {
      setState(336);
      match(METADATA);
      setState(337);
      match(UNQUOTED_SOURCE);
      setState(342);
      _errHandler.sync(this);
      _alt = getInterpreter().adaptivePredict(_input,27,_ctx);
      while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
        if ( _alt==1 ) {
          {
          {
          setState(338);
          match(COMMA);
          setState(339);
          match(UNQUOTED_SOURCE);
          }
          }
        }
        setState(344);
        _errHandler.sync(this);
        _alt = getInterpreter().adaptivePredict(_input,27,_ctx);
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class Deprecated_metadataContext extends ParserRuleContext {
    public TerminalNode OPENING_BRACKET() { return getToken(EsqlBaseParser.OPENING_BRACKET, 0); }
    public MetadataOptionContext metadataOption() {
      return getRuleContext(MetadataOptionContext.class,0);
    }
    public TerminalNode CLOSING_BRACKET() { return getToken(EsqlBaseParser.CLOSING_BRACKET, 0); }
    public Deprecated_metadataContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_deprecated_metadata; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterDeprecated_metadata(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitDeprecated_metadata(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitDeprecated_metadata(this);
      else return visitor.visitChildren(this);
    }
  }

  public final Deprecated_metadataContext deprecated_metadata() throws RecognitionException {
    Deprecated_metadataContext _localctx = new Deprecated_metadataContext(_ctx, getState());
    enterRule(_localctx, 46, RULE_deprecated_metadata);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(345);
      match(OPENING_BRACKET);
      setState(346);
      metadataOption();
      setState(347);
      match(CLOSING_BRACKET);
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class MetricsCommandContext extends ParserRuleContext {
    public FieldsContext aggregates;
    public FieldsContext grouping;
    public TerminalNode DEV_METRICS() { return getToken(EsqlBaseParser.DEV_METRICS, 0); }
    public List<IndexPatternContext> indexPattern() {
      return getRuleContexts(IndexPatternContext.class);
    }
    public IndexPatternContext indexPattern(int i) {
      return getRuleContext(IndexPatternContext.class,i);
    }
    public List<TerminalNode> COMMA() { return getTokens(EsqlBaseParser.COMMA); }
    public TerminalNode COMMA(int i) {
      return getToken(EsqlBaseParser.COMMA, i);
    }
    public TerminalNode BY() { return getToken(EsqlBaseParser.BY, 0); }
    public List<FieldsContext> fields() {
      return getRuleContexts(FieldsContext.class);
    }
    public FieldsContext fields(int i) {
      return getRuleContext(FieldsContext.class,i);
    }
    @SuppressWarnings("this-escape")
    public MetricsCommandContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_metricsCommand; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterMetricsCommand(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitMetricsCommand(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitMetricsCommand(this);
      else return visitor.visitChildren(this);
    }
  }

  public final MetricsCommandContext metricsCommand() throws RecognitionException {
    MetricsCommandContext _localctx = new MetricsCommandContext(_ctx, getState());
    enterRule(_localctx, 48, RULE_metricsCommand);
    try {
      int _alt;
      enterOuterAlt(_localctx, 1);
      {
      setState(349);
      match(DEV_METRICS);
      setState(350);
      indexPattern();
      setState(355);
      _errHandler.sync(this);
      _alt = getInterpreter().adaptivePredict(_input,28,_ctx);
      while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
        if ( _alt==1 ) {
          {
          {
          setState(351);
          match(COMMA);
          setState(352);
          indexPattern();
          }
          }
        }
        setState(357);
        _errHandler.sync(this);
        _alt = getInterpreter().adaptivePredict(_input,28,_ctx);
      }
      setState(359);
      _errHandler.sync(this);
      switch ( getInterpreter().adaptivePredict(_input,29,_ctx) ) {
      case 1:
        {
        setState(358);
        ((MetricsCommandContext)_localctx).aggregates = fields();
        }
        break;
      }
      setState(363);
      _errHandler.sync(this);
      switch ( getInterpreter().adaptivePredict(_input,30,_ctx) ) {
      case 1:
        {
        setState(361);
        match(BY);
        setState(362);
        ((MetricsCommandContext)_localctx).grouping = fields();
        }
        break;
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class EvalCommandContext extends ParserRuleContext {
    public TerminalNode EVAL() { return getToken(EsqlBaseParser.EVAL, 0); }
    public FieldsContext fields() {
      return getRuleContext(FieldsContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public EvalCommandContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_evalCommand; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterEvalCommand(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitEvalCommand(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitEvalCommand(this);
      else return visitor.visitChildren(this);
    }
  }

  public final EvalCommandContext evalCommand() throws RecognitionException {
    EvalCommandContext _localctx = new EvalCommandContext(_ctx, getState());
    enterRule(_localctx, 50, RULE_evalCommand);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(365);
      match(EVAL);
      setState(366);
      fields();
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class StatsCommandContext extends ParserRuleContext {
    public FieldsContext stats;
    public FieldsContext grouping;
    public TerminalNode STATS() { return getToken(EsqlBaseParser.STATS, 0); }
    public TerminalNode BY() { return getToken(EsqlBaseParser.BY, 0); }
    public List<FieldsContext> fields() {
      return getRuleContexts(FieldsContext.class);
    }
    public FieldsContext fields(int i) {
      return getRuleContext(FieldsContext.class,i);
    }
    @SuppressWarnings("this-escape")
    public StatsCommandContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_statsCommand; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterStatsCommand(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitStatsCommand(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitStatsCommand(this);
      else return visitor.visitChildren(this);
    }
  }

  public final StatsCommandContext statsCommand() throws RecognitionException {
    StatsCommandContext _localctx = new StatsCommandContext(_ctx, getState());
    enterRule(_localctx, 52, RULE_statsCommand);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(368);
      match(STATS);
      setState(370);
      _errHandler.sync(this);
      switch ( getInterpreter().adaptivePredict(_input,31,_ctx) ) {
      case 1:
        {
        setState(369);
        ((StatsCommandContext)_localctx).stats = fields();
        }
        break;
      }
      setState(374);
      _errHandler.sync(this);
      switch ( getInterpreter().adaptivePredict(_input,32,_ctx) ) {
      case 1:
        {
        setState(372);
        match(BY);
        setState(373);
        ((StatsCommandContext)_localctx).grouping = fields();
        }
        break;
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class QualifiedNameContext extends ParserRuleContext {
    public List<IdentifierContext> identifier() {
      return getRuleContexts(IdentifierContext.class);
    }
    public IdentifierContext identifier(int i) {
      return getRuleContext(IdentifierContext.class,i);
    }
    public List<TerminalNode> DOT() { return getTokens(EsqlBaseParser.DOT); }
    public TerminalNode DOT(int i) {
      return getToken(EsqlBaseParser.DOT, i);
    }
    @SuppressWarnings("this-escape")
    public QualifiedNameContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_qualifiedName; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterQualifiedName(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitQualifiedName(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitQualifiedName(this);
      else return visitor.visitChildren(this);
    }
  }

  public final QualifiedNameContext qualifiedName() throws RecognitionException {
    QualifiedNameContext _localctx = new QualifiedNameContext(_ctx, getState());
    enterRule(_localctx, 54, RULE_qualifiedName);
    try {
      int _alt;
      enterOuterAlt(_localctx, 1);
      {
      setState(376);
      identifier();
      setState(381);
      _errHandler.sync(this);
      _alt = getInterpreter().adaptivePredict(_input,33,_ctx);
      while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
        if ( _alt==1 ) {
          {
          {
          setState(377);
          match(DOT);
          setState(378);
          identifier();
          }
          }
        }
        setState(383);
        _errHandler.sync(this);
        _alt = getInterpreter().adaptivePredict(_input,33,_ctx);
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class QualifiedNamePatternContext extends ParserRuleContext {
    public List<IdentifierPatternContext> identifierPattern() {
      return getRuleContexts(IdentifierPatternContext.class);
    }
    public IdentifierPatternContext identifierPattern(int i) {
      return getRuleContext(IdentifierPatternContext.class,i);
    }
    public List<TerminalNode> DOT() { return getTokens(EsqlBaseParser.DOT); }
    public TerminalNode DOT(int i) {
      return getToken(EsqlBaseParser.DOT, i);
    }
    @SuppressWarnings("this-escape")
    public QualifiedNamePatternContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_qualifiedNamePattern; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterQualifiedNamePattern(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitQualifiedNamePattern(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitQualifiedNamePattern(this);
      else return visitor.visitChildren(this);
    }
  }

  public final QualifiedNamePatternContext qualifiedNamePattern() throws RecognitionException {
    QualifiedNamePatternContext _localctx = new QualifiedNamePatternContext(_ctx, getState());
    enterRule(_localctx, 56, RULE_qualifiedNamePattern);
    try {
      int _alt;
      enterOuterAlt(_localctx, 1);
      {
      setState(384);
      identifierPattern();
      setState(389);
      _errHandler.sync(this);
      _alt = getInterpreter().adaptivePredict(_input,34,_ctx);
      while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
        if ( _alt==1 ) {
          {
          {
          setState(385);
          match(DOT);
          setState(386);
          identifierPattern();
          }
          }
        }
        setState(391);
        _errHandler.sync(this);
        _alt = getInterpreter().adaptivePredict(_input,34,_ctx);
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class QualifiedNamePatternsContext extends ParserRuleContext {
    public List<QualifiedNamePatternContext> qualifiedNamePattern() {
      return getRuleContexts(QualifiedNamePatternContext.class);
    }
    public QualifiedNamePatternContext qualifiedNamePattern(int i) {
      return getRuleContext(QualifiedNamePatternContext.class,i);
    }
    public List<TerminalNode> COMMA() { return getTokens(EsqlBaseParser.COMMA); }
    public TerminalNode COMMA(int i) {
      return getToken(EsqlBaseParser.COMMA, i);
    }
    @SuppressWarnings("this-escape")
    public QualifiedNamePatternsContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_qualifiedNamePatterns; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterQualifiedNamePatterns(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitQualifiedNamePatterns(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitQualifiedNamePatterns(this);
      else return visitor.visitChildren(this);
    }
  }

  public final QualifiedNamePatternsContext qualifiedNamePatterns() throws RecognitionException {
    QualifiedNamePatternsContext _localctx = new QualifiedNamePatternsContext(_ctx, getState());
    enterRule(_localctx, 58, RULE_qualifiedNamePatterns);
    try {
      int _alt;
      enterOuterAlt(_localctx, 1);
      {
      setState(392);
      qualifiedNamePattern();
      setState(397);
      _errHandler.sync(this);
      _alt = getInterpreter().adaptivePredict(_input,35,_ctx);
      while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
        if ( _alt==1 ) {
          {
          {
          setState(393);
          match(COMMA);
          setState(394);
          qualifiedNamePattern();
          }
          }
        }
        setState(399);
        _errHandler.sync(this);
        _alt = getInterpreter().adaptivePredict(_input,35,_ctx);
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class IdentifierContext extends ParserRuleContext {
    public TerminalNode UNQUOTED_IDENTIFIER() { return getToken(EsqlBaseParser.UNQUOTED_IDENTIFIER, 0); }
    public TerminalNode QUOTED_IDENTIFIER() { return getToken(EsqlBaseParser.QUOTED_IDENTIFIER, 0); }
    @SuppressWarnings("this-escape")
    public IdentifierContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_identifier; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterIdentifier(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitIdentifier(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitIdentifier(this);
      else return visitor.visitChildren(this);
    }
  }

  public final IdentifierContext identifier() throws RecognitionException {
    IdentifierContext _localctx = new IdentifierContext(_ctx, getState());
    enterRule(_localctx, 60, RULE_identifier);
    int _la;
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(400);
      _la = _input.LA(1);
      if ( !(_la==UNQUOTED_IDENTIFIER || _la==QUOTED_IDENTIFIER) ) {
      _errHandler.recoverInline(this);
      }
      else {
        if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
        _errHandler.reportMatch(this);
        consume();
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class IdentifierPatternContext extends ParserRuleContext {
    public TerminalNode ID_PATTERN() { return getToken(EsqlBaseParser.ID_PATTERN, 0); }
    @SuppressWarnings("this-escape")
    public IdentifierPatternContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_identifierPattern; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterIdentifierPattern(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitIdentifierPattern(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitIdentifierPattern(this);
      else return visitor.visitChildren(this);
    }
  }

  public final IdentifierPatternContext identifierPattern() throws RecognitionException {
    IdentifierPatternContext _localctx = new IdentifierPatternContext(_ctx, getState());
    enterRule(_localctx, 62, RULE_identifierPattern);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(402);
      match(ID_PATTERN);
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class ConstantContext extends ParserRuleContext {
    @SuppressWarnings("this-escape")
    public ConstantContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_constant; }

    @SuppressWarnings("this-escape")
    public ConstantContext() { }
    public void copyFrom(ConstantContext ctx) {
      super.copyFrom(ctx);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class BooleanArrayLiteralContext extends ConstantContext {
    public TerminalNode OPENING_BRACKET() { return getToken(EsqlBaseParser.OPENING_BRACKET, 0); }
    public List<BooleanValueContext> booleanValue() {
      return getRuleContexts(BooleanValueContext.class);
    }
    public BooleanValueContext booleanValue(int i) {
      return getRuleContext(BooleanValueContext.class,i);
    }
    public TerminalNode CLOSING_BRACKET() { return getToken(EsqlBaseParser.CLOSING_BRACKET, 0); }
    public List<TerminalNode> COMMA() { return getTokens(EsqlBaseParser.COMMA); }
    public TerminalNode COMMA(int i) {
      return getToken(EsqlBaseParser.COMMA, i);
    }
    @SuppressWarnings("this-escape")
    public BooleanArrayLiteralContext(ConstantContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterBooleanArrayLiteral(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitBooleanArrayLiteral(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitBooleanArrayLiteral(this);
      else return visitor.visitChildren(this);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class DecimalLiteralContext extends ConstantContext {
    public DecimalValueContext decimalValue() {
      return getRuleContext(DecimalValueContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public DecimalLiteralContext(ConstantContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterDecimalLiteral(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitDecimalLiteral(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitDecimalLiteral(this);
      else return visitor.visitChildren(this);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class NullLiteralContext extends ConstantContext {
    public TerminalNode NULL() { return getToken(EsqlBaseParser.NULL, 0); }
    @SuppressWarnings("this-escape")
    public NullLiteralContext(ConstantContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterNullLiteral(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitNullLiteral(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitNullLiteral(this);
      else return visitor.visitChildren(this);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class QualifiedIntegerLiteralContext extends ConstantContext {
    public IntegerValueContext integerValue() {
      return getRuleContext(IntegerValueContext.class,0);
    }
    public TerminalNode UNQUOTED_IDENTIFIER() { return getToken(EsqlBaseParser.UNQUOTED_IDENTIFIER, 0); }
    @SuppressWarnings("this-escape")
    public QualifiedIntegerLiteralContext(ConstantContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterQualifiedIntegerLiteral(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitQualifiedIntegerLiteral(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitQualifiedIntegerLiteral(this);
      else return visitor.visitChildren(this);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class StringArrayLiteralContext extends ConstantContext {
    public TerminalNode OPENING_BRACKET() { return getToken(EsqlBaseParser.OPENING_BRACKET, 0); }
    public List<StringContext> string() {
      return getRuleContexts(StringContext.class);
    }
    public StringContext string(int i) {
      return getRuleContext(StringContext.class,i);
    }
    public TerminalNode CLOSING_BRACKET() { return getToken(EsqlBaseParser.CLOSING_BRACKET, 0); }
    public List<TerminalNode> COMMA() { return getTokens(EsqlBaseParser.COMMA); }
    public TerminalNode COMMA(int i) {
      return getToken(EsqlBaseParser.COMMA, i);
    }
    @SuppressWarnings("this-escape")
    public StringArrayLiteralContext(ConstantContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterStringArrayLiteral(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitStringArrayLiteral(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitStringArrayLiteral(this);
      else return visitor.visitChildren(this);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class StringLiteralContext extends ConstantContext {
    public StringContext string() {
      return getRuleContext(StringContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public StringLiteralContext(ConstantContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterStringLiteral(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitStringLiteral(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitStringLiteral(this);
      else return visitor.visitChildren(this);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class NumericArrayLiteralContext extends ConstantContext {
    public TerminalNode OPENING_BRACKET() { return getToken(EsqlBaseParser.OPENING_BRACKET, 0); }
    public List<NumericValueContext> numericValue() {
      return getRuleContexts(NumericValueContext.class);
    }
    public NumericValueContext numericValue(int i) {
      return getRuleContext(NumericValueContext.class,i);
    }
    public TerminalNode CLOSING_BRACKET() { return getToken(EsqlBaseParser.CLOSING_BRACKET, 0); }
    public List<TerminalNode> COMMA() { return getTokens(EsqlBaseParser.COMMA); }
    public TerminalNode COMMA(int i) {
      return getToken(EsqlBaseParser.COMMA, i);
    }
    @SuppressWarnings("this-escape")
    public NumericArrayLiteralContext(ConstantContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterNumericArrayLiteral(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitNumericArrayLiteral(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitNumericArrayLiteral(this);
      else return visitor.visitChildren(this);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class InputParamsContext extends ConstantContext {
    public ParamsContext params() {
      return getRuleContext(ParamsContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public InputParamsContext(ConstantContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterInputParams(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitInputParams(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitInputParams(this);
      else return visitor.visitChildren(this);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class IntegerLiteralContext extends ConstantContext {
    public IntegerValueContext integerValue() {
      return getRuleContext(IntegerValueContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public IntegerLiteralContext(ConstantContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterIntegerLiteral(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitIntegerLiteral(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitIntegerLiteral(this);
      else return visitor.visitChildren(this);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class BooleanLiteralContext extends ConstantContext {
    public BooleanValueContext booleanValue() {
      return getRuleContext(BooleanValueContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public BooleanLiteralContext(ConstantContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterBooleanLiteral(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitBooleanLiteral(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitBooleanLiteral(this);
      else return visitor.visitChildren(this);
    }
  }

  public final ConstantContext constant() throws RecognitionException {
    ConstantContext _localctx = new ConstantContext(_ctx, getState());
    enterRule(_localctx, 64, RULE_constant);
    int _la;
    try {
      setState(446);
      _errHandler.sync(this);
      switch ( getInterpreter().adaptivePredict(_input,39,_ctx) ) {
      case 1:
        _localctx = new NullLiteralContext(_localctx);
        enterOuterAlt(_localctx, 1);
        {
        setState(404);
        match(NULL);
        }
        break;
      case 2:
        _localctx = new QualifiedIntegerLiteralContext(_localctx);
        enterOuterAlt(_localctx, 2);
        {
        setState(405);
        integerValue();
        setState(406);
        match(UNQUOTED_IDENTIFIER);
        }
        break;
      case 3:
        _localctx = new DecimalLiteralContext(_localctx);
        enterOuterAlt(_localctx, 3);
        {
        setState(408);
        decimalValue();
        }
        break;
      case 4:
        _localctx = new IntegerLiteralContext(_localctx);
        enterOuterAlt(_localctx, 4);
        {
        setState(409);
        integerValue();
        }
        break;
      case 5:
        _localctx = new BooleanLiteralContext(_localctx);
        enterOuterAlt(_localctx, 5);
        {
        setState(410);
        booleanValue();
        }
        break;
      case 6:
        _localctx = new InputParamsContext(_localctx);
        enterOuterAlt(_localctx, 6);
        {
        setState(411);
        params();
        }
        break;
      case 7:
        _localctx = new StringLiteralContext(_localctx);
        enterOuterAlt(_localctx, 7);
        {
        setState(412);
        string();
        }
        break;
      case 8:
        _localctx = new NumericArrayLiteralContext(_localctx);
        enterOuterAlt(_localctx, 8);
        {
        setState(413);
        match(OPENING_BRACKET);
        setState(414);
        numericValue();
        setState(419);
        _errHandler.sync(this);
        _la = _input.LA(1);
        while (_la==COMMA) {
          {
          {
          setState(415);
          match(COMMA);
          setState(416);
          numericValue();
          }
          }
          setState(421);
          _errHandler.sync(this);
          _la = _input.LA(1);
        }
        setState(422);
        match(CLOSING_BRACKET);
        }
        break;
      case 9:
        _localctx = new BooleanArrayLiteralContext(_localctx);
        enterOuterAlt(_localctx, 9);
        {
        setState(424);
        match(OPENING_BRACKET);
        setState(425);
        booleanValue();
        setState(430);
        _errHandler.sync(this);
        _la = _input.LA(1);
        while (_la==COMMA) {
          {
          {
          setState(426);
          match(COMMA);
          setState(427);
          booleanValue();
          }
          }
          setState(432);
          _errHandler.sync(this);
          _la = _input.LA(1);
        }
        setState(433);
        match(CLOSING_BRACKET);
        }
        break;
      case 10:
        _localctx = new StringArrayLiteralContext(_localctx);
        enterOuterAlt(_localctx, 10);
        {
        setState(435);
        match(OPENING_BRACKET);
        setState(436);
        string();
        setState(441);
        _errHandler.sync(this);
        _la = _input.LA(1);
        while (_la==COMMA) {
          {
          {
          setState(437);
          match(COMMA);
          setState(438);
          string();
          }
          }
          setState(443);
          _errHandler.sync(this);
          _la = _input.LA(1);
        }
        setState(444);
        match(CLOSING_BRACKET);
        }
        break;
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class ParamsContext extends ParserRuleContext {
    @SuppressWarnings("this-escape")
    public ParamsContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_params; }

    @SuppressWarnings("this-escape")
    public ParamsContext() { }
    public void copyFrom(ParamsContext ctx) {
      super.copyFrom(ctx);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class InputNamedOrPositionalParamContext extends ParamsContext {
    public TerminalNode NAMED_OR_POSITIONAL_PARAM() { return getToken(EsqlBaseParser.NAMED_OR_POSITIONAL_PARAM, 0); }
    @SuppressWarnings("this-escape")
    public InputNamedOrPositionalParamContext(ParamsContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterInputNamedOrPositionalParam(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitInputNamedOrPositionalParam(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitInputNamedOrPositionalParam(this);
      else return visitor.visitChildren(this);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class InputParamContext extends ParamsContext {
    public TerminalNode PARAM() { return getToken(EsqlBaseParser.PARAM, 0); }
    @SuppressWarnings("this-escape")
    public InputParamContext(ParamsContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterInputParam(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitInputParam(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitInputParam(this);
      else return visitor.visitChildren(this);
    }
  }

  public final ParamsContext params() throws RecognitionException {
    ParamsContext _localctx = new ParamsContext(_ctx, getState());
    enterRule(_localctx, 66, RULE_params);
    try {
      setState(450);
      _errHandler.sync(this);
      switch (_input.LA(1)) {
      case PARAM:
        _localctx = new InputParamContext(_localctx);
        enterOuterAlt(_localctx, 1);
        {
        setState(448);
        match(PARAM);
        }
        break;
      case NAMED_OR_POSITIONAL_PARAM:
        _localctx = new InputNamedOrPositionalParamContext(_localctx);
        enterOuterAlt(_localctx, 2);
        {
        setState(449);
        match(NAMED_OR_POSITIONAL_PARAM);
        }
        break;
      default:
        throw new NoViableAltException(this);
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class LimitCommandContext extends ParserRuleContext {
    public TerminalNode LIMIT() { return getToken(EsqlBaseParser.LIMIT, 0); }
    public TerminalNode INTEGER_LITERAL() { return getToken(EsqlBaseParser.INTEGER_LITERAL, 0); }
    @SuppressWarnings("this-escape")
    public LimitCommandContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_limitCommand; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterLimitCommand(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitLimitCommand(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitLimitCommand(this);
      else return visitor.visitChildren(this);
    }
  }

  public final LimitCommandContext limitCommand() throws RecognitionException {
    LimitCommandContext _localctx = new LimitCommandContext(_ctx, getState());
    enterRule(_localctx, 68, RULE_limitCommand);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(452);
      match(LIMIT);
      setState(453);
      match(INTEGER_LITERAL);
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class SortCommandContext extends ParserRuleContext {
    public TerminalNode SORT() { return getToken(EsqlBaseParser.SORT, 0); }
    public List<OrderExpressionContext> orderExpression() {
      return getRuleContexts(OrderExpressionContext.class);
    }
    public OrderExpressionContext orderExpression(int i) {
      return getRuleContext(OrderExpressionContext.class,i);
    }
    public List<TerminalNode> COMMA() { return getTokens(EsqlBaseParser.COMMA); }
    public TerminalNode COMMA(int i) {
      return getToken(EsqlBaseParser.COMMA, i);
    }
    @SuppressWarnings("this-escape")
    public SortCommandContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_sortCommand; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterSortCommand(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitSortCommand(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitSortCommand(this);
      else return visitor.visitChildren(this);
    }
  }

  public final SortCommandContext sortCommand() throws RecognitionException {
    SortCommandContext _localctx = new SortCommandContext(_ctx, getState());
    enterRule(_localctx, 70, RULE_sortCommand);
    try {
      int _alt;
      enterOuterAlt(_localctx, 1);
      {
      setState(455);
      match(SORT);
      setState(456);
      orderExpression();
      setState(461);
      _errHandler.sync(this);
      _alt = getInterpreter().adaptivePredict(_input,41,_ctx);
      while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
        if ( _alt==1 ) {
          {
          {
          setState(457);
          match(COMMA);
          setState(458);
          orderExpression();
          }
          }
        }
        setState(463);
        _errHandler.sync(this);
        _alt = getInterpreter().adaptivePredict(_input,41,_ctx);
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class OrderExpressionContext extends ParserRuleContext {
    public Token ordering;
    public Token nullOrdering;
    public BooleanExpressionContext booleanExpression() {
      return getRuleContext(BooleanExpressionContext.class,0);
    }
    public TerminalNode NULLS() { return getToken(EsqlBaseParser.NULLS, 0); }
    public TerminalNode ASC() { return getToken(EsqlBaseParser.ASC, 0); }
    public TerminalNode DESC() { return getToken(EsqlBaseParser.DESC, 0); }
    public TerminalNode FIRST() { return getToken(EsqlBaseParser.FIRST, 0); }
    public TerminalNode LAST() { return getToken(EsqlBaseParser.LAST, 0); }
    @SuppressWarnings("this-escape")
    public OrderExpressionContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_orderExpression; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterOrderExpression(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitOrderExpression(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitOrderExpression(this);
      else return visitor.visitChildren(this);
    }
  }

  public final OrderExpressionContext orderExpression() throws RecognitionException {
    OrderExpressionContext _localctx = new OrderExpressionContext(_ctx, getState());
    enterRule(_localctx, 72, RULE_orderExpression);
    int _la;
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(464);
      booleanExpression(0);
      setState(466);
      _errHandler.sync(this);
      switch ( getInterpreter().adaptivePredict(_input,42,_ctx) ) {
      case 1:
        {
        setState(465);
        ((OrderExpressionContext)_localctx).ordering = _input.LT(1);
        _la = _input.LA(1);
        if ( !(_la==ASC || _la==DESC) ) {
          ((OrderExpressionContext)_localctx).ordering = (Token)_errHandler.recoverInline(this);
        }
        else {
          if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
          _errHandler.reportMatch(this);
          consume();
        }
        }
        break;
      }
      setState(470);
      _errHandler.sync(this);
      switch ( getInterpreter().adaptivePredict(_input,43,_ctx) ) {
      case 1:
        {
        setState(468);
        match(NULLS);
        setState(469);
        ((OrderExpressionContext)_localctx).nullOrdering = _input.LT(1);
        _la = _input.LA(1);
        if ( !(_la==FIRST || _la==LAST) ) {
          ((OrderExpressionContext)_localctx).nullOrdering = (Token)_errHandler.recoverInline(this);
        }
        else {
          if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
          _errHandler.reportMatch(this);
          consume();
        }
        }
        break;
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class KeepCommandContext extends ParserRuleContext {
    public TerminalNode KEEP() { return getToken(EsqlBaseParser.KEEP, 0); }
    public QualifiedNamePatternsContext qualifiedNamePatterns() {
      return getRuleContext(QualifiedNamePatternsContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public KeepCommandContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_keepCommand; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterKeepCommand(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitKeepCommand(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitKeepCommand(this);
      else return visitor.visitChildren(this);
    }
  }

  public final KeepCommandContext keepCommand() throws RecognitionException {
    KeepCommandContext _localctx = new KeepCommandContext(_ctx, getState());
    enterRule(_localctx, 74, RULE_keepCommand);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(472);
      match(KEEP);
      setState(473);
      qualifiedNamePatterns();
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class DropCommandContext extends ParserRuleContext {
    public TerminalNode DROP() { return getToken(EsqlBaseParser.DROP, 0); }
    public QualifiedNamePatternsContext qualifiedNamePatterns() {
      return getRuleContext(QualifiedNamePatternsContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public DropCommandContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_dropCommand; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterDropCommand(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitDropCommand(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitDropCommand(this);
      else return visitor.visitChildren(this);
    }
  }

  public final DropCommandContext dropCommand() throws RecognitionException {
    DropCommandContext _localctx = new DropCommandContext(_ctx, getState());
    enterRule(_localctx, 76, RULE_dropCommand);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(475);
      match(DROP);
      setState(476);
      qualifiedNamePatterns();
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class RenameCommandContext extends ParserRuleContext {
    public TerminalNode RENAME() { return getToken(EsqlBaseParser.RENAME, 0); }
    public List<RenameClauseContext> renameClause() {
      return getRuleContexts(RenameClauseContext.class);
    }
    public RenameClauseContext renameClause(int i) {
      return getRuleContext(RenameClauseContext.class,i);
    }
    public List<TerminalNode> COMMA() { return getTokens(EsqlBaseParser.COMMA); }
    public TerminalNode COMMA(int i) {
      return getToken(EsqlBaseParser.COMMA, i);
    }
    @SuppressWarnings("this-escape")
    public RenameCommandContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_renameCommand; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterRenameCommand(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitRenameCommand(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitRenameCommand(this);
      else return visitor.visitChildren(this);
    }
  }

  public final RenameCommandContext renameCommand() throws RecognitionException {
    RenameCommandContext _localctx = new RenameCommandContext(_ctx, getState());
    enterRule(_localctx, 78, RULE_renameCommand);
    try {
      int _alt;
      enterOuterAlt(_localctx, 1);
      {
      setState(478);
      match(RENAME);
      setState(479);
      renameClause();
      setState(484);
      _errHandler.sync(this);
      _alt = getInterpreter().adaptivePredict(_input,44,_ctx);
      while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
        if ( _alt==1 ) {
          {
          {
          setState(480);
          match(COMMA);
          setState(481);
          renameClause();
          }
          }
        }
        setState(486);
        _errHandler.sync(this);
        _alt = getInterpreter().adaptivePredict(_input,44,_ctx);
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class RenameClauseContext extends ParserRuleContext {
    public QualifiedNamePatternContext oldName;
    public QualifiedNamePatternContext newName;
    public TerminalNode AS() { return getToken(EsqlBaseParser.AS, 0); }
    public List<QualifiedNamePatternContext> qualifiedNamePattern() {
      return getRuleContexts(QualifiedNamePatternContext.class);
    }
    public QualifiedNamePatternContext qualifiedNamePattern(int i) {
      return getRuleContext(QualifiedNamePatternContext.class,i);
    }
    @SuppressWarnings("this-escape")
    public RenameClauseContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_renameClause; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterRenameClause(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitRenameClause(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitRenameClause(this);
      else return visitor.visitChildren(this);
    }
  }

  public final RenameClauseContext renameClause() throws RecognitionException {
    RenameClauseContext _localctx = new RenameClauseContext(_ctx, getState());
    enterRule(_localctx, 80, RULE_renameClause);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(487);
      ((RenameClauseContext)_localctx).oldName = qualifiedNamePattern();
      setState(488);
      match(AS);
      setState(489);
      ((RenameClauseContext)_localctx).newName = qualifiedNamePattern();
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class DissectCommandContext extends ParserRuleContext {
    public TerminalNode DISSECT() { return getToken(EsqlBaseParser.DISSECT, 0); }
    public PrimaryExpressionContext primaryExpression() {
      return getRuleContext(PrimaryExpressionContext.class,0);
    }
    public StringContext string() {
      return getRuleContext(StringContext.class,0);
    }
    public CommandOptionsContext commandOptions() {
      return getRuleContext(CommandOptionsContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public DissectCommandContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_dissectCommand; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterDissectCommand(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitDissectCommand(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitDissectCommand(this);
      else return visitor.visitChildren(this);
    }
  }

  public final DissectCommandContext dissectCommand() throws RecognitionException {
    DissectCommandContext _localctx = new DissectCommandContext(_ctx, getState());
    enterRule(_localctx, 82, RULE_dissectCommand);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(491);
      match(DISSECT);
      setState(492);
      primaryExpression(0);
      setState(493);
      string();
      setState(495);
      _errHandler.sync(this);
      switch ( getInterpreter().adaptivePredict(_input,45,_ctx) ) {
      case 1:
        {
        setState(494);
        commandOptions();
        }
        break;
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class GrokCommandContext extends ParserRuleContext {
    public TerminalNode GROK() { return getToken(EsqlBaseParser.GROK, 0); }
    public PrimaryExpressionContext primaryExpression() {
      return getRuleContext(PrimaryExpressionContext.class,0);
    }
    public StringContext string() {
      return getRuleContext(StringContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public GrokCommandContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_grokCommand; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterGrokCommand(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitGrokCommand(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitGrokCommand(this);
      else return visitor.visitChildren(this);
    }
  }

  public final GrokCommandContext grokCommand() throws RecognitionException {
    GrokCommandContext _localctx = new GrokCommandContext(_ctx, getState());
    enterRule(_localctx, 84, RULE_grokCommand);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(497);
      match(GROK);
      setState(498);
      primaryExpression(0);
      setState(499);
      string();
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class MvExpandCommandContext extends ParserRuleContext {
    public TerminalNode MV_EXPAND() { return getToken(EsqlBaseParser.MV_EXPAND, 0); }
    public QualifiedNameContext qualifiedName() {
      return getRuleContext(QualifiedNameContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public MvExpandCommandContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_mvExpandCommand; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterMvExpandCommand(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitMvExpandCommand(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitMvExpandCommand(this);
      else return visitor.visitChildren(this);
    }
  }

  public final MvExpandCommandContext mvExpandCommand() throws RecognitionException {
    MvExpandCommandContext _localctx = new MvExpandCommandContext(_ctx, getState());
    enterRule(_localctx, 86, RULE_mvExpandCommand);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(501);
      match(MV_EXPAND);
      setState(502);
      qualifiedName();
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class CommandOptionsContext extends ParserRuleContext {
    public List<CommandOptionContext> commandOption() {
      return getRuleContexts(CommandOptionContext.class);
    }
    public CommandOptionContext commandOption(int i) {
      return getRuleContext(CommandOptionContext.class,i);
    }
    public List<TerminalNode> COMMA() { return getTokens(EsqlBaseParser.COMMA); }
    public TerminalNode COMMA(int i) {
      return getToken(EsqlBaseParser.COMMA, i);
    }
    @SuppressWarnings("this-escape")
    public CommandOptionsContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_commandOptions; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterCommandOptions(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitCommandOptions(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitCommandOptions(this);
      else return visitor.visitChildren(this);
    }
  }

  public final CommandOptionsContext commandOptions() throws RecognitionException {
    CommandOptionsContext _localctx = new CommandOptionsContext(_ctx, getState());
    enterRule(_localctx, 88, RULE_commandOptions);
    try {
      int _alt;
      enterOuterAlt(_localctx, 1);
      {
      setState(504);
      commandOption();
      setState(509);
      _errHandler.sync(this);
      _alt = getInterpreter().adaptivePredict(_input,46,_ctx);
      while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
        if ( _alt==1 ) {
          {
          {
          setState(505);
          match(COMMA);
          setState(506);
          commandOption();
          }
          }
        }
        setState(511);
        _errHandler.sync(this);
        _alt = getInterpreter().adaptivePredict(_input,46,_ctx);
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class CommandOptionContext extends ParserRuleContext {
    public IdentifierContext identifier() {
      return getRuleContext(IdentifierContext.class,0);
    }
    public TerminalNode ASSIGN() { return getToken(EsqlBaseParser.ASSIGN, 0); }
    public ConstantContext constant() {
      return getRuleContext(ConstantContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public CommandOptionContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_commandOption; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterCommandOption(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitCommandOption(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitCommandOption(this);
      else return visitor.visitChildren(this);
    }
  }

  public final CommandOptionContext commandOption() throws RecognitionException {
    CommandOptionContext _localctx = new CommandOptionContext(_ctx, getState());
    enterRule(_localctx, 90, RULE_commandOption);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(512);
      identifier();
      setState(513);
      match(ASSIGN);
      setState(514);
      constant();
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class BooleanValueContext extends ParserRuleContext {
    public TerminalNode TRUE() { return getToken(EsqlBaseParser.TRUE, 0); }
    public TerminalNode FALSE() { return getToken(EsqlBaseParser.FALSE, 0); }
    @SuppressWarnings("this-escape")
    public BooleanValueContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_booleanValue; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterBooleanValue(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitBooleanValue(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitBooleanValue(this);
      else return visitor.visitChildren(this);
    }
  }

  public final BooleanValueContext booleanValue() throws RecognitionException {
    BooleanValueContext _localctx = new BooleanValueContext(_ctx, getState());
    enterRule(_localctx, 92, RULE_booleanValue);
    int _la;
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(516);
      _la = _input.LA(1);
      if ( !(_la==FALSE || _la==TRUE) ) {
      _errHandler.recoverInline(this);
      }
      else {
        if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
        _errHandler.reportMatch(this);
        consume();
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class NumericValueContext extends ParserRuleContext {
    public DecimalValueContext decimalValue() {
      return getRuleContext(DecimalValueContext.class,0);
    }
    public IntegerValueContext integerValue() {
      return getRuleContext(IntegerValueContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public NumericValueContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_numericValue; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterNumericValue(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitNumericValue(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitNumericValue(this);
      else return visitor.visitChildren(this);
    }
  }

  public final NumericValueContext numericValue() throws RecognitionException {
    NumericValueContext _localctx = new NumericValueContext(_ctx, getState());
    enterRule(_localctx, 94, RULE_numericValue);
    try {
      setState(520);
      _errHandler.sync(this);
      switch ( getInterpreter().adaptivePredict(_input,47,_ctx) ) {
      case 1:
        enterOuterAlt(_localctx, 1);
        {
        setState(518);
        decimalValue();
        }
        break;
      case 2:
        enterOuterAlt(_localctx, 2);
        {
        setState(519);
        integerValue();
        }
        break;
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class DecimalValueContext extends ParserRuleContext {
    public TerminalNode DECIMAL_LITERAL() { return getToken(EsqlBaseParser.DECIMAL_LITERAL, 0); }
    public TerminalNode PLUS() { return getToken(EsqlBaseParser.PLUS, 0); }
    public TerminalNode MINUS() { return getToken(EsqlBaseParser.MINUS, 0); }
    @SuppressWarnings("this-escape")
    public DecimalValueContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_decimalValue; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterDecimalValue(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitDecimalValue(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitDecimalValue(this);
      else return visitor.visitChildren(this);
    }
  }

  public final DecimalValueContext decimalValue() throws RecognitionException {
    DecimalValueContext _localctx = new DecimalValueContext(_ctx, getState());
    enterRule(_localctx, 96, RULE_decimalValue);
    int _la;
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(523);
      _errHandler.sync(this);
      _la = _input.LA(1);
      if (_la==PLUS || _la==MINUS) {
        {
        setState(522);
        _la = _input.LA(1);
        if ( !(_la==PLUS || _la==MINUS) ) {
        _errHandler.recoverInline(this);
        }
        else {
          if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
          _errHandler.reportMatch(this);
          consume();
        }
        }
      }

      setState(525);
      match(DECIMAL_LITERAL);
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class IntegerValueContext extends ParserRuleContext {
    public TerminalNode INTEGER_LITERAL() { return getToken(EsqlBaseParser.INTEGER_LITERAL, 0); }
    public TerminalNode PLUS() { return getToken(EsqlBaseParser.PLUS, 0); }
    public TerminalNode MINUS() { return getToken(EsqlBaseParser.MINUS, 0); }
    @SuppressWarnings("this-escape")
    public IntegerValueContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_integerValue; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterIntegerValue(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitIntegerValue(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitIntegerValue(this);
      else return visitor.visitChildren(this);
    }
  }

  public final IntegerValueContext integerValue() throws RecognitionException {
    IntegerValueContext _localctx = new IntegerValueContext(_ctx, getState());
    enterRule(_localctx, 98, RULE_integerValue);
    int _la;
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(528);
      _errHandler.sync(this);
      _la = _input.LA(1);
      if (_la==PLUS || _la==MINUS) {
        {
        setState(527);
        _la = _input.LA(1);
        if ( !(_la==PLUS || _la==MINUS) ) {
        _errHandler.recoverInline(this);
        }
        else {
          if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
          _errHandler.reportMatch(this);
          consume();
        }
        }
      }

      setState(530);
      match(INTEGER_LITERAL);
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class StringContext extends ParserRuleContext {
    public TerminalNode QUOTED_STRING() { return getToken(EsqlBaseParser.QUOTED_STRING, 0); }
    @SuppressWarnings("this-escape")
    public StringContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_string; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterString(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitString(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitString(this);
      else return visitor.visitChildren(this);
    }
  }

  public final StringContext string() throws RecognitionException {
    StringContext _localctx = new StringContext(_ctx, getState());
    enterRule(_localctx, 100, RULE_string);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(532);
      match(QUOTED_STRING);
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class ComparisonOperatorContext extends ParserRuleContext {
    public TerminalNode EQ() { return getToken(EsqlBaseParser.EQ, 0); }
    public TerminalNode NEQ() { return getToken(EsqlBaseParser.NEQ, 0); }
    public TerminalNode LT() { return getToken(EsqlBaseParser.LT, 0); }
    public TerminalNode LTE() { return getToken(EsqlBaseParser.LTE, 0); }
    public TerminalNode GT() { return getToken(EsqlBaseParser.GT, 0); }
    public TerminalNode GTE() { return getToken(EsqlBaseParser.GTE, 0); }
    @SuppressWarnings("this-escape")
    public ComparisonOperatorContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_comparisonOperator; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterComparisonOperator(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitComparisonOperator(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitComparisonOperator(this);
      else return visitor.visitChildren(this);
    }
  }

  public final ComparisonOperatorContext comparisonOperator() throws RecognitionException {
    ComparisonOperatorContext _localctx = new ComparisonOperatorContext(_ctx, getState());
    enterRule(_localctx, 102, RULE_comparisonOperator);
    int _la;
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(534);
      _la = _input.LA(1);
      if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 1125899906842624000L) != 0)) ) {
      _errHandler.recoverInline(this);
      }
      else {
        if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
        _errHandler.reportMatch(this);
        consume();
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class ExplainCommandContext extends ParserRuleContext {
    public TerminalNode EXPLAIN() { return getToken(EsqlBaseParser.EXPLAIN, 0); }
    public SubqueryExpressionContext subqueryExpression() {
      return getRuleContext(SubqueryExpressionContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public ExplainCommandContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_explainCommand; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterExplainCommand(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitExplainCommand(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitExplainCommand(this);
      else return visitor.visitChildren(this);
    }
  }

  public final ExplainCommandContext explainCommand() throws RecognitionException {
    ExplainCommandContext _localctx = new ExplainCommandContext(_ctx, getState());
    enterRule(_localctx, 104, RULE_explainCommand);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(536);
      match(EXPLAIN);
      setState(537);
      subqueryExpression();
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class SubqueryExpressionContext extends ParserRuleContext {
    public TerminalNode OPENING_BRACKET() { return getToken(EsqlBaseParser.OPENING_BRACKET, 0); }
    public QueryContext query() {
      return getRuleContext(QueryContext.class,0);
    }
    public TerminalNode CLOSING_BRACKET() { return getToken(EsqlBaseParser.CLOSING_BRACKET, 0); }
    @SuppressWarnings("this-escape")
    public SubqueryExpressionContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_subqueryExpression; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterSubqueryExpression(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitSubqueryExpression(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitSubqueryExpression(this);
      else return visitor.visitChildren(this);
    }
  }

  public final SubqueryExpressionContext subqueryExpression() throws RecognitionException {
    SubqueryExpressionContext _localctx = new SubqueryExpressionContext(_ctx, getState());
    enterRule(_localctx, 106, RULE_subqueryExpression);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(539);
      match(OPENING_BRACKET);
      setState(540);
      query(0);
      setState(541);
      match(CLOSING_BRACKET);
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class ShowCommandContext extends ParserRuleContext {
    @SuppressWarnings("this-escape")
    public ShowCommandContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_showCommand; }

    @SuppressWarnings("this-escape")
    public ShowCommandContext() { }
    public void copyFrom(ShowCommandContext ctx) {
      super.copyFrom(ctx);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class ShowInfoContext extends ShowCommandContext {
    public TerminalNode SHOW() { return getToken(EsqlBaseParser.SHOW, 0); }
    public TerminalNode INFO() { return getToken(EsqlBaseParser.INFO, 0); }
    @SuppressWarnings("this-escape")
    public ShowInfoContext(ShowCommandContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterShowInfo(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitShowInfo(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitShowInfo(this);
      else return visitor.visitChildren(this);
    }
  }

  public final ShowCommandContext showCommand() throws RecognitionException {
    ShowCommandContext _localctx = new ShowCommandContext(_ctx, getState());
    enterRule(_localctx, 108, RULE_showCommand);
    try {
      _localctx = new ShowInfoContext(_localctx);
      enterOuterAlt(_localctx, 1);
      {
      setState(543);
      match(SHOW);
      setState(544);
      match(INFO);
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class MetaCommandContext extends ParserRuleContext {
    @SuppressWarnings("this-escape")
    public MetaCommandContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_metaCommand; }

    @SuppressWarnings("this-escape")
    public MetaCommandContext() { }
    public void copyFrom(MetaCommandContext ctx) {
      super.copyFrom(ctx);
    }
  }
  @SuppressWarnings("CheckReturnValue")
  public static class MetaFunctionsContext extends MetaCommandContext {
    public TerminalNode META() { return getToken(EsqlBaseParser.META, 0); }
    public TerminalNode FUNCTIONS() { return getToken(EsqlBaseParser.FUNCTIONS, 0); }
    @SuppressWarnings("this-escape")
    public MetaFunctionsContext(MetaCommandContext ctx) { copyFrom(ctx); }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterMetaFunctions(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitMetaFunctions(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitMetaFunctions(this);
      else return visitor.visitChildren(this);
    }
  }

  public final MetaCommandContext metaCommand() throws RecognitionException {
    MetaCommandContext _localctx = new MetaCommandContext(_ctx, getState());
    enterRule(_localctx, 110, RULE_metaCommand);
    try {
      _localctx = new MetaFunctionsContext(_localctx);
      enterOuterAlt(_localctx, 1);
      {
      setState(546);
      match(META);
      setState(547);
      match(FUNCTIONS);
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class EnrichCommandContext extends ParserRuleContext {
    public Token policyName;
    public QualifiedNamePatternContext matchField;
    public TerminalNode ENRICH() { return getToken(EsqlBaseParser.ENRICH, 0); }
    public TerminalNode ENRICH_POLICY_NAME() { return getToken(EsqlBaseParser.ENRICH_POLICY_NAME, 0); }
    public TerminalNode ON() { return getToken(EsqlBaseParser.ON, 0); }
    public TerminalNode WITH() { return getToken(EsqlBaseParser.WITH, 0); }
    public List<EnrichWithClauseContext> enrichWithClause() {
      return getRuleContexts(EnrichWithClauseContext.class);
    }
    public EnrichWithClauseContext enrichWithClause(int i) {
      return getRuleContext(EnrichWithClauseContext.class,i);
    }
    public QualifiedNamePatternContext qualifiedNamePattern() {
      return getRuleContext(QualifiedNamePatternContext.class,0);
    }
    public List<TerminalNode> COMMA() { return getTokens(EsqlBaseParser.COMMA); }
    public TerminalNode COMMA(int i) {
      return getToken(EsqlBaseParser.COMMA, i);
    }
    @SuppressWarnings("this-escape")
    public EnrichCommandContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_enrichCommand; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterEnrichCommand(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitEnrichCommand(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitEnrichCommand(this);
      else return visitor.visitChildren(this);
    }
  }

  public final EnrichCommandContext enrichCommand() throws RecognitionException {
    EnrichCommandContext _localctx = new EnrichCommandContext(_ctx, getState());
    enterRule(_localctx, 112, RULE_enrichCommand);
    try {
      int _alt;
      enterOuterAlt(_localctx, 1);
      {
      setState(549);
      match(ENRICH);
      setState(550);
      ((EnrichCommandContext)_localctx).policyName = match(ENRICH_POLICY_NAME);
      setState(553);
      _errHandler.sync(this);
      switch ( getInterpreter().adaptivePredict(_input,50,_ctx) ) {
      case 1:
        {
        setState(551);
        match(ON);
        setState(552);
        ((EnrichCommandContext)_localctx).matchField = qualifiedNamePattern();
        }
        break;
      }
      setState(564);
      _errHandler.sync(this);
      switch ( getInterpreter().adaptivePredict(_input,52,_ctx) ) {
      case 1:
        {
        setState(555);
        match(WITH);
        setState(556);
        enrichWithClause();
        setState(561);
        _errHandler.sync(this);
        _alt = getInterpreter().adaptivePredict(_input,51,_ctx);
        while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
          if ( _alt==1 ) {
            {
            {
            setState(557);
            match(COMMA);
            setState(558);
            enrichWithClause();
            }
            }
          }
          setState(563);
          _errHandler.sync(this);
          _alt = getInterpreter().adaptivePredict(_input,51,_ctx);
        }
        }
        break;
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class EnrichWithClauseContext extends ParserRuleContext {
    public QualifiedNamePatternContext newName;
    public QualifiedNamePatternContext enrichField;
    public List<QualifiedNamePatternContext> qualifiedNamePattern() {
      return getRuleContexts(QualifiedNamePatternContext.class);
    }
    public QualifiedNamePatternContext qualifiedNamePattern(int i) {
      return getRuleContext(QualifiedNamePatternContext.class,i);
    }
    public TerminalNode ASSIGN() { return getToken(EsqlBaseParser.ASSIGN, 0); }
    @SuppressWarnings("this-escape")
    public EnrichWithClauseContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_enrichWithClause; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterEnrichWithClause(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitEnrichWithClause(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitEnrichWithClause(this);
      else return visitor.visitChildren(this);
    }
  }

  public final EnrichWithClauseContext enrichWithClause() throws RecognitionException {
    EnrichWithClauseContext _localctx = new EnrichWithClauseContext(_ctx, getState());
    enterRule(_localctx, 114, RULE_enrichWithClause);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(569);
      _errHandler.sync(this);
      switch ( getInterpreter().adaptivePredict(_input,53,_ctx) ) {
      case 1:
        {
        setState(566);
        ((EnrichWithClauseContext)_localctx).newName = qualifiedNamePattern();
        setState(567);
        match(ASSIGN);
        }
        break;
      }
      setState(571);
      ((EnrichWithClauseContext)_localctx).enrichField = qualifiedNamePattern();
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class LookupCommandContext extends ParserRuleContext {
    public IndexPatternContext tableName;
    public QualifiedNamePatternsContext matchFields;
    public TerminalNode DEV_LOOKUP() { return getToken(EsqlBaseParser.DEV_LOOKUP, 0); }
    public TerminalNode ON() { return getToken(EsqlBaseParser.ON, 0); }
    public IndexPatternContext indexPattern() {
      return getRuleContext(IndexPatternContext.class,0);
    }
    public QualifiedNamePatternsContext qualifiedNamePatterns() {
      return getRuleContext(QualifiedNamePatternsContext.class,0);
    }
    @SuppressWarnings("this-escape")
    public LookupCommandContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_lookupCommand; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterLookupCommand(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitLookupCommand(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitLookupCommand(this);
      else return visitor.visitChildren(this);
    }
  }

  public final LookupCommandContext lookupCommand() throws RecognitionException {
    LookupCommandContext _localctx = new LookupCommandContext(_ctx, getState());
    enterRule(_localctx, 116, RULE_lookupCommand);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(573);
      match(DEV_LOOKUP);
      setState(574);
      ((LookupCommandContext)_localctx).tableName = indexPattern();
      setState(575);
      match(ON);
      setState(576);
      ((LookupCommandContext)_localctx).matchFields = qualifiedNamePatterns();
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  @SuppressWarnings("CheckReturnValue")
  public static class InlinestatsCommandContext extends ParserRuleContext {
    public FieldsContext stats;
    public FieldsContext grouping;
    public TerminalNode DEV_INLINESTATS() { return getToken(EsqlBaseParser.DEV_INLINESTATS, 0); }
    public List<FieldsContext> fields() {
      return getRuleContexts(FieldsContext.class);
    }
    public FieldsContext fields(int i) {
      return getRuleContext(FieldsContext.class,i);
    }
    public TerminalNode BY() { return getToken(EsqlBaseParser.BY, 0); }
    @SuppressWarnings("this-escape")
    public InlinestatsCommandContext(ParserRuleContext parent, int invokingState) {
      super(parent, invokingState);
    }
    @Override public int getRuleIndex() { return RULE_inlinestatsCommand; }
    @Override
    public void enterRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).enterInlinestatsCommand(this);
    }
    @Override
    public void exitRule(ParseTreeListener listener) {
      if ( listener instanceof EsqlBaseParserListener ) ((EsqlBaseParserListener)listener).exitInlinestatsCommand(this);
    }
    @Override
    public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
      if ( visitor instanceof EsqlBaseParserVisitor ) return ((EsqlBaseParserVisitor<? extends T>)visitor).visitInlinestatsCommand(this);
      else return visitor.visitChildren(this);
    }
  }

  public final InlinestatsCommandContext inlinestatsCommand() throws RecognitionException {
    InlinestatsCommandContext _localctx = new InlinestatsCommandContext(_ctx, getState());
    enterRule(_localctx, 118, RULE_inlinestatsCommand);
    try {
      enterOuterAlt(_localctx, 1);
      {
      setState(578);
      match(DEV_INLINESTATS);
      setState(579);
      ((InlinestatsCommandContext)_localctx).stats = fields();
      setState(582);
      _errHandler.sync(this);
      switch ( getInterpreter().adaptivePredict(_input,54,_ctx) ) {
      case 1:
        {
        setState(580);
        match(BY);
        setState(581);
        ((InlinestatsCommandContext)_localctx).grouping = fields();
        }
        break;
      }
      }
    }
    catch (RecognitionException re) {
      _localctx.exception = re;
      _errHandler.reportError(this, re);
      _errHandler.recover(this, re);
    }
    finally {
      exitRule();
    }
    return _localctx;
  }

  public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
    switch (ruleIndex) {
    case 1:
      return query_sempred((QueryContext)_localctx, predIndex);
    case 2:
      return sourceCommand_sempred((SourceCommandContext)_localctx, predIndex);
    case 3:
      return processingCommand_sempred((ProcessingCommandContext)_localctx, predIndex);
    case 5:
      return booleanExpression_sempred((BooleanExpressionContext)_localctx, predIndex);
    case 9:
      return operatorExpression_sempred((OperatorExpressionContext)_localctx, predIndex);
    case 10:
      return primaryExpression_sempred((PrimaryExpressionContext)_localctx, predIndex);
    case 12:
      return functionName_sempred((FunctionNameContext)_localctx, predIndex);
    }
    return true;
  }
  private boolean query_sempred(QueryContext _localctx, int predIndex) {
    switch (predIndex) {
    case 0:
      return precpred(_ctx, 1);
    }
    return true;
  }
  private boolean sourceCommand_sempred(SourceCommandContext _localctx, int predIndex) {
    switch (predIndex) {
    case 1:
      return this.isDevVersion();
    }
    return true;
  }
  private boolean processingCommand_sempred(ProcessingCommandContext _localctx, int predIndex) {
    switch (predIndex) {
    case 2:
      return this.isDevVersion();
    case 3:
      return this.isDevVersion();
    }
    return true;
  }
  private boolean booleanExpression_sempred(BooleanExpressionContext _localctx, int predIndex) {
    switch (predIndex) {
    case 4:
      return this.isDevVersion();
    case 5:
      return precpred(_ctx, 5);
    case 6:
      return precpred(_ctx, 4);
    }
    return true;
  }
  private boolean operatorExpression_sempred(OperatorExpressionContext _localctx, int predIndex) {
    switch (predIndex) {
    case 7:
      return precpred(_ctx, 2);
    case 8:
      return precpred(_ctx, 1);
    }
    return true;
  }
  private boolean primaryExpression_sempred(PrimaryExpressionContext _localctx, int predIndex) {
    switch (predIndex) {
    case 9:
      return precpred(_ctx, 1);
    }
    return true;
  }
  private boolean functionName_sempred(FunctionNameContext _localctx, int predIndex) {
    switch (predIndex) {
    case 10:
      return this.isDevVersion();
    }
    return true;
  }

  public static final String _serializedATN =
    "\u0004\u0001}\u0249\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
    "\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
    "\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007\u0002"+
    "\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b\u0002"+
    "\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0002\u000f\u0007\u000f"+
    "\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011\u0002\u0012\u0007\u0012"+
    "\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014\u0002\u0015\u0007\u0015"+
    "\u0002\u0016\u0007\u0016\u0002\u0017\u0007\u0017\u0002\u0018\u0007\u0018"+
    "\u0002\u0019\u0007\u0019\u0002\u001a\u0007\u001a\u0002\u001b\u0007\u001b"+
    "\u0002\u001c\u0007\u001c\u0002\u001d\u0007\u001d\u0002\u001e\u0007\u001e"+
    "\u0002\u001f\u0007\u001f\u0002 \u0007 \u0002!\u0007!\u0002\"\u0007\"\u0002"+
    "#\u0007#\u0002$\u0007$\u0002%\u0007%\u0002&\u0007&\u0002\'\u0007\'\u0002"+
    "(\u0007(\u0002)\u0007)\u0002*\u0007*\u0002+\u0007+\u0002,\u0007,\u0002"+
    "-\u0007-\u0002.\u0007.\u0002/\u0007/\u00020\u00070\u00021\u00071\u0002"+
    "2\u00072\u00023\u00073\u00024\u00074\u00025\u00075\u00026\u00076\u0002"+
    "7\u00077\u00028\u00078\u00029\u00079\u0002:\u0007:\u0002;\u0007;\u0001"+
    "\u0000\u0001\u0000\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
    "\u0001\u0001\u0001\u0001\u0001\u0005\u0001\u0082\b\u0001\n\u0001\f\u0001"+
    "\u0085\t\u0001\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
    "\u0001\u0002\u0001\u0002\u0003\u0002\u008e\b\u0002\u0001\u0003\u0001\u0003"+
    "\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003"+
    "\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003"+
    "\u0001\u0003\u0001\u0003\u0003\u0003\u00a0\b\u0003\u0001\u0004\u0001\u0004"+
    "\u0001\u0004\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005"+
    "\u0001\u0005\u0001\u0005\u0003\u0005\u00ac\b\u0005\u0001\u0005\u0001\u0005"+
    "\u0001\u0005\u0001\u0005\u0001\u0005\u0005\u0005\u00b3\b\u0005\n\u0005"+
    "\f\u0005\u00b6\t\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005"+
    "\u0001\u0005\u0003\u0005\u00bd\b\u0005\u0001\u0005\u0001\u0005\u0001\u0005"+
    "\u0001\u0005\u0003\u0005\u00c3\b\u0005\u0001\u0005\u0001\u0005\u0001\u0005"+
    "\u0001\u0005\u0001\u0005\u0001\u0005\u0005\u0005\u00cb\b\u0005\n\u0005"+
    "\f\u0005\u00ce\t\u0005\u0001\u0006\u0001\u0006\u0003\u0006\u00d2\b\u0006"+
    "\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0003\u0006"+
    "\u00d9\b\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0003\u0006\u00de\b"+
    "\u0006\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\b\u0001\b"+
    "\u0001\b\u0001\b\u0001\b\u0003\b\u00e9\b\b\u0001\t\u0001\t\u0001\t\u0001"+
    "\t\u0003\t\u00ef\b\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0005"+
    "\t\u00f7\b\t\n\t\f\t\u00fa\t\t\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n"+
    "\u0001\n\u0001\n\u0001\n\u0003\n\u0104\b\n\u0001\n\u0001\n\u0001\n\u0005"+
    "\n\u0109\b\n\n\n\f\n\u010c\t\n\u0001\u000b\u0001\u000b\u0001\u000b\u0001"+
    "\u000b\u0001\u000b\u0001\u000b\u0005\u000b\u0114\b\u000b\n\u000b\f\u000b"+
    "\u0117\t\u000b\u0003\u000b\u0119\b\u000b\u0001\u000b\u0001\u000b\u0001"+
    "\f\u0001\f\u0001\f\u0003\f\u0120\b\f\u0001\r\u0001\r\u0001\u000e\u0001"+
    "\u000e\u0001\u000e\u0001\u000f\u0001\u000f\u0001\u000f\u0005\u000f\u012a"+
    "\b\u000f\n\u000f\f\u000f\u012d\t\u000f\u0001\u0010\u0001\u0010\u0001\u0010"+
    "\u0001\u0010\u0001\u0010\u0003\u0010\u0134\b\u0010\u0001\u0011\u0001\u0011"+
    "\u0001\u0011\u0001\u0011\u0005\u0011\u013a\b\u0011\n\u0011\f\u0011\u013d"+
    "\t\u0011\u0001\u0011\u0003\u0011\u0140\b\u0011\u0001\u0012\u0001\u0012"+
    "\u0001\u0012\u0001\u0012\u0001\u0012\u0003\u0012\u0147\b\u0012\u0001\u0013"+
    "\u0001\u0013\u0001\u0014\u0001\u0014\u0001\u0015\u0001\u0015\u0003\u0015"+
    "\u014f\b\u0015\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0016\u0005\u0016"+
    "\u0155\b\u0016\n\u0016\f\u0016\u0158\t\u0016\u0001\u0017\u0001\u0017\u0001"+
    "\u0017\u0001\u0017\u0001\u0018\u0001\u0018\u0001\u0018\u0001\u0018\u0005"+
    "\u0018\u0162\b\u0018\n\u0018\f\u0018\u0165\t\u0018\u0001\u0018\u0003\u0018"+
    "\u0168\b\u0018\u0001\u0018\u0001\u0018\u0003\u0018\u016c\b\u0018\u0001"+
    "\u0019\u0001\u0019\u0001\u0019\u0001\u001a\u0001\u001a\u0003\u001a\u0173"+
    "\b\u001a\u0001\u001a\u0001\u001a\u0003\u001a\u0177\b\u001a\u0001\u001b"+
    "\u0001\u001b\u0001\u001b\u0005\u001b\u017c\b\u001b\n\u001b\f\u001b\u017f"+
    "\t\u001b\u0001\u001c\u0001\u001c\u0001\u001c\u0005\u001c\u0184\b\u001c"+
    "\n\u001c\f\u001c\u0187\t\u001c\u0001\u001d\u0001\u001d\u0001\u001d\u0005"+
    "\u001d\u018c\b\u001d\n\u001d\f\u001d\u018f\t\u001d\u0001\u001e\u0001\u001e"+
    "\u0001\u001f\u0001\u001f\u0001 \u0001 \u0001 \u0001 \u0001 \u0001 \u0001"+
    " \u0001 \u0001 \u0001 \u0001 \u0001 \u0001 \u0005 \u01a2\b \n \f \u01a5"+
    "\t \u0001 \u0001 \u0001 \u0001 \u0001 \u0001 \u0005 \u01ad\b \n \f \u01b0"+
    "\t \u0001 \u0001 \u0001 \u0001 \u0001 \u0001 \u0005 \u01b8\b \n \f \u01bb"+
    "\t \u0001 \u0001 \u0003 \u01bf\b \u0001!\u0001!\u0003!\u01c3\b!\u0001"+
    "\"\u0001\"\u0001\"\u0001#\u0001#\u0001#\u0001#\u0005#\u01cc\b#\n#\f#\u01cf"+
    "\t#\u0001$\u0001$\u0003$\u01d3\b$\u0001$\u0001$\u0003$\u01d7\b$\u0001"+
    "%\u0001%\u0001%\u0001&\u0001&\u0001&\u0001\'\u0001\'\u0001\'\u0001\'\u0005"+
    "\'\u01e3\b\'\n\'\f\'\u01e6\t\'\u0001(\u0001(\u0001(\u0001(\u0001)\u0001"+
    ")\u0001)\u0001)\u0003)\u01f0\b)\u0001*\u0001*\u0001*\u0001*\u0001+\u0001"+
    "+\u0001+\u0001,\u0001,\u0001,\u0005,\u01fc\b,\n,\f,\u01ff\t,\u0001-\u0001"+
    "-\u0001-\u0001-\u0001.\u0001.\u0001/\u0001/\u0003/\u0209\b/\u00010\u0003"+
    "0\u020c\b0\u00010\u00010\u00011\u00031\u0211\b1\u00011\u00011\u00012\u0001"+
    "2\u00013\u00013\u00014\u00014\u00014\u00015\u00015\u00015\u00015\u0001"+
    "6\u00016\u00016\u00017\u00017\u00017\u00018\u00018\u00018\u00018\u0003"+
    "8\u022a\b8\u00018\u00018\u00018\u00018\u00058\u0230\b8\n8\f8\u0233\t8"+
    "\u00038\u0235\b8\u00019\u00019\u00019\u00039\u023a\b9\u00019\u00019\u0001"+
    ":\u0001:\u0001:\u0001:\u0001:\u0001;\u0001;\u0001;\u0001;\u0003;\u0247"+
    "\b;\u0001;\u0000\u0004\u0002\n\u0012\u0014<\u0000\u0002\u0004\u0006\b"+
    "\n\f\u000e\u0010\u0012\u0014\u0016\u0018\u001a\u001c\u001e \"$&(*,.02"+
    "468:<>@BDFHJLNPRTVXZ\\^`bdfhjlnprtv\u0000\b\u0001\u0000<=\u0001\u0000"+
    ">@\u0002\u0000\u001b\u001bMM\u0001\u0000DE\u0002\u0000  $$\u0002\u0000"+
    "\'\'**\u0002\u0000&&44\u0002\u0000557;\u0262\u0000x\u0001\u0000\u0000"+
    "\u0000\u0002{\u0001\u0000\u0000\u0000\u0004\u008d\u0001\u0000\u0000\u0000"+
    "\u0006\u009f\u0001\u0000\u0000\u0000\b\u00a1\u0001\u0000\u0000\u0000\n"+
    "\u00c2\u0001\u0000\u0000\u0000\f\u00dd\u0001\u0000\u0000\u0000\u000e\u00df"+
    "\u0001\u0000\u0000\u0000\u0010\u00e8\u0001\u0000\u0000\u0000\u0012\u00ee"+
    "\u0001\u0000\u0000\u0000\u0014\u0103\u0001\u0000\u0000\u0000\u0016\u010d"+
    "\u0001\u0000\u0000\u0000\u0018\u011f\u0001\u0000\u0000\u0000\u001a\u0121"+
    "\u0001\u0000\u0000\u0000\u001c\u0123\u0001\u0000\u0000\u0000\u001e\u0126"+
    "\u0001\u0000\u0000\u0000 \u0133\u0001\u0000\u0000\u0000\"\u0135\u0001"+
    "\u0000\u0000\u0000$\u0146\u0001\u0000\u0000\u0000&\u0148\u0001\u0000\u0000"+
    "\u0000(\u014a\u0001\u0000\u0000\u0000*\u014e\u0001\u0000\u0000\u0000,"+
    "\u0150\u0001\u0000\u0000\u0000.\u0159\u0001\u0000\u0000\u00000\u015d\u0001"+
    "\u0000\u0000\u00002\u016d\u0001\u0000\u0000\u00004\u0170\u0001\u0000\u0000"+
    "\u00006\u0178\u0001\u0000\u0000\u00008\u0180\u0001\u0000\u0000\u0000:"+
    "\u0188\u0001\u0000\u0000\u0000<\u0190\u0001\u0000\u0000\u0000>\u0192\u0001"+
    "\u0000\u0000\u0000@\u01be\u0001\u0000\u0000\u0000B\u01c2\u0001\u0000\u0000"+
    "\u0000D\u01c4\u0001\u0000\u0000\u0000F\u01c7\u0001\u0000\u0000\u0000H"+
    "\u01d0\u0001\u0000\u0000\u0000J\u01d8\u0001\u0000\u0000\u0000L\u01db\u0001"+
    "\u0000\u0000\u0000N\u01de\u0001\u0000\u0000\u0000P\u01e7\u0001\u0000\u0000"+
    "\u0000R\u01eb\u0001\u0000\u0000\u0000T\u01f1\u0001\u0000\u0000\u0000V"+
    "\u01f5\u0001\u0000\u0000\u0000X\u01f8\u0001\u0000\u0000\u0000Z\u0200\u0001"+
    "\u0000\u0000\u0000\\\u0204\u0001\u0000\u0000\u0000^\u0208\u0001\u0000"+
    "\u0000\u0000`\u020b\u0001\u0000\u0000\u0000b\u0210\u0001\u0000\u0000\u0000"+
    "d\u0214\u0001\u0000\u0000\u0000f\u0216\u0001\u0000\u0000\u0000h\u0218"+
    "\u0001\u0000\u0000\u0000j\u021b\u0001\u0000\u0000\u0000l\u021f\u0001\u0000"+
    "\u0000\u0000n\u0222\u0001\u0000\u0000\u0000p\u0225\u0001\u0000\u0000\u0000"+
    "r\u0239\u0001\u0000\u0000\u0000t\u023d\u0001\u0000\u0000\u0000v\u0242"+
    "\u0001\u0000\u0000\u0000xy\u0003\u0002\u0001\u0000yz\u0005\u0000\u0000"+
    "\u0001z\u0001\u0001\u0000\u0000\u0000{|\u0006\u0001\uffff\uffff\u0000"+
    "|}\u0003\u0004\u0002\u0000}\u0083\u0001\u0000\u0000\u0000~\u007f\n\u0001"+
    "\u0000\u0000\u007f\u0080\u0005\u001a\u0000\u0000\u0080\u0082\u0003\u0006"+
    "\u0003\u0000\u0081~\u0001\u0000\u0000\u0000\u0082\u0085\u0001\u0000\u0000"+
    "\u0000\u0083\u0081\u0001\u0000\u0000\u0000\u0083\u0084\u0001\u0000\u0000"+
    "\u0000\u0084\u0003\u0001\u0000\u0000\u0000\u0085\u0083\u0001\u0000\u0000"+
    "\u0000\u0086\u008e\u0003h4\u0000\u0087\u008e\u0003\"\u0011\u0000\u0088"+
    "\u008e\u0003n7\u0000\u0089\u008e\u0003\u001c\u000e\u0000\u008a\u008e\u0003"+
    "l6\u0000\u008b\u008c\u0004\u0002\u0001\u0000\u008c\u008e\u00030\u0018"+
    "\u0000\u008d\u0086\u0001\u0000\u0000\u0000\u008d\u0087\u0001\u0000\u0000"+
    "\u0000\u008d\u0088\u0001\u0000\u0000\u0000\u008d\u0089\u0001\u0000\u0000"+
    "\u0000\u008d\u008a\u0001\u0000\u0000\u0000\u008d\u008b\u0001\u0000\u0000"+
    "\u0000\u008e\u0005\u0001\u0000\u0000\u0000\u008f\u00a0\u00032\u0019\u0000"+
    "\u0090\u00a0\u0003\b\u0004\u0000\u0091\u00a0\u0003J%\u0000\u0092\u00a0"+
    "\u0003D\"\u0000\u0093\u00a0\u00034\u001a\u0000\u0094\u00a0\u0003F#\u0000"+
    "\u0095\u00a0\u0003L&\u0000\u0096\u00a0\u0003N\'\u0000\u0097\u00a0\u0003"+
    "R)\u0000\u0098\u00a0\u0003T*\u0000\u0099\u00a0\u0003p8\u0000\u009a\u00a0"+
    "\u0003V+\u0000\u009b\u009c\u0004\u0003\u0002\u0000\u009c\u00a0\u0003v"+
    ";\u0000\u009d\u009e\u0004\u0003\u0003\u0000\u009e\u00a0\u0003t:\u0000"+
    "\u009f\u008f\u0001\u0000\u0000\u0000\u009f\u0090\u0001\u0000\u0000\u0000"+
    "\u009f\u0091\u0001\u0000\u0000\u0000\u009f\u0092\u0001\u0000\u0000\u0000"+
    "\u009f\u0093\u0001\u0000\u0000\u0000\u009f\u0094\u0001\u0000\u0000\u0000"+
    "\u009f\u0095\u0001\u0000\u0000\u0000\u009f\u0096\u0001\u0000\u0000\u0000"+
    "\u009f\u0097\u0001\u0000\u0000\u0000\u009f\u0098\u0001\u0000\u0000\u0000"+
    "\u009f\u0099\u0001\u0000\u0000\u0000\u009f\u009a\u0001\u0000\u0000\u0000"+
    "\u009f\u009b\u0001\u0000\u0000\u0000\u009f\u009d\u0001\u0000\u0000\u0000"+
    "\u00a0\u0007\u0001\u0000\u0000\u0000\u00a1\u00a2\u0005\u0011\u0000\u0000"+
    "\u00a2\u00a3\u0003\n\u0005\u0000\u00a3\t\u0001\u0000\u0000\u0000\u00a4"+
    "\u00a5\u0006\u0005\uffff\uffff\u0000\u00a5\u00a6\u0005-\u0000\u0000\u00a6"+
    "\u00c3\u0003\n\u0005\b\u00a7\u00c3\u0003\u0010\b\u0000\u00a8\u00c3\u0003"+
    "\f\u0006\u0000\u00a9\u00ab\u0003\u0010\b\u0000\u00aa\u00ac\u0005-\u0000"+
    "\u0000\u00ab\u00aa\u0001\u0000\u0000\u0000\u00ab\u00ac\u0001\u0000\u0000"+
    "\u0000\u00ac\u00ad\u0001\u0000\u0000\u0000\u00ad\u00ae\u0005(\u0000\u0000"+
    "\u00ae\u00af\u0005,\u0000\u0000\u00af\u00b4\u0003\u0010\b\u0000\u00b0"+
    "\u00b1\u0005#\u0000\u0000\u00b1\u00b3\u0003\u0010\b\u0000\u00b2\u00b0"+
    "\u0001\u0000\u0000\u0000\u00b3\u00b6\u0001\u0000\u0000\u0000\u00b4\u00b2"+
    "\u0001\u0000\u0000\u0000\u00b4\u00b5\u0001\u0000\u0000\u0000\u00b5\u00b7"+
    "\u0001\u0000\u0000\u0000\u00b6\u00b4\u0001\u0000\u0000\u0000\u00b7\u00b8"+
    "\u00053\u0000\u0000\u00b8\u00c3\u0001\u0000\u0000\u0000\u00b9\u00ba\u0003"+
    "\u0010\b\u0000\u00ba\u00bc\u0005)\u0000\u0000\u00bb\u00bd\u0005-\u0000"+
    "\u0000\u00bc\u00bb\u0001\u0000\u0000\u0000\u00bc\u00bd\u0001\u0000\u0000"+
    "\u0000\u00bd\u00be\u0001\u0000\u0000\u0000\u00be\u00bf\u0005.\u0000\u0000"+
    "\u00bf\u00c3\u0001\u0000\u0000\u0000\u00c0\u00c1\u0004\u0005\u0004\u0000"+
    "\u00c1\u00c3\u0003\u000e\u0007\u0000\u00c2\u00a4\u0001\u0000\u0000\u0000"+
    "\u00c2\u00a7\u0001\u0000\u0000\u0000\u00c2\u00a8\u0001\u0000\u0000\u0000"+
    "\u00c2\u00a9\u0001\u0000\u0000\u0000\u00c2\u00b9\u0001\u0000\u0000\u0000"+
    "\u00c2\u00c0\u0001\u0000\u0000\u0000\u00c3\u00cc\u0001\u0000\u0000\u0000"+
    "\u00c4\u00c5\n\u0005\u0000\u0000\u00c5\u00c6\u0005\u001f\u0000\u0000\u00c6"+
    "\u00cb\u0003\n\u0005\u0006\u00c7\u00c8\n\u0004\u0000\u0000\u00c8\u00c9"+
    "\u00050\u0000\u0000\u00c9\u00cb\u0003\n\u0005\u0005\u00ca\u00c4\u0001"+
    "\u0000\u0000\u0000\u00ca\u00c7\u0001\u0000\u0000\u0000\u00cb\u00ce\u0001"+
    "\u0000\u0000\u0000\u00cc\u00ca\u0001\u0000\u0000\u0000\u00cc\u00cd\u0001"+
    "\u0000\u0000\u0000\u00cd\u000b\u0001\u0000\u0000\u0000\u00ce\u00cc\u0001"+
    "\u0000\u0000\u0000\u00cf\u00d1\u0003\u0010\b\u0000\u00d0\u00d2\u0005-"+
    "\u0000\u0000\u00d1\u00d0\u0001\u0000\u0000\u0000\u00d1\u00d2\u0001\u0000"+
    "\u0000\u0000\u00d2\u00d3\u0001\u0000\u0000\u0000\u00d3\u00d4\u0005+\u0000"+
    "\u0000\u00d4\u00d5\u0003d2\u0000\u00d5\u00de\u0001\u0000\u0000\u0000\u00d6"+
    "\u00d8\u0003\u0010\b\u0000\u00d7\u00d9\u0005-\u0000\u0000\u00d8\u00d7"+
    "\u0001\u0000\u0000\u0000\u00d8\u00d9\u0001\u0000\u0000\u0000\u00d9\u00da"+
    "\u0001\u0000\u0000\u0000\u00da\u00db\u00052\u0000\u0000\u00db\u00dc\u0003"+
    "d2\u0000\u00dc\u00de\u0001\u0000\u0000\u0000\u00dd\u00cf\u0001\u0000\u0000"+
    "\u0000\u00dd\u00d6\u0001\u0000\u0000\u0000\u00de\r\u0001\u0000\u0000\u0000"+
    "\u00df\u00e0\u0003\u0010\b\u0000\u00e0\u00e1\u0005\u0014\u0000\u0000\u00e1"+
    "\u00e2\u0003d2\u0000\u00e2\u000f\u0001\u0000\u0000\u0000\u00e3\u00e9\u0003"+
    "\u0012\t\u0000\u00e4\u00e5\u0003\u0012\t\u0000\u00e5\u00e6\u0003f3\u0000"+
    "\u00e6\u00e7\u0003\u0012\t\u0000\u00e7\u00e9\u0001\u0000\u0000\u0000\u00e8"+
    "\u00e3\u0001\u0000\u0000\u0000\u00e8\u00e4\u0001\u0000\u0000\u0000\u00e9"+
    "\u0011\u0001\u0000\u0000\u0000\u00ea\u00eb\u0006\t\uffff\uffff\u0000\u00eb"+
    "\u00ef\u0003\u0014\n\u0000\u00ec\u00ed\u0007\u0000\u0000\u0000\u00ed\u00ef"+
    "\u0003\u0012\t\u0003\u00ee\u00ea\u0001\u0000\u0000\u0000\u00ee\u00ec\u0001"+
    "\u0000\u0000\u0000\u00ef\u00f8\u0001\u0000\u0000\u0000\u00f0\u00f1\n\u0002"+
    "\u0000\u0000\u00f1\u00f2\u0007\u0001\u0000\u0000\u00f2\u00f7\u0003\u0012"+
    "\t\u0003\u00f3\u00f4\n\u0001\u0000\u0000\u00f4\u00f5\u0007\u0000\u0000"+
    "\u0000\u00f5\u00f7\u0003\u0012\t\u0002\u00f6\u00f0\u0001\u0000\u0000\u0000"+
    "\u00f6\u00f3\u0001\u0000\u0000\u0000\u00f7\u00fa\u0001\u0000\u0000\u0000"+
    "\u00f8\u00f6\u0001\u0000\u0000\u0000\u00f8\u00f9\u0001\u0000\u0000\u0000"+
    "\u00f9\u0013\u0001\u0000\u0000\u0000\u00fa\u00f8\u0001\u0000\u0000\u0000"+
    "\u00fb\u00fc\u0006\n\uffff\uffff\u0000\u00fc\u0104\u0003@ \u0000\u00fd"+
    "\u0104\u00036\u001b\u0000\u00fe\u0104\u0003\u0016\u000b\u0000\u00ff\u0100"+
    "\u0005,\u0000\u0000\u0100\u0101\u0003\n\u0005\u0000\u0101\u0102\u0005"+
    "3\u0000\u0000\u0102\u0104\u0001\u0000\u0000\u0000\u0103\u00fb\u0001\u0000"+
    "\u0000\u0000\u0103\u00fd\u0001\u0000\u0000\u0000\u0103\u00fe\u0001\u0000"+
    "\u0000\u0000\u0103\u00ff\u0001\u0000\u0000\u0000\u0104\u010a\u0001\u0000"+
    "\u0000\u0000\u0105\u0106\n\u0001\u0000\u0000\u0106\u0107\u0005\"\u0000"+
    "\u0000\u0107\u0109\u0003\u001a\r\u0000\u0108\u0105\u0001\u0000\u0000\u0000"+
    "\u0109\u010c\u0001\u0000\u0000\u0000\u010a\u0108\u0001\u0000\u0000\u0000"+
    "\u010a\u010b\u0001\u0000\u0000\u0000\u010b\u0015\u0001\u0000\u0000\u0000"+
    "\u010c\u010a\u0001\u0000\u0000\u0000\u010d\u010e\u0003\u0018\f\u0000\u010e"+
    "\u0118\u0005,\u0000\u0000\u010f\u0119\u0005>\u0000\u0000\u0110\u0115\u0003"+
    "\n\u0005\u0000\u0111\u0112\u0005#\u0000\u0000\u0112\u0114\u0003\n\u0005"+
    "\u0000\u0113\u0111\u0001\u0000\u0000\u0000\u0114\u0117\u0001\u0000\u0000"+
    "\u0000\u0115\u0113\u0001\u0000\u0000\u0000\u0115\u0116\u0001\u0000\u0000"+
    "\u0000\u0116\u0119\u0001\u0000\u0000\u0000\u0117\u0115\u0001\u0000\u0000"+
    "\u0000\u0118\u010f\u0001\u0000\u0000\u0000\u0118\u0110\u0001\u0000\u0000"+
    "\u0000\u0118\u0119\u0001\u0000\u0000\u0000\u0119\u011a\u0001\u0000\u0000"+
    "\u0000\u011a\u011b\u00053\u0000\u0000\u011b\u0017\u0001\u0000\u0000\u0000"+
    "\u011c\u011d\u0004\f\n\u0000\u011d\u0120\u0005\u0014\u0000\u0000\u011e"+
    "\u0120\u0003<\u001e\u0000\u011f\u011c\u0001\u0000\u0000\u0000\u011f\u011e"+
    "\u0001\u0000\u0000\u0000\u0120\u0019\u0001\u0000\u0000\u0000\u0121\u0122"+
    "\u0003<\u001e\u0000\u0122\u001b\u0001\u0000\u0000\u0000\u0123\u0124\u0005"+
    "\r\u0000\u0000\u0124\u0125\u0003\u001e\u000f\u0000\u0125\u001d\u0001\u0000"+
    "\u0000\u0000\u0126\u012b\u0003 \u0010\u0000\u0127\u0128\u0005#\u0000\u0000"+
    "\u0128\u012a\u0003 \u0010\u0000\u0129\u0127\u0001\u0000\u0000\u0000\u012a"+
    "\u012d\u0001\u0000\u0000\u0000\u012b\u0129\u0001\u0000\u0000\u0000\u012b"+
    "\u012c\u0001\u0000\u0000\u0000\u012c\u001f\u0001\u0000\u0000\u0000\u012d"+
    "\u012b\u0001\u0000\u0000\u0000\u012e\u0134\u0003\n\u0005\u0000\u012f\u0130"+
    "\u00036\u001b\u0000\u0130\u0131\u0005!\u0000\u0000\u0131\u0132\u0003\n"+
    "\u0005\u0000\u0132\u0134\u0001\u0000\u0000\u0000\u0133\u012e\u0001\u0000"+
    "\u0000\u0000\u0133\u012f\u0001\u0000\u0000\u0000\u0134!\u0001\u0000\u0000"+
    "\u0000\u0135\u0136\u0005\u0006\u0000\u0000\u0136\u013b\u0003$\u0012\u0000"+
    "\u0137\u0138\u0005#\u0000\u0000\u0138\u013a\u0003$\u0012\u0000\u0139\u0137"+
    "\u0001\u0000\u0000\u0000\u013a\u013d\u0001\u0000\u0000\u0000\u013b\u0139"+
    "\u0001\u0000\u0000\u0000\u013b\u013c\u0001\u0000\u0000\u0000\u013c\u013f"+
    "\u0001\u0000\u0000\u0000\u013d\u013b\u0001\u0000\u0000\u0000\u013e\u0140"+
    "\u0003*\u0015\u0000\u013f\u013e\u0001\u0000\u0000\u0000\u013f\u0140\u0001"+
    "\u0000\u0000\u0000\u0140#\u0001\u0000\u0000\u0000\u0141\u0142\u0003&\u0013"+
    "\u0000\u0142\u0143\u0005m\u0000\u0000\u0143\u0144\u0003(\u0014\u0000\u0144"+
    "\u0147\u0001\u0000\u0000\u0000\u0145\u0147\u0003(\u0014\u0000\u0146\u0141"+
    "\u0001\u0000\u0000\u0000\u0146\u0145\u0001\u0000\u0000\u0000\u0147%\u0001"+
    "\u0000\u0000\u0000\u0148\u0149\u0005M\u0000\u0000\u0149\'\u0001\u0000"+
    "\u0000\u0000\u014a\u014b\u0007\u0002\u0000\u0000\u014b)\u0001\u0000\u0000"+
    "\u0000\u014c\u014f\u0003,\u0016\u0000\u014d\u014f\u0003.\u0017\u0000\u014e"+
    "\u014c\u0001\u0000\u0000\u0000\u014e\u014d\u0001\u0000\u0000\u0000\u014f"+
    "+\u0001\u0000\u0000\u0000\u0150\u0151\u0005L\u0000\u0000\u0151\u0156\u0005"+
    "M\u0000\u0000\u0152\u0153\u0005#\u0000\u0000\u0153\u0155\u0005M\u0000"+
    "\u0000\u0154\u0152\u0001\u0000\u0000\u0000\u0155\u0158\u0001\u0000\u0000"+
    "\u0000\u0156\u0154\u0001\u0000\u0000\u0000\u0156\u0157\u0001\u0000\u0000"+
    "\u0000\u0157-\u0001\u0000\u0000\u0000\u0158\u0156\u0001\u0000\u0000\u0000"+
    "\u0159\u015a\u0005B\u0000\u0000\u015a\u015b\u0003,\u0016\u0000\u015b\u015c"+
    "\u0005C\u0000\u0000\u015c/\u0001\u0000\u0000\u0000\u015d\u015e\u0005\u0015"+
    "\u0000\u0000\u015e\u0163\u0003$\u0012\u0000\u015f\u0160\u0005#\u0000\u0000"+
    "\u0160\u0162\u0003$\u0012\u0000\u0161\u015f\u0001\u0000\u0000\u0000\u0162"+
    "\u0165\u0001\u0000\u0000\u0000\u0163\u0161\u0001\u0000\u0000\u0000\u0163"+
    "\u0164\u0001\u0000\u0000\u0000\u0164\u0167\u0001\u0000\u0000\u0000\u0165"+
    "\u0163\u0001\u0000\u0000\u0000\u0166\u0168\u0003\u001e\u000f\u0000\u0167"+
    "\u0166\u0001\u0000\u0000\u0000\u0167\u0168\u0001\u0000\u0000\u0000\u0168"+
    "\u016b\u0001\u0000\u0000\u0000\u0169\u016a\u0005\u001e\u0000\u0000\u016a"+
    "\u016c\u0003\u001e\u000f\u0000\u016b\u0169\u0001\u0000\u0000\u0000\u016b"+
    "\u016c\u0001\u0000\u0000\u0000\u016c1\u0001\u0000\u0000\u0000\u016d\u016e"+
    "\u0005\u0004\u0000\u0000\u016e\u016f\u0003\u001e\u000f\u0000\u016f3\u0001"+
    "\u0000\u0000\u0000\u0170\u0172\u0005\u0010\u0000\u0000\u0171\u0173\u0003"+
    "\u001e\u000f\u0000\u0172\u0171\u0001\u0000\u0000\u0000\u0172\u0173\u0001"+
    "\u0000\u0000\u0000\u0173\u0176\u0001\u0000\u0000\u0000\u0174\u0175\u0005"+
    "\u001e\u0000\u0000\u0175\u0177\u0003\u001e\u000f\u0000\u0176\u0174\u0001"+
    "\u0000\u0000\u0000\u0176\u0177\u0001\u0000\u0000\u0000\u01775\u0001\u0000"+
    "\u0000\u0000\u0178\u017d\u0003<\u001e\u0000\u0179\u017a\u0005%\u0000\u0000"+
    "\u017a\u017c\u0003<\u001e\u0000\u017b\u0179\u0001\u0000\u0000\u0000\u017c"+
    "\u017f\u0001\u0000\u0000\u0000\u017d\u017b\u0001\u0000\u0000\u0000\u017d"+
    "\u017e\u0001\u0000\u0000\u0000\u017e7\u0001\u0000\u0000\u0000\u017f\u017d"+
    "\u0001\u0000\u0000\u0000\u0180\u0185\u0003>\u001f\u0000\u0181\u0182\u0005"+
    "%\u0000\u0000\u0182\u0184\u0003>\u001f\u0000\u0183\u0181\u0001\u0000\u0000"+
    "\u0000\u0184\u0187\u0001\u0000\u0000\u0000\u0185\u0183\u0001\u0000\u0000"+
    "\u0000\u0185\u0186\u0001\u0000\u0000\u0000\u01869\u0001\u0000\u0000\u0000"+
    "\u0187\u0185\u0001\u0000\u0000\u0000\u0188\u018d\u00038\u001c\u0000\u0189"+
    "\u018a\u0005#\u0000\u0000\u018a\u018c\u00038\u001c\u0000\u018b\u0189\u0001"+
    "\u0000\u0000\u0000\u018c\u018f\u0001\u0000\u0000\u0000\u018d\u018b\u0001"+
    "\u0000\u0000\u0000\u018d\u018e\u0001\u0000\u0000\u0000\u018e;\u0001\u0000"+
    "\u0000\u0000\u018f\u018d\u0001\u0000\u0000\u0000\u0190\u0191\u0007\u0003"+
    "\u0000\u0000\u0191=\u0001\u0000\u0000\u0000\u0192\u0193\u0005Q\u0000\u0000"+
    "\u0193?\u0001\u0000\u0000\u0000\u0194\u01bf\u0005.\u0000\u0000\u0195\u0196"+
    "\u0003b1\u0000\u0196\u0197\u0005D\u0000\u0000\u0197\u01bf\u0001\u0000"+
    "\u0000\u0000\u0198\u01bf\u0003`0\u0000\u0199\u01bf\u0003b1\u0000\u019a"+
    "\u01bf\u0003\\.\u0000\u019b\u01bf\u0003B!\u0000\u019c\u01bf\u0003d2\u0000"+
    "\u019d\u019e\u0005B\u0000\u0000\u019e\u01a3\u0003^/\u0000\u019f\u01a0"+
    "\u0005#\u0000\u0000\u01a0\u01a2\u0003^/\u0000\u01a1\u019f\u0001\u0000"+
    "\u0000\u0000\u01a2\u01a5\u0001\u0000\u0000\u0000\u01a3\u01a1\u0001\u0000"+
    "\u0000\u0000\u01a3\u01a4\u0001\u0000\u0000\u0000\u01a4\u01a6\u0001\u0000"+
    "\u0000\u0000\u01a5\u01a3\u0001\u0000\u0000\u0000\u01a6\u01a7\u0005C\u0000"+
    "\u0000\u01a7\u01bf\u0001\u0000\u0000\u0000\u01a8\u01a9\u0005B\u0000\u0000"+
    "\u01a9\u01ae\u0003\\.\u0000\u01aa\u01ab\u0005#\u0000\u0000\u01ab\u01ad"+
    "\u0003\\.\u0000\u01ac\u01aa\u0001\u0000\u0000\u0000\u01ad\u01b0\u0001"+
    "\u0000\u0000\u0000\u01ae\u01ac\u0001\u0000\u0000\u0000\u01ae\u01af\u0001"+
    "\u0000\u0000\u0000\u01af\u01b1\u0001\u0000\u0000\u0000\u01b0\u01ae\u0001"+
    "\u0000\u0000\u0000\u01b1\u01b2\u0005C\u0000\u0000\u01b2\u01bf\u0001\u0000"+
    "\u0000\u0000\u01b3\u01b4\u0005B\u0000\u0000\u01b4\u01b9\u0003d2\u0000"+
    "\u01b5\u01b6\u0005#\u0000\u0000\u01b6\u01b8\u0003d2\u0000\u01b7\u01b5"+
    "\u0001\u0000\u0000\u0000\u01b8\u01bb\u0001\u0000\u0000\u0000\u01b9\u01b7"+
    "\u0001\u0000\u0000\u0000\u01b9\u01ba\u0001\u0000\u0000\u0000\u01ba\u01bc"+
    "\u0001\u0000\u0000\u0000\u01bb\u01b9\u0001\u0000\u0000\u0000\u01bc\u01bd"+
    "\u0005C\u0000\u0000\u01bd\u01bf\u0001\u0000\u0000\u0000\u01be\u0194\u0001"+
    "\u0000\u0000\u0000\u01be\u0195\u0001\u0000\u0000\u0000\u01be\u0198\u0001"+
    "\u0000\u0000\u0000\u01be\u0199\u0001\u0000\u0000\u0000\u01be\u019a\u0001"+
    "\u0000\u0000\u0000\u01be\u019b\u0001\u0000\u0000\u0000\u01be\u019c\u0001"+
    "\u0000\u0000\u0000\u01be\u019d\u0001\u0000\u0000\u0000\u01be\u01a8\u0001"+
    "\u0000\u0000\u0000\u01be\u01b3\u0001\u0000\u0000\u0000\u01bfA\u0001\u0000"+
    "\u0000\u0000\u01c0\u01c3\u00051\u0000\u0000\u01c1\u01c3\u0005A\u0000\u0000"+
    "\u01c2\u01c0\u0001\u0000\u0000\u0000\u01c2\u01c1\u0001\u0000\u0000\u0000"+
    "\u01c3C\u0001\u0000\u0000\u0000\u01c4\u01c5\u0005\t\u0000\u0000\u01c5"+
    "\u01c6\u0005\u001c\u0000\u0000\u01c6E\u0001\u0000\u0000\u0000\u01c7\u01c8"+
    "\u0005\u000f\u0000\u0000\u01c8\u01cd\u0003H$\u0000\u01c9\u01ca\u0005#"+
    "\u0000\u0000\u01ca\u01cc\u0003H$\u0000\u01cb\u01c9\u0001\u0000\u0000\u0000"+
    "\u01cc\u01cf\u0001\u0000\u0000\u0000\u01cd\u01cb\u0001\u0000\u0000\u0000"+
    "\u01cd\u01ce\u0001\u0000\u0000\u0000\u01ceG\u0001\u0000\u0000\u0000\u01cf"+
    "\u01cd\u0001\u0000\u0000\u0000\u01d0\u01d2\u0003\n\u0005\u0000\u01d1\u01d3"+
    "\u0007\u0004\u0000\u0000\u01d2\u01d1\u0001\u0000\u0000\u0000\u01d2\u01d3"+
    "\u0001\u0000\u0000\u0000\u01d3\u01d6\u0001\u0000\u0000\u0000\u01d4\u01d5"+
    "\u0005/\u0000\u0000\u01d5\u01d7\u0007\u0005\u0000\u0000\u01d6\u01d4\u0001"+
    "\u0000\u0000\u0000\u01d6\u01d7\u0001\u0000\u0000\u0000\u01d7I\u0001\u0000"+
    "\u0000\u0000\u01d8\u01d9\u0005\b\u0000\u0000\u01d9\u01da\u0003:\u001d"+
    "\u0000\u01daK\u0001\u0000\u0000\u0000\u01db\u01dc\u0005\u0002\u0000\u0000"+
    "\u01dc\u01dd\u0003:\u001d\u0000\u01ddM\u0001\u0000\u0000\u0000\u01de\u01df"+
    "\u0005\f\u0000\u0000\u01df\u01e4\u0003P(\u0000\u01e0\u01e1\u0005#\u0000"+
    "\u0000\u01e1\u01e3\u0003P(\u0000\u01e2\u01e0\u0001\u0000\u0000\u0000\u01e3"+
    "\u01e6\u0001\u0000\u0000\u0000\u01e4\u01e2\u0001\u0000\u0000\u0000\u01e4"+
    "\u01e5\u0001\u0000\u0000\u0000\u01e5O\u0001\u0000\u0000\u0000\u01e6\u01e4"+
    "\u0001\u0000\u0000\u0000\u01e7\u01e8\u00038\u001c\u0000\u01e8\u01e9\u0005"+
    "U\u0000\u0000\u01e9\u01ea\u00038\u001c\u0000\u01eaQ\u0001\u0000\u0000"+
    "\u0000\u01eb\u01ec\u0005\u0001\u0000\u0000\u01ec\u01ed\u0003\u0014\n\u0000"+
    "\u01ed\u01ef\u0003d2\u0000\u01ee\u01f0\u0003X,\u0000\u01ef\u01ee\u0001"+
    "\u0000\u0000\u0000\u01ef\u01f0\u0001\u0000\u0000\u0000\u01f0S\u0001\u0000"+
    "\u0000\u0000\u01f1\u01f2\u0005\u0007\u0000\u0000\u01f2\u01f3\u0003\u0014"+
    "\n\u0000\u01f3\u01f4\u0003d2\u0000\u01f4U\u0001\u0000\u0000\u0000\u01f5"+
    "\u01f6\u0005\u000b\u0000\u0000\u01f6\u01f7\u00036\u001b\u0000\u01f7W\u0001"+
    "\u0000\u0000\u0000\u01f8\u01fd\u0003Z-\u0000\u01f9\u01fa\u0005#\u0000"+
    "\u0000\u01fa\u01fc\u0003Z-\u0000\u01fb\u01f9\u0001\u0000\u0000\u0000\u01fc"+
    "\u01ff\u0001\u0000\u0000\u0000\u01fd\u01fb\u0001\u0000\u0000\u0000\u01fd"+
    "\u01fe\u0001\u0000\u0000\u0000\u01feY\u0001\u0000\u0000\u0000\u01ff\u01fd"+
    "\u0001\u0000\u0000\u0000\u0200\u0201\u0003<\u001e\u0000\u0201\u0202\u0005"+
    "!\u0000\u0000\u0202\u0203\u0003@ \u0000\u0203[\u0001\u0000\u0000\u0000"+
    "\u0204\u0205\u0007\u0006\u0000\u0000\u0205]\u0001\u0000\u0000\u0000\u0206"+
    "\u0209\u0003`0\u0000\u0207\u0209\u0003b1\u0000\u0208\u0206\u0001\u0000"+
    "\u0000\u0000\u0208\u0207\u0001\u0000\u0000\u0000\u0209_\u0001\u0000\u0000"+
    "\u0000\u020a\u020c\u0007\u0000\u0000\u0000\u020b\u020a\u0001\u0000\u0000"+
    "\u0000\u020b\u020c\u0001\u0000\u0000\u0000\u020c\u020d\u0001\u0000\u0000"+
    "\u0000\u020d\u020e\u0005\u001d\u0000\u0000\u020ea\u0001\u0000\u0000\u0000"+
    "\u020f\u0211\u0007\u0000\u0000\u0000\u0210\u020f\u0001\u0000\u0000\u0000"+
    "\u0210\u0211\u0001\u0000\u0000\u0000\u0211\u0212\u0001\u0000\u0000\u0000"+
    "\u0212\u0213\u0005\u001c\u0000\u0000\u0213c\u0001\u0000\u0000\u0000\u0214"+
    "\u0215\u0005\u001b\u0000\u0000\u0215e\u0001\u0000\u0000\u0000\u0216\u0217"+
    "\u0007\u0007\u0000\u0000\u0217g\u0001\u0000\u0000\u0000\u0218\u0219\u0005"+
    "\u0005\u0000\u0000\u0219\u021a\u0003j5\u0000\u021ai\u0001\u0000\u0000"+
    "\u0000\u021b\u021c\u0005B\u0000\u0000\u021c\u021d\u0003\u0002\u0001\u0000"+
    "\u021d\u021e\u0005C\u0000\u0000\u021ek\u0001\u0000\u0000\u0000\u021f\u0220"+
    "\u0005\u000e\u0000\u0000\u0220\u0221\u0005e\u0000\u0000\u0221m\u0001\u0000"+
    "\u0000\u0000\u0222\u0223\u0005\n\u0000\u0000\u0223\u0224\u0005i\u0000"+
    "\u0000\u0224o\u0001\u0000\u0000\u0000\u0225\u0226\u0005\u0003\u0000\u0000"+
    "\u0226\u0229\u0005[\u0000\u0000\u0227\u0228\u0005Y\u0000\u0000\u0228\u022a"+
    "\u00038\u001c\u0000\u0229\u0227\u0001\u0000\u0000\u0000\u0229\u022a\u0001"+
    "\u0000\u0000\u0000\u022a\u0234\u0001\u0000\u0000\u0000\u022b\u022c\u0005"+
    "Z\u0000\u0000\u022c\u0231\u0003r9\u0000\u022d\u022e\u0005#\u0000\u0000"+
    "\u022e\u0230\u0003r9\u0000\u022f\u022d\u0001\u0000\u0000\u0000\u0230\u0233"+
    "\u0001\u0000\u0000\u0000\u0231\u022f\u0001\u0000\u0000\u0000\u0231\u0232"+
    "\u0001\u0000\u0000\u0000\u0232\u0235\u0001\u0000\u0000\u0000\u0233\u0231"+
    "\u0001\u0000\u0000\u0000\u0234\u022b\u0001\u0000\u0000\u0000\u0234\u0235"+
    "\u0001\u0000\u0000\u0000\u0235q\u0001\u0000\u0000\u0000\u0236\u0237\u0003"+
    "8\u001c\u0000\u0237\u0238\u0005!\u0000\u0000\u0238\u023a\u0001\u0000\u0000"+
    "\u0000\u0239\u0236\u0001\u0000\u0000\u0000\u0239\u023a\u0001\u0000\u0000"+
    "\u0000\u023a\u023b\u0001\u0000\u0000\u0000\u023b\u023c\u00038\u001c\u0000"+
    "\u023cs\u0001\u0000\u0000\u0000\u023d\u023e\u0005\u0013\u0000\u0000\u023e"+
    "\u023f\u0003$\u0012\u0000\u023f\u0240\u0005Y\u0000\u0000\u0240\u0241\u0003"+
    ":\u001d\u0000\u0241u\u0001\u0000\u0000\u0000\u0242\u0243\u0005\u0012\u0000"+
    "\u0000\u0243\u0246\u0003\u001e\u000f\u0000\u0244\u0245\u0005\u001e\u0000"+
    "\u0000\u0245\u0247\u0003\u001e\u000f\u0000\u0246\u0244\u0001\u0000\u0000"+
    "\u0000\u0246\u0247\u0001\u0000\u0000\u0000\u0247w\u0001\u0000\u0000\u0000"+
    "7\u0083\u008d\u009f\u00ab\u00b4\u00bc\u00c2\u00ca\u00cc\u00d1\u00d8\u00dd"+
    "\u00e8\u00ee\u00f6\u00f8\u0103\u010a\u0115\u0118\u011f\u012b\u0133\u013b"+
    "\u013f\u0146\u014e\u0156\u0163\u0167\u016b\u0172\u0176\u017d\u0185\u018d"+
    "\u01a3\u01ae\u01b9\u01be\u01c2\u01cd\u01d2\u01d6\u01e4\u01ef\u01fd\u0208"+
    "\u020b\u0210\u0229\u0231\u0234\u0239\u0246";
  public static final ATN _ATN =
    new ATNDeserializer().deserialize(_serializedATN.toCharArray());
  static {
    _decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
    for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
      _decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
    }
  }
}
