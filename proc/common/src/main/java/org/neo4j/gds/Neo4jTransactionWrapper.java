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
package org.neo4j.gds;

import org.neo4j.gds.api.GdsTransactionApi;
import org.neo4j.gds.config.BaseConfig;
import org.neo4j.gds.core.utils.TerminationFlag;
import org.neo4j.gds.executor.AlgorithmMetaData;
import org.neo4j.graphdb.Node;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.impl.coreapi.InternalTransaction;

public class Neo4jTransactionWrapper implements GdsTransactionApi {

    private final KernelTransaction kernelTransaction;
    private final InternalTransaction internalTransaction;

    public Neo4jTransactionWrapper(KernelTransaction kernelTransaction) {
        this.kernelTransaction = kernelTransaction;
        this.internalTransaction = kernelTransaction.internalTransaction();
    }

    @Override
    public void registerCloseableResource(AutoCloseable resource, Runnable action) {
        try(var statement = kernelTransaction.acquireStatement()) {
            statement.registerCloseableResource(resource);
            action.run();
        }
    }

    @Override
    public <CONFIG extends BaseConfig> void setAlgorithmMetaData(CONFIG algoConfig) {
        if (kernelTransaction == null) {
            return;
        }
        var metaData = kernelTransaction.getMetaData();
        if (metaData instanceof AlgorithmMetaData) {
            ((AlgorithmMetaData) metaData).set(algoConfig);
        }
    }

    @Override
    public TerminationFlag terminationFlag() {
        return TerminationFlag.wrap(kernelTransaction);
    }

    @Override
    public Node getNodeById(long id) {
        return internalTransaction.getNodeById(id);
    }
}
