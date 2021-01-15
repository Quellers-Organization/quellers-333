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

package org.elasticsearch.gradle.release;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ChangelogEntry {
    private int pr;
    private List<Integer> issues;
    private String area;
    private String type;
    private String summary;
    private Highlight highlight;
    private Breaking breaking;
    private List<String> versions;

    public int getPr() {
        return pr;
    }

    public void setPr(int pr) {
        this.pr = pr;
    }

    public List<Integer> getIssues() {
        return issues;
    }

    public void setIssues(List<Integer> issues) {
        this.issues = issues;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Highlight getHighlight() {
        return highlight;
    }

    public void setHighlight(Highlight highlight) {
        this.highlight = highlight;
    }

    public Breaking getBreaking() {
        return breaking;
    }

    public void setBreaking(Breaking breaking) {
        this.breaking = breaking;
    }

    public List<String> getVersions() {
        return versions;
    }

    public void setVersions(List<String> versions) {
        this.versions = versions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ChangelogEntry that = (ChangelogEntry) o;
        return pr == that.pr
            && Objects.equals(issues, that.issues)
            && Objects.equals(area, that.area)
            && Objects.equals(type, that.type)
            && Objects.equals(summary, that.summary)
            && Objects.equals(highlight, that.highlight)
            && Objects.equals(breaking, that.breaking)
            && Objects.equals(versions, that.versions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pr, issues, area, type, summary, highlight, breaking, versions);
    }

    @Override
    public String toString() {
        return String.format(
            Locale.ROOT,
            "ChangelogEntry{pr=%d, issues=%s, area='%s', type='%s', summary='%s', highlight=%s, breaking=%s, versions=%s}",
            pr,
            issues,
            area,
            type,
            summary,
            highlight,
            breaking,
            versions
        );
    }

    public void validate() {
        // TODO: check that this object is correctly populated.
    }

    public static class Highlight {
        private boolean notable;
        private String title;
        private String body;

        public boolean isNotable() {
            return notable;
        }

        public void setNotable(boolean notable) {
            this.notable = notable;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Highlight highlight = (Highlight) o;
            return Objects.equals(notable, highlight.notable)
                && Objects.equals(title, highlight.title)
                && Objects.equals(body, highlight.body);
        }

        @Override
        public int hashCode() {
            return Objects.hash(notable, title, body);
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "Highlight{notable=%s, title='%s', body='%s'}", notable, title, body);
        }
    }

    public static class Breaking {
        private String area;
        private String title;
        private String body;
        private boolean isNotable;
        private String anchor;

        public String getArea() {
            return area;
        }

        public void setArea(String area) {
            this.area = area;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public boolean isNotable() {
            return isNotable;
        }

        public void setNotable(boolean notable) {
            isNotable = notable;
        }

        public String getAnchor() {
            return anchor;
        }

        public void setAnchor(String anchor) {
            this.anchor = anchor;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Breaking breaking = (Breaking) o;
            return Objects.equals(area, breaking.area)
                && Objects.equals(title, breaking.title)
                && Objects.equals(body, breaking.body)
                && Objects.equals(isNotable, breaking.isNotable)
                && Objects.equals(anchor, breaking.anchor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(area, title, body, isNotable, anchor);
        }

        @Override
        public String toString() {
            return String.format(
                Locale.ROOT,
                "Breaking{area='%s', title='%s', body='%s', isNotable=%s, anchor='%s'}",
                area,
                title,
                body,
                isNotable,
                anchor
            );
        }
    }
}
