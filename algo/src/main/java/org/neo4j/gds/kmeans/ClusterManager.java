/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gds.kmeans;

import org.neo4j.gds.api.nodeproperties.ValueType;
import org.neo4j.gds.api.properties.nodes.NodePropertyValues;
import org.neo4j.gds.core.utils.Intersections;

import java.util.Arrays;
import java.util.List;

abstract class ClusterManager {

    final long[] nodesInCluster;
    final NodePropertyValues nodePropertyValues;
    final int dimensions;
    final int k;

    public ClusterManager(NodePropertyValues values, int dimensions, int k) {
        this.dimensions = dimensions;
        this.k = k;
        this.nodePropertyValues = values;
        this.nodesInCluster = new long[k];
    }

    abstract void initialAssignCluster(int i, long id);

    abstract void reset();

    abstract void normalizeClusters();

    abstract void updateFromTask(KmeansTask task);

    void initializeCenters(List<Long> initialCenterIds) {
        int clusterUpdateId = 0;
        for (Long currentId : initialCenterIds) {
            initialAssignCluster(clusterUpdateId++, currentId);
        }
    }

    abstract int findClosestCenter(long nodeId);

    static ClusterManager createClusterManager(NodePropertyValues values, int dimensions, int k) {
        if (values.valueType() == ValueType.FLOAT_ARRAY) {
            return new FloatClusterManager(values, dimensions, k);
        }
        return new DoubleClusterManager(values, dimensions, k);
    }
}

class FloatClusterManager extends ClusterManager {
    private final float[][] clusterCenters;

    public FloatClusterManager(NodePropertyValues values, int dimensions, int k) {
        super(values, dimensions, k);
        this.clusterCenters = new float[k][dimensions];
    }


    @Override
    public void reset() {
        for (int centerId = 0; centerId < k; ++centerId) {
            nodesInCluster[centerId] = 0;
            Arrays.fill(clusterCenters[centerId], 0.0f);
        }
    }

    @Override
    public void normalizeClusters() {
        for (int centreId = 0; centreId < k; ++centreId) {
            for (int dimension = 0; dimension < dimensions; ++dimension)
                clusterCenters[centreId][dimension] /= (float) nodesInCluster[centreId];
        }
    }

    @Override
    public void initialAssignCluster(int i, long id) {
        float[] cluster = nodePropertyValues.floatArrayValue(id);
        System.arraycopy(cluster, 0, clusterCenters[i], 0, cluster.length);
    }

    @Override
    public void updateFromTask(KmeansTask task) {
        var floatKmeansTask = (FloatKmeansTask) task;
        for (int centerId = 0; centerId < k; ++centerId) {
            nodesInCluster[centerId] += task.getNumAssignedAtCenter(centerId);
            for (int dimension = 0; dimension < dimensions; ++dimension) {
                clusterCenters[centerId][dimension] += floatKmeansTask.getCenterContribution(centerId)[dimension];
            }
        }
    }

    private float floatEuclidean(float[] left, float[] right) {
        return (float) Math.sqrt(Intersections.sumSquareDelta(left, right, right.length));
    }

    @Override
    public int findClosestCenter(long nodeId) {
        var property = nodePropertyValues.floatArrayValue(nodeId);
        int community = 0;
        float smallestDistance = Float.MAX_VALUE;
        for (int centerId = 0; centerId < k; ++centerId) {
            float distance = floatEuclidean(property, clusterCenters[centerId]);
            if (Float.compare(distance, smallestDistance) < 0) {
                smallestDistance = distance;
                community = centerId;
            }
        }
        return community;
    }
}

class DoubleClusterManager extends ClusterManager {
    private final double[][] clusterCenters;

    public DoubleClusterManager(NodePropertyValues values, int dimensions, int k) {
        super(values, dimensions, k);
        this.clusterCenters = new double[k][dimensions];
    }

    @Override
    public void reset() {
        for (int centerId = 0; centerId < k; ++centerId) {
            nodesInCluster[centerId] = 0;
            Arrays.fill(clusterCenters[centerId], 0.0d);
        }
    }

    @Override
    public void normalizeClusters() {
        for (int centreId = 0; centreId < k; ++centreId) {
            for (int dimension = 0; dimension < dimensions; ++dimension)
                clusterCenters[centreId][dimension] /= (double) nodesInCluster[centreId];
        }
    }

    @Override
    public void updateFromTask(KmeansTask task) {
        var doubleKmeansTask = (DoubleKmeansTask) task;
        for (int centerId = 0; centerId < k; ++centerId) {
            nodesInCluster[centerId] += task.getNumAssignedAtCenter(centerId);

            for (int dimension = 0; dimension < dimensions; ++dimension) {
                clusterCenters[centerId][dimension] += doubleKmeansTask.getCenterContribution(centerId)[dimension];
            }
        }
    }

    @Override
    public void initialAssignCluster(int i, long id) {
        double[] cluster = nodePropertyValues.doubleArrayValue(id);
        System.arraycopy(cluster, 0, clusterCenters[i], 0, cluster.length);
    }

    private double doubleEuclidean(double[] left, double[] right) {
        return Math.sqrt(Intersections.sumSquareDelta(left, right, right.length));
    }

    @Override
    public int findClosestCenter(long nodeId) {
        var property = nodePropertyValues.doubleArrayValue(nodeId);
        int community = 0;
        double smallestDistance = Double.MAX_VALUE;
        for (int centerId = 0; centerId < k; ++centerId) {
            double distance = doubleEuclidean(property, clusterCenters[centerId]);
            if (Double.compare(distance, smallestDistance) < 0) {
                smallestDistance = distance;
                community = centerId;
            }
        }
        return community;
    }

}
