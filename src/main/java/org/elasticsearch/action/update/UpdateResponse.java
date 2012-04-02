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

package org.elasticsearch.action.update;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.index.get.GetField;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Maps.newHashMapWithExpectedSize;
import static org.elasticsearch.index.get.GetField.readGetField;

/**
 */
public class UpdateResponse implements ActionResponse {

    private String index;

    private String id;

    private String type;

    private long version;

    private List<String> matches;

    private Map<String, GetField> fields;

    public UpdateResponse() {

    }

    public UpdateResponse(String index, String type, String id, long version) {
        this.index = index;
        this.id = id;
        this.type = type;
        this.version = version;
    }

    /**
     * The index the document was indexed into.
     */
    public String index() {
        return this.index;
    }

    /**
     * The index the document was indexed into.
     */
    public String getIndex() {
        return index;
    }

    /**
     * The type of the document indexed.
     */
    public String type() {
        return this.type;
    }

    /**
     * The type of the document indexed.
     */
    public String getType() {
        return type;
    }

    /**
     * The id of the document indexed.
     */
    public String id() {
        return this.id;
    }

    /**
     * The id of the document indexed.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the version of the doc indexed.
     */
    public long version() {
        return this.version;
    }

    /**
     * Returns the version of the doc indexed.
     */
    public long getVersion() {
        return version();
    }

    /**
     * Returns the percolate queries matches. <tt>null</tt> if no percolation was requested.
     */
    public List<String> matches() {
        return this.matches;
    }

    /**
     * Returns the percolate queries matches. <tt>null</tt> if no percolation was requested.
     */
    public List<String> getMatches() {
        return this.matches;
    }

    /**
     * Internal.
     */
    public void fields(Map<String, GetField> fields) {
        this.fields = fields;
    }

    /**
     * Returns extracted fields from updated source. <tt>null</tt> if no field was requested.
     */
    public Map<String, GetField> fields() {
        return this.fields;
    }

    /**
     * Returns extracted fields from updated source. <tt>null</tt> if no field was requested.
     */
    public Map<String, GetField> getFields() {
        return this.fields;
    }

    /**
     * Internal.
     */
    public void matches(List<String> matches) {
        this.matches = matches;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        index = in.readUTF();
        id = in.readUTF();
        type = in.readUTF();
        version = in.readLong();
        if (in.readBoolean()) {
            int size = in.readVInt();
            if (size == 0) {
                matches = ImmutableList.of();
            } else if (size == 1) {
                matches = ImmutableList.of(in.readUTF());
            } else if (size == 2) {
                matches = ImmutableList.of(in.readUTF(), in.readUTF());
            } else if (size == 3) {
                matches = ImmutableList.of(in.readUTF(), in.readUTF(), in.readUTF());
            } else if (size == 4) {
                matches = ImmutableList.of(in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF());
            } else if (size == 5) {
                matches = ImmutableList.of(in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF());
            } else {
                matches = new ArrayList<String>();
                for (int i = 0; i < size; i++) {
                    matches.add(in.readUTF());
                }
            }
        }
        int size = in.readVInt();
        if (size == 0) {
            fields = ImmutableMap.of();
        } else {
            fields = newHashMapWithExpectedSize(size);
            for (int i = 0; i < size; i++) {
                GetField field = readGetField(in);
                fields.put(field.name(), field);
            }
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeUTF(index);
        out.writeUTF(id);
        out.writeUTF(type);
        out.writeLong(version);
        if (matches == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            out.writeVInt(matches.size());
            for (String match : matches) {
                out.writeUTF(match);
            }
        }
        if (fields == null) {
            out.writeVInt(0);
        } else {
            out.writeVInt(fields.size());
            for (GetField field : fields.values()) {
                field.writeTo(out);
            }
        }
    }
}
