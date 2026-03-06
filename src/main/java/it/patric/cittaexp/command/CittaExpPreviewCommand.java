package it.patric.cittaexp.command;

import dev.patric.commonlib.api.capability.CapabilityRegistry;
import dev.patric.commonlib.api.capability.CapabilityStatus;
import dev.patric.commonlib.api.capability.StandardCapabilities;
import dev.patric.commonlib.api.itemsadder.ItemsAdderService;
import it.patric.cittaexp.preview.PreviewScenario;
import it.patric.cittaexp.preview.PreviewSettings;
import it.patric.cittaexp.preview.ThemeMode;
import it.patric.cittaexp.ui.contract.UiPermissionGate;
import it.patric.cittaexp.ui.contract.UiScreenKey;
import it.patric.cittaexp.ui.framework.GuiFlowOrchestrator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class CittaExpPreviewCommand implements CommandExecutor, TabCompleter {

    private final GuiFlowOrchestrator guiFlowOrchestrator;
    private final PreviewSettings previewSettings;
    private final UiPermissionGate permissionGate;
    private final CapabilityRegistry capabilityRegistry;
    private final ItemsAdderService itemsAdderService;

    public CittaExpPreviewCommand(
            GuiFlowOrchestrator guiFlowOrchestrator,
            PreviewSettings previewSettings,
            UiPermissionGate permissionGate,
            CapabilityRegistry capabilityRegistry,
            ItemsAdderService itemsAdderService
    ) {
        this.guiFlowOrchestrator = Objects.requireNonNull(guiFlowOrchestrator, "guiFlowOrchestrator");
        this.previewSettings = Objects.requireNonNull(previewSettings, "previewSettings");
        this.permissionGate = Objects.requireNonNull(permissionGate, "permissionGate");
        this.capabilityRegistry = Objects.requireNonNull(capabilityRegistry, "capabilityRegistry");
        this.itemsAdderService = Objects.requireNonNull(itemsAdderService, "itemsAdderService");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!permissionGate.canOpenPreview(sender)) {
            sender.sendMessage("[CittaEXP] Missing permission: cittaexp.admin.gui.preview");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(usage(label));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        Optional<UiScreenKey> screen = UiScreenKey.fromCommandToken(sub);
        if (screen.isPresent()) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("[CittaEXP] This action requires an in-game player.");
                return true;
            }
            GuiFlowOrchestrator.OpenResult result = guiFlowOrchestrator.open(
                    player,
                    screen.get(),
                    previewSettings.scenario(),
                    previewSettings.themeMode()
            );
            sender.sendMessage("[CittaEXP] Open " + screen.get().commandToken() + " -> " + result.name());
            return true;
        }

        return switch (sub) {
            case "scenario" -> handleScenario(sender, args);
            case "theme" -> handleTheme(sender, args);
            case "hud" -> handleHud(sender, args);
            case "probe" -> handleProbe(sender);
            default -> {
                sender.sendMessage(usage(label));
                yield true;
            }
        };
    }

    private boolean handleScenario(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("[CittaEXP] Usage: /cittaexp scenario <default|freeze|capital|lowfunds|kingdom>");
            return true;
        }
        Optional<PreviewScenario> scenario = PreviewScenario.parse(args[1]);
        if (scenario.isEmpty()) {
            sender.sendMessage("[CittaEXP] Invalid scenario.");
            return true;
        }
        previewSettings.setScenario(scenario.get());
        sender.sendMessage("[CittaEXP] Scenario set to " + scenario.get().key());
        return true;
    }

    private boolean handleTheme(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("[CittaEXP] Usage: /cittaexp theme <auto|vanilla|itemsadder>");
            return true;
        }
        Optional<ThemeMode> mode = ThemeMode.parse(args[1]);
        if (mode.isEmpty()) {
            sender.sendMessage("[CittaEXP] Invalid theme mode.");
            return true;
        }
        previewSettings.setThemeMode(mode.get());
        sender.sendMessage("[CittaEXP] Theme set to " + mode.get().key());
        return true;
    }

    private boolean handleHud(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("[CittaEXP] This action requires an in-game player.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("[CittaEXP] Usage: /cittaexp hud <show|hide> <hudId>");
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        String hudId = args[2];

        boolean result = switch (action) {
            case "show" -> itemsAdderService.showHud(player.getUniqueId(), hudId);
            case "hide" -> itemsAdderService.hideHud(player.getUniqueId(), hudId);
            default -> false;
        };

        if (!action.equals("show") && !action.equals("hide")) {
            sender.sendMessage("[CittaEXP] Usage: /cittaexp hud <show|hide> <hudId>");
            return true;
        }

        sender.sendMessage("[CittaEXP] HUD " + action + "(" + hudId + ") -> " + result);
        return true;
    }

    private boolean handleProbe(CommandSender sender) {
        sender.sendMessage("[CittaEXP] Capability probe");
        sender.sendMessage(capabilityLine("GUI", capabilityRegistry.status(StandardCapabilities.GUI)));
        sender.sendMessage(capabilityLine("DIALOG", capabilityRegistry.status(StandardCapabilities.DIALOG)));
        sender.sendMessage(capabilityLine("ITEMSADDER", capabilityRegistry.status(StandardCapabilities.ITEMSADDER)));
        sender.sendMessage("[CittaEXP] ItemsAdderService available=" + itemsAdderService.isAvailable());
        sender.sendMessage("[CittaEXP] ItemsAdder features=" + itemsAdderService.features());
        return true;
    }

    private static String capabilityLine(String label, Optional<CapabilityStatus<String>> status) {
        if (status.isEmpty()) {
            return "[CittaEXP] " + label + " -> unavailable (not published)";
        }
        CapabilityStatus<String> value = status.get();
        if (value.available()) {
            return "[CittaEXP] " + label + " -> available (" + value.metadata() + ")";
        }
        return "[CittaEXP] " + label + " -> unavailable (" + value.reason() + ")";
    }

    private static String usage(String label) {
        return "[CittaEXP] /" + label + " <dashboard|members|roles|taxes|wizard|scenario|theme|hud|probe>";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!permissionGate.canOpenPreview(sender)) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> first = new ArrayList<>();
            first.addAll(Arrays.stream(UiScreenKey.values()).map(UiScreenKey::commandToken).distinct().toList());
            first.addAll(List.of("scenario", "theme", "hud", "probe"));
            return complete(first, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("scenario")) {
            return complete(Arrays.stream(PreviewScenario.values()).map(PreviewScenario::key).toList(), args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("theme")) {
            return complete(Arrays.stream(ThemeMode.values()).map(ThemeMode::key).toList(), args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("hud")) {
            return complete(List.of("show", "hide"), args[1]);
        }

        return List.of();
    }

    private static List<String> complete(List<String> options, String token) {
        String normalized = token == null ? "" : token.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(normalized))
                .sorted()
                .collect(Collectors.toList());
    }
}
