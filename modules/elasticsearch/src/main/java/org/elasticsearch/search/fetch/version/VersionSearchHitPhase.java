/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.fetch.version;

import org.apache.lucene.index.Term;
import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.common.collect.ImmutableMap;
import org.elasticsearch.common.lucene.uid.UidField;
import org.elasticsearch.index.mapper.UidFieldMapper;
import org.elasticsearch.search.SearchParseElement;
import org.elasticsearch.search.fetch.SearchHitPhase;
import org.elasticsearch.search.internal.SearchContext;

import java.util.Map;

/**
 * @author kimchy (shay.banon)
 */
public class VersionSearchHitPhase implements SearchHitPhase {

    @Override public Map<String, ? extends SearchParseElement> parseElements() {
        return ImmutableMap.of();
    }

    @Override public boolean executionNeeded(SearchContext context) {
        return true;
    }

    @Override public void execute(SearchContext context, HitContext hitContext) throws ElasticSearchException {
        long version = UidField.loadVersion(hitContext.reader(), new Term(UidFieldMapper.NAME, hitContext.doc().get(UidFieldMapper.NAME)));
        if (version < 0) {
            version = -1;
        }
        hitContext.hit().version(version);
    }
}
