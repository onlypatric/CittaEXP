package it.patric.cittaexp.chat;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.utils.CommandBridgeUtils;
import it.patric.cittaexp.utils.CommandGuards;
import it.patric.cittaexp.utils.PluginConfigUtils;
import it.patric.cittaexp.utils.TownMemberGuards;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.william278.husktowns.town.Privilege;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class TownChatCommand {

    private TownChatCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create(Plugin plugin, HuskTownsApiHook huskTownsApiHook) {
        PluginConfigUtils cfg = new PluginConfigUtils(plugin);
        return Commands.literal("chat")
                .executes(ctx -> sendOrToggle(ctx, plugin, huskTownsApiHook, cfg, null))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> sendOrToggle(
                                ctx,
                                plugin,
                                huskTownsApiHook,
                                cfg,
                                StringArgumentType.getString(ctx, "message")
                        )));
    }

    private static int sendOrToggle(
            CommandContext<CommandSourceStack> ctx,
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            PluginConfigUtils cfg,
            String message
    ) {
        Player player = CommandGuards.cityPlayer(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        if (TownMemberGuards.requirePrivilege(
                player,
                huskTownsApiHook,
                cfg,
                Privilege.CHAT,
                "city.chat.errors.no_town",
                "<red>Devi essere membro attivo di una citta.</red>",
                "city.chat.errors.no_permission",
                "<red>Non hai i permessi per usare la chat della citta.</red>"
        ).isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }

        String normalized = message == null ? "" : message.trim();
        if (normalized.isEmpty()) {
            return CommandBridgeUtils.dispatchTownCommand(plugin, player, "chat");
        }
        return CommandBridgeUtils.dispatchTownCommand(plugin, player, "chat", normalized);
    }

}
