/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.optimizer.rules.physical.local;

import org.apache.lucene.util.BytesRef;
import org.elasticsearch.geometry.Circle;
import org.elasticsearch.geometry.Geometry;
import org.elasticsearch.geometry.Point;
import org.elasticsearch.geometry.utils.WellKnownBinary;
import org.elasticsearch.xpack.esql.core.expression.Alias;
import org.elasticsearch.xpack.esql.core.expression.Expression;
import org.elasticsearch.xpack.esql.core.expression.Literal;
import org.elasticsearch.xpack.esql.core.expression.ReferenceAttribute;
import org.elasticsearch.xpack.esql.core.expression.predicate.logical.And;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.core.type.DataType;
import org.elasticsearch.xpack.esql.expression.function.scalar.spatial.SpatialDisjoint;
import org.elasticsearch.xpack.esql.expression.function.scalar.spatial.SpatialIntersects;
import org.elasticsearch.xpack.esql.expression.function.scalar.spatial.SpatialRelatesUtils;
import org.elasticsearch.xpack.esql.expression.function.scalar.spatial.StDistance;
import org.elasticsearch.xpack.esql.expression.predicate.operator.comparison.EsqlBinaryComparison;
import org.elasticsearch.xpack.esql.optimizer.LocalPhysicalOptimizerContext;
import org.elasticsearch.xpack.esql.optimizer.PhysicalOptimizerRules;
import org.elasticsearch.xpack.esql.plan.physical.EsQueryExec;
import org.elasticsearch.xpack.esql.plan.physical.EvalExec;
import org.elasticsearch.xpack.esql.plan.physical.FilterExec;
import org.elasticsearch.xpack.esql.plan.physical.PhysicalPlan;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * When a spatial distance predicate can be pushed down to lucene, this is done by capturing the distance within the same function.
 * In principle this is like re-writing the predicate:
 * <pre>WHERE ST_DISTANCE(field, TO_GEOPOINT("POINT(0 0)")) &lt;= 10000</pre>
 * as:
 * <pre>WHERE ST_INTERSECTS(field, TO_GEOSHAPE("CIRCLE(0,0,10000)"))</pre>
 */
public class EnableSpatialDistancePushdown extends PhysicalOptimizerRules.ParameterizedOptimizerRule<
    FilterExec,
    LocalPhysicalOptimizerContext> {

    @Override
    protected PhysicalPlan rule(FilterExec filterExec, LocalPhysicalOptimizerContext ctx) {
        PhysicalPlan plan = filterExec;
        if (filterExec.child() instanceof EsQueryExec esQueryExec) {
            plan = rewrite(filterExec, esQueryExec);
        } else if (filterExec.child() instanceof EvalExec evalExec && evalExec.child() instanceof EsQueryExec esQueryExec) {
            plan = rewrite(filterExec, evalExec, esQueryExec);
        }

        return plan;
    }

    private FilterExec rewrite(FilterExec filterExec, EsQueryExec esQueryExec) {
        // Find and rewrite any binary comparisons that involve a distance function and a literal
        var rewritten = filterExec.condition().transformDown(EsqlBinaryComparison.class, comparison -> {
            ComparisonType comparisonType = ComparisonType.from(comparison.getFunctionType());
            if (comparison.left() instanceof StDistance dist && comparison.right().foldable()) {
                return rewriteComparison(comparison, dist, comparison.right(), comparisonType);
            } else if (comparison.right() instanceof StDistance dist && comparison.left().foldable()) {
                return rewriteComparison(comparison, dist, comparison.left(), ComparisonType.invert(comparisonType));
            }
            return comparison;
        });
        if (rewritten.equals(filterExec.condition()) == false) {
            return new FilterExec(filterExec.source(), esQueryExec, rewritten);
        }
        return filterExec;
    }

    private boolean isPushableAlias(Alias alias) {
        // TODO: Add support for more pushable aliases, like simple field references
        return alias.child() instanceof StDistance;
    }

    private PhysicalPlan rewrite(FilterExec filterExec, EvalExec evalExec, EsQueryExec esQueryExec) {
        Map<String, Alias> pushable = new LinkedHashMap<>();
        List<Alias> nonPushable = new ArrayList<>();
        evalExec.fields().forEach(alias -> {
            if (isPushableAlias(alias)) {
                pushable.put(alias.name(), alias);
            } else {
                nonPushable.add(alias);
            }
        });
        // TODO support mixing pushable and non-pushable aliases
        if (nonPushable.isEmpty()) {
            // Find and rewrite any binary comparisons that involve a distance function and a literal
            var rewritten = filterExec.condition().transformDown(EsqlBinaryComparison.class, comparison -> {
                ComparisonType comparisonType = ComparisonType.from(comparison.getFunctionType());
                if (comparison.left() instanceof ReferenceAttribute d && pushable.containsKey(d.name()) && comparison.right().foldable()) {
                    StDistance dist = (StDistance) pushable.get(d.name()).child();
                    return rewriteComparison(comparison, dist, comparison.right(), comparisonType);
                } else if (comparison.right() instanceof ReferenceAttribute d
                    && pushable.containsKey(d.name())
                    && comparison.left().foldable()) {
                        StDistance dist = (StDistance) pushable.get(d.name()).child();
                        return rewriteComparison(comparison, dist, comparison.left(), ComparisonType.invert(comparisonType));
                    }
                return comparison;
            });
            if (rewritten.equals(filterExec.condition()) == false) {
                FilterExec filter = new FilterExec(filterExec.source(), esQueryExec, rewritten);
                return new EvalExec(evalExec.source(), filter, evalExec.fields());
            }
        }
        return filterExec;
    }

    private Expression rewriteComparison(
        EsqlBinaryComparison comparison,
        StDistance dist,
        Expression literal,
        ComparisonType comparisonType
    ) {
        Object value = literal.fold();
        if (value instanceof Number number) {
            if (dist.right().foldable()) {
                return rewriteDistanceFilter(comparison, dist.left(), dist.right(), number, comparisonType);
            } else if (dist.left().foldable()) {
                return rewriteDistanceFilter(comparison, dist.right(), dist.left(), number, comparisonType);
            }
        }
        return comparison;
    }

    private Expression rewriteDistanceFilter(
        EsqlBinaryComparison comparison,
        Expression spatialExp,
        Expression literalExp,
        Number number,
        ComparisonType comparisonType
    ) {
        Geometry geometry = SpatialRelatesUtils.makeGeometryFromLiteral(literalExp);
        if (geometry instanceof Point point) {
            double distance = number.doubleValue();
            Source source = comparison.source();
            if (comparisonType.lt) {
                distance = comparisonType.eq ? distance : Math.nextDown(distance);
                return new SpatialIntersects(source, spatialExp, makeCircleLiteral(point, distance, literalExp));
            } else if (comparisonType.gt) {
                distance = comparisonType.eq ? distance : Math.nextUp(distance);
                return new SpatialDisjoint(source, spatialExp, makeCircleLiteral(point, distance, literalExp));
            } else if (comparisonType.eq) {
                return new And(
                    source,
                    new SpatialIntersects(source, spatialExp, makeCircleLiteral(point, distance, literalExp)),
                    new SpatialDisjoint(source, spatialExp, makeCircleLiteral(point, Math.nextDown(distance), literalExp))
                );
            }
        }
        return comparison;
    }

    private Literal makeCircleLiteral(Point point, double distance, Expression literalExpression) {
        var circle = new Circle(point.getX(), point.getY(), distance);
        var wkb = WellKnownBinary.toWKB(circle, ByteOrder.LITTLE_ENDIAN);
        return new Literal(literalExpression.source(), new BytesRef(wkb), DataType.GEO_SHAPE);
    }

    /**
     * This enum captures the key differences between various inequalities as perceived from the spatial distance function.
     * In particular, we need to know which direction the inequality points, with lt=true meaning the left is expected to be smaller
     * than the right. And eq=true meaning we expect euality as well. We currently don't support Equals and NotEquals, so the third
     * field disables those.
     */
    enum ComparisonType {
        LTE(true, false, true),
        LT(true, false, false),
        GTE(false, true, true),
        GT(false, true, false),
        EQ(false, false, true);

        private final boolean lt;
        private final boolean gt;
        private final boolean eq;

        ComparisonType(boolean lt, boolean gt, boolean eq) {
            this.lt = lt;
            this.gt = gt;
            this.eq = eq;
        }

        static ComparisonType from(EsqlBinaryComparison.BinaryComparisonOperation op) {
            return switch (op) {
                case LT -> LT;
                case LTE -> LTE;
                case GT -> GT;
                case GTE -> GTE;
                default -> EQ;
            };
        }

        static ComparisonType invert(ComparisonType comparisonType) {
            return switch (comparisonType) {
                case LT -> GT;
                case LTE -> GTE;
                case GT -> LT;
                case GTE -> LTE;
                default -> EQ;
            };
        }
    }
}
