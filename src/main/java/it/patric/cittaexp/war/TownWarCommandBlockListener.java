package it.patric.cittaexp.war;

import it.patric.cittaexp.utils.PluginConfigUtils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;

public final class TownWarCommandBlockListener implements Listener {

    private static final String BYPASS_PERMISSION = "cittaexp.town.war.bypass";
    private final CityWarService cityWarService;
    private final PluginConfigUtils cfg;

    public TownWarCommandBlockListener(Plugin plugin, CityWarService cityWarService) {
        this.cityWarService = cityWarService;
        this.cfg = new PluginConfigUtils(plugin);
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
        if (!cityWarService.shouldBlockNewWarCommands()) {
            return;
        }
        if (isBlockedWarCommand(normalized)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(cfg.msg(
                    "city.war.errors.active_global_block",
                    "<red>Una guerra e gia attiva sul server.</red> <gray>Nuove dichiarazioni e accettazioni sono sospese finche il conflitto non si chiude.</gray>"
            ));
        }
    }

    private boolean isBlockedWarCommand(String normalized) {
        return starts(normalized, "war declare")
                || starts(normalized, "town war declare")
                || starts(normalized, "t war declare")
                || starts(normalized, "husktowns:war declare")
                || starts(normalized, "husktowns:town war declare")
                || starts(normalized, "husktowns:t war declare")
                || starts(normalized, "war accept")
                || starts(normalized, "town war accept")
                || starts(normalized, "t war accept")
                || starts(normalized, "husktowns:war accept")
                || starts(normalized, "husktowns:town war accept")
                || starts(normalized, "husktowns:t war accept");
    }

    private boolean starts(String normalized, String prefix) {
        return normalized.equals(prefix) || normalized.startsWith(prefix + " ");
    }
}
