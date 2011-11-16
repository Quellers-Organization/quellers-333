/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
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
package org.elasticsearch.index;

import org.elasticsearch.ElasticSearchIllegalArgumentException;

/**
 *
 */
public enum VersionType {
    INTERNAL((byte) 0),
    EXTERNAL((byte) 1),
    EXTERNAL_QUIET((byte) 2);

    private final byte value;

    VersionType(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }

    public static VersionType fromString(String versionType) {
        if ("internal".equals(versionType)) {
            return INTERNAL;
        } else if ("external".equals(versionType)) {
            return EXTERNAL;
        } else if ("external_quiet".equals(versionType)) {
            return EXTERNAL_QUIET;
        }
        throw new ElasticSearchIllegalArgumentException("No version type match [" + versionType + "]");
    }

    public static VersionType fromString(String versionType, VersionType defaultVersionType) {
        if (versionType == null) {
            return defaultVersionType;
        }
        if ("internal".equals(versionType)) {
            return INTERNAL;
        } else if ("external".equals(versionType)) {
            return EXTERNAL;
        } else if ("external_quiet".equals(versionType)) {
            return EXTERNAL_QUIET;
        }
        throw new ElasticSearchIllegalArgumentException("No version type match [" + versionType + "]");
    }

    public static VersionType fromValue(byte value) {
        if (value == 0) {
            return INTERNAL;
        } else if (value == 1) {
            return EXTERNAL;
        } else if (value == 2) {
            return EXTERNAL_QUIET;
        }
        throw new ElasticSearchIllegalArgumentException("No version type match [" + value + "]");
    }
}