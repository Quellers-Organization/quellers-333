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

package org.elasticsearch.geo.geometry;

/**
 * Support class for creating Geometry Visitors.
 * <p>
 * This is an implementation of the Visitor pattern. The basic idea is to simplify adding new operations on Geometries, without
 * constantly modifying and adding new functionality to the Geometry hierarchy and keeping it as lightweight as possible.
 * <p>
 * It is a more object-oriented alternative to structures like this:
 * <pre>
 * if (obj instanceof This) {
 *   doThis((This) obj);
 * } elseif (obj instanceof That) {
 *   doThat((That) obj);
 * ...
 * } else {
 *   throw new IllegalArgumentException("Unknown object " + obj);
 * }
 * </pre>
 * <p>
 * The Visitor Pattern replaces this structure with Interface inheritance making it easier to identify all places that are using this
 * structure, and making a shape a compile-time failure instead of runtime.
 * <p>
 * See {@link org.elasticsearch.geo.utils.WellKnownText#toWKT(Geometry, StringBuilder)} for an example of how this interface is used.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Visitor_pattern">Visitor Pattern</a>
 */
public interface GeometryVisitor<T> {

    T visit(Circle circle);

    T visit(GeometryCollection<?> collection);

    T visit(Line line);

    T visit(LinearRing ring);

    T visit(MultiLine multiLine);

    T visit(MultiPoint multiPoint);

    T visit(MultiPolygon multiPolygon);

    T visit(Point point);

    T visit(Polygon polygon);

    T visit(Rectangle rectangle);

}
