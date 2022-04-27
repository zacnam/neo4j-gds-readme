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
package org.neo4j.gds.core.cypher;

import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.api.GraphStoreAdapter;
import org.neo4j.gds.api.IdMap;
import org.neo4j.gds.api.properties.nodes.NodePropertyValues;
import org.neo4j.token.TokenHolders;

import java.util.Set;
import java.util.function.Consumer;

public class CypherGraphStore extends GraphStoreAdapter implements NodeLabelUpdater {

    private final CypherIdMap cypherIdMap;

    private RelationshipIds relationshipIds;
    private Consumer<String> propertyRemovalCallback;
    private Consumer<String> propertyAddedCallback;

    public CypherGraphStore(GraphStore graphStore) {
        super(graphStore);
        this.cypherIdMap = new CypherIdMap(super.nodes());
    }

    public void initialize(TokenHolders tokenHolders) {
        this.relationshipIds = RelationshipIds.fromGraphStore(innerGraphStore(), tokenHolders);
    }

    public void registerPropertyRemovalCallback(Consumer<String> propertyRemovalCallback) {
        this.propertyRemovalCallback = propertyRemovalCallback;
    }

    public void registerPropertyAddedCallback(Consumer<String> propertyAddedCallback) {
        this.propertyAddedCallback = propertyAddedCallback;
    }

    @Override
    public IdMap nodes() {
        return this.cypherIdMap;
    }

    @Override
    public void addNodeLabel(NodeLabel nodeLabel) {
        this.cypherIdMap.addNodeLabel(nodeLabel);
    }

    @Override
    public void addLabelToNode(long nodeId, NodeLabel nodeLabel) {
        this.cypherIdMap.addLabelToNode(nodeId, nodeLabel);
    }

    @Override
    public void removeNodeProperty(String propertyKey) {
        super.removeNodeProperty(propertyKey);
        if (propertyRemovalCallback != null) {
            propertyRemovalCallback.accept(propertyKey);
        }
    }

    @Override
    public void addNodeProperty(
        Set<NodeLabel> nodeLabels, String propertyKey, NodePropertyValues propertyValues
    ) {
        super.addNodeProperty(nodeLabels, propertyKey, propertyValues);
        if (propertyAddedCallback != null) {
            propertyAddedCallback.accept(propertyKey);
        }
    }

    public RelationshipIds relationshipIds() {
        return this.relationshipIds;
    }
}
