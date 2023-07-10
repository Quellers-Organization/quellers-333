/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.synonyms;

import org.elasticsearch.indices.AbstractSystemIndexFormatVersionTests;
import org.elasticsearch.indices.SystemIndexDescriptor;

import java.util.Collection;
import java.util.List;

public class SynonymsSystemIndexTests extends AbstractSystemIndexFormatVersionTests {

    @Override
    public Collection<SystemIndexDescriptor> getSystemIndexDescriptors() {
        return List.of(SynonymsManagementAPIService.SYNONYMS_DESCRIPTOR);
    }
}
