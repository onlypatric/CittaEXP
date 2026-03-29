package it.patric.cittaexp.levels;

import it.patric.cittaexp.utils.PluginConfigUtils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;

public final class TownLevelCommandBlockListener implements Listener {

    private static final String BYPASS_PERMISSION = "cittaexp.town.level.bypass";
    private final PluginConfigUtils configUtils;

    public TownLevelCommandBlockListener(Plugin plugin) {
        this.configUtils = new PluginConfigUtils(plugin);
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

        if (normalized.startsWith("town level")
                || normalized.equals("town level")
                || normalized.startsWith("t level")
                || normalized.equals("t level")
                || normalized.startsWith("husktowns:town level")
                || normalized.startsWith("husktowns:t level")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(configUtils.msg(
                    "city.level.blocked_town_level",
                    "<red>Usa /city -> Azioni Citta -> Livelli per gestire il livello citta.</red>"
            ));
            return;
        }

        if (normalized.startsWith("town privacy")
                || normalized.equals("town privacy")
                || normalized.startsWith("t privacy")
                || normalized.equals("t privacy")
                || normalized.startsWith("husktowns:town privacy")
                || normalized.startsWith("husktowns:t privacy")
                || normalized.startsWith("town spawnprivacy")
                || normalized.startsWith("t spawnprivacy")
                || normalized.startsWith("husktowns:town spawnprivacy")
                || normalized.startsWith("husktowns:t spawnprivacy")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(configUtils.msg(
                    "city.privacy.blocked_town_privacy",
                    "<red>Usa la GUI /city -> Azioni Citta per gestire la privacy del warp.</red>"
            ));
        }
    }
}
