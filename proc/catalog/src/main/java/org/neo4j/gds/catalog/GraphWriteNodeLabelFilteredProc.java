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
package org.neo4j.gds.catalog;

import org.neo4j.gds.ProcPreconditions;
import org.neo4j.gds.beta.filter.NodesFilter;
import org.neo4j.gds.beta.filter.expression.ExpressionParser;
import org.neo4j.gds.config.WriteLabelConfig;
import org.neo4j.gds.core.concurrency.Pools;
import org.neo4j.gds.core.utils.ProgressTimer;
import org.neo4j.gds.core.utils.TerminationFlag;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.core.write.NodeLabelExporter;
import org.neo4j.gds.core.write.NodeLabelExporterBuilder;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;
import org.opencypher.v9_0.parser.javacc.ParseException;

import java.util.Map;
import java.util.stream.Stream;

import static org.neo4j.procedure.Mode.WRITE;

public class GraphWriteNodeLabelFilteredProc extends CatalogProc {

    @Context
    public NodeLabelExporterBuilder<? extends NodeLabelExporter> nodeLabelExporterBuilder;

    @Procedure(name = "gds.alpha.graph.nodeLabel.writeFiltered", mode = WRITE)
    @Description("Writes the given node Label to an online Neo4j database.")
    public Stream<Result> writeNodeLabelFiltered(
        @Name(value = "graphName") String graphName,
        @Name(value = "nodeLabel") String nodeLabel,
        @Name(value = "nodePropertyFilter") String nodePropertyFilter,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) throws ParseException {

        ProcPreconditions.check();

        var procedureConfig = WriteLabelConfig.of(configuration);

        var graphStore = graphStoreFromCatalog(graphName).graphStore();
        var filter = ExpressionParser.parse(nodePropertyFilter, Map.of());

        Result.Builder resultBuilder = new Result.Builder(graphName, nodeLabel);
        try (ProgressTimer ignored = ProgressTimer.start(resultBuilder::withWriteMillis)) {
            var filteredNodes = NodesFilter.filterNodes(
                graphStore,
                filter,
                procedureConfig.concurrency(),
                Map.of(),
                Pools.DEFAULT,
                ProgressTracker.NULL_TRACKER
            );

            var nodeLabelExporter = nodeLabelExporterBuilder
                .withIdMap(filteredNodes.idMap())
                .withTerminationFlag(TerminationFlag.wrap(transaction))
                .parallel(Pools.DEFAULT, procedureConfig.concurrency())
                .build();

            runWithExceptionLogging(
                "Node label writing failed",
                () -> nodeLabelExporter.write(nodeLabel)
            );

            resultBuilder.withNodeLabelsWritten(nodeLabelExporter.nodeLabelsWritten());
        }


        return Stream.of(resultBuilder.build());
    }

    @SuppressWarnings("unused")
    public static class Result {
        public final long writeMillis;
        public final String graphName;
        public final String nodeLabel;
        public final long nodeLabelsWritten;

        Result(long writeMillis, String graphName, String nodeLabel, long nodeLabelsWritten) {
            this.writeMillis = writeMillis;
            this.graphName = graphName;
            this.nodeLabel = nodeLabel;
            this.nodeLabelsWritten = nodeLabelsWritten;
        }

        static class Builder {
            private final String graphName;
            private final String nodeLabel;
            private long nodeLabelsWritten;
            private long writeMillis;

            Builder(String graphName, String nodeLabel) {
                this.graphName = graphName;
                this.nodeLabel = nodeLabel;
            }

            Builder withWriteMillis(long writeMillis) {
                this.writeMillis = writeMillis;
                return this;
            }

            Builder withNodeLabelsWritten(long propertiesWritten) {
                this.nodeLabelsWritten = propertiesWritten;
                return this;
            }

            Result build() {
                return new Result(writeMillis, graphName, nodeLabel, nodeLabelsWritten);
            }
        }
    }

}
