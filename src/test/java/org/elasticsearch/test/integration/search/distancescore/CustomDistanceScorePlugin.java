/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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
package org.elasticsearch.test.integration.search.distancescore;

import org.elasticsearch.index.query.distancescoring.DistanceScoringModule;
import org.elasticsearch.plugins.AbstractPlugin;

/**
 *
 */
public class CustomDistanceScorePlugin extends AbstractPlugin {

    @Override
    public String name() {
        return "test-plugin-distance-score";
    }

    @Override
    public String description() {
        return "Distance score plugin to test pluggable implementation";
    }

    public void onModule(DistanceScoringModule scoreModule) {
        scoreModule.registerParser(CustomDistanceScoreParser.class);
    }

}
