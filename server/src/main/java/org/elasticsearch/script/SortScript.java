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

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Scorer;
import org.elasticsearch.index.fielddata.ScriptDocValues;
import org.elasticsearch.search.lookup.LeafSearchLookup;
import org.elasticsearch.search.lookup.SearchLookup;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.function.DoubleSupplier;

/**
 * A script used for computing sort values on a per document basis for {@link org.elasticsearch.search.sort.ScriptSortBuilder}.
 */
public abstract class SortScript {
    
    public static final String[] PARAMETERS = {};
    
    private final Map<String, Object> params;
    private final LeafSearchLookup leafLookup;
    private DoubleSupplier scoreSupplier = () -> 0.0;
    
    public SortScript(Map<String, Object> params, SearchLookup lookup, LeafReaderContext leafContext) {
        this.params = params;
        this.leafLookup = lookup.getLeafSearchLookup(leafContext);
    }
    
    // String script sort returns a string
    public abstract Object execute();
    
    // Number script sort returns a double
    public double executeAsDouble() {
        return ((Number) execute()).doubleValue();
    }
    
    // TODO: Maybe have two sort script classes? (StringSortScript and NumberScriptSort)
    
    public Map<String, Object> getParams() {
        return params;
    }
    
    public final Map<String, ScriptDocValues<?>> getDoc() {
        return leafLookup.doc();
    }
    
    public void setDocument(int docid) {
        leafLookup.setDocument(docid);
    }
    
    // TODO: should _score be accessible in sort scripts? Maybe deprecate and remove?
    // (if sorting by _score is required then ScoreSortBuilder can be used)
    public void setScorer(Scorer scorer) {
        this.scoreSupplier = () -> {
            try {
                return scorer.score();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }
    
    public double get_score() {
        return scoreSupplier.getAsDouble();
    }
    
    public interface LeafFactory {
        SortScript newInstance(LeafReaderContext ctx) throws IOException;
    }
    
    public interface Factory {
        SortScript.LeafFactory newFactory(Map<String, Object> params, SearchLookup lookup);
    }
    
    public static final ScriptContext<SortScript.Factory> CONTEXT = new ScriptContext<>("sort", SortScript.Factory.class);
}
