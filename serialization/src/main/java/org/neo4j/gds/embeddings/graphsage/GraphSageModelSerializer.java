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
package org.neo4j.gds.embeddings.graphsage;

import org.neo4j.gds.embeddings.graphsage.algo.GraphSageTrainConfig;
import org.neo4j.graphalgo.config.GraphSageTrainConfigSerializer;
import org.neo4j.graphalgo.core.model.Model;
import org.neo4j.graphalgo.core.model.ModelMetaDataSerializer;
import org.neo4j.graphalgo.core.model.proto.GraphSageProto;
import org.neo4j.graphalgo.core.model.proto.ModelProto;

import java.io.IOException;

public final class GraphSageModelSerializer {

    private GraphSageModelSerializer() {}

    public static GraphSageProto.GraphSageModel toSerializable(Model<ModelData, GraphSageTrainConfig> model) throws
        IOException {
        var modelDataBuilder = GraphSageProto.ModelData.newBuilder();
        for (int i = 0; i < modelData.layers().length; i++) {
            GraphSageProto.Layer layer = LayerSerializer.toSerializable(modelData.layers()[i]);
            modelDataBuilder.addLayers(i, layer);
        }

        return GraphSageProto.GraphSageModel.newBuilder()
            .setData(modelDataBuilder)
            .setFeatureFunction(FeatureFunctionSerializer.toSerializable(modelData.featureFunction()))
            .build();
    }

    public static Model<ModelData, GraphSageTrainConfig> fromSerializable(
        GraphSageProto.GraphSageModel protoModel,
        ModelProto.ModelMetaData modelMetaData
    ) throws
        IOException, ClassNotFoundException {

        var modelBuilder =
            ModelMetaDataSerializer.<ModelData, GraphSageTrainConfig>fromSerializable(modelMetaData);
        return modelBuilder.data(
            ModelData.of(
                LayerSerializer.fromSerializable(protoModel.getData().getLayersList()),
                FeatureFunctionSerializer.fromSerializable(
                    protoModel.getFeatureFunction(),
                    GraphSageTrainConfigSerializer.fromSerializable(modelMetaData.getGraphSageTrainConfig())
                )
            )
        )
            .customInfo(Model.Mappable.EMPTY)
            .build();
    }
}
