/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.security.authz;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.IndicesRequest;
import org.elasticsearch.transport.RemoteConnectionManager;
import org.elasticsearch.transport.Transport;
import org.elasticsearch.transport.TransportRequest;
import org.elasticsearch.xpack.core.security.SecurityContext;
import org.elasticsearch.xpack.core.security.authz.AuthorizationEngine;
import org.elasticsearch.xpack.core.security.authz.accesscontrol.IndicesAccessControl;
import org.elasticsearch.xpack.core.security.authz.permission.Role;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class PreAuthorizationUtil {

    private static final Logger logger = LogManager.getLogger(PreAuthorizationUtil.class);

    /**
     * This map holds parent-child action relationships for which we can optimize authorization
     * and skip authorization for child actions if the parent action is successfully authorized.
     * Normally every action would be authorized on a local node on which it's being executed.
     * Here we define all child actions for which the authorization can be safely skipped
     * on a remote node as they only access a subset of resources.
     */
    public static final Map<String, Set<String>> CHILD_ACTIONS_PRE_AUTHORIZED_BY_PARENT = Map.of(
        "indices:data/read/search",
        Set.of(
            "indices:data/read/search[free_context]",
            "indices:data/read/search[phase/dfs]",
            "indices:data/read/search[phase/query]",
            "indices:data/read/search[phase/query/id]",
            "indices:data/read/search[phase/fetch/id]",
            "indices:data/read/search[can_match]",
            "indices:data/read/search[can_match][n]"
        )
    );

    /**
     * This method sets {@link AuthorizationEngine.ParentActionAuthorization} as a header in the thread context,
     * which will be used for skipping authorization of child actions if the following conditions are met:
     *
     * <ul>
     * <li>parent action is one of the white listed in {@link #CHILD_ACTIONS_PRE_AUTHORIZED_BY_PARENT}</li>
     * <li>FLS and DLS are not configured</li>
     * <li>RBACEngine was used to authorize parent request and not a custom authorization engine</li>
     * </ul>
     */
    public static void maybeSkipChildrenActionAuthorization(
        SecurityContext securityContext,
        TransportRequest parentRequest,
        AuthorizationEngine.AuthorizationContext parentAuthorizationContext
    ) {
        final String parentAction = parentAuthorizationContext.getAction();
        if (CHILD_ACTIONS_PRE_AUTHORIZED_BY_PARENT.containsKey(parentAction) == false) {
            // This is not one of the white listed parent actions.
            return;
        }

        final IndicesAccessControl indicesAccessControl = parentAuthorizationContext.getIndicesAccessControl();
        if (indicesAccessControl == null) {
            // This can happen if the parent request was authorized by index name only - e.g. bulk request
            // A missing IAC is not an error, but it means we can't safely tie authz of the child action to the parent authz
            return;
        }

        // Just a sanity check. If we ended up here, the authz should have been granted.
        if (indicesAccessControl.isGranted() == false) {
            return;
        }

        if (parentRequest instanceof IndicesRequest == false) {
            // We can only pre-authorize indices request.
            return;
        }

        final Role role = RBACEngine.maybeGetRBACEngineRole(parentAuthorizationContext.getAuthorizationInfo());
        if (role == null) {
            // If role is null, it means a custom authorization engine is in use, hence we cannot do the optimization here.
            return;
        }

        // We can't safely pre-authorize actions if DLS or FLS is configured without passing IAC as well with the authorization result.
        // For simplicity, we only pre-authorize actions when FLS and DLS are not configured, otherwise we would have to compute and send
        // indices access control as well, which we want to avoid with this optimization.
        if (role.hasFieldOrDocumentLevelSecurity()) {
            return;
        }

        final Optional<AuthorizationEngine.ParentActionAuthorization> existingParentAuthorization = Optional.ofNullable(
            securityContext.getParentAuthorization()
        );
        if (existingParentAuthorization.isPresent()) {
            if (existingParentAuthorization.get().action().equals(parentAction)) {
                // Single request can fan-out a child action to multiple nodes in the cluster, e.g. node1 and node2.
                // Sending a child action to node1 would have already put parent authorization in the thread context.
                // To avoid attempting to pre-authorize the same parent action twice we simply return here
                // since parent authorization is already set in the thread context.
                if (logger.isDebugEnabled()) {
                    logger.debug("authorization for parent action [" + parentAction + "] is already set in the thread context");
                }
            } else {
                throw new AssertionError(
                    "found parent authorization for action ["
                        + existingParentAuthorization.get().action()
                        + "] while attempting to set authorization for new parent action ["
                        + parentAction
                        + "]"
                );
            }
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("adding authorization for parent action [" + parentAction + "] to the thread context");
            }
            securityContext.setParentAuthorization(new AuthorizationEngine.ParentActionAuthorization(parentAction));
        }
    }

    public static boolean shouldPreAuthorizeChildByParentAction(final String parent, final String child) {
        final Set<String> children = CHILD_ACTIONS_PRE_AUTHORIZED_BY_PARENT.get(parent);
        return children != null && (parent.equals(child) || children.contains(child));
    }

    public static boolean shouldRemoveParentAuthorizationFromThreadContext(
        Transport.Connection connection,
        String childAction,
        SecurityContext securityContext
    ) {
        final AuthorizationEngine.ParentActionAuthorization parentAuthorization = securityContext.getParentAuthorization();
        if (parentAuthorization == null) {
            // Nothing to remove.
            return false;
        }

        if (RemoteConnectionManager.resolveRemoteClusterAlias(connection).isPresent()) {
            // We never want to send the parent authorization header to remote clusters.
            return true;
        }

        if (shouldPreAuthorizeChildByParentAction(parentAuthorization.action(), childAction) == false) {
            // We want to remove the parent authorization header if the child action is not one of the white listed.
            return true;
        }

        return false;
    }

}
