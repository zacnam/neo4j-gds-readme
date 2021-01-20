/*
 * Copyright (c) 2017-2021 "Neo4j,"
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
package org.neo4j.gds.model.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.gds.embeddings.graphsage.Layer;
import org.neo4j.gds.embeddings.graphsage.ModelData;
import org.neo4j.gds.embeddings.graphsage.SingleLabelFeatureFunction;
import org.neo4j.gds.embeddings.graphsage.algo.GraphSage;
import org.neo4j.gds.embeddings.graphsage.algo.GraphSageTrainConfig;
import org.neo4j.gds.embeddings.graphsage.algo.ImmutableGraphSageTrainConfig;
import org.neo4j.graphalgo.api.DefaultValue;
import org.neo4j.graphalgo.api.schema.GraphSchema;
import org.neo4j.graphalgo.core.model.Model;
import org.neo4j.graphalgo.gdl.GdlFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.graphalgo.utils.StringFormatting.formatWithLocale;

class ModelToFileExporterTest {

    private static final String FILE_NAME = "TestModel";

    private static final GraphSchema GRAPH_SCHEMA = GdlFactory
        .of("(n1:Node {a: 1.1})-[:R {rp: 4.2}]->(n2:Node {a: 1.337})")
        .build()
        .graphStore()
        .schema();

    private static final GraphSageTrainConfig TRAIN_CONFIG = ImmutableGraphSageTrainConfig.builder()
        .featureProperties(List.of("numEmployees", "numIngredients", "rating", "numPurchases"))
        .embeddingDimension(64)
        .modelName("testModel")
        .degreeAsProperty(true)
        .projectedFeatureDimension(5)
        .build();

    private static final Model<ModelData, GraphSageTrainConfig> MODEL = Model.of(
        "user1",
        "testModel",
        GraphSage.MODEL_TYPE,
        GRAPH_SCHEMA,
        ModelData.of(new Layer[]{}, new SingleLabelFeatureFunction()),
        TRAIN_CONFIG
    );

    @Test
    void shouldWriteToFile(@TempDir Path exportPath) throws IOException {
        assertThat(exportPath.resolve(FILE_NAME)).doesNotExist();

        ModelToFileExporter.toFile(exportPath, MODEL);

        var metaDataFileName = formatWithLocale("%s.%s", MODEL.name(), "meta");
        try (var metaDataInputStream = new FileInputStream(exportPath.resolve(metaDataFileName).toFile())) {
            var metaDataBytes = metaDataInputStream.readAllBytes();
            assertThat(metaDataBytes).isNotEmpty();
        }

        var modelDataFileName = formatWithLocale("%s.%s", MODEL.name(), "data");
        try(var modelDataInputStream = new FileInputStream(exportPath.resolve(modelDataFileName).toFile())) {
            var modelDataBytes = modelDataInputStream.readAllBytes();
            assertThat(modelDataBytes).isNotEmpty();
        }
    }

    @Test
    void shouldReadFromFile(@TempDir Path exportPath) throws IOException, ClassNotFoundException {
        ModelToFileExporter.toFile(exportPath, MODEL);
        Model<ModelData, GraphSageTrainConfig> deserializedModel = ModelToFileExporter.fromFile(exportPath, FILE_NAME);
        assertThat(deserializedModel)
            .usingRecursiveComparison()
            .withStrictTypeChecking()
            .ignoringFieldsOfTypes(DefaultValue.class)
            .isEqualTo(MODEL);
    }

}
