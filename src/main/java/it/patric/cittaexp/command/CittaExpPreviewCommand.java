package it.patric.cittaexp.command;

import dev.patric.commonlib.api.MessageService;
import dev.patric.commonlib.api.capability.CapabilityRegistry;
import dev.patric.commonlib.api.capability.CapabilityStatus;
import dev.patric.commonlib.api.capability.StandardCapabilities;
import dev.patric.commonlib.api.itemsadder.ItemsAdderService;
import it.patric.cittaexp.dialog.showcase.DialogShowcaseKey;
import it.patric.cittaexp.dialog.showcase.DialogShowcaseService;
import it.patric.cittaexp.persistence.runtime.PersistenceStatusService;
import it.patric.cittaexp.preview.PreviewScenario;
import it.patric.cittaexp.preview.PreviewSettings;
import it.patric.cittaexp.preview.ThemeMode;
import it.patric.cittaexp.runtime.dependency.RequiredDependencyStatusService;
import it.patric.cittaexp.ui.contract.UiPermissionGate;
import it.patric.cittaexp.ui.contract.UiScreenKey;
import it.patric.cittaexp.ui.framework.GuiFlowOrchestrator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class CittaExpPreviewCommand implements CommandExecutor, TabCompleter {

    private static final Logger LOGGER = Logger.getLogger(CittaExpPreviewCommand.class.getName());

    private final GuiFlowOrchestrator guiFlowOrchestrator;
    private final PreviewSettings previewSettings;
    private final UiPermissionGate permissionGate;
    private final CapabilityRegistry capabilityRegistry;
    private final ItemsAdderService itemsAdderService;
    private final DialogShowcaseService dialogShowcaseService;
    private final PersistenceStatusService persistenceStatusService;
    private final MessageService messageService;
    private final RequiredDependencyStatusService requiredDependencyStatusService;

    public CittaExpPreviewCommand(
            GuiFlowOrchestrator guiFlowOrchestrator,
            PreviewSettings previewSettings,
            UiPermissionGate permissionGate,
            CapabilityRegistry capabilityRegistry,
            ItemsAdderService itemsAdderService,
            DialogShowcaseService dialogShowcaseService,
            PersistenceStatusService persistenceStatusService,
            MessageService messageService,
            RequiredDependencyStatusService requiredDependencyStatusService
    ) {
        this.guiFlowOrchestrator = Objects.requireNonNull(guiFlowOrchestrator, "guiFlowOrchestrator");
        this.previewSettings = Objects.requireNonNull(previewSettings, "previewSettings");
        this.permissionGate = Objects.requireNonNull(permissionGate, "permissionGate");
        this.capabilityRegistry = Objects.requireNonNull(capabilityRegistry, "capabilityRegistry");
        this.itemsAdderService = Objects.requireNonNull(itemsAdderService, "itemsAdderService");
        this.dialogShowcaseService = Objects.requireNonNull(dialogShowcaseService, "dialogShowcaseService");
        this.persistenceStatusService = Objects.requireNonNull(persistenceStatusService, "persistenceStatusService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.requiredDependencyStatusService = Objects.requireNonNull(
                requiredDependencyStatusService,
                "requiredDependencyStatusService"
        );
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!permissionGate.canOpenPreview(sender)) {
            sender.sendMessage(msg(sender, "cittaexp.command.no_permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(msg(sender, "cittaexp.command.usage.root", Map.of("label", label)));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        Optional<UiScreenKey> screen = UiScreenKey.fromCommandToken(sub);
        if (screen.isPresent()) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(msg(sender, "cittaexp.command.player_only"));
                return true;
            }
            PreviewScenario scenario = previewSettings.scenario();
            ThemeMode themeMode = previewSettings.themeMode();
            GuiFlowOrchestrator.OpenResult result;
            try {
                result = guiFlowOrchestrator.open(player, screen.get(), scenario, themeMode);
            } catch (RuntimeException ex) {
                LOGGER.log(
                        Level.SEVERE,
                        "Unhandled GUI open failure screen="
                                + screen.get().commandToken()
                                + ", scenario="
                                + scenario.key()
                                + ", theme="
                                + themeMode.key(),
                        ex
                );
                sender.sendMessage(msg(
                        sender,
                        "cittaexp.command.open.internal_error",
                        Map.of("screen", screen.get().commandToken())
                ));
                return true;
            }
            sender.sendMessage(msg(
                    sender,
                    "cittaexp.command.open.result",
                    Map.of("screen", screen.get().commandToken(), "result", result.name())
            ));
            return true;
        }

        return switch (sub) {
            case "scenario" -> handleScenario(sender, args);
            case "theme" -> handleTheme(sender, args);
            case "hud" -> handleHud(sender, args);
            case "probe" -> handleProbe(sender);
            case "dialog" -> handleDialog(sender, args);
            default -> {
                sender.sendMessage(msg(sender, "cittaexp.command.usage.root", Map.of("label", label)));
                yield true;
            }
        };
    }

    private boolean handleScenario(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(msg(sender, "cittaexp.command.usage.scenario"));
            return true;
        }
        Optional<PreviewScenario> scenario = PreviewScenario.parse(args[1]);
        if (scenario.isEmpty()) {
            sender.sendMessage(msg(sender, "cittaexp.command.scenario.invalid"));
            return true;
        }
        previewSettings.setScenario(scenario.get());
        sender.sendMessage(msg(sender, "cittaexp.command.scenario.set", Map.of("scenario", scenario.get().key())));
        return true;
    }

    private boolean handleTheme(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(msg(sender, "cittaexp.command.usage.theme"));
            return true;
        }
        Optional<ThemeMode> mode = ThemeMode.parse(args[1]);
        if (mode.isEmpty()) {
            sender.sendMessage(msg(sender, "cittaexp.command.theme.invalid"));
            return true;
        }
        previewSettings.setThemeMode(mode.get());
        sender.sendMessage(msg(sender, "cittaexp.command.theme.set", Map.of("theme", mode.get().key())));
        return true;
    }

    private boolean handleHud(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg(sender, "cittaexp.command.player_only"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(msg(sender, "cittaexp.command.usage.hud"));
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
            sender.sendMessage(msg(sender, "cittaexp.command.usage.hud"));
            return true;
        }

        sender.sendMessage(msg(
                sender,
                "cittaexp.command.hud.result",
                Map.of("action", action, "hudId", hudId, "result", String.valueOf(result))
        ));
        return true;
    }

    private boolean handleProbe(CommandSender sender) {
        sender.sendMessage(msg(sender, "cittaexp.probe.title"));
        sender.sendMessage(capabilityLine(sender, "GUI", capabilityRegistry.status(StandardCapabilities.GUI)));
        sender.sendMessage(capabilityLine(sender, "DIALOG", capabilityRegistry.status(StandardCapabilities.DIALOG)));
        sender.sendMessage(capabilityLine(sender, "ITEMSADDER", capabilityRegistry.status(StandardCapabilities.ITEMSADDER)));
        sender.sendMessage(capabilityLine(sender, "PERSISTENCE_SQL", capabilityRegistry.status(StandardCapabilities.PERSISTENCE_SQL)));
        sender.sendMessage(msg(
                sender,
                "cittaexp.probe.itemsadder.available",
                Map.of("value", String.valueOf(itemsAdderService.isAvailable()))
        ));
        sender.sendMessage(msg(
                sender,
                "cittaexp.probe.itemsadder.features",
                Map.of("value", String.valueOf(itemsAdderService.features()))
        ));
        var persistence = persistenceStatusService.snapshot();
        sender.sendMessage(msg(sender, "cittaexp.probe.persistence.mode", Map.of("value", persistence.mode().name())));
        sender.sendMessage(msg(sender, "cittaexp.probe.persistence.mysql_reachable", Map.of("value", String.valueOf(persistence.mysqlReachable()))));
        sender.sendMessage(msg(sender, "cittaexp.probe.persistence.fallback_enabled", Map.of("value", String.valueOf(persistence.fallbackEnabled()))));
        sender.sendMessage(msg(sender, "cittaexp.probe.persistence.outbox_pending", Map.of("value", String.valueOf(persistence.outboxPending()))));
        sender.sendMessage(msg(sender, "cittaexp.probe.persistence.outbox_conflicts", Map.of("value", String.valueOf(persistence.outboxConflicts()))));
        sender.sendMessage(msg(
                sender,
                "cittaexp.probe.persistence.last_switch",
                Map.of(
                        "timestamp", String.valueOf(persistence.lastModeSwitchEpochMilli()),
                        "reason", persistence.lastSwitchReason()
                )
        ));
        for (var dependency : requiredDependencyStatusService.snapshot().dependencies()) {
            sender.sendMessage(msg(
                    sender,
                    "cittaexp.probe.dependency.status",
                    Map.of(
                            "name", dependency.name(),
                            "state", dependency.state().name(),
                            "version", dependency.version(),
                            "reason", dependency.reason().isBlank() ? "-" : dependency.reason()
                    )
            ));
        }
        return true;
    }

    private boolean handleDialog(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(msg(sender, "cittaexp.command.usage.dialog"));
            return true;
        }

        String dialogSub = args[1].toLowerCase(Locale.ROOT);
        if ("list".equals(dialogSub) && args.length == 2) {
            sender.sendMessage(msg(sender, "cittaexp.dialog.list.title"));
            for (DialogShowcaseKey key : DialogShowcaseKey.values()) {
                sender.sendMessage(msg(
                        sender,
                        "cittaexp.dialog.list.entry",
                        Map.of("alias", key.alias(), "template", key.templateKey())
                ));
            }
            sender.sendMessage(msg(sender, "cittaexp.dialog.list.hint"));
            return true;
        }

        if ("list".equals(dialogSub) && args.length >= 3 && "open".equalsIgnoreCase(args[2])) {
            dialogSub = "list";
        }

        Optional<DialogShowcaseKey> showcaseKey = DialogShowcaseKey.parse(dialogSub);
        if (showcaseKey.isEmpty()) {
            sender.sendMessage(msg(sender, "cittaexp.dialog.invalid"));
            sender.sendMessage(msg(sender, "cittaexp.command.usage.dialog"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg(sender, "cittaexp.command.player_only"));
            return true;
        }

        try {
            DialogShowcaseService.OpenResult result = dialogShowcaseService.open(player, showcaseKey.get());
            sender.sendMessage(msg(
                    sender,
                    "cittaexp.dialog.open.result",
                    Map.of("alias", showcaseKey.get().alias(), "result", result.name())
            ));
            return true;
        } catch (RuntimeException ex) {
            LOGGER.log(
                    Level.SEVERE,
                    "Unhandled dialog showcase open failure key=" + showcaseKey.get().alias(),
                    ex
            );
            sender.sendMessage(msg(
                    sender,
                    "cittaexp.dialog.open.internal_error",
                    Map.of("alias", showcaseKey.get().alias())
            ));
            return true;
        }
    }

    private Component capabilityLine(CommandSender sender, String label, Optional<CapabilityStatus<String>> status) {
        if (status.isEmpty()) {
            return msg(
                    sender,
                    "cittaexp.probe.capability.unavailable",
                    Map.of("name", label, "reason", "not-published")
            );
        }
        CapabilityStatus<String> value = status.get();
        if (value.available()) {
            return msg(
                    sender,
                    "cittaexp.probe.capability.available",
                    Map.of("name", label, "metadata", value.metadata())
            );
        }
        return msg(
                sender,
                "cittaexp.probe.capability.unavailable",
                Map.of("name", label, "reason", value.reason())
        );
    }

    private Component msg(CommandSender sender, String key) {
        return msg(sender, key, Map.of());
    }

    private Component msg(CommandSender sender, String key, Map<String, String> placeholders) {
        return messageService.render(key, placeholders, resolveLocale(sender));
    }

    private static Locale resolveLocale(CommandSender sender) {
        if (sender instanceof Player player) {
            String raw = player.getLocale();
            if (raw != null && !raw.isBlank()) {
                return Locale.forLanguageTag(raw.replace('_', '-'));
            }
        }
        return Locale.ITALIAN;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!permissionGate.canOpenPreview(sender)) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> first = new ArrayList<>();
            first.addAll(Arrays.stream(UiScreenKey.values()).map(UiScreenKey::commandToken).distinct().toList());
            first.addAll(List.of("scenario", "theme", "hud", "probe", "dialog"));
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

        if (args.length == 2 && args[0].equalsIgnoreCase("dialog")) {
            List<String> options = new ArrayList<>();
            options.add("list");
            options.addAll(DialogShowcaseKey.aliases());
            return complete(options, args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("dialog") && args[1].equalsIgnoreCase("list")) {
            return complete(List.of("open"), args[2]);
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
