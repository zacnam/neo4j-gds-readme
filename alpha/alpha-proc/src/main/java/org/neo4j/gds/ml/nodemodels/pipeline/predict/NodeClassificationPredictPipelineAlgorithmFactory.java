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
package org.neo4j.gds.ml.nodemodels.pipeline.predict;

import org.neo4j.gds.BaseProc;
import org.neo4j.gds.GraphStoreAlgorithmFactory;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.core.model.ModelCatalog;
import org.neo4j.gds.core.utils.mem.AllocationTracker;
import org.neo4j.gds.core.utils.mem.MemoryEstimation;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.core.utils.progress.tasks.Task;
import org.neo4j.gds.core.utils.progress.tasks.Tasks;
import org.neo4j.gds.exceptions.MemoryEstimationNotImplementedException;
import org.neo4j.gds.ml.nodemodels.NodeClassificationPredictAlgorithmFactory;
import org.neo4j.gds.ml.nodemodels.NodeClassificationPredictConfig;
import org.neo4j.gds.ml.nodemodels.NodeClassificationPredictConfigImpl;

import java.util.List;

import static org.neo4j.gds.ml.nodemodels.pipeline.NodeClassificationPipelineCompanion.getTrainedNCPipelineModel;

public class NodeClassificationPredictPipelineAlgorithmFactory
    <CONFIG extends NodeClassificationPredictPipelineBaseConfig>
    extends GraphStoreAlgorithmFactory<NodeClassificationPredictPipelineExecutor, CONFIG>
{

    private final ModelCatalog modelCatalog;
    private final BaseProc caller;
    private final NodeClassificationPredictAlgorithmFactory<NodeClassificationPredictConfig> innerFactory;

    NodeClassificationPredictPipelineAlgorithmFactory(ModelCatalog modelCatalog, BaseProc caller) {
        super();
        this.modelCatalog = modelCatalog;
        this.caller = caller;
        this.innerFactory = new NodeClassificationPredictAlgorithmFactory<>(modelCatalog);
    }

    @Override
    protected Task progressTask(Graph graph, CONFIG config) {
        var trainingPipeline = getTrainedNCPipelineModel(
            modelCatalog,
            config.modelName(),
            config.username()
        ).customInfo()
            .trainingPipeline();

        return Tasks.task(
            taskName(),
            Tasks.iterativeFixed(
                "execute node property steps",
                () -> List.of(Tasks.leaf("step")),
                trainingPipeline.nodePropertySteps().size()
            ),
            innerFactory.progressTask(graph,innerConfig(config))
        );
    }

    private NodeClassificationPredictConfig innerConfig(CONFIG configuration) {
        return new NodeClassificationPredictConfigImpl(
            configuration.username(),
            CypherMapWrapper.create(configuration.toMap())
                .withEntry("includePredictedProbabilities",configuration.includePredictedProbabilities())
                .withoutEntry("predictedProbabilityProperty")
            );
    }

    @Override
    protected String taskName() {
        return "Node Classification Predict Pipeline";
    }

    @Override
    protected NodeClassificationPredictPipelineExecutor build(
        Graph graph,
        GraphStore graphStore,
        CONFIG configuration,
        AllocationTracker allocationTracker,
        ProgressTracker progressTracker
    ) {
        var model = getTrainedNCPipelineModel(
            modelCatalog,
            configuration.modelName(),
            configuration.username()
        );
        var nodeClassificationPipeline = model.customInfo().trainingPipeline();
        return new NodeClassificationPredictPipelineExecutor(
            nodeClassificationPipeline,
            configuration,
            caller,
            graphStore,
            configuration.graphName(),
            progressTracker,
            model.data()
        );
    }

    @Override
    public MemoryEstimation memoryEstimation(CONFIG configuration) {
        throw new MemoryEstimationNotImplementedException();
    }
}
