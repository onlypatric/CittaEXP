package it.patric.cittaexp.persistence.runtime;

import dev.patric.commonlib.api.CommonComponent;
import dev.patric.commonlib.api.CommonContext;
import dev.patric.commonlib.api.capability.CapabilityRegistry;
import dev.patric.commonlib.api.capability.CapabilityStatus;
import dev.patric.commonlib.api.capability.StandardCapabilities;
import dev.patric.commonlib.api.persistence.SchemaMigration;
import dev.patric.commonlib.api.persistence.SchemaMigrationContext;
import dev.patric.commonlib.api.persistence.SchemaMigrationService;
import it.patric.cittaexp.core.port.CityTreasuryLedgerPort;
import it.patric.cittaexp.core.port.EconomyStatePort;
import it.patric.cittaexp.core.port.StaffApprovalPort;
import it.patric.cittaexp.core.port.TaxPolicyPort;
import it.patric.cittaexp.persistence.config.PersistenceConfigLoader;
import it.patric.cittaexp.persistence.config.PersistenceSettings;
import it.patric.cittaexp.persistence.port.CityReadPort;
import it.patric.cittaexp.persistence.port.CityTxPort;
import it.patric.cittaexp.persistence.port.CityWritePort;
import it.patric.cittaexp.persistence.port.DomainEventOutboxPort;

public final class CittaExpPersistenceComponent implements CommonComponent {

    private static final String MIGRATION_NAMESPACE = "cittaexp-core";

    private CityPersistenceService persistenceService;
    private ReplayService replayService;

    @Override
    public String id() {
        return "cittaexp-persistence";
    }

    @Override
    public void onLoad(CommonContext context) {
        PersistenceSettings settings = new PersistenceConfigLoader(context.plugin()).load();
        PersistenceModeManager modeManager = new PersistenceModeManager(settings, context.logger());
        modeManager.initialize();

        SchemaInstaller schemaInstaller = new SchemaInstaller(context.plugin(), settings, modeManager);
        this.persistenceService = new CityPersistenceService(
                context.plugin(),
                settings,
                modeManager,
                schemaInstaller,
                context.logger()
        );

        context.services().register(PersistenceSettings.class, settings);
        context.services().register(PersistenceModeManager.class, modeManager);
        context.services().register(CityReadPort.class, persistenceService);
        context.services().register(CityWritePort.class, persistenceService);
        context.services().register(CityTxPort.class, persistenceService);
        context.services().register(DomainEventOutboxPort.class, persistenceService);
        context.services().register(PersistenceStatusService.class, persistenceService);
        context.services().register(TaxPolicyPort.class, persistenceService);
        context.services().register(CityTreasuryLedgerPort.class, persistenceService);
        context.services().register(EconomyStatePort.class, persistenceService);
        context.services().register(StaffApprovalPort.class, persistenceService);

        // Always enforce baseline schema on boot so backend switches (SQLite <-> MySQL)
        // do not depend on migration-version deltas.
        persistenceService.ensureSchema();

        SchemaMigrationService migrationService = context.services().require(SchemaMigrationService.class);
        migrationService.register(MIGRATION_NAMESPACE, new SchemaMigration() {
            @Override
            public int fromVersion() {
                return 0;
            }

            @Override
            public int toVersion() {
                return 1;
            }

            @Override
            public void migrate(SchemaMigrationContext migrationContext) {
                persistenceService.ensureSchema();
            }
        });
        migrationService.register(MIGRATION_NAMESPACE, new SchemaMigration() {
            @Override
            public int fromVersion() {
                return 1;
            }

            @Override
            public int toVersion() {
                return 2;
            }

            @Override
            public void migrate(SchemaMigrationContext migrationContext) {
                persistenceService.ensureSchema();
            }
        });
        migrationService.register(MIGRATION_NAMESPACE, new SchemaMigration() {
            @Override
            public int fromVersion() {
                return 2;
            }

            @Override
            public int toVersion() {
                return 3;
            }

            @Override
            public void migrate(SchemaMigrationContext migrationContext) {
                persistenceService.ensureSchema();
            }
        });
        migrationService.migrateToLatest(MIGRATION_NAMESPACE);

        CapabilityRegistry capabilities = context.services().require(CapabilityRegistry.class);
        if (modeManager.currentMode() == PersistenceRuntimeMode.UNAVAILABLE) {
            capabilities.publish(
                    StandardCapabilities.PERSISTENCE_SQL,
                    CapabilityStatus.unavailable("cittaexp-persistence-unavailable")
            );
        } else {
            capabilities.publish(
                    StandardCapabilities.PERSISTENCE_SQL,
                    CapabilityStatus.available("cittaexp:mysql-primary-sqlite-fallback:v1")
            );
        }

        this.replayService = new ReplayService(persistenceService, settings, context.scheduler(), context.logger());
    }

    @Override
    public void onEnable(CommonContext context) {
        if (replayService != null) {
            replayService.start();
        }
    }

    @Override
    public void onDisable(CommonContext context) {
        if (replayService != null) {
            replayService.stop();
        }
    }
}
