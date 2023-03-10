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
package org.neo4j.gds.embeddings.hashgnn;

import org.junit.jupiter.api.BeforeEach;
import org.neo4j.gds.AlgoBaseProcTest;
import org.neo4j.gds.BaseProcTest;
import org.neo4j.gds.ImmutableRelationshipProjections;
import org.neo4j.gds.MemoryEstimateTest;
import org.neo4j.gds.Orientation;
import org.neo4j.gds.RelationshipProjection;
import org.neo4j.gds.RelationshipProjections;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.catalog.GraphProjectProc;
import org.neo4j.gds.catalog.GraphWriteNodePropertiesProc;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.extension.Neo4jGraph;
import org.neo4j.graphdb.GraphDatabaseService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

abstract class HashGNNProcTest<CONFIG extends HashGNNConfig> extends BaseProcTest implements
    AlgoBaseProcTest<HashGNN, CONFIG, HashGNN.HashGNNResult>,
    MemoryEstimateTest<HashGNN, CONFIG, HashGNN.HashGNNResult> {

    @Neo4jGraph
    private static final String DB_CYPHER =
        "CREATE" +
        "  (a:N {f1: 1, f2: [0.0, 0.0]})" +
        ", (b:N {f1: 0, f2: [1.0, 0.0]})" +
        ", (c:N {f1: 0, f2: [0.0, 1.0]})" +
        ", (b)-[:R1]->(a)" +
        ", (b)-[:R2]->(c)";

    @BeforeEach
    void setupWritePropertiesProc() throws Exception {
        registerProcedures(
            GraphProjectProc.class,
            GraphWriteNodePropertiesProc.class,
            getProcedureClazz()
        );
    }

    @Override
    public GraphDatabaseService graphDb() {
        return db;
    }

    @Override
    public RelationshipProjections relationshipProjections() {
        return ImmutableRelationshipProjections.of(Map.of(
            RelationshipType.of("R1"), RelationshipProjection.of("R1", Orientation.NATURAL),
            RelationshipType.of("R2"), RelationshipProjection.of("R2", Orientation.NATURAL)
        ));
    }

    @Override
    public CypherMapWrapper createMinimalConfig(CypherMapWrapper userInput) {
        return userInput
            .withBoolean("heterogeneous", true)
            .withNumber("iterations", 2)
            .withNumber("embeddingDensity", 2)
            .withNumber("randomSeed", 42L)
            .withEntry("featureProperties", List.of("f1", "f2"));
    }

    @Override
    public void assertResultEquals(HashGNN.HashGNNResult result1, HashGNN.HashGNNResult result2) {
        assertThat(result1.embeddings().size()).isEqualTo(result2.embeddings().size());
        for (int i = 0; i < result1.embeddings().size(); i++) {
            assertThat(result1.embeddings().get(i)).containsExactly(result2.embeddings().get(i));
        }
    }

    @Override
    public List<String> nodeProperties() {
        return List.of("f1", "f2");
    }

}
