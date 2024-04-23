/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */
package org.elasticsearch;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.rest.RestStatus;

import java.io.IOException;

public class ResourceAlreadyUploadedException extends ResourceNotFoundException {

    public ResourceAlreadyUploadedException(String msg, Object... args) {
        super(msg, args);
    }

    public ResourceAlreadyUploadedException(StreamInput in) throws IOException {
        super(in);
    }

}
