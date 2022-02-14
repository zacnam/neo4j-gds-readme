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
package org.neo4j.gds.ml.linkmodels.pipeline.logisticRegression;

import org.apache.commons.lang3.mutable.MutableInt;
import org.neo4j.gds.core.utils.paged.HugeDoubleArray;
import org.neo4j.gds.core.utils.paged.HugeObjectArray;
import org.neo4j.gds.mem.MemoryUsage;
import org.neo4j.gds.ml.Objective;
import org.neo4j.gds.ml.core.Dimensions;
import org.neo4j.gds.ml.core.Variable;
import org.neo4j.gds.ml.core.batch.Batch;
import org.neo4j.gds.ml.core.functions.Constant;
import org.neo4j.gds.ml.core.functions.ConstantScale;
import org.neo4j.gds.ml.core.functions.EWiseAddMatrixScalar;
import org.neo4j.gds.ml.core.functions.ElementSum;
import org.neo4j.gds.ml.core.functions.L2NormSquared;
import org.neo4j.gds.ml.core.functions.LogisticLoss;
import org.neo4j.gds.ml.core.functions.MatrixMultiplyWithTransposedSecondOperand;
import org.neo4j.gds.ml.core.functions.Sigmoid;
import org.neo4j.gds.ml.core.functions.Weights;
import org.neo4j.gds.ml.core.tensor.Matrix;
import org.neo4j.gds.ml.core.tensor.Scalar;
import org.neo4j.gds.ml.core.tensor.Tensor;
import org.neo4j.gds.ml.core.tensor.Vector;

import java.util.List;
import java.util.Optional;

public class LinkLogisticRegressionObjective implements Objective<LinkLogisticRegressionData> {
    private final LinkLogisticRegressionData modelData;
    private final double penalty;
    private final HugeObjectArray<double[]> linkFeatures;
    private final HugeDoubleArray targets;

    public LinkLogisticRegressionObjective(
        LinkLogisticRegressionData llrData,
        double penalty,
        HugeObjectArray<double[]> linkFeatures,
        HugeDoubleArray targets
    ) {
        this.modelData = llrData;
        this.penalty = penalty;
        this.linkFeatures = linkFeatures;
        this.targets = targets;
    }

    public static long sizeOfBatchInBytes(int batchSize, int featureDim, boolean useBias) {
        // we consider each variable in the computation graph
        var batchedTargets = Matrix.sizeInBytes(batchSize, 1);
        var features = Matrix.sizeInBytes(batchSize, featureDim);

        var weightedFeatures = MatrixMultiplyWithTransposedSecondOperand.sizeInBytes(
            Dimensions.matrix(batchSize, featureDim),
            Dimensions.matrix(1, featureDim)
        );

        var sigmoid = weightedFeatures;
        var unpenalizedLoss = Scalar.sizeInBytes();
        var l2norm = Scalar.sizeInBytes();
        var constantScale = Scalar.sizeInBytes();
        var elementSum = Scalar.sizeInBytes();

        var maybeBias = useBias ? weightedFeatures : 0;
        var penalty = l2norm + constantScale;

        // 2 * x == computing data and gradient for this computation variable
        return MemoryUsage.sizeOfInstance(LinkLogisticRegressionPredictor.class) +
               Weights.sizeInBytes(1, featureDim) + // only gradient as data is the model data
               batchedTargets +
               features +
               2 * weightedFeatures +
               2 * maybeBias +
               2 * sigmoid +
               2 * unpenalizedLoss +
               2 * penalty +
               2 * elementSum;
    }

    @Override
    public List<Weights<? extends Tensor<?>>> weights() {
        Optional<Weights<Scalar>> bias = modelData.bias();
        if (bias.isPresent()) {
            Weights<Scalar> weights = bias.get();
            return List.of(modelData.weights(), weights);
        } else {
            return List.of(modelData.weights());
        }
    }

    @Override
    public Variable<Scalar> loss(Batch relationshipBatch, long trainSize) {
        Constant<Vector> batchedTargets = makeTargetsArray(relationshipBatch);
        Constant<Matrix> features = features(relationshipBatch, linkFeatures);
        Variable<Matrix> weightedFeatures = MatrixMultiplyWithTransposedSecondOperand.of(features, modelData.weights());

        LogisticLoss unpenalizedLoss;

        Optional<Weights<Scalar>> potentialBias = modelData.bias();
        if (potentialBias.isPresent()) {
            Weights<Scalar> bias = potentialBias.get();
            var weightedFeaturesWithBias = new EWiseAddMatrixScalar(weightedFeatures, bias);
            var predictions = new Sigmoid<>(weightedFeaturesWithBias);
            unpenalizedLoss = new LogisticLoss(modelData.weights(), bias, predictions, features, batchedTargets);
        } else {
            var predictions = new Sigmoid<>(weightedFeatures);
            unpenalizedLoss = new LogisticLoss(modelData.weights(), predictions, features, batchedTargets);
        }

        var penaltyVariable = new ConstantScale<>(
            new L2NormSquared(modelData.weights()),
            relationshipBatch.size() * penalty / trainSize
        );

        return new ElementSum(List.of(unpenalizedLoss, penaltyVariable));
    }

    @Override
    public LinkLogisticRegressionData modelData() {
        return modelData;
    }

    Constant<Vector> makeTargetsArray(Batch batch) {
        var batchedTargets = new Vector(batch.size());
        var batchOffset = new MutableInt();

        batch.nodeIds().forEach(
            relationshipId -> batchedTargets.setDataAt(batchOffset.getAndIncrement(), targets.get(relationshipId))
        );

        return new Constant<>(batchedTargets);
    }

    /**
     * @param batch Set of relationship ids
     * @param linkFeatures LinkFeatures for relationships
     */
    Constant<Matrix> features(Batch batch, HugeObjectArray<double[]> linkFeatures) {
        assert linkFeatures.size() > 0;

        // assume the batch contains relationship ids
        int rows = batch.size();
        int cols = linkFeatures.get(0).length;
        var batchFeatures = new Matrix(rows, cols);
        var batchFeaturesOffset = new MutableInt();

        batch.nodeIds().forEach(id -> batchFeatures.setRow(batchFeaturesOffset.getAndIncrement(), linkFeatures.get(id)));

        return new Constant<>(batchFeatures);
    }
}
