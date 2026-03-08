package it.patric.cittaexp;

import dev.patric.commonlib.adapter.griefprevention.GriefPreventionAdapterComponent;
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
import it.patric.cittaexp.claims.config.ClaimEconomyConfigLoader;
import it.patric.cittaexp.claims.config.ClaimEconomySettings;
import it.patric.cittaexp.command.CittaExpPreviewCommand;
import it.patric.cittaexp.command.CityCommand;
import it.patric.cittaexp.core.port.CityClaimsPort;
import it.patric.cittaexp.core.port.StaffApprovalPort;
import it.patric.cittaexp.core.runtime.DefaultCityClaimBlockService;
import it.patric.cittaexp.core.runtime.DefaultCityLifecycleService;
import it.patric.cittaexp.core.service.CityClaimBlockService;
import it.patric.cittaexp.core.runtime.DefaultStaffApprovalService;
import it.patric.cittaexp.core.service.EconomyDiagnosticsService;
import it.patric.cittaexp.core.service.CityLifecycleDiagnosticsService;
import it.patric.cittaexp.core.service.CityModerationService;
import it.patric.cittaexp.data.DatabaseCityViewReadPort;
import it.patric.cittaexp.dialog.CreationDialogTemplates;
import it.patric.cittaexp.dialog.showcase.DialogShowcaseService;
import it.patric.cittaexp.dialog.showcase.DialogShowcaseTemplates;
import it.patric.cittaexp.economy.runtime.CittaExpEconomyComponent;
import it.patric.cittaexp.i18n.LocalizedText;
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
import it.patric.cittaexp.runtime.integration.GriefPreventionResizeGuardListener;
import it.patric.cittaexp.style.CityBannerDisplayService;
import it.patric.cittaexp.style.CityStyleService;
import it.patric.cittaexp.style.YamlCityStyleService;
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
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

public final class CittaExpPlugin extends JavaPlugin {

    private CommonRuntime runtime;

    @Override
    public void onLoad() {
        OperationResult<CommonRuntime> built = RuntimeBootstrap.build(this, builder ->
                builder.component(new RequiredDependenciesComponent())
                        .component(new GriefPreventionAdapterComponent())
                        .component(new ItemsAdderAdapterComponent())
                        .component(new RequiredIntegrationsComponent())
                        .component(new CittaExpPersistenceComponent())
                        .component(new CittaExpEconomyComponent())
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
        mergeBundledYamlDefaults("economy.yml");
        mergeBundledYamlDefaults("claims-economy.yml");
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
        LocalizedText localizedText = new LocalizedText(messageService);
        validateRequiredMessageKeys(localizedText);
        RequiredDependencyStatusService requiredDependencyStatusService = runtime.services().require(RequiredDependencyStatusService.class);
        RequiredIntegrationStatusService requiredIntegrationStatusService =
                runtime.services().require(RequiredIntegrationStatusService.class);
        EconomyDiagnosticsService economyDiagnosticsService =
                runtime.services().require(EconomyDiagnosticsService.class);
        CityReadPort cityReadPort = runtime.services().require(CityReadPort.class);
        CityWritePort cityWritePort = runtime.services().require(CityWritePort.class);
        CityTxPort cityTxPort = runtime.services().require(CityTxPort.class);
        CityClaimsPort cityClaimsPort = runtime.services().require(CityClaimsPort.class);
        RankingPort rankingPort = runtime.services().require(RankingPort.class);
        StaffApprovalPort staffApprovalPort = runtime.services().require(StaffApprovalPort.class);

        DefaultCityLifecycleService cityLifecycleService = new DefaultCityLifecycleService(
                cityReadPort,
                cityWritePort,
                cityTxPort,
                cityClaimsPort,
                getLogger()
        );
        DefaultStaffApprovalService staffApprovalService = new DefaultStaffApprovalService(
                staffApprovalPort,
                cityReadPort,
                cityWritePort,
                cityTxPort,
                cityLifecycleService,
                cityLifecycleService,
                getLogger()
        );
        ClaimEconomySettings claimEconomySettings = new ClaimEconomyConfigLoader(this).load();
        CityClaimBlockService claimBlockService = new DefaultCityClaimBlockService(
                cityReadPort,
                cityWritePort,
                cityTxPort,
                cityClaimsPort,
                runtime.services().require(it.patric.cittaexp.core.port.VaultEconomyPort.class),
                claimEconomySettings,
                getLogger()
        );
        getServer().getPluginManager().registerEvents(
                new GriefPreventionResizeGuardListener(cityReadPort, messageService, getLogger()),
                this
        );

        PreviewSettings previewSettings = new PreviewSettings();
        RuntimeUiCapabilityGate capabilityGate = new RuntimeUiCapabilityGate(capabilityRegistry);
        StaffUiPermissionGate permissionGate = new StaffUiPermissionGate("cittaexp.admin.gui.preview");
        DefaultUiAssetResolver assetResolver = new DefaultUiAssetResolver(itemsAdderService, localizedText);
        GuiActionRouter actionRouter = new GuiActionRouter();
        GuiStateComposer stateComposer = new GuiStateComposer(
                new DatabaseCityViewReadPort(
                        cityLifecycleService,
                        cityReadPort,
                        rankingPort,
                        claimBlockService,
                        claimEconomySettings,
                        staffApprovalService
                ),
                capabilityGate
        );
        DefaultGuiCatalog guiCatalog = new DefaultGuiCatalog(assetResolver, actionRouter, localizedText);
        GuiFlowOrchestrator guiFlowOrchestrator = new GuiFlowOrchestrator(
                guiDefinitionRegistry,
                guiSessionService,
                guiCatalog,
                stateComposer
        );

        new CreationDialogTemplates(localizedText).register(dialogTemplateRegistry);
        new DialogShowcaseTemplates(localizedText).register(dialogTemplateRegistry);
        DialogShowcaseService dialogShowcaseService = new DialogShowcaseService(
                dialogService,
                dialogTemplateRegistry,
                messageService,
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
                cityLifecycleService,
                economyDiagnosticsService,
                staffApprovalService
        );
        if (getCommand("cittaexp") != null) {
            getCommand("cittaexp").setExecutor(previewCommand);
            getCommand("cittaexp").setTabCompleter(previewCommand);
        }
        CityCommand cityCommand = new CityCommand(
                this,
                cityLifecycleService,
                staffApprovalService,
                cityLifecycleService,
                claimBlockService,
                guiFlowOrchestrator,
                messageService,
                buildCityStyleService(),
                buildCityBannerDisplayService()
        );
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

    private void validateRequiredMessageKeys(LocalizedText texts) {
        List<String> requiredKeys = List.of(
                "cittaexp.command.player_only",
                "cittaexp.command.open.internal_error",
                "cittaexp.city.error.generic",
                "cittaexp.city.gui.open.dashboard",
                "cittaexp.city.gui.open.roles",
                "cittaexp.city.gui.open.requests",
                "cittaexp.city.gui.open.claim_shop",
                "cittaexp.city.gui.open.failed",
                "cittaexp.gui.dashboard.title",
                "cittaexp.gui.members.title",
                "cittaexp.gui.roles.title",
                "cittaexp.gui.taxes.title",
                "cittaexp.gui.requests.title",
                "cittaexp.gui.claim_shop.title",
                "cittaexp.gui.staff.queue.title",
                "cittaexp.gui.staff.detail.title",
                "cittaexp.dialog.creation.city_name.title",
                "cittaexp.dialog.creation.city_tag.title",
                "cittaexp.dialog.showcase.notice.title",
                "cittaexp.dialog.showcase.submit.feedback"
        );
        List<String> missing = requiredKeys.stream()
                .filter(texts::isMissingKey)
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing required message keys: " + missing);
        }
    }

    private CityStyleService buildCityStyleService() {
        return new YamlCityStyleService(this, getLogger());
    }

    private CityBannerDisplayService buildCityBannerDisplayService() {
        return new CityBannerDisplayService(this, getLogger());
    }
}
