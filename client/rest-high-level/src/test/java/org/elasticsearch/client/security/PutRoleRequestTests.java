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

package org.elasticsearch.client.security;

import org.elasticsearch.client.SecurityIT;
import org.elasticsearch.client.security.user.privileges.ApplicationResourcePrivileges;
import org.elasticsearch.client.security.user.privileges.ApplicationResourcePrivilegesTests;
import org.elasticsearch.client.security.user.privileges.GlobalOperationPrivilege;
import org.elasticsearch.client.security.user.privileges.GlobalPrivileges;
import org.elasticsearch.client.security.user.privileges.GlobalPrivilegesTests;
import org.elasticsearch.client.security.user.privileges.IndicesPrivileges;
import org.elasticsearch.client.security.user.privileges.IndicesPrivilegesTests;
import org.elasticsearch.client.security.user.privileges.Role;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.test.AbstractXContentTestCase;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class PutRoleRequestTests extends AbstractXContentTestCase<PutRoleRequest> {

    private static final String roleName = "testRoleName";

    @Override
    protected PutRoleRequest createTestInstance() {
        final Role role = randomRole(roleName);
        return new PutRoleRequest(role, null);
    }

    @Override
    protected PutRoleRequest doParseInstance(XContentParser parser) throws IOException {
        return new PutRoleRequest(Role.fromXContent(parser, roleName), null);
    }

    @Override
    protected boolean supportsUnknownFields() {
        return false;
    }

    private static final Role randomRole(String roleName) {
        final Role.Builder roleBuilder = Role.builder().name(roleName)
                .clusterPrivileges(randomSubsetOf(randomInt(3), Role.ClusterPrivilegeName.ARRAY))
                .indicesPrivileges(
                        randomArray(3, IndicesPrivileges[]::new, () -> IndicesPrivilegesTests.createNewRandom(randomAlphaOfLength(3))))
                .applicationResourcePrivileges(randomArray(3, ApplicationResourcePrivileges[]::new,
                        () -> ApplicationResourcePrivilegesTests.createNewRandom(randomAlphaOfLength(3).toLowerCase())))
                .runAsPrivilege(randomArray(3, String[]::new, () -> randomAlphaOfLength(3)));
        if (randomBoolean()) {
            roleBuilder.globalApplicationPrivileges(new GlobalPrivileges(Arrays.asList(
                    randomArray(1, 3, GlobalOperationPrivilege[]::new, () -> GlobalPrivilegesTests.buildRandomGlobalScopedPrivilege()))));
        }
        if (randomBoolean()) {
            final Map<String, Object> metadata = new HashMap<>();
            for (int i = 0; i < randomInt(3); i++) {
                metadata.put(randomAlphaOfLength(3), randomAlphaOfLength(3));
            }
            roleBuilder.metadata(metadata);
        }
        if (randomBoolean()) {
            final Map<String, Object> transientMetadata = new HashMap<>();
            for (int i = 0; i < randomInt(3); i++) {
                transientMetadata.put(randomAlphaOfLength(3), randomAlphaOfLength(3));
            }
            roleBuilder.metadata(transientMetadata);
        }
        return roleBuilder.build();
    }

}
