package it.patric.cittaexp;

import dev.patric.commonlib.adapter.itemsadder.ItemsAdderAdapterComponent;
import dev.patric.commonlib.api.CommonRuntime;
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
import it.patric.cittaexp.data.InMemoryCityViewReadPort;
import it.patric.cittaexp.dialog.CreationDialogTemplates;
import it.patric.cittaexp.dialog.showcase.DialogShowcaseService;
import it.patric.cittaexp.dialog.showcase.DialogShowcaseTemplates;
import it.patric.cittaexp.permission.StaffUiPermissionGate;
import it.patric.cittaexp.persistence.runtime.CittaExpPersistenceComponent;
import it.patric.cittaexp.persistence.runtime.PersistenceStatusService;
import it.patric.cittaexp.preview.PreviewSettings;
import it.patric.cittaexp.ui.framework.GuiActionRouter;
import it.patric.cittaexp.ui.framework.GuiFlowOrchestrator;
import it.patric.cittaexp.ui.framework.GuiStateComposer;
import it.patric.cittaexp.ui.framework.catalog.DefaultGuiCatalog;
import it.patric.cittaexp.ui.theme.DefaultUiAssetResolver;
import org.bukkit.plugin.java.JavaPlugin;

public final class CittaExpPlugin extends JavaPlugin {

    private CommonRuntime runtime;

    @Override
    public void onLoad() {
        OperationResult<CommonRuntime> built = RuntimeBootstrap.build(this, builder ->
                builder.component(new ItemsAdderAdapterComponent())
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

        PreviewSettings previewSettings = new PreviewSettings();
        RuntimeUiCapabilityGate capabilityGate = new RuntimeUiCapabilityGate(capabilityRegistry);
        StaffUiPermissionGate permissionGate = new StaffUiPermissionGate("cittaexp.admin.gui.preview");
        DefaultUiAssetResolver assetResolver = new DefaultUiAssetResolver(itemsAdderService);
        GuiActionRouter actionRouter = new GuiActionRouter();
        GuiStateComposer stateComposer = new GuiStateComposer(new InMemoryCityViewReadPort(), capabilityGate);
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
                messageService
        );
        if (getCommand("cittaexp") != null) {
            getCommand("cittaexp").setExecutor(previewCommand);
            getCommand("cittaexp").setTabCompleter(previewCommand);
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
}
