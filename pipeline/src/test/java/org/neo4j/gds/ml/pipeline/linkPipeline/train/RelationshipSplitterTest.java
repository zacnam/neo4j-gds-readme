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
package org.neo4j.gds.ml.pipeline.linkPipeline.train;

import org.junit.jupiter.api.Test;
import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.core.utils.TerminationFlag;
import org.neo4j.gds.core.utils.mem.MemoryRange;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.extension.GdlExtension;
import org.neo4j.gds.extension.GdlGraph;
import org.neo4j.gds.extension.Inject;
import org.neo4j.gds.ml.pipeline.linkPipeline.LinkPredictionSplitConfigImpl;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.gds.TestSupport.assertMemoryRange;

@GdlExtension
class RelationshipSplitterTest {

    @GdlGraph
    private static final String GRAPH =
        "CREATE " +
        "(a:N {scalar: 0, array: [-1.0, -2.0, 1.0, 1.0, 3.0]}), " +
        "(b:N {scalar: 4, array: [2.0, 1.0, -2.0, 2.0, 1.0]}), " +
        "(c:N {scalar: 0, array: [-3.0, 4.0, 3.0, 3.0, 2.0]}), " +
        "(d:N {scalar: 3, array: [1.0, 3.0, 1.0, -1.0, -1.0]}), " +
        "(e:N {scalar: 1, array: [-2.0, 1.0, 2.0, 1.0, -1.0]}), " +
        "(f:N {scalar: 0, array: [-1.0, -3.0, 1.0, 2.0, 2.0]}), " +
        "(g:N {scalar: 1, array: [3.0, 1.0, -3.0, 3.0, 1.0]}), " +
        "(h:N {scalar: 3, array: [-1.0, 3.0, 2.0, 1.0, -3.0]}), " +
        "(i:N {scalar: 3, array: [4.0, 1.0, 1.0, 2.0, 1.0]}), " +
        "(j:N {scalar: 4, array: [1.0, -4.0, 2.0, -2.0, 2.0]}), " +
        "(k:N {scalar: 0, array: [2.0, 1.0, 3.0, 1.0, 1.0]}), " +
        "(l:N {scalar: 1, array: [-1.0, 3.0, -2.0, 3.0, -2.0]}), " +
        "(m:N {scalar: 0, array: [4.0, 4.0, 1.0, 1.0, 1.0]}), " +
        "(n:N {scalar: 3, array: [1.0, -2.0, 3.0, 2.0, 3.0]}), " +
        "(o:N {scalar: 2, array: [-3.0, 3.0, -1.0, -1.0, 1.0]}), " +
        "" +
        "(a)-[:REL {weight: 2.0}]->(b), " +
        "(a)-[:REL {weight: 1.0}]->(c), " +
        "(b)-[:REL {weight: 3.0}]->(c), " +
        "(c)-[:REL {weight: 4.0}]->(d), " +
        "(e)-[:REL {weight: 5.0}]->(f), " +
        "(f)-[:REL {weight: 2.0}]->(g), " +
        "(h)-[:REL {weight: 2.0}]->(i), " +
        "(j)-[:REL {weight: 2.0}]->(k), " +
        "(k)-[:REL {weight: 2.0}]->(l), " +
        "(m)-[:REL {weight: 2.0}]->(n), " +
        "(n)-[:REL {weight: 4.0}]->(o), " +
        "(a)-[:REL {weight: 2.0}]->(d), " +
        "(b)-[:REL {weight: 2.0}]->(d), " +
        "(e)-[:REL {weight: 0.5}]->(g), " +
        "(j)-[:REL {weight: 2.0}]->(l), " +
        "(m)-[:REL {weight: 2.0}]->(o)";

    @Inject
    GraphStore graphStore;

    @Test
    void splitWeightedGraph() {
        var splitConfig = LinkPredictionSplitConfigImpl.builder()
            .trainFraction(0.3)
            .testFraction(0.3)
            .validationFolds(2)
            .negativeSamplingRatio(1.0)
            .build();

        RelationshipSplitter relationshipSplitter = new RelationshipSplitter(
            graphStore,
            splitConfig,
            ProgressTracker.NULL_TRACKER,
            TerminationFlag.RUNNING_TRUE
        );

        relationshipSplitter.splitRelationships(RelationshipType.of("REL"), List.of(NodeLabel.of("N")),  Optional.of(42L), Optional.of("weight"));

        var expectedRelTypes = Stream.of(
                splitConfig.trainRelationshipType(),
                splitConfig.testRelationshipType(),
                splitConfig.featureInputRelationshipType(),
                RelationshipType.of("REL")
            )
            .collect(Collectors.toList());

        assertThat(graphStore.relationshipTypes()).containsExactlyInAnyOrderElementsOf(expectedRelTypes);

        var expectedRelProperties = Map.of(
            splitConfig.featureInputRelationshipType(), Set.of("weight"),
            splitConfig.trainRelationshipType(), Set.of("label"),
            splitConfig.testRelationshipType(), Set.of("label"),
            RelationshipType.of("REL"), Set.of("weight")
        );

        Map<RelationshipType, Set<String>> actualRelProperties = graphStore
            .schema()
            .relationshipSchema()
            .properties()
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, i -> i.getValue().keySet()));

        assertThat(actualRelProperties).usingRecursiveComparison().isEqualTo(expectedRelProperties);
    }

    @Test
    void estimateWithDifferentTestFraction() {
        var splitConfigBuilder = LinkPredictionSplitConfigImpl.builder()
            .trainFraction(0.3)
            .validationFolds(3)
            .negativeSamplingRatio(1.0);

        var splitConfig = splitConfigBuilder.testFraction(0.2).build();
        var actualEstimation = RelationshipSplitter.splitEstimation(splitConfig, "REL", Optional.empty())
            .estimate(splitConfig.expectedGraphDimensions(100, 1_000), 4);

        assertMemoryRange(actualEstimation.memoryUsage(), MemoryRange.of(28_800, 35_840));

        splitConfig = splitConfigBuilder.testFraction(0.8).build();
        actualEstimation = RelationshipSplitter.splitEstimation(splitConfig, "REL", Optional.empty())
            .estimate(splitConfig.expectedGraphDimensions(100, 1_000), 4);

        // higher testFraction -> lower estimation as test-complement is smaller
        // the test_complement is kept until the end of all splitting
        assertMemoryRange(actualEstimation.memoryUsage(), MemoryRange.of(19_152, 32_912));
    }

    @Test
    void estimateWithDifferentTrainFraction() {
        var splitConfigBuilder = LinkPredictionSplitConfigImpl.builder()
            .testFraction(0.3)
            .validationFolds(3)
            .negativeSamplingRatio(1.0);

        var splitConfig = splitConfigBuilder.trainFraction(0.2).build();
        var actualEstimation = RelationshipSplitter.splitEstimation(splitConfig, "REL", Optional.empty())
            .estimate(splitConfig.expectedGraphDimensions(100, 1_000), 4);

        assertMemoryRange(actualEstimation.memoryUsage(), MemoryRange.of(27_200, 34_240));

        splitConfig = splitConfigBuilder.trainFraction(0.8).build();
        actualEstimation = RelationshipSplitter.splitEstimation(splitConfig, "REL", Optional.empty())
            .estimate(splitConfig.expectedGraphDimensions(100, 1_000), 4);

        assertMemoryRange(actualEstimation.memoryUsage(), MemoryRange.of(27_184, 40_944));
    }

    @Test
    void estimateWithDifferentNegativeSampling() {
        var splitConfigBuilder = LinkPredictionSplitConfigImpl.builder()
            .testFraction(0.3)
            .trainFraction(0.3)
            .validationFolds(3);

        var splitConfig = splitConfigBuilder.negativeSamplingRatio(1).build();
        var actualEstimation = RelationshipSplitter.splitEstimation(splitConfig, "REL", Optional.empty())
            .estimate(splitConfig.expectedGraphDimensions(100, 1_000), 4);

        assertMemoryRange(actualEstimation.memoryUsage(), MemoryRange.of(27184, 35_344));

        splitConfig = splitConfigBuilder.negativeSamplingRatio(4).build();
        actualEstimation = RelationshipSplitter.splitEstimation(splitConfig, "REL", Optional.empty())
            .estimate(splitConfig.expectedGraphDimensions(100, 1_000), 4);

        assertMemoryRange(actualEstimation.memoryUsage(), MemoryRange.of(39424, 59_824));
    }

    @Test
    void estimateWithDifferentRelationshipWeight() {
        var splitConfig = LinkPredictionSplitConfigImpl.builder()
            .testFraction(0.3)
            .trainFraction(0.3)
            .validationFolds(3).negativeSamplingRatio(1).build();
        var unweightedEstimation = RelationshipSplitter.splitEstimation(splitConfig, "REL", Optional.empty())
            .estimate(splitConfig.expectedGraphDimensions(100, 1_000), 4);

        var weightedEstimation = RelationshipSplitter.splitEstimation(splitConfig, "REL", Optional.of("weight"))
            .estimate(splitConfig.expectedGraphDimensions(100, 1_000), 4);

        assertThat(unweightedEstimation.memoryUsage()).isNotEqualTo(weightedEstimation.memoryUsage());
    }
}
