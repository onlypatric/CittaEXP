package it.patric.cittaexp.integration.husktowns;

import it.patric.cittaexp.utils.PluginConfigUtils;
import java.util.LinkedHashSet;
import java.util.Set;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;

public final class HuskTownsRawCommandBlockListener implements Listener {

    private static final String BYPASS_PERMISSION = "cittaexp.husktowns.raw.bypass";
    private final PluginConfigUtils configUtils;
    private final Set<String> blockedRoots;

    public HuskTownsRawCommandBlockListener(Plugin plugin, HuskTownsApiHook huskTownsApiHook) {
        this.configUtils = new PluginConfigUtils(plugin);
        this.blockedRoots = resolveBlockedRoots(huskTownsApiHook);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.getPlayer().hasPermission(BYPASS_PERMISSION)) {
            return;
        }
        String raw = event.getMessage();
        if (raw == null || raw.isBlank()) {
            return;
        }
        String normalized = raw.trim().toLowerCase();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (!isBlockedRoot(normalized)) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(configUtils.msg(
                "city.commands.blocked_husktowns_raw",
                "<red>Usa /city per i comandi citta.</red> <gray>Gli admin possono usare /cittaexp admin.</gray>"
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandSend(PlayerCommandSendEvent event) {
        if (event.getPlayer().hasPermission(BYPASS_PERMISSION)) {
            return;
        }
        event.getCommands().removeIf(this::isBlockedSuggestion);
    }

    private boolean isBlockedRoot(String normalized) {
        String commandToken = normalized;
        int firstSpace = commandToken.indexOf(' ');
        if (firstSpace >= 0) {
            commandToken = commandToken.substring(0, firstSpace);
        }
        int namespaceSeparator = commandToken.indexOf(':');
        if (namespaceSeparator >= 0 && namespaceSeparator + 1 < commandToken.length()) {
            normalized = commandToken.substring(namespaceSeparator + 1)
                    + normalized.substring(commandToken.length());
        }
        for (String root : blockedRoots) {
            if (starts(normalized, root)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlockedSuggestion(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        String normalized = command.trim().toLowerCase();
        int namespaceSeparator = normalized.indexOf(':');
        if (namespaceSeparator >= 0 && namespaceSeparator + 1 < normalized.length()) {
            normalized = normalized.substring(namespaceSeparator + 1);
        }
        return blockedRoots.contains(normalized);
    }

    private boolean starts(String normalized, String root) {
        return normalized.equals(root) || normalized.startsWith(root + " ");
    }

    private static Set<String> resolveBlockedRoots(HuskTownsApiHook huskTownsApiHook) {
        Set<String> roots = new LinkedHashSet<>();
        roots.add("town");
        roots.add("war");
        roots.add("admintown");
        roots.add("at");
        roots.add("husktowns");
        roots.add("ht");
        try {
            roots.addAll(huskTownsApiHook.plugin().getSettings().getAliases());
        } catch (RuntimeException ignored) {
            roots.add("t");
        }
        return roots;
    }
}
