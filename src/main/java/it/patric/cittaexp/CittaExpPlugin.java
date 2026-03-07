package it.patric.cittaexp;

import dev.patric.commonlib.adapter.huskclaims.HuskClaimsAdapterComponent;
import dev.patric.commonlib.adapter.itemsadder.ItemsAdderAdapterComponent;
import dev.patric.commonlib.api.CommonRuntime;
import dev.patric.commonlib.api.ConfigService;
import dev.patric.commonlib.api.bootstrap.RuntimeBootstrap;
import dev.patric.commonlib.api.capability.CapabilityRegistry;
import dev.patric.commonlib.api.dialog.DialogService;
import dev.patric.commonlib.api.dialog.DialogTemplateRegistry;
import dev.patric.commonlib.api.error.OperationResult;
import dev.patric.commonlib.api.gui.GuiDefinitionRegistry;
import dev.patric.commonlib.api.gui.GuiSessionService;
import dev.patric.commonlib.api.itemsadder.ItemsAdderService;
import dev.patric.commonlib.api.MessageService;
import it.patric.cittaexp.capability.RuntimeUiCapabilityGate;
import it.patric.cittaexp.command.CittaExpPreviewCommand;
import it.patric.cittaexp.command.CityCommand;
import it.patric.cittaexp.core.port.HuskClaimsPort;
import it.patric.cittaexp.core.runtime.DefaultCityLifecycleService;
import it.patric.cittaexp.core.service.CityLifecycleDiagnosticsService;
import it.patric.cittaexp.core.service.CityModerationService;
import it.patric.cittaexp.data.DatabaseCityViewReadPort;
import it.patric.cittaexp.dialog.CreationDialogTemplates;
import it.patric.cittaexp.dialog.showcase.DialogShowcaseService;
import it.patric.cittaexp.dialog.showcase.DialogShowcaseTemplates;
import it.patric.cittaexp.permission.StaffUiPermissionGate;
import it.patric.cittaexp.persistence.runtime.CittaExpPersistenceComponent;
import it.patric.cittaexp.persistence.runtime.PersistenceStatusService;
import it.patric.cittaexp.persistence.port.CityReadPort;
import it.patric.cittaexp.persistence.port.CityTxPort;
import it.patric.cittaexp.persistence.port.CityWritePort;
import it.patric.cittaexp.preview.PreviewSettings;
import it.patric.cittaexp.runtime.dependency.RequiredDependenciesComponent;
import it.patric.cittaexp.runtime.dependency.RequiredDependencyStatusService;
import it.patric.cittaexp.runtime.integration.RequiredIntegrationStatusService;
import it.patric.cittaexp.runtime.integration.RequiredIntegrationsComponent;
import it.patric.cittaexp.core.port.RankingPort;
import it.patric.cittaexp.ui.framework.GuiActionRouter;
import it.patric.cittaexp.ui.framework.GuiFlowOrchestrator;
import it.patric.cittaexp.ui.framework.GuiStateComposer;
import it.patric.cittaexp.ui.framework.catalog.DefaultGuiCatalog;
import it.patric.cittaexp.ui.theme.DefaultUiAssetResolver;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

public final class CittaExpPlugin extends JavaPlugin {

    private CommonRuntime runtime;

    @Override
    public void onLoad() {
        OperationResult<CommonRuntime> built = RuntimeBootstrap.build(this, builder ->
                builder.component(new RequiredDependenciesComponent())
                        .component(new HuskClaimsAdapterComponent())
                        .component(new ItemsAdderAdapterComponent())
                        .component(new RequiredIntegrationsComponent())
                        .component(new CittaExpPersistenceComponent())
        );
        if (built.isFailure()) {
            throw new IllegalStateException(
                    "Unable to build CittaEXP runtime",
                    built.errorOrNull() == null ? null : built.errorOrNull().cause()
            );
        }
        runtime = built.valueOrNull();

        OperationResult<Void> loadResult = RuntimeBootstrap.safeLoad(runtime);
        if (loadResult.isFailure()) {
            throw new IllegalStateException(
                    "Unable to load CittaEXP runtime",
                    loadResult.errorOrNull() == null ? null : loadResult.errorOrNull().cause()
            );
        }
    }

    @Override
    public void onEnable() {
        if (runtime == null) {
            throw new IllegalStateException("Runtime was not initialized in onLoad.");
        }

        mergeBundledYamlDefaults("messages.yml");
        mergeBundledYamlDefaults("integration.yml");
        mergeBundledYamlDefaults("persistence.yml");
        runtime.services().require(ConfigService.class).reloadAll();

        OperationResult<Void> enableResult = RuntimeBootstrap.safeEnable(runtime);
        if (enableResult.isFailure()) {
            throw new IllegalStateException(
                    "Unable to enable CittaEXP runtime",
                    enableResult.errorOrNull() == null ? null : enableResult.errorOrNull().cause()
            );
        }

        GuiSessionService guiSessionService = runtime.services().require(GuiSessionService.class);
        GuiDefinitionRegistry guiDefinitionRegistry = runtime.services().require(GuiDefinitionRegistry.class);
        DialogTemplateRegistry dialogTemplateRegistry = runtime.services().require(DialogTemplateRegistry.class);
        DialogService dialogService = runtime.services().require(DialogService.class);
        CapabilityRegistry capabilityRegistry = runtime.services().require(CapabilityRegistry.class);
        ItemsAdderService itemsAdderService = runtime.services().require(ItemsAdderService.class);
        PersistenceStatusService persistenceStatusService = runtime.services().require(PersistenceStatusService.class);
        MessageService messageService = runtime.services().require(MessageService.class);
        RequiredDependencyStatusService requiredDependencyStatusService = runtime.services().require(RequiredDependencyStatusService.class);
        RequiredIntegrationStatusService requiredIntegrationStatusService =
                runtime.services().require(RequiredIntegrationStatusService.class);
        CityReadPort cityReadPort = runtime.services().require(CityReadPort.class);
        CityWritePort cityWritePort = runtime.services().require(CityWritePort.class);
        CityTxPort cityTxPort = runtime.services().require(CityTxPort.class);
        HuskClaimsPort huskClaimsPort = runtime.services().require(HuskClaimsPort.class);
        RankingPort rankingPort = runtime.services().require(RankingPort.class);

        DefaultCityLifecycleService cityLifecycleService = new DefaultCityLifecycleService(
                cityReadPort,
                cityWritePort,
                cityTxPort,
                huskClaimsPort,
                getLogger()
        );

        PreviewSettings previewSettings = new PreviewSettings();
        RuntimeUiCapabilityGate capabilityGate = new RuntimeUiCapabilityGate(capabilityRegistry);
        StaffUiPermissionGate permissionGate = new StaffUiPermissionGate("cittaexp.admin.gui.preview");
        DefaultUiAssetResolver assetResolver = new DefaultUiAssetResolver(itemsAdderService);
        GuiActionRouter actionRouter = new GuiActionRouter();
        GuiStateComposer stateComposer = new GuiStateComposer(
                new DatabaseCityViewReadPort(cityLifecycleService, cityReadPort, rankingPort),
                capabilityGate
        );
        DefaultGuiCatalog guiCatalog = new DefaultGuiCatalog(assetResolver, actionRouter);
        GuiFlowOrchestrator guiFlowOrchestrator = new GuiFlowOrchestrator(
                guiDefinitionRegistry,
                guiSessionService,
                guiCatalog,
                stateComposer
        );

        new CreationDialogTemplates().register(dialogTemplateRegistry);
        new DialogShowcaseTemplates().register(dialogTemplateRegistry);
        DialogShowcaseService dialogShowcaseService = new DialogShowcaseService(
                dialogService,
                dialogTemplateRegistry,
                getLogger()
        );

        CittaExpPreviewCommand previewCommand = new CittaExpPreviewCommand(
                guiFlowOrchestrator,
                previewSettings,
                permissionGate,
                capabilityRegistry,
                itemsAdderService,
                dialogShowcaseService,
                persistenceStatusService,
                messageService,
                requiredDependencyStatusService,
                requiredIntegrationStatusService,
                cityLifecycleService,
                cityLifecycleService
        );
        if (getCommand("cittaexp") != null) {
            getCommand("cittaexp").setExecutor(previewCommand);
            getCommand("cittaexp").setTabCompleter(previewCommand);
        }
        CityCommand cityCommand = new CityCommand(this, cityLifecycleService, messageService);
        if (getCommand("city") != null) {
            getCommand("city").setExecutor(cityCommand);
            getCommand("city").setTabCompleter(cityCommand);
        }

        getLogger().info(
                "CittaEXP enabled. gui="
                        + capabilityGate.guiAvailable()
                        + ", dialog="
                        + capabilityGate.dialogAvailable()
                        + ", itemsadder="
                        + capabilityGate.itemsAdderAvailable()
        );
    }

    @Override
    public void onDisable() {
        if (runtime != null) {
            RuntimeBootstrap.safeDisable(runtime);
        }
        getLogger().info("CittaEXP disabled.");
    }

    private void mergeBundledYamlDefaults(String resourcePath) {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Cannot create plugin data folder: " + dataFolder);
        }
        File targetFile = new File(dataFolder, resourcePath);
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Cannot create parent directory: " + parent);
        }

        YamlConfiguration current = YamlConfiguration.loadConfiguration(targetFile);
        try (InputStream input = getResource(resourcePath)) {
            if (input == null) {
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8)
            );
            current.setDefaults(defaults);
            current.options().copyDefaults(true);
            current.save(targetFile);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to merge default config for " + resourcePath, ex);
        }
    }
}
