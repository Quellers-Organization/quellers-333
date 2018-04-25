/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.core.graph.action;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ToXContentFragment;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

/**
 * A vertex in a graph response represents a single term (a field and value pair)
 * which appears in one or more documents found as part of the graph exploration.
 * 
 * A vertex term could be a bank account number, an email address, a hashtag or any 
 * other term that appears in documents and is interesting to represent in a network.  
 */
public class Vertex implements ToXContentFragment {

    private final String field;
    private final String term;
    private double weight;
    private final int depth;
    private final long bg;
    private long fg;

    public Vertex(String field, String term, double weight, int depth, long bg, long fg) {
        super();
        this.field = field;
        this.term = term;
        this.weight = weight;
        this.depth = depth;
        this.bg = bg;
        this.fg = fg;
    }

    static Vertex readFrom(StreamInput in) throws IOException {
        return new Vertex(in.readString(), in.readString(), in.readDouble(), in.readVInt(), in.readVLong(), in.readVLong());
    }

    void writeTo(StreamOutput out) throws IOException {
        out.writeString(field);
        out.writeString(term);
        out.writeDouble(weight);
        out.writeVInt(depth);
        out.writeVLong(bg);
        out.writeVLong(fg);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        boolean returnDetailedInfo = params.paramAsBoolean(GraphExploreResponse.RETURN_DETAILED_INFO_PARAM, false);
        builder.field("field", field);
        builder.field("term", term);
        builder.field("weight", weight);
        builder.field("depth", depth);
        if (returnDetailedInfo) {
            builder.field("fg", fg);
            builder.field("bg", bg);
        }
        return builder;
    }

    /**
     * @return a {@link VertexId} object that uniquely identifies this Vertex
     */
    public VertexId getId() {
        return createId(field, term);
    }

    /**
     * A convenience method for creating a {@link VertexId}
     * @param field the field
     * @param term the term
     * @return a {@link VertexId} that can be used for looking up vertices
     */
    public static VertexId createId(String field, String term) {
        return new VertexId(field,term);
    }

    @Override
    public String toString() {
        return getId().toString();
    }

    public String getField() {
        return field;
    }

    public String getTerm() {
        return term;
    }

    /**
     * The weight of a vertex is an accumulation of all of the {@link Connection}s
     * that are linked to this {@link Vertex} as part of a graph exploration.
     * It is used internally to identify the most interesting vertices to be returned.
     * @return a measure of the {@link Vertex}'s relative importance.
     */
    public double getWeight() {
        return weight;
    }

    public void setWeight(final double weight) {
        this.weight = weight;
    }

    /**
     * If the {@link GraphExploreRequest#useSignificance(boolean)} is true (the default)
     * this statistic is available. 
     * @return the number of documents in the index that contain this term (see bg_count in 
 * <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-bucket-significantterms-aggregation.html">
     * the significant_terms aggregation</a>) 
     */
    public long getBg() {
        return bg;
    }

    /**
     * If the {@link GraphExploreRequest#useSignificance(boolean)} is true (the default) 
     * this statistic is available. 
     * Together with {@link #getBg()} these numbers are used to derive the significance of a term.
     * @return the number of documents in the sample of best matching documents that contain this term (see fg_count in 
 * <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-bucket-significantterms-aggregation.html">
     * the significant_terms aggregation</a>) 
     */
    public long getFg() {
        return fg;
    }

    public void setFg(final long fg) {
        this.fg = fg;
    }

    /**
     * @return the sequence number in the series of hops where this Vertex term was first encountered
     */
    public int getHopDepth() {
        return depth;
    }
    
    /**
     * An identifier (implements hashcode and equals) that represents a
     * unique key for a {@link Vertex}
     */
    public static class VertexId {
        private final String field;
        private final String term;

        public VertexId(String field, String term) {
            this.field = field;
            this.term = term;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            VertexId vertexId = (VertexId) o;

            if (field != null ? !field.equals(vertexId.field) : vertexId.field != null)
                return false;
            if (term != null ? !term.equals(vertexId.term) : vertexId.term != null)
                return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = field != null ? field.hashCode() : 0;
            result = 31 * result + (term != null ? term.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return field + ":" + term;
        }
    }    

}
