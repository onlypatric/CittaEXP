package it.patric.cittaexp.utils;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class GuiNavigationUtils {

    private GuiNavigationUtils() {
    }

    public static void openCityHub(Plugin plugin, Player player) {
        CityGuiRouteService.open(plugin, player, CityGuiRouteService.Route.HUB);
    }
}
