package it.patric.cittaexp.utils;

import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CommandGuards {

    private static final String CITY_PERMISSION = "cittaexp.city.use";
    private static final String CITY_PLAYER_ONLY = "[CittaEXP] Questo comando puo essere eseguito solo da un player.";
    private static final String CITY_NO_PERMISSION = "[CittaEXP] Non hai il permesso per usare /city.";

    private CommandGuards() {
    }

    public static Player cityPlayer(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CITY_PLAYER_ONLY);
            return null;
        }
        if (!sender.hasPermission(CITY_PERMISSION)) {
            sender.sendMessage(CITY_NO_PERMISSION);
            return null;
        }
        return player;
    }
}
