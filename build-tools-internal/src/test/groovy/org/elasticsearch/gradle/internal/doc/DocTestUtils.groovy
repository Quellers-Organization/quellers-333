/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.gradle.internal.doc;

class DocTestUtils {
    public static Map<String,String> SAMPLE_TEST_DOCS = Map.of(
        "painless-field-context.asciidoc", """
[[painless-field-context]]
=== Field context

Use a Painless script to create a
{ref}/search-fields.html#script-fields[script field] to return
a customized value for each document in the results of a query.

*Variables*

`params` (`Map`, read-only)::
        User-defined parameters passed in as part of the query.

`doc` (`Map`, read-only)::
        Contains the fields of the specified document where each field is a
        `List` of values.

{ref}/mapping-source-field.html[`params['_source']`] (`Map`, read-only)::
        Contains extracted JSON in a `Map` and `List` structure for the fields
        existing in a stored document.

*Return*

`Object`::
        The customized value for each document.

*API*

Both the standard <<painless-api-reference-shared, Painless API>> and
<<painless-api-reference-field, Specialized Field API>> are available.


*Example*

To run this example, first follow the steps in
<<painless-context-examples, context examples>>.

You can then use these two example scripts to compute custom information
for each search hit and output it to two new fields.

The first script gets the doc value for the `datetime` field and calls
the `getDayOfWeekEnum` function to determine the corresponding day of the week.

[source,Painless]
----
doc['datetime'].value.getDayOfWeekEnum().getDisplayName(TextStyle.FULL, Locale.ROOT)
----

The second script calculates the number of actors. Actors' names are stored
as a keyword array in the `actors` field.

[source,Painless]
----
doc['actors'].size()  <1>
----

<1> By default, doc values are not available for `text` fields. If `actors` was
a `text` field, you could still calculate the number of actors by extracting
values from `_source` with `params['_source']['actors'].size()`.

The following request returns the calculated day of week and the number of
actors that appear in each play:

[source,console]
----
GET seats/_search
{
  "size": 2,
  "query": {
    "match_all": {}
  },
  "script_fields": {
    "day-of-week": {
      "script": {
        "source": "doc['datetime'].value.getDayOfWeekEnum().getDisplayName(TextStyle.FULL, Locale.ROOT)"
      }
    },
    "number-of-actors": {
      "script": {
        "source": "doc['actors'].size()"
      }
    }
  }
}
----
// TEST[setup:seats]

[source,console-result]
----
{
  "took" : 68,
  "timed_out" : false,
  "_shards" : {
    "total" : 1,
    "successful" : 1,
    "skipped" : 0,
    "failed" : 0
  },
  "hits" : {
    "total" : {
      "value" : 11,
      "relation" : "eq"
    },
    "max_score" : 1.0,
    "hits" : [
      {
        "_index" : "seats",
        "_id" : "1",
        "_score" : 1.0,
        "fields" : {
          "day-of-week" : [
            "Thursday"
          ],
          "number-of-actors" : [
            4
          ]
        }
      },
      {
        "_index" : "seats",
        "_id" : "2",
        "_score" : 1.0,
        "fields" : {
          "day-of-week" : [
            "Thursday"
          ],
          "number-of-actors" : [
            1
          ]
        }
      }
    ]
  }
}
----
// TESTRESPONSE[s/"took" : 68/"took" : "\$body.took"/]
""",
        "painless-field-context.mdx", """---
id: enElasticsearchPainlessPainlessFieldContext
slug: /en/elasticsearch/painless/painless-field-context
title: Field context
description: Description to be written
tags: []
---

<div id="painless-field-context"></div>

Use a Painless script to create a
[script field](((ref))/search-fields.html#script-fields) to return
a customized value for each document in the results of a query.

**Variables**

`params` (`Map`, read-only)
    : User-defined parameters passed in as part of the query.

`doc` (`Map`, read-only)
    : Contains the fields of the specified document where each field is a
    `List` of values.

[`params['_source']`](((ref))/mapping-source-field.html) (`Map`, read-only)
    : Contains extracted JSON in a `Map` and `List` structure for the fields
    existing in a stored document.

**Return**

`Object`
    : The customized value for each document.

**API**

Both the standard <DocLink id="enElasticsearchPainlessPainlessApiReferenceShared">Painless API</DocLink> and
<DocLink id="enElasticsearchPainlessPainlessApiReferenceField">Specialized Field API</DocLink> are available.

**Example**

To run this example, first follow the steps in
<DocLink id="enElasticsearchPainlessPainlessContextExamples">context examples</DocLink>.

You can then use these two example scripts to compute custom information
for each search hit and output it to two new fields.

The first script gets the doc value for the `datetime` field and calls
the `getDayOfWeekEnum` function to determine the corresponding day of the week.

```Painless
doc['datetime'].value.getDayOfWeekEnum().getDisplayName(TextStyle.FULL, Locale.ROOT)
```

The second script calculates the number of actors. Actors' names are stored
as a keyword array in the `actors` field.

```Painless
doc['actors'].size()   [^1]
```
[^1]: By default, doc values are not available for `text` fields. If `actors` was
a `text` field, you could still calculate the number of actors by extracting
values from `_source` with `params['_source']['actors'].size()`.

The following request returns the calculated day of week and the number of
actors that appear in each play:

```console
GET seats/_search
{
  "size": 2,
  "query": {
    "match_all": {}
  },
  "script_fields": {
    "day-of-week": {
      "script": {
        "source": "doc['datetime'].value.getDayOfWeekEnum().getDisplayName(TextStyle.FULL, Locale.ROOT)"
      }
    },
    "number-of-actors": {
      "script": {
        "source": "doc['actors'].size()"
      }
    }
  }
}
```
{/* TEST[setup:seats] */}

```console-result
{
  "took" : 68,
  "timed_out" : false,
  "_shards" : {
    "total" : 1,
    "successful" : 1,
    "skipped" : 0,
    "failed" : 0
  },
  "hits" : {
    "total" : {
      "value" : 11,
      "relation" : "eq"
    },
    "max_score" : 1.0,
    "hits" : [
      {
        "_index" : "seats",
        "_id" : "1",
        "_score" : 1.0,
        "fields" : {
          "day-of-week" : [
            "Thursday"
          ],
          "number-of-actors" : [
            4
          ]
        }
      },
      {
        "_index" : "seats",
        "_id" : "2",
        "_score" : 1.0,
        "fields" : {
          "day-of-week" : [
            "Thursday"
          ],
          "number-of-actors" : [
            1
          ]
        }
      }
    ]
  }
}
```
{/* TESTRESPONSE[s/"took" : 68/"took" : "\$body.took"/] */}
"""
    );
}
