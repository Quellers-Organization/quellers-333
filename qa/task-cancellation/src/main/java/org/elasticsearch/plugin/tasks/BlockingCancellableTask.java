/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.plugin.tasks;

import org.apache.lucene.util.SetOnce;
import org.elasticsearch.tasks.CancellableTask;
import org.elasticsearch.tasks.TaskId;

import java.util.Map;

final class BlockingCancellableTask extends CancellableTask {
    private final SetOnce<Runnable> onCancel = new SetOnce<>();

    BlockingCancellableTask(long id, String type, String action, String description, TaskId parentTaskId, Map<String, String> headers) {
        super(id, type, action, description, parentTaskId, headers);
    }

    void setOnCancel(Runnable onCancel) {
        this.onCancel.set(onCancel);
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        if (onCancel.get() != null) {
            onCancel.get().run();
        }
    }

    @Override
    public boolean isCancelled() {
        return super.isCancelled();
    }

    @Override
    public boolean shouldCancelChildrenOnCancellation() {
        return true;
    }
}
