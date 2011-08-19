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

package org.elasticsearch.index.search.geo;

import org.elasticsearch.ElasticSearchIllegalArgumentException;
import org.elasticsearch.common.unit.DistanceUnit;

/**
 * Geo distance calculation.
 *
 * @author kimchy (shay.banon)
 */
public enum GeoDistance {
    /**
     * Calculates distance as points on a plane. Faster, but less accurate than {@link #ARC}.
     */
    PLANE() {
        @Override public double calculate(double sourceLatitude, double sourceLongitude, double targetLatitude, double targetLongitude, DistanceUnit unit) {
            double px = targetLongitude - sourceLongitude;
            double py = targetLatitude - sourceLatitude;
            return Math.sqrt(px * px + py * py) * unit.getDistancePerDegree();
        }

        @Override public double normalize(double distance, DistanceUnit unit) {
            return distance;
        }
    },
    /**
     * Calculates distance factor.
     */
    FACTOR() {
        @Override public double calculate(double sourceLatitude, double sourceLongitude, double targetLatitude, double targetLongitude, DistanceUnit unit) {
            // TODO: we might want to normalize longitude as we did in LatLng...
            double longitudeDifference = targetLongitude - sourceLongitude;
            double a = Math.toRadians(90D - sourceLatitude);
            double c = Math.toRadians(90D - targetLatitude);
            return (Math.cos(a) * Math.cos(c)) + (Math.sin(a) * Math.sin(c) * Math.cos(Math.toRadians(longitudeDifference)));
        }

        @Override public double normalize(double distance, DistanceUnit unit) {
            return Math.cos(distance / unit.getEarthRadius());
        }
    },
    /**
     * Calculates distance as points in a globe.
     */
    ARC() {
        @Override public double calculate(double sourceLatitude, double sourceLongitude, double targetLatitude, double targetLongitude, DistanceUnit unit) {
            // TODO: we might want to normalize longitude as we did in LatLng...
            double longitudeDifference = targetLongitude - sourceLongitude;
            double a = Math.toRadians(90D - sourceLatitude);
            double c = Math.toRadians(90D - targetLatitude);
            double factor = (Math.cos(a) * Math.cos(c)) + (Math.sin(a) * Math.sin(c) * Math.cos(Math.toRadians(longitudeDifference)));

            if (factor < -1D) {
                return Math.PI * unit.getEarthRadius();
            } else if (factor >= 1D) {
                return 0;
            } else {
                return Math.acos(factor) * unit.getEarthRadius();
            }
        }

        @Override public double normalize(double distance, DistanceUnit unit) {
            return distance;
        }
    };

    public abstract double normalize(double distance, DistanceUnit unit);

    public abstract double calculate(double sourceLatitude, double sourceLongitude, double targetLatitude, double targetLongitude, DistanceUnit unit);

    public static GeoDistance fromString(String s) {
        if ("plane".equals(s)) {
            return PLANE;
        } else if ("arc".equals(s)) {
            return ARC;
        } else if ("factor".equals(s)) {
            return FACTOR;
        }
        throw new ElasticSearchIllegalArgumentException("No geo distance for [" + s + "]");
    }
}
