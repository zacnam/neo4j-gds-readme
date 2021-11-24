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
package org.neo4j.gds.conductance;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.neo4j.gds.AlgoBaseProcTest;
import org.neo4j.gds.BaseProcTest;
import org.neo4j.gds.GdsCypher;
import org.neo4j.gds.api.DefaultValue;
import org.neo4j.gds.catalog.GraphCreateProc;
import org.neo4j.gds.catalog.GraphWriteNodePropertiesProc;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.core.loading.GraphStoreCatalog;
import org.neo4j.gds.extension.Neo4jGraph;
import org.neo4j.gds.impl.conductance.Conductance;
import org.neo4j.gds.impl.conductance.ConductanceConfig;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import static org.junit.jupiter.api.Assertions.assertEquals;

abstract class ConductanceProcTest<CONFIG extends ConductanceConfig> extends BaseProcTest implements
    AlgoBaseProcTest<Conductance, CONFIG, Conductance.Result> {

    static final String GRAPH_NAME = "myGraph";

    @Override
    public GraphDatabaseAPI graphDb() {
        return db;
    }

    @Neo4jGraph
    @Language("Cypher")
    private static final
    String DB_CYPHER =
        "CREATE" +
        "  (a:Label1 { community: 0 })" +
        ", (b:Label1 { community: 0 })" +
        ", (c:Label1 { community: 0 })" +
        ", (d:Label1 { community: 1 })" +
        ", (e:Label1 { community: 1 })" +
        ", (f:Label1 { community: 1 })" +
        ", (g:Label1 { community: 1 })" +

        ", (a)-[:TYPE1 {weight: 81.0}]->(b)" +
        ", (a)-[:TYPE1 {weight: 7.0}]->(d)" +
        ", (b)-[:TYPE1 {weight: 1.0}]->(d)" +
        ", (b)-[:TYPE1 {weight: 1.0}]->(g)" +
        ", (c)-[:TYPE1 {weight: 45.0}]->(b)" +
        ", (c)-[:TYPE1 {weight: 3.0}]->(e)" +
        ", (d)-[:TYPE1 {weight: 3.0}]->(c)" +
        ", (e)-[:TYPE1 {weight: 1.0}]->(b)" +
        ", (f)-[:TYPE1 {weight: 3.0}]->(a)" +
        ", (g)-[:TYPE1 {weight: 4.0}]->(c)" +
        ", (g)-[:TYPE1 {weight: 999.0}]->(g)";

    @BeforeEach
    void setupGraph() throws Exception {
        registerProcedures(
            getProcedureClazz(),
            GraphCreateProc.class,
            GraphWriteNodePropertiesProc.class
        );

        String createQuery = GdsCypher.call()
            .withNodeProperty("community", DefaultValue.of(0L))
            .withRelationshipProperty("weight", DefaultValue.of(1.0D))
            .loadEverything()
            .graphCreate(GRAPH_NAME)
            .yields();

        runQuery(createQuery);
    }

    @AfterEach
    void clearStore() {
        GraphStoreCatalog.removeAllLoadedGraphs();
    }

    @Override
    public void assertResultEquals(Conductance.Result result1, Conductance.Result result2) {
        assertEquals(result1.globalAverageConductance(), result2.globalAverageConductance());
    }

    @Override
    public CypherMapWrapper createMinimalConfig(CypherMapWrapper mapWrapper) {
        // In case we're not testing something explicit with concurrency we want a deterministic result.
        // For this reason we set concurrency to 1 and fix a randomSeed != -1.

        if (!mapWrapper.containsKey("communityProperty")) {
            mapWrapper = mapWrapper.withString("communityProperty", "community");
        }


        return mapWrapper;
    }
}