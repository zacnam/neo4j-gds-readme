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
package org.neo4j.graphalgo.core.utils.export.db;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.neo4j.common.Validator;
import org.neo4j.configuration.Config;
import org.neo4j.graphalgo.compat.Neo4jProxy;
import org.neo4j.graphalgo.core.Settings;
import org.neo4j.graphalgo.core.utils.export.Exporter;
import org.neo4j.graphalgo.core.utils.export.GraphStoreExportConfig;
import org.neo4j.graphalgo.core.utils.export.GraphStoreInput;
import org.neo4j.internal.batchimport.AdditionalInitialIds;
import org.neo4j.internal.batchimport.BatchImporterFactory;
import org.neo4j.internal.batchimport.Configuration;
import org.neo4j.internal.batchimport.ImportLogic;
import org.neo4j.internal.batchimport.input.Collector;
import org.neo4j.internal.batchimport.input.Input;
import org.neo4j.internal.batchimport.staging.ExecutionMonitors;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.layout.Neo4jLayout;
import org.neo4j.io.pagecache.tracing.PageCacheTracer;
import org.neo4j.kernel.impl.store.format.RecordFormatSelector;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.kernel.lifecycle.LifeSupport;
import org.neo4j.logging.internal.LogService;
import org.neo4j.logging.internal.NullLogService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.neo4j.graphalgo.utils.StringFormatting.formatWithLocale;
import static org.neo4j.io.ByteUnit.mebiBytes;
import static org.neo4j.kernel.impl.scheduler.JobSchedulerFactory.createScheduler;

public class DatabaseExporter extends Exporter {

    private final Path neo4jHome;
    private final GraphStoreExportConfig config;
    private final FileSystemAbstraction fs;

    public DatabaseExporter(
        GraphStoreInput graphStoreInput,
        GraphDatabaseAPI api,
        GraphStoreExportConfig config
    ) {
        super(graphStoreInput);
        this.neo4jHome = Neo4jProxy.homeDirectory(api.databaseLayout());
        this.config = config;
        this.fs = api.getDependencyResolver().resolveDependency(FileSystemAbstraction.class);
    }

    @Override
    public void export() {
        run(false);
    }


    /**
     * Runs with default configuration geared towards
     * unit/integration test environments, for example,
     * lower default buffer sizes.
     */
    @TestOnly
    public void runFromTests() {
        run(true);
    }

    private void run(boolean defaultSettingsSuitableForTests) {
        DIRECTORY_IS_WRITABLE.validate(neo4jHome);
        var databaseConfig = Config.defaults(Settings.neo4jHome(), neo4jHome);
        var databaseLayout = Neo4jLayout.of(databaseConfig).databaseLayout(config.dbName());
        var importConfig = getImportConfig(defaultSettingsSuitableForTests);

        var lifeSupport = new LifeSupport();

        try {
            LogService logService;
            if (config.enableDebugLog()) {
                var storeInternalLogPath = databaseConfig.get(Settings.storeInternalLogPath());
                logService = Neo4jProxy.logProviderForStoreAndRegister(storeInternalLogPath, fs, lifeSupport);
            } else {
                logService = NullLogService.getInstance();
            }
            var jobScheduler = lifeSupport.add(createScheduler());

            lifeSupport.start();

            Input input = Neo4jProxy.batchInputFrom(graphStoreInput);

            var metaDataPath = Neo4jProxy.metadataStore(databaseLayout);
            var dbExists = Files.exists(metaDataPath) && Files.isReadable(metaDataPath);
            if (dbExists) {
                throw new IllegalArgumentException(formatWithLocale(
                    "The database [%s] already exists. The graph export procedure can only create new databases.",
                    config.dbName()
                ));
            }

            var importer = Neo4jProxy.instantiateBatchImporter(
                BatchImporterFactory.withHighestPriority(),
                databaseLayout,
                fs,
                null, // no external page cache
                PageCacheTracer.NULL,
                importConfig,
                logService,
                ExecutionMonitors.invisible(),
                AdditionalInitialIds.EMPTY,
                databaseConfig,
                RecordFormatSelector.selectForConfig(databaseConfig, logService.getInternalLogProvider()),
                ImportLogic.NO_MONITOR,
                jobScheduler,
                Collector.EMPTY
            );
            importer.doImport(input);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            lifeSupport.shutdown();
        }
    }

    @NotNull
    private org.neo4j.internal.batchimport.Configuration getImportConfig(boolean defaultSettingsSuitableForTests) {
        return new org.neo4j.internal.batchimport.Configuration() {
            @Override
            public int maxNumberOfProcessors() {
                return config.writeConcurrency();
            }

            @Override
            public long pageCacheMemory() {
                return defaultSettingsSuitableForTests ? mebiBytes(8) : Configuration.super.pageCacheMemory();
            }

            @Override
            public boolean highIO() {
                return false;
            }
        };
    }

    public static final Validator<Path> DIRECTORY_IS_WRITABLE = value -> {
        try {
            Files.createDirectories(value);
            if (!Files.isDirectory(value)) {
                throw new IllegalArgumentException("'" + value + "' is not a directory");
            }
            if (!Files.isWritable(value)) {
                throw new IllegalArgumentException("Directory '" + value + "' not writable");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Directory '" + value + "' not writable: ", e);
        }
    };
}
