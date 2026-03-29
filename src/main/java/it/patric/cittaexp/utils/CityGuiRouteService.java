package it.patric.cittaexp.utils;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class CityGuiRouteService {

    public enum Route {
        HUB
    }

    private CityGuiRouteService() {
    }

    public static void open(Plugin plugin, Player player, Route route) {
        if (plugin == null || player == null || route == null || !player.isOnline()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.closeInventory();
            if (route == Route.HUB) {
                player.performCommand("city");
            }
        });
    }
}

