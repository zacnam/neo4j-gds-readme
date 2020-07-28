/*
 * Copyright (c) 2017-2020 "Neo4j,"
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
package org.neo4j.graphalgo.core;

import org.neo4j.graphalgo.api.NodeProperties;
import org.neo4j.graphalgo.api.nodeproperties.ValueType;

public class IdentityProperties implements NodeProperties {
    private final long expectedPropertyCount;

    public IdentityProperties(long expectedPropertyCount) {
        this.expectedPropertyCount = expectedPropertyCount;
    }

    @Override
    public double getDouble(long nodeId) {
        return nodeId;
    }

    @Override
    public long getLong(long nodeId) {
        return nodeId;
    }

    @Override
    public ValueType getType() {
        return ValueType.LONG;
    }

    @Override
    public long size() {
        return expectedPropertyCount;
    }
}