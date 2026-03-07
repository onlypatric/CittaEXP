package it.patric.cittaexp.command;

import dev.patric.commonlib.api.MessageService;
import dev.patric.commonlib.api.capability.CapabilityRegistry;
import dev.patric.commonlib.api.itemsadder.ItemsAdderService;
import it.patric.cittaexp.dialog.showcase.DialogShowcaseKey;
import it.patric.cittaexp.dialog.showcase.DialogShowcaseService;
import it.patric.cittaexp.permission.StaffUiPermissionGate;
import it.patric.cittaexp.persistence.runtime.PersistenceStatusService;
import it.patric.cittaexp.persistence.runtime.PersistenceStatusSnapshot;
import it.patric.cittaexp.persistence.runtime.PersistenceRuntimeMode;
import it.patric.cittaexp.preview.PreviewScenario;
import it.patric.cittaexp.preview.PreviewSettings;
import it.patric.cittaexp.preview.ThemeMode;
import it.patric.cittaexp.runtime.dependency.DependencyState;
import it.patric.cittaexp.runtime.dependency.ExternalDependencyStatus;
import it.patric.cittaexp.runtime.dependency.RequiredDependencySnapshot;
import it.patric.cittaexp.runtime.dependency.RequiredDependencyStatusService;
import it.patric.cittaexp.runtime.integration.AdapterState;
import it.patric.cittaexp.runtime.integration.AdapterStatus;
import it.patric.cittaexp.runtime.integration.IntegrationStatusSnapshot;
import it.patric.cittaexp.runtime.integration.RankingScanStats;
import it.patric.cittaexp.runtime.integration.RequiredIntegrationStatusService;
import it.patric.cittaexp.core.service.CityLifecycleDiagnosticsService;
import it.patric.cittaexp.core.service.CityModerationService;
import java.util.List;
import it.patric.cittaexp.ui.framework.GuiFlowOrchestrator;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CittaExpPreviewCommandTest {

    @Test
    void scenarioCommandUpdatesPreviewSettings() {
        MessageService messageService = messageService();
        PreviewSettings settings = new PreviewSettings();
        CittaExpPreviewCommand command = new CittaExpPreviewCommand(
                mock(GuiFlowOrchestrator.class),
                settings,
                new StaffUiPermissionGate("cittaexp.admin.gui.preview"),
                mock(CapabilityRegistry.class),
                mock(ItemsAdderService.class),
                mock(DialogShowcaseService.class),
                mock(PersistenceStatusService.class),
                messageService,
                dependencyStatusService(),
                integrationStatusService(),
                moderationService(),
                lifecycleDiagnosticsService()
        );

        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("cittaexp.admin.gui.preview")).thenReturn(true);

        command.onCommand(sender, mock(Command.class), "cittaexp", new String[]{"scenario", "freeze"});

        assertEquals(PreviewScenario.FREEZE, settings.scenario());
        verify(messageService).render(eq("cittaexp.command.scenario.set"), anyMap(), any(Locale.class));
    }

    @Test
    void themeCommandUpdatesPreviewSettings() {
        MessageService messageService = messageService();
        PreviewSettings settings = new PreviewSettings();
        CittaExpPreviewCommand command = new CittaExpPreviewCommand(
                mock(GuiFlowOrchestrator.class),
                settings,
                new StaffUiPermissionGate("cittaexp.admin.gui.preview"),
                mock(CapabilityRegistry.class),
                mock(ItemsAdderService.class),
                mock(DialogShowcaseService.class),
                mock(PersistenceStatusService.class),
                messageService,
                dependencyStatusService(),
                integrationStatusService(),
                moderationService(),
                lifecycleDiagnosticsService()
        );

        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("cittaexp.admin.gui.preview")).thenReturn(true);

        command.onCommand(sender, mock(Command.class), "cittaexp", new String[]{"theme", "itemsadder"});

        assertEquals(ThemeMode.ITEMSADDER, settings.themeMode());
        verify(messageService).render(eq("cittaexp.command.theme.set"), anyMap(), any(Locale.class));
    }

    @Test
    void dashboardCommandOpensGuiForPlayer() {
        MessageService messageService = messageService();
        GuiFlowOrchestrator orchestrator = mock(GuiFlowOrchestrator.class);
        when(orchestrator.open(any(Player.class), eq(it.patric.cittaexp.ui.contract.UiScreenKey.DASHBOARD), eq(PreviewScenario.DEFAULT), eq(ThemeMode.AUTO)))
                .thenReturn(GuiFlowOrchestrator.OpenResult.OPENED);

        CittaExpPreviewCommand command = new CittaExpPreviewCommand(
                orchestrator,
                new PreviewSettings(),
                new StaffUiPermissionGate("cittaexp.admin.gui.preview"),
                mock(CapabilityRegistry.class),
                mock(ItemsAdderService.class),
                mock(DialogShowcaseService.class),
                mock(PersistenceStatusService.class),
                messageService,
                dependencyStatusService(),
                integrationStatusService(),
                moderationService(),
                lifecycleDiagnosticsService()
        );

        Player sender = mock(Player.class);
        when(sender.hasPermission("cittaexp.admin.gui.preview")).thenReturn(true);

        command.onCommand(sender, mock(Command.class), "cittaexp", new String[]{"dashboard"});

        verify(orchestrator).open(eq(sender), eq(it.patric.cittaexp.ui.contract.UiScreenKey.DASHBOARD), eq(PreviewScenario.DEFAULT), eq(ThemeMode.AUTO));
        verify(messageService).render(eq("cittaexp.command.open.result"), anyMap(), any(Locale.class));
    }

    @Test
    void dashboardCommandHandlesOpenRuntimeExceptionWithoutThrowing() {
        MessageService messageService = messageService();
        GuiFlowOrchestrator orchestrator = mock(GuiFlowOrchestrator.class);
        when(orchestrator.open(any(Player.class), eq(it.patric.cittaexp.ui.contract.UiScreenKey.DASHBOARD), eq(PreviewScenario.DEFAULT), eq(ThemeMode.AUTO)))
                .thenThrow(new IllegalArgumentException("slot already configured"));

        CittaExpPreviewCommand command = new CittaExpPreviewCommand(
                orchestrator,
                new PreviewSettings(),
                new StaffUiPermissionGate("cittaexp.admin.gui.preview"),
                mock(CapabilityRegistry.class),
                mock(ItemsAdderService.class),
                mock(DialogShowcaseService.class),
                mock(PersistenceStatusService.class),
                messageService,
                dependencyStatusService(),
                integrationStatusService(),
                moderationService(),
                lifecycleDiagnosticsService()
        );

        Player sender = mock(Player.class);
        when(sender.hasPermission("cittaexp.admin.gui.preview")).thenReturn(true);

        assertDoesNotThrow(() -> command.onCommand(sender, mock(Command.class), "cittaexp", new String[]{"dashboard"}));
        verify(messageService).render(eq("cittaexp.command.open.internal_error"), anyMap(), any(Locale.class));
    }

    @Test
    void dialogListCommandShowsAvailableShowcases() {
        MessageService messageService = messageService();
        CittaExpPreviewCommand command = new CittaExpPreviewCommand(
                mock(GuiFlowOrchestrator.class),
                new PreviewSettings(),
                new StaffUiPermissionGate("cittaexp.admin.gui.preview"),
                mock(CapabilityRegistry.class),
                mock(ItemsAdderService.class),
                mock(DialogShowcaseService.class),
                mock(PersistenceStatusService.class),
                messageService,
                dependencyStatusService(),
                integrationStatusService(),
                moderationService(),
                lifecycleDiagnosticsService()
        );

        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("cittaexp.admin.gui.preview")).thenReturn(true);

        command.onCommand(sender, mock(Command.class), "cittaexp", new String[]{"dialog", "list"});

        verify(messageService).render(eq("cittaexp.dialog.list.title"), anyMap(), any(Locale.class));
        verify(messageService, atLeastOnce()).render(eq("cittaexp.dialog.list.entry"), anyMap(), any(Locale.class));
        verify(messageService).render(eq("cittaexp.dialog.list.hint"), anyMap(), any(Locale.class));
    }

    @Test
    void dialogCommandRequiresPlayerSender() {
        MessageService messageService = messageService();
        DialogShowcaseService dialogShowcaseService = mock(DialogShowcaseService.class);
        CittaExpPreviewCommand command = new CittaExpPreviewCommand(
                mock(GuiFlowOrchestrator.class),
                new PreviewSettings(),
                new StaffUiPermissionGate("cittaexp.admin.gui.preview"),
                mock(CapabilityRegistry.class),
                mock(ItemsAdderService.class),
                dialogShowcaseService,
                mock(PersistenceStatusService.class),
                messageService,
                dependencyStatusService(),
                integrationStatusService(),
                moderationService(),
                lifecycleDiagnosticsService()
        );

        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("cittaexp.admin.gui.preview")).thenReturn(true);

        command.onCommand(sender, mock(Command.class), "cittaexp", new String[]{"dialog", "notice"});

        verify(messageService).render(eq("cittaexp.command.player_only"), anyMap(), any(Locale.class));
    }

    @Test
    void dialogCommandOpensSelectedShowcase() {
        MessageService messageService = messageService();
        DialogShowcaseService dialogShowcaseService = mock(DialogShowcaseService.class);
        when(dialogShowcaseService.open(any(Player.class), eq(DialogShowcaseKey.NOTICE)))
                .thenReturn(DialogShowcaseService.OpenResult.OPENED);

        CittaExpPreviewCommand command = new CittaExpPreviewCommand(
                mock(GuiFlowOrchestrator.class),
                new PreviewSettings(),
                new StaffUiPermissionGate("cittaexp.admin.gui.preview"),
                mock(CapabilityRegistry.class),
                mock(ItemsAdderService.class),
                dialogShowcaseService,
                mock(PersistenceStatusService.class),
                messageService,
                dependencyStatusService(),
                integrationStatusService(),
                moderationService(),
                lifecycleDiagnosticsService()
        );

        Player sender = mock(Player.class);
        when(sender.hasPermission("cittaexp.admin.gui.preview")).thenReturn(true);

        command.onCommand(sender, mock(Command.class), "cittaexp", new String[]{"dialog", "notice"});

        verify(dialogShowcaseService).open(eq(sender), eq(DialogShowcaseKey.NOTICE));
        verify(messageService).render(eq("cittaexp.dialog.open.result"), anyMap(), any(Locale.class));
    }

    @Test
    void dialogListOpenCommandOpensListShowcase() {
        MessageService messageService = messageService();
        DialogShowcaseService dialogShowcaseService = mock(DialogShowcaseService.class);
        when(dialogShowcaseService.open(any(Player.class), eq(DialogShowcaseKey.LIST)))
                .thenReturn(DialogShowcaseService.OpenResult.OPENED);

        CittaExpPreviewCommand command = new CittaExpPreviewCommand(
                mock(GuiFlowOrchestrator.class),
                new PreviewSettings(),
                new StaffUiPermissionGate("cittaexp.admin.gui.preview"),
                mock(CapabilityRegistry.class),
                mock(ItemsAdderService.class),
                dialogShowcaseService,
                mock(PersistenceStatusService.class),
                messageService,
                dependencyStatusService(),
                integrationStatusService(),
                moderationService(),
                lifecycleDiagnosticsService()
        );

        Player sender = mock(Player.class);
        when(sender.hasPermission("cittaexp.admin.gui.preview")).thenReturn(true);

        command.onCommand(sender, mock(Command.class), "cittaexp", new String[]{"dialog", "list", "open"});

        verify(dialogShowcaseService).open(eq(sender), eq(DialogShowcaseKey.LIST));
        verify(messageService).render(eq("cittaexp.dialog.open.result"), anyMap(), any(Locale.class));
    }

    @Test
    void dialogCommandRejectsInvalidShowcaseKey() {
        MessageService messageService = messageService();
        CittaExpPreviewCommand command = new CittaExpPreviewCommand(
                mock(GuiFlowOrchestrator.class),
                new PreviewSettings(),
                new StaffUiPermissionGate("cittaexp.admin.gui.preview"),
                mock(CapabilityRegistry.class),
                mock(ItemsAdderService.class),
                mock(DialogShowcaseService.class),
                mock(PersistenceStatusService.class),
                messageService,
                dependencyStatusService(),
                integrationStatusService(),
                moderationService(),
                lifecycleDiagnosticsService()
        );

        Player sender = mock(Player.class);
        when(sender.hasPermission("cittaexp.admin.gui.preview")).thenReturn(true);

        command.onCommand(sender, mock(Command.class), "cittaexp", new String[]{"dialog", "unknown"});

        verify(messageService).render(eq("cittaexp.dialog.invalid"), anyMap(), any(Locale.class));
    }

    @Test
    void probeCommandIncludesPersistenceLines() {
        MessageService messageService = messageService();
        PersistenceStatusService persistenceStatusService = mock(PersistenceStatusService.class);
        when(persistenceStatusService.snapshot()).thenReturn(new PersistenceStatusSnapshot(
                PersistenceRuntimeMode.SQLITE_FALLBACK,
                false,
                true,
                5L,
                1L,
                123L,
                "mysql-unreachable"
        ));

        CittaExpPreviewCommand command = new CittaExpPreviewCommand(
                mock(GuiFlowOrchestrator.class),
                new PreviewSettings(),
                new StaffUiPermissionGate("cittaexp.admin.gui.preview"),
                mock(CapabilityRegistry.class),
                mock(ItemsAdderService.class),
                mock(DialogShowcaseService.class),
                persistenceStatusService,
                messageService,
                dependencyStatusService(),
                integrationStatusService(),
                moderationService(),
                lifecycleDiagnosticsService()
        );

        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("cittaexp.admin.gui.preview")).thenReturn(true);

        command.onCommand(sender, mock(Command.class), "cittaexp", new String[]{"probe"});

        verify(messageService).render(eq("cittaexp.probe.persistence.mode"), anyMap(), any(Locale.class));
        verify(messageService).render(eq("cittaexp.probe.persistence.outbox_pending"), anyMap(), any(Locale.class));
        verify(messageService, atLeastOnce()).render(eq("cittaexp.probe.dependency.status"), anyMap(), any(Locale.class));
        verify(messageService, atLeastOnce()).render(eq("cittaexp.probe.integration.status"), anyMap(), any(Locale.class));
        verify(messageService).render(eq("cittaexp.probe.integration.ranking.scan"), anyMap(), any(Locale.class));
    }

    private static MessageService messageService() {
        MessageService service = mock(MessageService.class);
        when(service.render(anyString(), anyMap(), any(Locale.class)))
                .thenAnswer(invocation -> Component.text(invocation.getArgument(0, String.class)));
        when(service.render(anyString(), any(Locale.class)))
                .thenAnswer(invocation -> Component.text(invocation.getArgument(0, String.class)));
        when(service.render(any())).thenAnswer(invocation -> Component.text("message"));
        return service;
    }

    private static RequiredDependencyStatusService dependencyStatusService() {
        RequiredDependencyStatusService service = mock(RequiredDependencyStatusService.class);
        when(service.snapshot()).thenReturn(new RequiredDependencySnapshot(List.of(
                new ExternalDependencyStatus("Vault", DependencyState.AVAILABLE, "1.7", ""),
                new ExternalDependencyStatus("HuskClaims", DependencyState.AVAILABLE, "3.0.0", ""),
                new ExternalDependencyStatus("ClassificheEXP", DependencyState.AVAILABLE, "1.0.0", "")
        )));
        return service;
    }

    private static RequiredIntegrationStatusService integrationStatusService() {
        RequiredIntegrationStatusService service = mock(RequiredIntegrationStatusService.class);
        when(service.snapshot()).thenReturn(new IntegrationStatusSnapshot(
                List.of(
                        new AdapterStatus("Vault", AdapterState.AVAILABLE, "1.7", "api-registered"),
                        new AdapterStatus("HuskClaims", AdapterState.AVAILABLE, "4.0.0", "api-registered"),
                        new AdapterStatus("ClassificheEXP", AdapterState.AVAILABLE, "0.2.0", "api-registered")
                ),
                new RankingScanStats(100, 95, 5, 123456L)
        ));
        return service;
    }

    private static CityModerationService moderationService() {
        return mock(CityModerationService.class);
    }

    private static CityLifecycleDiagnosticsService lifecycleDiagnosticsService() {
        CityLifecycleDiagnosticsService service = mock(CityLifecycleDiagnosticsService.class);
        when(service.snapshot()).thenReturn(new CityLifecycleDiagnosticsService.Snapshot(true, 1L, 2L, 3L));
        return service;
    }
}
