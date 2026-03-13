package it.patric.cittaexp.utils;

import com.mojang.brigadier.Command;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class CommandBridgeUtils {

    private CommandBridgeUtils() {
    }

    public static int dispatchTownCommand(Plugin plugin, Player player, String action) {
        dispatchTown(plugin, player, action);
        return Command.SINGLE_SUCCESS;
    }

    public static int dispatchTownCommand(Plugin plugin, Player player, String action, String arg) {
        dispatchTown(plugin, player, action, arg);
        return Command.SINGLE_SUCCESS;
    }

    public static int dispatchTownCommand(Plugin plugin, Player player, String action, BigDecimal amount) {
        dispatchTown(plugin, player, action, amount);
        return Command.SINGLE_SUCCESS;
    }

    public static boolean dispatchCommand(Plugin plugin, Player player, String commandLine) {
        boolean ok = player.performCommand(commandLine);
        if (!ok) {
            player.sendMessage("[CittaEXP] Comando backend non riuscito: /" + commandLine);
            plugin.getLogger().warning("Backend command failed player=" + player.getUniqueId() + " cmd=/" + commandLine);
        }
        return ok;
    }

    public static boolean dispatchTown(Plugin plugin, Player player, String action) {
        return dispatchCommand(plugin, player, "town " + action);
    }

    public static boolean dispatchTown(Plugin plugin, Player player, String action, String arg) {
        return dispatchCommand(plugin, player, "town " + action + " " + arg);
    }

    public static boolean dispatchTown(Plugin plugin, Player player, String action, BigDecimal amount) {
        String normalizedAmount = amount.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        return dispatchTown(plugin, player, action, normalizedAmount);
    }
}
