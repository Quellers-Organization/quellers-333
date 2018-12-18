/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.core.security.authz.permission;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.automaton.TooComplexToDeterminizeException;
import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.cluster.metadata.AliasOrIndex;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.xpack.core.security.authz.accesscontrol.IndicesAccessControl;
import org.elasticsearch.xpack.core.security.authz.privilege.IndexPrivilege;
import org.elasticsearch.xpack.core.security.index.SystemIndicesNames;
import org.elasticsearch.xpack.core.security.support.Automatons;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;

/**
 * A permission that is based on privileges for index related actions executed
 * on specific indices
 */
public final class IndicesPermission {

    public static final IndicesPermission NONE = new IndicesPermission();

    private static final Logger logger = LogManager.getLogger();

    private final ConcurrentMap<String, Predicate<String>> allowedIndicesMatchersForAction = new ConcurrentHashMap<>();
    private final ConcurrentMap<Group, Automaton> indexGroupAutomatonCache = new ConcurrentHashMap<>();
    private final Group[] groups;
    private final Automaton systemIndicesAutomaton;

    public IndicesPermission(Group... groups) {
        this.groups = groups;
        this.systemIndicesAutomaton = Automatons.patterns(SystemIndicesNames.indexNames());
    }

    private static Predicate<String> indexMatcherPredicate(Collection<String> indices) {
        Set<String> exactMatch = new HashSet<>();
        List<String> nonExactMatch = new ArrayList<>();
        for (String indexPattern : indices) {
            if (isIndexPattern(indexPattern)) {
                nonExactMatch.add(indexPattern);
            } else {
                exactMatch.add(indexPattern);
            }
        }

        if (exactMatch.isEmpty() && nonExactMatch.isEmpty()) {
            return s -> false;
        } else if (exactMatch.isEmpty()) {
            return buildAutomataPredicate(nonExactMatch);
        } else if (nonExactMatch.isEmpty()) {
            return buildExactMatchPredicate(exactMatch);
        } else {
            return buildExactMatchPredicate(exactMatch).or(buildAutomataPredicate(nonExactMatch));
        }
    }

    private Automaton indexMatcherAutomaton(String... indices) {
        final List<String> exactMatch = new ArrayList<>();
        final List<String> patternMatch = new ArrayList<>();
        for (String indexPattern : indices) {
            if (isIndexPattern(indexPattern)) {
                patternMatch.add(indexPattern);
            } else {
                exactMatch.add(indexPattern);
            }
        }
        try {
            final Automaton exactMatchAutomaton = Automatons.patterns(exactMatch);
            final Automaton indexPatternAutomaton = Automatons.patterns(patternMatch);
            return Automatons.unionAndMinimize(
                    Arrays.asList(exactMatchAutomaton, Automatons.minusAndMinimize(indexPatternAutomaton, systemIndicesAutomaton)));
        } catch (TooComplexToDeterminizeException e) {
            logger.debug("Index pattern automaton [{}] is too complex", Strings.arrayToCommaDelimitedString(indices));
            String description = Strings.arrayToCommaDelimitedString(indices);
            if (description.length() > 80) {
                description = Strings.cleanTruncate(description, 80) + "...";
            }
            throw new ElasticsearchSecurityException("The set of permitted index patterns [{}] is too complex to evaluate", e, description);
        }
    }

    private static Predicate<String> buildExactMatchPredicate(Set<String> indices) {
        if (indices.size() == 1) {
            final String singleValue = indices.iterator().next();
            return singleValue::equals;
        }
        return indices::contains;
    }

    private static Predicate<String> buildAutomataPredicate(final Collection<String> indices) {
        final Predicate<String> indicesPredicate;
        try {
            indicesPredicate = Automatons.predicate(indices);
        } catch (TooComplexToDeterminizeException e) {
            logger.debug("Index pattern automaton [{}] is too complex", indices);
            String description = Strings.collectionToCommaDelimitedString(indices);
            if (description.length() > 80) {
                description = Strings.cleanTruncate(description, 80) + "...";
            }
            throw new ElasticsearchSecurityException("The set of permitted index patterns [{}] is too complex to evaluate", e, description);
        }
        return (index) -> {
            assert false == isIndexPattern(index);
            if (indicesPredicate.test(index)) {
                if (isSystemIndex(index)) {
                    logger.debug("Index pattern automaton [{}] cannot match system indices [{}]", indices, SystemIndicesNames.indexNames());
                    return false;
                } else {
                    return true;
                }
            } else {
                return false;
            }
        };
    }

    public Group[] groups() {
        return groups;
    }

    /**
     * @return A predicate that will match all the indices that this permission
     * has the privilege for executing the given action on.
     */
    public Predicate<String> allowedIndicesMatcher(String action) {
        return allowedIndicesMatchersForAction.computeIfAbsent(action, (theAction) -> {
            List<String> indices = new ArrayList<>();
            for (Group group : groups) {
                if (group.check(theAction)) {
                    indices.addAll(Arrays.asList(group.indices));
                }
            }
            return indexMatcherPredicate(indices);
        });
    }

    /**
     * Checks if the permission matches the provided action, without looking at indices.
     * To be used in very specific cases where indices actions need to be authorized regardless of their indices.
     * The usecase for this is composite actions that are initially only authorized based on the action name (indices are not
     * checked on the coordinating node), and properly authorized later at the shard level checking their indices as well.
     */
    public boolean check(String action) {
        for (Group group : groups) {
            if (group.check(action)) {
                return true;
            }
        }
        return false;
    }

    public Automaton allowedActionsMatcher(String index) {
        List<Automaton> automatonList = new ArrayList<>();
        for (Group group : groups) {
            if (group.indexNameMatcher.test(index)) {
                automatonList.add(group.privilege.getAutomaton());
            }
        }
        return automatonList.isEmpty() ? Automatons.EMPTY : Automatons.unionAndMinimize(automatonList);
    }

    public boolean testIndexMatch(String checkIndex, String checkPrivilegeName) {
        final Automaton checkIndexAutomaton = indexMatcherAutomaton(checkIndex);
        final List<Automaton> privilegeAutomatons = new ArrayList<>();
        for (IndicesPermission.Group group : groups) {
            final Automaton groupIndexAutomaton = indexGroupAutomatonCache.computeIfAbsent(group,
                    theGroup -> indexMatcherAutomaton(theGroup.indices()));
            if (Operations.subsetOf(checkIndexAutomaton, groupIndexAutomaton)) {
                final IndexPrivilege rolePrivilege = group.privilege();
                if (rolePrivilege.name().contains(checkPrivilegeName)) {
                    return true;
                }
                privilegeAutomatons.add(rolePrivilege.getAutomaton());
            }
        }
        final IndexPrivilege checkPrivilege = IndexPrivilege.get(Collections.singleton(checkPrivilegeName));
        return Operations.subsetOf(checkPrivilege.getAutomaton(), Automatons.unionAndMinimize(privilegeAutomatons));
    }

    /**
     * Authorizes the provided action against the provided indices, given the current cluster metadata
     */
    public Map<String, IndicesAccessControl.IndexAccessControl> authorize(String action, Set<String> requestedIndicesOrAliases,
                                                                          MetaData metaData, FieldPermissionsCache fieldPermissionsCache) {
        // now... every index that is associated with the request, must be granted
        // by at least one indices permission group

        SortedMap<String, AliasOrIndex> allAliasesAndIndices = metaData.getAliasAndIndexLookup();
        Map<String, Set<FieldPermissions>> fieldPermissionsByIndex = new HashMap<>();
        Map<String, DocumentLevelPermissions> roleQueriesByIndex = new HashMap<>();
        Map<String, Boolean> grantedBuilder = new HashMap<>();

        for (String indexOrAlias : requestedIndicesOrAliases) {
            boolean granted = false;
            Set<String> concreteIndices = new HashSet<>();
            AliasOrIndex aliasOrIndex = allAliasesAndIndices.get(indexOrAlias);
            if (aliasOrIndex != null) {
                for (IndexMetaData indexMetaData : aliasOrIndex.getIndices()) {
                    concreteIndices.add(indexMetaData.getIndex().getName());
                }
            }

            for (Group group : groups) {
                if (group.check(action, indexOrAlias)) {
                    granted = true;
                    for (String index : concreteIndices) {
                        Set<FieldPermissions> fieldPermissions = fieldPermissionsByIndex.computeIfAbsent(index, (k) -> new HashSet<>());
                        fieldPermissionsByIndex.put(indexOrAlias, fieldPermissions);
                        fieldPermissions.add(group.getFieldPermissions());
                        DocumentLevelPermissions permissions =
                                roleQueriesByIndex.computeIfAbsent(index, (k) -> new DocumentLevelPermissions());
                        roleQueriesByIndex.putIfAbsent(indexOrAlias, permissions);
                        if (group.hasQuery()) {
                            permissions.addAll(group.getQuery());
                        } else {
                            // if more than one permission matches for a concrete index here and if
                            // a single permission doesn't have a role query then DLS will not be
                            // applied even when other permissions do have a role query
                            permissions.setAllowAll(true);
                        }
                    }
                }
            }

            if (concreteIndices.isEmpty()) {
                grantedBuilder.put(indexOrAlias, granted);
            } else {
                grantedBuilder.put(indexOrAlias, granted);
                for (String concreteIndex : concreteIndices) {
                    grantedBuilder.put(concreteIndex, granted);
                }
            }
        }

        Map<String, IndicesAccessControl.IndexAccessControl> indexPermissions = new HashMap<>();
        for (Map.Entry<String, Boolean> entry : grantedBuilder.entrySet()) {
            String index = entry.getKey();
            DocumentLevelPermissions permissions = roleQueriesByIndex.get(index);
            final Set<BytesReference> roleQueries;
            if (permissions != null && permissions.isAllowAll() == false) {
                roleQueries = unmodifiableSet(permissions.queries);
            } else {
                roleQueries = null;
            }

            final FieldPermissions fieldPermissions;
            final Set<FieldPermissions> indexFieldPermissions = fieldPermissionsByIndex.get(index);
            if (indexFieldPermissions != null && indexFieldPermissions.isEmpty() == false) {
                fieldPermissions = indexFieldPermissions.size() == 1 ? indexFieldPermissions.iterator().next() :
                        fieldPermissionsCache.getFieldPermissions(indexFieldPermissions);
            } else {
                fieldPermissions = FieldPermissions.DEFAULT;
            }
            indexPermissions.put(index, new IndicesAccessControl.IndexAccessControl(entry.getValue(), fieldPermissions, roleQueries));
        }
        return unmodifiableMap(indexPermissions);
    }

    private static boolean isIndexPattern(String indexPattern) {
        return indexPattern.startsWith("/") || indexPattern.contains("*") || indexPattern.contains("?");
    }

    private static boolean isSystemIndex(String index) {
        return SystemIndicesNames.indexNames().contains(index);
    }

    public static class Group {
        private final IndexPrivilege privilege;
        private final Predicate<String> actionMatcher;
        private final String[] indices;
        private final Predicate<String> indexNameMatcher;
        private final FieldPermissions fieldPermissions;
        private final Set<BytesReference> query;

        public Group(IndexPrivilege privilege, FieldPermissions fieldPermissions, @Nullable Set<BytesReference> query, String... indices) {
            assert indices.length != 0;
            this.privilege = privilege;
            this.actionMatcher = privilege.predicate();
            this.indices = indices;
            this.indexNameMatcher = indexMatcherPredicate(Arrays.asList(indices));
            this.fieldPermissions = Objects.requireNonNull(fieldPermissions);
            this.query = query;
        }

        private IndexPrivilege privilege() {
            return privilege;
        }

        public Set<String> privilegeName() {
            return privilege.name();
        }

        public String[] indices() {
            return indices;
        }

        public FieldPermissions getFieldPermissions() {
            return fieldPermissions;
        }

        @Nullable
        public Set<BytesReference> getQuery() {
            return query;
        }

        private boolean check(String action) {
            return actionMatcher.test(action);
        }

        private boolean check(String action, String index) {
            assert index != null;
            return check(action) && indexNameMatcher.test(index);
        }

        boolean hasQuery() {
            return query != null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Group that = (Group) o;

            return Objects.equals(privilege, that.privilege())
                    && Arrays.equals(indices, that.indices())
                    && Objects.equals(fieldPermissions, that.fieldPermissions)
                    && Objects.equals(query, that.query);
        }

        @Override
        public int hashCode() {
            return Objects.hash(privilege, indices, fieldPermissions, query);
        }
    }

    private static class DocumentLevelPermissions {

        private Set<BytesReference> queries = null;
        private boolean allowAll = false;

        private void addAll(Set<BytesReference> query) {
            if (allowAll == false) {
                if (queries == null) {
                    queries = new HashSet<>();
                }
                queries.addAll(query);
            }
        }

        private boolean isAllowAll() {
            return allowAll;
        }

        private void setAllowAll(boolean allowAll) {
            this.allowAll = allowAll;
        }
    }
}
