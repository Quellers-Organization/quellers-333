/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.application.rules;

import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.Query;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.get.TransportMultiGetAction;
import org.elasticsearch.client.internal.Client;
import org.elasticsearch.client.internal.ElasticsearchClient;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.get.GetResult;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.SearchExecutionContext;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.AbstractQueryTestCase;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.elasticsearch.xpack.application.LocalStateEnterpriseSearch;
import org.elasticsearch.xpack.searchbusinessrules.SearchBusinessRules;
import org.hamcrest.Matchers;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.xpack.application.rules.QueryRuleCriteriaType.EXACT;
import static org.hamcrest.CoreMatchers.instanceOf;

public class RuleQueryBuilderTests extends AbstractQueryTestCase<RuleQueryBuilder> {

    // This criteria has to be constant, to ensure the rule hits.
    private static final Map<String, Object> MATCH_CRITERIA = Map.of("query_string", "elastic");

    @Override
    protected RuleQueryBuilder doCreateTestQueryBuilder() {
        return new RuleQueryBuilder(new MatchAllQueryBuilder(), MATCH_CRITERIA, randomList(1, 3, this::randomRulesetId));
    }

    private String randomRulesetId() {
        return randomAlphaOfLengthBetween(1, 10);
    }

    @Override
    protected void doAssertLuceneQuery(RuleQueryBuilder queryBuilder, Query query, SearchExecutionContext context) {
        // The query rule always applies here, so we turn into a pinned query which is rewritten into a Dismax query.
        assertTrue(query.toString(), query instanceof DisjunctionMaxQuery);
    }

    @Override
    protected Collection<Class<? extends Plugin>> getPlugins() {
        return List.of(LocalStateEnterpriseSearch.class, SearchBusinessRules.class);
    }

    public void testIllegalArguments() {
        expectThrows(IllegalArgumentException.class, () -> new RuleQueryBuilder(new MatchAllQueryBuilder(), null, List.of("rulesetId")));
        expectThrows(IllegalArgumentException.class, () -> new RuleQueryBuilder(new MatchAllQueryBuilder(), MATCH_CRITERIA, List.of()));
        expectThrows(IllegalArgumentException.class, () -> new RuleQueryBuilder(new MatchAllQueryBuilder(), MATCH_CRITERIA, null));
        expectThrows(IllegalArgumentException.class, () -> new RuleQueryBuilder(new MatchAllQueryBuilder(), MATCH_CRITERIA, List.of("")));
        expectThrows(IllegalArgumentException.class, () -> new RuleQueryBuilder(null, MATCH_CRITERIA, List.of("rulesetId")));
        expectThrows(IllegalArgumentException.class, () -> new RuleQueryBuilder(null, Collections.emptyMap(), List.of("rulesetId")));
    }

    public void testFromJson() throws IOException {
        String query = """
            {
              "rule": {
                "organic": {
                  "term": {
                    "tag": {
                      "value": "search"
                    }
                  }
                },
                "match_criteria": {
                  "query_string": "elastic"
                },
                "ruleset_ids": [ "ruleset1", "ruleset2" ]
              }
            }""";

        RuleQueryBuilder queryBuilder = (RuleQueryBuilder) parseQuery(query);
        checkGeneratedJson(query, queryBuilder);

        assertEquals(2, queryBuilder.rulesetIds().size());
        assertEquals("ruleset1", queryBuilder.rulesetIds().get(0));
        assertEquals("ruleset2", queryBuilder.rulesetIds().get(1));
        assertEquals(query, "elastic", queryBuilder.matchCriteria().get("query_string"));
        assertThat(queryBuilder.organicQuery(), instanceOf(TermQueryBuilder.class));
    }

    /**
    * test that unknown query names in the clauses throw an error
    */
    public void testUnknownQueryName() {
        String query = "{\"rule\" : {\"organic\" : { \"unknown_query\" : { } } } }";

        ParsingException ex = expectThrows(ParsingException.class, () -> parseQuery(query));
        assertEquals("[1:44] [rule] failed to parse field [organic]", ex.getMessage());
    }

    public void testRewrite() throws IOException {
        RuleQueryBuilder ruleQueryBuilder = new RuleQueryBuilder(
            new TermQueryBuilder("foo", 1),
            Map.of("query_string", "bar"),
            List.of("baz", "qux")
        );
        QueryBuilder rewritten = ruleQueryBuilder.rewrite(createSearchExecutionContext());
        assertThat(rewritten, instanceOf(RuleQueryBuilder.class));
    }

    @Override
    protected boolean canSimulateMethod(Method method, Object[] args) throws NoSuchMethodException {
        if (method.getDeclaringClass().equals(ElasticsearchClient.class) && method.getName().equals("execute")) {
            return true;
        } else if (method.getDeclaringClass().equals(Client.class) && method.getName().equals("settings")) {
            return true;
        }

        return super.canSimulateMethod(method, args);
    }

    @Override
    protected Object simulateMethod(Method method, Object[] args) {
        // Get request, to pull the query ruleset from the system index using clientWithOrigin
        String declaringClass = method.getDeclaringClass().getName();
        String methodName = method.getName();
        Object arg = args[0];
        if (method.getDeclaringClass().equals(ElasticsearchClient.class)
            && method.getName().equals("execute")
            && args[0] == TransportMultiGetAction.TYPE) {

            List<QueryRuleset> queryRulesets = new ArrayList<>();
            MultiGetRequest multiGetRequest = (MultiGetRequest) args[1];
            multiGetRequest.getItems().forEach(getRequest -> {
                assertThat(getRequest.index(), Matchers.equalTo(QueryRulesIndexService.QUERY_RULES_ALIAS_NAME));
                String rulesetId = getRequest.id();
                List<QueryRule> rules = List.of(
                    new QueryRule(
                        "my_rule1",
                        QueryRule.QueryRuleType.PINNED,
                        List.of(new QueryRuleCriteria(EXACT, "query_string", List.of("elastic"))),
                        Map.of("ids", List.of("id1", "id2")),
                        null
                    )
                );
                QueryRuleset queryRuleset = new QueryRuleset(rulesetId, rules);
                queryRulesets.add(queryRuleset);
            });

            MultiGetItemResponse[] multiGetItemResponses = new MultiGetItemResponse[queryRulesets.size()];
            for (int i = 0; i < queryRulesets.size(); i++) {
                QueryRuleset queryRuleset = queryRulesets.get(i);
                String rulesetId = queryRuleset.id();
                String json;
                try {
                    XContentBuilder builder = queryRuleset.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
                    json = Strings.toString(builder);

                    MultiGetItemResponse multiGetItemResponse = new MultiGetItemResponse(
                        new GetResponse(
                            new GetResult(
                                QueryRulesIndexService.QUERY_RULES_ALIAS_NAME,
                                rulesetId,
                                0,
                                1,
                                0L,
                                true,
                                new BytesArray(json),
                                null,
                                null
                            )
                        ),
                        null
                    );
                    multiGetItemResponses[i] = multiGetItemResponse;

                } catch (IOException ex) {
                    throw new ElasticsearchException("boom", ex);
                }
            }

            MultiGetResponse response = new MultiGetResponse(multiGetItemResponses);

            @SuppressWarnings("unchecked")
            ActionListener<MultiGetResponse> listener = (ActionListener<MultiGetResponse>) args[2];
            listener.onResponse(response);

            return null;
        }

        // Client settings, used when creating the client with origin
        if (method.getDeclaringClass().equals(Client.class) && method.getName().equals("settings")) {
            return Settings.EMPTY;
        }

        return super.simulateMethod(method, args);
    }

    @Override
    protected Map<String, String> getObjectsHoldingArbitraryContent() {
        // document contains arbitrary content, no error expected when an object is added to it
        final Map<String, String> objects = new HashMap<>();
        objects.put(RuleQueryBuilder.MATCH_CRITERIA_FIELD.getPreferredName(), null);
        return objects;
    }
}
