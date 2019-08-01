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


package org.elasticsearch.index.mapper;

import org.apache.lucene.document.LatLonShape;
import org.apache.lucene.index.IndexableField;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.geo.geometry.Circle;
import org.elasticsearch.geo.geometry.Geometry;
import org.elasticsearch.geo.geometry.GeometryCollection;
import org.elasticsearch.geo.geometry.GeometryVisitor;
import org.elasticsearch.geo.geometry.Line;
import org.elasticsearch.geo.geometry.LinearRing;
import org.elasticsearch.geo.geometry.MultiLine;
import org.elasticsearch.geo.geometry.MultiPoint;
import org.elasticsearch.geo.geometry.MultiPolygon;
import org.elasticsearch.geo.geometry.Point;
import org.elasticsearch.geo.geometry.Polygon;
import org.elasticsearch.geo.geometry.Rectangle;
import org.locationtech.spatial4j.exception.InvalidShapeException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.lucene.geo.GeoUtils.orient;
import static org.elasticsearch.common.geo.GeoUtils.normalizeLat;
import static org.elasticsearch.common.geo.GeoUtils.normalizeLon;

/**
 * Utility class that converts geometries into Lucene-compatible form
 */
public final class GeoShapeIndexer implements AbstractGeometryFieldMapper.Indexer<Geometry, Geometry> {

    private static final double DATELINE = 180;

    protected static final Comparator<Edge> INTERSECTION_ORDER = Comparator.comparingDouble(o -> o.intersect.getLat());

    private final boolean orientation;

    private final String name;

    public GeoShapeIndexer(boolean orientation, String name) {
        this.orientation = orientation;
        this.name = name;
    }

    public Geometry prepareForIndexing(Geometry geometry) {
        if (geometry == null) {
            return null;
        }

        return geometry.visit(new GeometryVisitor<>() {
            @Override
            public Geometry visit(Circle circle) {
                throw new UnsupportedOperationException("CIRCLE geometry is not supported");
            }

            @Override
            public Geometry visit(GeometryCollection<?> collection) {
                if (collection.isEmpty()) {
                    return GeometryCollection.EMPTY;
                }
                List<Geometry> shapes = new ArrayList<>(collection.size());

                // Flatten collection and convert each geometry to Lucene-friendly format
                for (Geometry shape : collection) {
                    shapes.add(shape.visit(this));
                }

                if (shapes.size() == 1) {
                    return shapes.get(0);
                } else {
                    return new GeometryCollection<>(shapes);
                }
            }

            @Override
            public Geometry visit(Line line) {
                // decompose linestrings crossing dateline into array of Lines
                List<Line> lines = decomposeGeometry(line, new ArrayList<>());
                if (lines.size() == 1) {
                    return lines.get(0);
                } else {
                    return new MultiLine(lines);
                }
            }

            @Override
            public Geometry visit(LinearRing ring) {
                throw new UnsupportedOperationException("cannot index linear ring [" + ring + "] directly");
            }

            @Override
            public Geometry visit(MultiLine multiLine) {
                List<Line> lines = new ArrayList<>();
                for (Line line : multiLine) {
                    decomposeGeometry(line, lines);
                }
                if (lines.isEmpty()) {
                    return GeometryCollection.EMPTY;
                } else if (lines.size() == 1) {
                    return lines.get(0);
                } else {
                    return new MultiLine(lines);
                }
            }

            @Override
            public Geometry visit(MultiPoint multiPoint) {
                if (multiPoint.isEmpty()) {
                    return MultiPoint.EMPTY;
                } else if (multiPoint.size() == 1) {
                    return multiPoint.get(0).visit(this);
                } else {
                    List<Point> points = new ArrayList<>();
                    for (Point point : multiPoint) {
                        points.add((Point) point.visit(this));
                    }
                    return new MultiPoint(points);
                }
            }

            @Override
            public Geometry visit(MultiPolygon multiPolygon) {
                List<Polygon> polygons = new ArrayList<>();
                for (Polygon polygon : multiPolygon) {
                    polygons.addAll(decompose(polygon, orientation));
                }
                if (polygons.size() == 1) {
                    return polygons.get(0);
                } else {
                    return new MultiPolygon(polygons);
                }
            }

            @Override
            public Geometry visit(Point point) {
                //TODO: Just remove altitude for now. We need to add normalization later
                return new Point(point.getLat(), point.getLon());
            }

            @Override
            public Geometry visit(Polygon polygon) {
                List<Polygon> polygons = decompose(polygon, orientation);
                if (polygons.size() == 1) {
                    return polygons.get(0);
                } else {
                    return new MultiPolygon(polygons);
                }
            }

            @Override
            public Geometry visit(Rectangle rectangle) {
                return rectangle;
            }
        });
    }

    @Override
    public Class<Geometry> processedClass() {
        return Geometry.class;
    }

    @Override
    public List<IndexableField> indexShape(ParseContext context, Geometry shape) {
        LuceneGeometryIndexer visitor = new LuceneGeometryIndexer(name);
        shape.visit(visitor);
        return visitor.fields();
    }

    /**
     * Calculate the intersection of a line segment and a vertical dateline.
     *
     * @param p1x      longitude of the start-point of the line segment
     * @param p2x      longitude of the end-point of the line segment
     * @param dateline x-coordinate of the vertical dateline
     * @return position of the intersection in the open range (0..1] if the line
     * segment intersects with the line segment. Otherwise this method
     * returns {@link Double#NaN}
     */
    protected static double intersection(double p1x, double p2x, double dateline) {
        if (p1x == p2x && p1x != dateline) {
            return Double.NaN;
        } else if (p1x == p2x && p1x == dateline) {
            return 1.0;
        } else {
            final double t = (dateline - p1x) / (p2x - p1x);
            if (t > 1 || t <= 0) {
                return Double.NaN;
            } else {
                return t;
            }
        }
    }

    /**
     * Splits the specified line by datelines and adds them to the supplied lines array
     */
    private List<Line> decomposeGeometry(Line line, List<Line> lines) {

        for (Line partPlus : decompose(+DATELINE, line)) {
            for (Line partMinus : decompose(-DATELINE, partPlus)) {
                double[] lats = new double[partMinus.length()];
                double[] lons = new double[partMinus.length()];
                for (int i = 0; i < partMinus.length(); i++) {
                    lats[i] = normalizeLat(partMinus.getLat(i));
                    lons[i] = normalizeLon(partMinus.getLon(i));
                }
                lines.add(new Line(lats, lons));
            }
        }
        return lines;
    }

    /**
     * Decompose a linestring given as array of coordinates at a vertical line.
     *
     * @param dateline x-axis intercept of the vertical line
     * @param line     linestring that should be decomposed
     * @return array of linestrings given as coordinate arrays
     */
    private List<Line> decompose(double dateline, Line line) {
        double[] lons = line.getLons();
        double[] lats = line.getLats();
        return decompose(dateline, lons, lats);
    }

    /**
     * Decompose a linestring given as two arrays of coordinates at a vertical line.
     */
    private List<Line> decompose(double dateline, double[] lons, double[] lats) {
        int offset = 0;
        ArrayList<Line> parts = new ArrayList<>();

        double lastLon = lons[0];
        double shift = lastLon > DATELINE ? DATELINE : (lastLon < -DATELINE ? -DATELINE : 0);

        for (int i = 1; i < lons.length; i++) {
            double t = intersection(lastLon, lons[i], dateline);
            lastLon = lons[i];
            if (Double.isNaN(t) == false) {
                double[] partLons = Arrays.copyOfRange(lons, offset, i + 1);
                double[] partLats = Arrays.copyOfRange(lats, offset, i + 1);
                if (t < 1) {
                    Point intersection = position(new Point(lats[i - 1], lons[i - 1]), new Point(lats[i], lons[i]), t);
                    partLons[partLons.length - 1] = intersection.getLon();
                    partLats[partLats.length - 1] = intersection.getLat();

                    lons[offset + i - 1] = intersection.getLon();
                    lats[offset + i - 1] = intersection.getLat();

                    shift(shift, lons);
                    offset = i - 1;
                    shift = lons[i] > DATELINE ? DATELINE : (lons[i] < -DATELINE ? -DATELINE : 0);
                } else {
                    shift(shift, partLons);
                    offset = i;
                }
                parts.add(new Line(partLats, partLons));
            }
        }

        if (offset == 0) {
            shift(shift, lons);
            parts.add(new Line(lats, lons));
        } else if (offset < lons.length - 1) {
            double[] partLons = Arrays.copyOfRange(lons, offset, lons.length);
            double[] partLats = Arrays.copyOfRange(lats, offset, lats.length);
            shift(shift, partLons);
            parts.add(new Line(partLats, partLons));
        }
        return parts;
    }

    /**
     * shifts all coordinates by  (- shift * 2)
     */
    private static void shift(double shift, double[] lons) {
        if (shift != 0) {
            for (int j = 0; j < lons.length; j++) {
                lons[j] = lons[j] - 2 * shift;
            }
        }
    }

    protected static Point shift(Point coordinate, double dateline) {
        if (dateline == 0) {
            return coordinate;
        } else {
            return new Point(coordinate.getLat(), -2 * dateline + coordinate.getLon());
        }
    }

    private List<Polygon> decompose(Polygon polygon, boolean orientation) {
        int numEdges = polygon.getPolygon().length() - 1; // Last point is repeated
        for (int i = 0; i < polygon.getNumberOfHoles(); i++) {
            numEdges += polygon.getHole(i).length() - 1;
            validateHole(polygon.getPolygon(), polygon.getHole(i));
        }

        Edge[] edges = new Edge[numEdges];
        Edge[] holeComponents = new Edge[polygon.getNumberOfHoles()];
        final AtomicBoolean translated = new AtomicBoolean(false);
        int offset = createEdges(0, orientation, polygon.getPolygon(), null, edges, 0, translated);
        for (int i = 0; i < polygon.getNumberOfHoles(); i++) {
            int length = createEdges(i + 1, orientation, polygon.getPolygon(), polygon.getHole(i), edges, offset, translated);
            holeComponents[i] = edges[offset];
            offset += length;
        }

        int numHoles = holeComponents.length;

        numHoles = merge(edges, 0, intersections(+DATELINE, edges), holeComponents, numHoles);
        numHoles = merge(edges, 0, intersections(-DATELINE, edges), holeComponents, numHoles);

        return compose(edges, holeComponents, numHoles);
    }

    private void validateHole(LinearRing shell, LinearRing hole) {
        Set<Point> exterior = new HashSet<>();
        Set<Point> interior = new HashSet<>();
        for (int i = 0; i < shell.length(); i++) {
            exterior.add(new Point(shell.getLat(i), shell.getLon(i)));
        }
        for (int i = 0; i < hole.length(); i++) {
            interior.add(new Point(hole.getLat(i), hole.getLon(i)));
        }
        exterior.retainAll(interior);
        if (exterior.size() >= 2) {
            throw new IllegalArgumentException("Invalid polygon, interior cannot share more than one point with the exterior");
        }
    }

    /**
     * This helper class implements a linked list for {@link Point}. It contains
     * fields for a dateline intersection and component id
     */
    private static final class Edge {
        Point coordinate; // coordinate of the start point
        Edge next; // next segment
        Point intersect; // potential intersection with dateline
        int component = -1; // id of the component this edge belongs to
        public static final Point MAX_COORDINATE = new Point(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);

        protected Edge(Point coordinate, Edge next, Point intersection) {
            this.coordinate = coordinate;
            // use setter to catch duplicate point cases
            this.setNext(next);
            this.intersect = intersection;
            if (next != null) {
                this.component = next.component;
            }
        }

        protected Edge(Point coordinate, Edge next) {
            this(coordinate, next, Edge.MAX_COORDINATE);
        }

        protected void setNext(Edge next) {
            // don't bother setting next if its null
            if (next != null) {
                // self-loop throws an invalid shape
                if (this.coordinate.equals(next.coordinate)) {
                    throw new InvalidShapeException("Provided shape has duplicate consecutive coordinates at: " + this.coordinate);
                }
                this.next = next;
            }
        }

        /**
         * Set the intersection of this line segment to the given position
         *
         * @param position position of the intersection [0..1]
         * @return the {@link Point} of the intersection
         */
        protected Point intersection(double position) {
            return intersect = position(coordinate, next.coordinate, position);
        }

        @Override
        public String toString() {
            return "Edge[Component=" + component + "; start=" + coordinate + " " + "; intersection=" + intersect + "]";
        }
    }

    protected static Point position(Point p1, Point p2, double position) {
        if (position == 0) {
            return p1;
        } else if (position == 1) {
            return p2;
        } else {
            final double x = p1.getLon() + position * (p2.getLon() - p1.getLon());
            final double y = p1.getLat() + position * (p2.getLat() - p1.getLat());
            return new Point(y, x);
        }
    }

    private int createEdges(int component, boolean orientation, LinearRing shell,
                            LinearRing hole, Edge[] edges, int offset, final AtomicBoolean translated) {
        // inner rings (holes) have an opposite direction than the outer rings
        // XOR will invert the orientation for outer ring cases (Truth Table:, T/T = F, T/F = T, F/T = T, F/F = F)
        boolean direction = (component == 0 ^ orientation);
        // set the points array accordingly (shell or hole)
        Point[] points = (hole != null) ? points(hole) : points(shell);
        ring(component, direction, orientation == false, points, 0, edges, offset, points.length - 1, translated);
        return points.length - 1;
    }

    private Point[] points(LinearRing linearRing) {
        Point[] points = new Point[linearRing.length()];
        for (int i = 0; i < linearRing.length(); i++) {
            points[i] = new Point(linearRing.getLat(i), linearRing.getLon(i));
        }
        return points;
    }

    /**
     * Create a connected list of a list of coordinates
     *
     * @param points array of point
     * @param offset index of the first point
     * @param length number of points
     * @return Array of edges
     */
    private Edge[] ring(int component, boolean direction, boolean handedness,
                        Point[] points, int offset, Edge[] edges, int toffset, int length, final AtomicBoolean translated) {

        boolean orientation = getOrientation(points, offset, length);

        // OGC requires shell as ccw (Right-Handedness) and holes as cw (Left-Handedness)
        // since GeoJSON doesn't specify (and doesn't need to) GEO core will assume OGC standards
        // thus if orientation is computed as cw, the logic will translate points across dateline
        // and convert to a right handed system

        // compute the bounding box and calculate range
        double[] range = range(points, offset, length);
        final double rng = range[1] - range[0];
        // translate the points if the following is true
        //   1.  shell orientation is cw and range is greater than a hemisphere (180 degrees) but not spanning 2 hemispheres
        //       (translation would result in a collapsed poly)
        //   2.  the shell of the candidate hole has been translated (to preserve the coordinate system)
        boolean incorrectOrientation = component == 0 && handedness != orientation;
        if ((incorrectOrientation && (rng > DATELINE && rng != 2 * DATELINE)) || (translated.get() && component != 0)) {
            translate(points);
            // flip the translation bit if the shell is being translated
            if (component == 0) {
                translated.set(true);
            }
            // correct the orientation post translation (ccw for shell, cw for holes)
            if (component == 0 || (component != 0 && handedness == orientation)) {
                orientation = !orientation;
            }
        }
        return concat(component, direction ^ orientation, points, offset, edges, toffset, length);
    }

    /**
     * Transforms coordinates in the eastern hemisphere (-180:0) to a (180:360) range
     */
    private static void translate(Point[] points) {
        for (int i = 0; i < points.length; i++) {
            if (points[i].getLon() < 0) {
                points[i] = new Point(points[i].getLat(), points[i].getLon() + 2 * DATELINE);
            }
        }
    }

    /**
     * @return whether the points are clockwise (true) or anticlockwise (false)
     */
    private static boolean getOrientation(Point[] points, int offset, int length) {
        // calculate the direction of the points: find the southernmost point
        // and check its neighbors orientation.

        final int top = top(points, offset, length);
        final int prev = (top + length - 1) % length;
        final int next = (top + 1) % length;

        final int determinantSign = orient(
            points[offset + prev].getLon(), points[offset + prev].getLat(),
            points[offset + top].getLon(), points[offset + top].getLat(),
            points[offset + next].getLon(), points[offset + next].getLat());

        if (determinantSign == 0) {
            // Points are collinear, but `top` is not in the middle if so, so the edges either side of `top` are intersecting.
            throw new InvalidShapeException("Cannot determine orientation: edges adjacent to ("
                + points[offset + top].getLon() + "," + points[offset + top].getLat() + ") coincide");
        }

        return determinantSign < 0;
    }

    /**
     * @return the (offset) index of the point that is furthest west amongst
     * those points that are the furthest south in the set.
     */
    private static int top(Point[] points, int offset, int length) {
        int top = 0; // we start at 1 here since top points to 0
        for (int i = 1; i < length; i++) {
            if (points[offset + i].getLat() < points[offset + top].getLat()) {
                top = i;
            } else if (points[offset + i].getLat() == points[offset + top].getLat()) {
                if (points[offset + i].getLon() < points[offset + top].getLon()) {
                    top = i;
                }
            }
        }
        return top;
    }


    private static double[] range(Point[] points, int offset, int length) {
        double minX = points[0].getLon();
        double maxX = minX;
        double minY = points[0].getLat();
        double maxY = minY;
        // compute the bounding coordinates (@todo: cleanup brute force)
        for (int i = 1; i < length; ++i) {
            Point point = points[offset + i];
            if (point.getLon() < minX) {
                minX = point.getLon();
            }
            if (point.getLon() > maxX) {
                maxX = point.getLon();
            }
            if (point.getLat() < minY) {
                minY = point.getLat();
            }
            if (point.getLat() > maxY) {
                maxY = point.getLat();
            }
        }
        return new double[]{minX, maxX, minY, maxY};
    }

    private int merge(Edge[] intersections, int offset, int length, Edge[] holes, int numHoles) {
        // Intersections appear pairwise. On the first edge the inner of
        // of the polygon is entered. On the second edge the outer face
        // is entered. Other kinds of intersections are discard by the
        // intersection function

        for (int i = 0; i < length; i += 2) {
            Edge e1 = intersections[offset + i + 0];
            Edge e2 = intersections[offset + i + 1];

            // If two segments are connected maybe a hole must be deleted
            // Since Edges of components appear pairwise we need to check
            // the second edge only (the first edge is either polygon or
            // already handled)
            if (e2.component > 0) {
                //TODO: Check if we could save the set null step
                numHoles--;
                holes[e2.component - 1] = holes[numHoles];
                holes[numHoles] = null;
            }
            // only connect edges if intersections are pairwise
            // 1. per the comment above, the edge array is sorted by y-value of the intersection
            // with the dateline.  Two edges have the same y intercept when they cross the
            // dateline thus they appear sequentially (pairwise) in the edge array. Two edges
            // do not have the same y intercept when we're forming a multi-poly from a poly
            // that wraps the dateline (but there are 2 ordered intercepts).
            // The connect method creates a new edge for these paired edges in the linked list.
            // For boundary conditions (e.g., intersect but not crossing) there is no sibling edge
            // to connect. Thus the first logic check enforces the pairwise rule
            // 2. the second logic check ensures the two candidate edges aren't already connected by an
            //    existing edge along the dateline - this is necessary due to a logic change in
            //    ShapeBuilder.intersection that computes dateline edges as valid intersect points
            //    in support of OGC standards
            if (e1.intersect != Edge.MAX_COORDINATE && e2.intersect != Edge.MAX_COORDINATE
                && !(e1.next.next.coordinate.equals(e2.coordinate) && Math.abs(e1.next.coordinate.getLon()) == DATELINE
                && Math.abs(e2.coordinate.getLon()) == DATELINE)) {
                connect(e1, e2);
            }
        }
        return numHoles;
    }

    private void connect(Edge in, Edge out) {
        assert in != null && out != null;
        assert in != out;
        // Connecting two Edges by inserting the point at
        // dateline intersection and connect these by adding
        // two edges between this points. One per direction
        if (in.intersect != in.next.coordinate) {
            // NOTE: the order of the object creation is crucial here! Don't change it!
            // first edge has no point on dateline
            Edge e1 = new Edge(in.intersect, in.next);

            if (out.intersect != out.next.coordinate) {
                // second edge has no point on dateline
                Edge e2 = new Edge(out.intersect, out.next);
                in.next = new Edge(in.intersect, e2, in.intersect);
            } else {
                // second edge intersects with dateline
                in.next = new Edge(in.intersect, out.next, in.intersect);
            }
            out.next = new Edge(out.intersect, e1, out.intersect);
        } else if (in.next != out && in.coordinate != out.intersect) {
            // first edge intersects with dateline
            Edge e2 = new Edge(out.intersect, in.next, out.intersect);

            if (out.intersect != out.next.coordinate) {
                // second edge has no point on dateline
                Edge e1 = new Edge(out.intersect, out.next);
                in.next = new Edge(in.intersect, e1, in.intersect);

            } else {
                // second edge intersects with dateline
                in.next = new Edge(in.intersect, out.next, in.intersect);
            }
            out.next = e2;
        }
    }

    /**
     * Concatenate a set of points to a polygon
     *
     * @param component   component id of the polygon
     * @param direction   direction of the ring
     * @param points      list of points to concatenate
     * @param pointOffset index of the first point
     * @param edges       Array of edges to write the result to
     * @param edgeOffset  index of the first edge in the result
     * @param length      number of points to use
     * @return the edges creates
     */
    private static Edge[] concat(int component, boolean direction, Point[] points, final int pointOffset, Edge[] edges,
                                 final int edgeOffset, int length) {
        assert edges.length >= length + edgeOffset;
        assert points.length >= length + pointOffset;
        edges[edgeOffset] = new Edge(new Point(points[pointOffset].getLat(), points[pointOffset].getLon()), null);
        for (int i = 1; i < length; i++) {
            Point nextPoint = new Point(points[pointOffset + i].getLat(), points[pointOffset + i].getLon());
            if (direction) {
                edges[edgeOffset + i] = new Edge(nextPoint, edges[edgeOffset + i - 1]);
                edges[edgeOffset + i].component = component;
            } else if (!edges[edgeOffset + i - 1].coordinate.equals(nextPoint)) {
                edges[edgeOffset + i - 1].next = edges[edgeOffset + i] = new Edge(nextPoint, null);
                edges[edgeOffset + i - 1].component = component;
            } else {
                throw new InvalidShapeException("Provided shape has duplicate consecutive coordinates at: (" + nextPoint + ")");
            }
        }

        if (direction) {
            edges[edgeOffset].setNext(edges[edgeOffset + length - 1]);
            edges[edgeOffset].component = component;
        } else {
            edges[edgeOffset + length - 1].setNext(edges[edgeOffset]);
            edges[edgeOffset + length - 1].component = component;
        }

        return edges;
    }

    /**
     * Calculate all intersections of line segments and a vertical line. The
     * Array of edges will be ordered asc by the y-coordinate of the
     * intersections of edges.
     *
     * @param dateline x-coordinate of the dateline
     * @param edges    set of edges that may intersect with the dateline
     * @return number of intersecting edges
     */
    protected static int intersections(double dateline, Edge[] edges) {
        int numIntersections = 0;
        assert !Double.isNaN(dateline);
        for (int i = 0; i < edges.length; i++) {
            Point p1 = edges[i].coordinate;
            Point p2 = edges[i].next.coordinate;
            assert !Double.isNaN(p2.getLon()) && !Double.isNaN(p1.getLon());
            edges[i].intersect = Edge.MAX_COORDINATE;

            double position = intersection(p1.getLon(), p2.getLon(), dateline);
            if (!Double.isNaN(position)) {
                edges[i].intersection(position);
                numIntersections++;
            }
        }
        Arrays.sort(edges, INTERSECTION_ORDER);
        return numIntersections;
    }


    private static Edge[] edges(Edge[] edges, int numHoles, List<List<Point[]>> components) {
        ArrayList<Edge> mainEdges = new ArrayList<>(edges.length);

        for (int i = 0; i < edges.length; i++) {
            if (edges[i].component >= 0) {
                double[] partitionPoint = new double[3];
                int length = component(edges[i], -(components.size() + numHoles + 1), mainEdges, partitionPoint);
                List<Point[]> component = new ArrayList<>();
                component.add(coordinates(edges[i], new Point[length + 1], partitionPoint));
                components.add(component);
            }
        }

        return mainEdges.toArray(new Edge[mainEdges.size()]);
    }

    private static List<Polygon> compose(Edge[] edges, Edge[] holes, int numHoles) {
        final List<List<Point[]>> components = new ArrayList<>();
        assign(holes, holes(holes, numHoles), numHoles, edges(edges, numHoles, components), components);
        return buildPoints(components);
    }

    private static void assign(Edge[] holes, Point[][] points, int numHoles, Edge[] edges, List<List<Point[]>> components) {
        // Assign Hole to related components
        // To find the new component the hole belongs to all intersections of the
        // polygon edges with a vertical line are calculated. This vertical line
        // is an arbitrary point of the hole. The polygon edge next to this point
        // is part of the polygon the hole belongs to.
        for (int i = 0; i < numHoles; i++) {
            // To do the assignment we assume (and later, elsewhere, check) that each hole is within
            // a single component, and the components do not overlap. Based on this assumption, it's
            // enough to find a component that contains some vertex of the hole, and
            // holes[i].coordinate is such a vertex, so we use that one.

            // First, we sort all the edges according to their order of intersection with the line
            // of longitude through holes[i].coordinate, in order from south to north. Edges that do
            // not intersect this line are sorted to the end of the array and of no further interest
            // here.
            final Edge current = new Edge(holes[i].coordinate, holes[i].next);
            current.intersect = current.coordinate;
            final int intersections = intersections(current.coordinate.getLon(), edges);

            if (intersections == 0) {
                // There were no edges that intersect the line of longitude through
                // holes[i].coordinate, so there's no way this hole is within the polygon.
                throw new InvalidShapeException("Invalid shape: Hole is not within polygon");
            }

            // Next we do a binary search to find the position of holes[i].coordinate in the array.
            // The binary search returns the index of an exact match, or (-insertionPoint - 1) if
            // the vertex lies between the intersections of edges[insertionPoint] and
            // edges[insertionPoint+1]. The latter case is vastly more common.

            final int pos;
            boolean sharedVertex = false;
            if (((pos = Arrays.binarySearch(edges, 0, intersections, current, INTERSECTION_ORDER)) >= 0)
                && !(sharedVertex = (edges[pos].intersect.equals(current.coordinate)))) {
                // The binary search returned an exact match, but we checked again using compareTo()
                // and it didn't match after all.

                // TODO Can this actually happen? Needs a test to exercise it, or else needs to be removed.
                throw new InvalidShapeException("Invalid shape: Hole is not within polygon");
            }

            final int index;
            if (sharedVertex) {
                // holes[i].coordinate lies exactly on an edge.
                index = 0; // TODO Should this be pos instead of 0? This assigns exact matches to the southernmost component.
            } else if (pos == -1) {
                // holes[i].coordinate is strictly south of all intersections. Assign it to the
                // southernmost component, and allow later validation to spot that it is not
                // entirely within the chosen component.
                index = 0;
            } else {
                // holes[i].coordinate is strictly north of at least one intersection. Assign it to
                // the component immediately to its south.
                index = -(pos + 2);
            }

            final int component = -edges[index].component - numHoles - 1;

            components.get(component).add(points[i]);
        }
    }

    /**
     * This method sets the component id of all edges in a ring to a given id and shifts the
     * coordinates of this component according to the dateline
     *
     * @param edge  An arbitrary edge of the component
     * @param id    id to apply to the component
     * @param edges a list of edges to which all edges of the component will be added (could be <code>null</code>)
     * @return number of edges that belong to this component
     */
    private static int component(final Edge edge, final int id, final ArrayList<Edge> edges, double[] partitionPoint) {
        // find a coordinate that is not part of the dateline
        Edge any = edge;
        while (any.coordinate.getLon() == +DATELINE || any.coordinate.getLon() == -DATELINE) {
            if ((any = any.next) == edge) {
                break;
            }
        }

        double shiftOffset = any.coordinate.getLon() > DATELINE ? DATELINE : (any.coordinate.getLon() < -DATELINE ? -DATELINE : 0);

        // run along the border of the component, collect the
        // edges, shift them according to the dateline and
        // update the component id
        int length = 0, connectedComponents = 0;
        // if there are two connected components, splitIndex keeps track of where to split the edge array
        // start at 1 since the source coordinate is shared
        int splitIndex = 1;
        Edge current = edge;
        Edge prev = edge;
        // bookkeep the source and sink of each visited coordinate
        HashMap<Point, Tuple<Edge, Edge>> visitedEdge = new HashMap<>();
        do {
            current.coordinate = shift(current.coordinate, shiftOffset);
            current.component = id;

            if (edges != null) {
                // found a closed loop - we have two connected components so we need to slice into two distinct components
                if (visitedEdge.containsKey(current.coordinate)) {
                    partitionPoint[0] = current.coordinate.getLon();
                    partitionPoint[1] = current.coordinate.getLat();
                    if (connectedComponents > 0 && current.next != edge) {
                        throw new InvalidShapeException("Shape contains more than one shared point");
                    }

                    // a negative id flags the edge as visited for the edges(...) method.
                    // since we're splitting connected components, we want the edges method to visit
                    // the newly separated component
                    final int visitID = -id;
                    Edge firstAppearance = visitedEdge.get(current.coordinate).v2();
                    // correct the graph pointers by correcting the 'next' pointer for both the
                    // first appearance and this appearance of the edge
                    Edge temp = firstAppearance.next;
                    firstAppearance.next = current.next;
                    current.next = temp;
                    current.component = visitID;
                    // backtrack until we get back to this coordinate, setting the visit id to
                    // a non-visited value (anything positive)
                    do {
                        prev.component = visitID;
                        prev = visitedEdge.get(prev.coordinate).v1();
                        ++splitIndex;
                    } while (!current.coordinate.equals(prev.coordinate));
                    ++connectedComponents;
                } else {
                    visitedEdge.put(current.coordinate, new Tuple<Edge, Edge>(prev, current));
                }
                edges.add(current);
                prev = current;
            }
            length++;
        } while (connectedComponents == 0 && (current = current.next) != edge);

        return (splitIndex != 1) ? length - splitIndex : length;
    }

    /**
     * Compute all coordinates of a component
     *
     * @param component   an arbitrary edge of the component
     * @param coordinates Array of coordinates to write the result to
     * @return the coordinates parameter
     */
    private static Point[] coordinates(Edge component, Point[] coordinates, double[] partitionPoint) {
        for (int i = 0; i < coordinates.length; i++) {
            coordinates[i] = (component = component.next).coordinate;
        }
        // First and last coordinates must be equal
        if (coordinates[0].equals(coordinates[coordinates.length - 1]) == false) {
            if (partitionPoint[2] == Double.NaN) {
                throw new InvalidShapeException("Self-intersection at or near point ["
                    + partitionPoint[0] + "," + partitionPoint[1] + "]");
            } else {
                throw new InvalidShapeException("Self-intersection at or near point ["
                    + partitionPoint[0] + "," + partitionPoint[1] + "," + partitionPoint[2] + "]");
            }
        }
        return coordinates;
    }

    private static List<Polygon> buildPoints(List<List<Point[]>> components) {
        List<Polygon> result = new ArrayList<>(components.size());
        for (int i = 0; i < components.size(); i++) {
            List<Point[]> component = components.get(i);
            result.add(buildPolygon(component));
        }
        return result;
    }

    private static Polygon buildPolygon(List<Point[]> polygon) {
        List<LinearRing> holes;
        Point[] shell = polygon.get(0);
        if (polygon.size() > 1) {
            holes = new ArrayList<>(polygon.size() - 1);
            for (int i = 1; i < polygon.size(); ++i) {
                Point[] coords = polygon.get(i);
                //We do not have holes on the dateline as they get eliminated
                //when breaking the polygon around it.
                double[] x = new double[coords.length];
                double[] y = new double[coords.length];
                for (int c = 0; c < coords.length; ++c) {
                    x[c] = normalizeLon(coords[c].getLon());
                    y[c] = normalizeLat(coords[c].getLat());
                }
                holes.add(new LinearRing(y, x));
            }
        } else {
            holes = Collections.emptyList();
        }

        double[] x = new double[shell.length];
        double[] y = new double[shell.length];
        for (int i = 0; i < shell.length; ++i) {
            //Lucene Tessellator treats different +180 and -180 and we should keep the sign.
            //normalizeLon method excludes -180.
            x[i] = Math.abs(shell[i].getLon()) > 180 ? normalizeLon(shell[i].getLon()) : shell[i].getLon();
            y[i] = normalizeLat(shell[i].getLat());
        }

        return new Polygon(new LinearRing(y, x), holes);
    }

    private static Point[][] holes(Edge[] holes, int numHoles) {
        if (numHoles == 0) {
            return new Point[0][];
        }
        final Point[][] points = new Point[numHoles][];

        for (int i = 0; i < numHoles; i++) {
            double[] partitionPoint = new double[3];
            int length = component(holes[i], -(i + 1), null, partitionPoint); // mark as visited by inverting the sign
            points[i] = coordinates(holes[i], new Point[length + 1], partitionPoint);
        }

        return points;
    }


    private static class LuceneGeometryIndexer implements GeometryVisitor<Void, RuntimeException> {
        private List<IndexableField> fields = new ArrayList<>();
        private String name;

        private LuceneGeometryIndexer(String name) {
            this.name = name;
        }

        List<IndexableField> fields() {
            return fields;
        }

        @Override
        public Void visit(Circle circle) {
            throw new IllegalArgumentException("invalid shape type found [Circle] while indexing shape");
        }

        @Override
        public Void visit(GeometryCollection<?> collection) {
            for (Geometry geometry : collection) {
                geometry.visit(this);
            }
            return null;
        }

        @Override
        public Void visit(org.elasticsearch.geo.geometry.Line line) {
            addFields(LatLonShape.createIndexableFields(name, new org.apache.lucene.geo.Line(line.getLats(), line.getLons())));
            return null;
        }

        @Override
        public Void visit(LinearRing ring) {
            throw new IllegalArgumentException("invalid shape type found [LinearRing] while indexing shape");
        }

        @Override
        public Void visit(MultiLine multiLine) {
            for (org.elasticsearch.geo.geometry.Line line : multiLine) {
                visit(line);
            }
            return null;
        }

        @Override
        public Void visit(MultiPoint multiPoint) {
            for(Point point : multiPoint) {
                visit(point);
            }
            return null;
        }

        @Override
        public Void visit(MultiPolygon multiPolygon) {
            for(org.elasticsearch.geo.geometry.Polygon polygon : multiPolygon) {
                visit(polygon);
            }
            return null;
        }

        @Override
        public Void visit(Point point) {
            addFields(LatLonShape.createIndexableFields(name, point.getLat(), point.getLon()));
            return null;
        }

        @Override
        public Void visit(org.elasticsearch.geo.geometry.Polygon polygon) {
            addFields(LatLonShape.createIndexableFields(name, toLucenePolygon(polygon)));
            return null;
        }

        @Override
        public Void visit(org.elasticsearch.geo.geometry.Rectangle r) {
            org.apache.lucene.geo.Polygon p = new org.apache.lucene.geo.Polygon(
                new double[]{r.getMinLat(), r.getMinLat(), r.getMaxLat(), r.getMaxLat(), r.getMinLat()},
                new double[]{r.getMinLon(), r.getMaxLon(), r.getMaxLon(), r.getMinLon(), r.getMinLon()});
            addFields(LatLonShape.createIndexableFields(name, p));
            return null;
        }

        private void addFields(IndexableField[] fields) {
            this.fields.addAll(Arrays.asList(fields));
        }
    }


    public static org.apache.lucene.geo.Polygon toLucenePolygon(org.elasticsearch.geo.geometry.Polygon polygon) {
        org.apache.lucene.geo.Polygon[] holes = new org.apache.lucene.geo.Polygon[polygon.getNumberOfHoles()];
        for(int i = 0; i<holes.length; i++) {
            holes[i] = new org.apache.lucene.geo.Polygon(polygon.getHole(i).getLats(), polygon.getHole(i).getLons());
        }
        return new org.apache.lucene.geo.Polygon(polygon.getPolygon().getLats(), polygon.getPolygon().getLons(), holes);
    }
}
