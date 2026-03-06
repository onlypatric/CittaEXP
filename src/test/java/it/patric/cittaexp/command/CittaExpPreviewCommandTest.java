package it.patric.cittaexp.command;

import dev.patric.commonlib.api.capability.CapabilityRegistry;
import dev.patric.commonlib.api.itemsadder.ItemsAdderService;
import it.patric.cittaexp.permission.StaffUiPermissionGate;
import it.patric.cittaexp.preview.PreviewScenario;
import it.patric.cittaexp.preview.PreviewSettings;
import it.patric.cittaexp.preview.ThemeMode;
import it.patric.cittaexp.ui.framework.GuiFlowOrchestrator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CittaExpPreviewCommandTest {

    @Test
    void scenarioCommandUpdatesPreviewSettings() {
        PreviewSettings settings = new PreviewSettings();
        CittaExpPreviewCommand command = new CittaExpPreviewCommand(
                mock(GuiFlowOrchestrator.class),
                settings,
                new StaffUiPermissionGate("cittaexp.admin.gui.preview"),
                mock(CapabilityRegistry.class),
                mock(ItemsAdderService.class)
        );

        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("cittaexp.admin.gui.preview")).thenReturn(true);

        command.onCommand(sender, mock(Command.class), "cittaexp", new String[]{"scenario", "freeze"});

        assertEquals(PreviewScenario.FREEZE, settings.scenario());
    }

    @Test
    void themeCommandUpdatesPreviewSettings() {
        PreviewSettings settings = new PreviewSettings();
        CittaExpPreviewCommand command = new CittaExpPreviewCommand(
                mock(GuiFlowOrchestrator.class),
                settings,
                new StaffUiPermissionGate("cittaexp.admin.gui.preview"),
                mock(CapabilityRegistry.class),
                mock(ItemsAdderService.class)
        );

        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("cittaexp.admin.gui.preview")).thenReturn(true);

        command.onCommand(sender, mock(Command.class), "cittaexp", new String[]{"theme", "itemsadder"});

        assertEquals(ThemeMode.ITEMSADDER, settings.themeMode());
    }

    @Test
    void dashboardCommandOpensGuiForPlayer() {
        GuiFlowOrchestrator orchestrator = mock(GuiFlowOrchestrator.class);
        when(orchestrator.open(any(Player.class), eq(it.patric.cittaexp.ui.contract.UiScreenKey.DASHBOARD), eq(PreviewScenario.DEFAULT), eq(ThemeMode.AUTO)))
                .thenReturn(GuiFlowOrchestrator.OpenResult.OPENED);

        CittaExpPreviewCommand command = new CittaExpPreviewCommand(
                orchestrator,
                new PreviewSettings(),
                new StaffUiPermissionGate("cittaexp.admin.gui.preview"),
                mock(CapabilityRegistry.class),
                mock(ItemsAdderService.class)
        );

        Player sender = mock(Player.class);
        when(sender.hasPermission("cittaexp.admin.gui.preview")).thenReturn(true);

        command.onCommand(sender, mock(Command.class), "cittaexp", new String[]{"dashboard"});

        verify(orchestrator).open(eq(sender), eq(it.patric.cittaexp.ui.contract.UiScreenKey.DASHBOARD), eq(PreviewScenario.DEFAULT), eq(ThemeMode.AUTO));
    }
}
