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

package org.elasticsearch.common.lucene.index;

import com.google.common.collect.Lists;
import org.apache.lucene.index.*;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Filter;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.ElasticsearchIllegalArgumentException;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.lucene.docset.DocIdSets;
import org.elasticsearch.common.lucene.search.ApplyAcceptedDocsFilter;
import org.elasticsearch.common.lucene.search.Queries;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/**
 * A frequency TermsEnum that returns frequencies derived from a collection of cached leaf termEnums. 
 * It also allows to provide a filter to explicitly compute frequencies only for docs that match the filter (heavier!).
 */
/**
 *
 */
public class FilterableTermsEnum extends TermsEnum  {

    static class Holder {
        final TermsEnum termsEnum;
        @Nullable
        DocsEnum docsEnum;
        @Nullable
        final Bits bits;

        Holder(TermsEnum termsEnum, Bits bits) {
            this.termsEnum = termsEnum;
            this.bits = bits;
        }
    }
    protected final static int NOT_FOUND = -2;
    private final Holder[] enums;
    protected int currentDocFreq = 0;
    protected long currentTotalTermFreq = 0;
    protected BytesRef current;
    protected boolean needDocFreqs;
    protected boolean needTotalTermFreqs;
    protected int numDocs;

    public FilterableTermsEnum(IndexReader reader, String field, boolean needDocFreq, boolean needTotalTermFreq, @Nullable Filter filter) throws IOException {
        if (!needDocFreq && !needTotalTermFreq) {
            throw new ElasticsearchIllegalArgumentException("either needDocFreq or needTotalTermFreq must be true");
        }        
        this.needDocFreqs = needDocFreq;
        this.needTotalTermFreqs = needTotalTermFreq;
        if (filter == null) {
            numDocs = reader.numDocs();
        }
        
        List<AtomicReaderContext> leaves = reader.leaves();
        List<Holder> enums = Lists.newArrayListWithExpectedSize(leaves.size());
        for (AtomicReaderContext context : leaves) {
            Terms terms = context.reader().terms(field);
            if (terms == null) {
                continue;
            }
            TermsEnum termsEnum = terms.iterator(null);
            if (termsEnum == null) {
                continue;
            }
            Bits bits = null;
            if (filter != null) {
                if (filter == Queries.MATCH_ALL_FILTER) {
                    bits = context.reader().getLiveDocs();
                } else {
                    // we want to force apply deleted docs
                    filter = new ApplyAcceptedDocsFilter(filter);
                    DocIdSet docIdSet = filter.getDocIdSet(context, context.reader().getLiveDocs());
                    if (DocIdSets.isEmpty(docIdSet)) {
                        // fully filtered, none matching, no need to iterate on this
                        continue;
                    }
                    bits = DocIdSets.toSafeBits(context.reader(), docIdSet);
                    // Count how many docs are in our filtered set 
                    // TODO make this lazy-loaded only for those that need it?
                    DocIdSetIterator iterator = docIdSet.iterator();
                    if (iterator != null) {
                        while (iterator.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                            numDocs++;
                        }
                    }
                }
            }
            enums.add(new Holder(termsEnum, bits));
        }
        this.enums = enums.toArray(new Holder[enums.size()]);
    }
    
    public int getNumDocs() {
        return numDocs;
    }

    @Override
    public BytesRef term() throws IOException {
        return current;
    }

    @Override
    public boolean seekExact(BytesRef text) throws IOException {
        boolean found = false;
        int docFreq = 0;
        long totalTermFreq = 0;
        boolean hasMissingTotalTermFreq = false;
        for (Holder anEnum : enums) {
            if (!anEnum.termsEnum.seekExact(text)) {
                continue;
            }
            found = true;
            if (anEnum.bits == null) {
                if (needDocFreqs) {
                    docFreq += anEnum.termsEnum.docFreq();
                }
                if (needTotalTermFreqs) {
                    long leafTotalTermFreq = anEnum.termsEnum.totalTermFreq();
                    if (leafTotalTermFreq < 0) {
                        hasMissingTotalTermFreq = true;
                    } else {
                        totalTermFreq += leafTotalTermFreq;
                    }
                }
            } else {
                DocsEnum docsEnum = anEnum.docsEnum = anEnum.termsEnum.docs(anEnum.bits, anEnum.docsEnum, needTotalTermFreqs ? DocsEnum.FLAG_FREQS : DocsEnum.FLAG_NONE);
                for (int docId = docsEnum.nextDoc(); docId != DocIdSetIterator.NO_MORE_DOCS; docId = docsEnum.nextDoc()) {
                    docFreq++;
                    if (needTotalTermFreqs) {
                        totalTermFreq += docsEnum.freq();
                    }
                }
            }
        }

        current = found ? text : null;
        if(found){
            currentDocFreq = docFreq;
            if (hasMissingTotalTermFreq) {
                // at least one of the segments has no freqs info so declare result as
                // missing rather than using partial info
                currentTotalTermFreq = -1;
            }else{
                currentTotalTermFreq = totalTermFreq;
            }
        }else{
            currentDocFreq = NOT_FOUND;
            currentTotalTermFreq = NOT_FOUND;
        } 

        return found;
    }

    @Override
    public int docFreq() throws IOException {
        return currentDocFreq;
    }

    @Override
    public long totalTermFreq() throws IOException {
        return currentTotalTermFreq;
    }

    @Override
    public void seekExact(long ord) throws IOException {
        throw new UnsupportedOperationException("freq terms enum");
    }

    @Override
    public SeekStatus seekCeil(BytesRef text) throws IOException {
        throw new UnsupportedOperationException("freq terms enum");
    }

    @Override
    public long ord() throws IOException {
        throw new UnsupportedOperationException("freq terms enum");
    }

    @Override
    public DocsEnum docs(Bits liveDocs, DocsEnum reuse, int flags) throws IOException {
        throw new UnsupportedOperationException("freq terms enum");
    }

    @Override
    public DocsAndPositionsEnum docsAndPositions(Bits liveDocs, DocsAndPositionsEnum reuse, int flags) throws IOException {
        throw new UnsupportedOperationException("freq terms enum");
    }

    @Override
    public BytesRef next() throws IOException {
        throw new UnsupportedOperationException("freq terms enum");
    }

    @Override
    public Comparator<BytesRef> getComparator() {
        throw new UnsupportedOperationException("freq terms enum");
    }
}