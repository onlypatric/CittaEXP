package it.patric.cittaexp.membership;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.utils.CommandBridgeUtils;
import it.patric.cittaexp.utils.CommandGuards;
import it.patric.cittaexp.utils.FeedbackUtils;
import it.patric.cittaexp.utils.PluginConfigUtils;
import it.patric.cittaexp.utils.TownMemberGuards;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Optional;
import net.william278.husktowns.town.Member;
import net.william278.husktowns.town.Privilege;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class InviteCommand {

    private static final String USAGE = "[CittaEXP] Uso: /city invite <player|accept [player]|decline [player]>";

    private InviteCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            CityLevelService cityLevelService
    ) {
        PluginConfigUtils cfg = new PluginConfigUtils(plugin);
        return Commands.literal("invite")
                .executes(ctx -> usage(ctx.getSource().getSender()))
                .then(Commands.literal("accept")
                        .executes(ctx -> inviteDecision(ctx, plugin, "accept", false))
                        .then(Commands.argument("player", StringArgumentType.greedyString())
                                .executes(ctx -> inviteDecision(ctx, plugin, "accept", true))))
                .then(Commands.literal("decline")
                        .executes(ctx -> inviteDecision(ctx, plugin, "decline", false))
                        .then(Commands.argument("player", StringArgumentType.greedyString())
                                .executes(ctx -> inviteDecision(ctx, plugin, "decline", true))))
                .then(Commands.argument("player", StringArgumentType.greedyString())
                        .executes(ctx -> invitePlayer(ctx, plugin, huskTownsApiHook, cityLevelService, cfg)));
    }

    private static int invitePlayer(
            CommandContext<CommandSourceStack> ctx,
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            CityLevelService cityLevelService,
            PluginConfigUtils cfg
    ) {
        Player player = CommandGuards.cityPlayer(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }

        Optional<Member> member = TownMemberGuards.requirePrivilege(
                player,
                huskTownsApiHook,
                cfg,
                Privilege.INVITE,
                "city.invite.errors.no_town",
                "<red>Devi essere membro attivo di una citta per invitare.</red>",
                "city.invite.errors.no_permission",
                "<red>Non hai i permessi per invitare nuovi membri.</red>"
        );
        if (member.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        CityLevelService.LevelStatus status = cityLevelService.statusForTown(member.get().town().getId(), false);
        int currentMembers = member.get().town().getMembers().size();
        int memberCap = status.memberCap();
        if (currentMembers >= memberCap) {
            FeedbackUtils.send(
                    player,
                    cfg,
                    "city.invite.errors.member_cap_reached",
                    "<red>Limite membri raggiunto per lo stage corrente ({stage}).</red>",
                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("stage", status.stage().displayName())
            );
            return Command.SINGLE_SUCCESS;
        }

        String target = StringArgumentType.getString(ctx, "player").trim();
        if (target.isBlank()) {
            player.sendMessage(USAGE);
            return Command.SINGLE_SUCCESS;
        }
        return CommandBridgeUtils.dispatchTownCommand(plugin, player, "invite", target);
    }

    private static int inviteDecision(
            CommandContext<CommandSourceStack> ctx,
            Plugin plugin,
            String action,
            boolean withInviterArg
    ) {
        Player player = CommandGuards.cityPlayer(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        if (!withInviterArg) {
            return CommandBridgeUtils.dispatchTownCommand(plugin, player, "invite " + action);
        }
        String inviter = StringArgumentType.getString(ctx, "player").trim();
        if (inviter.isBlank()) {
            return CommandBridgeUtils.dispatchTownCommand(plugin, player, "invite " + action);
        }
        return CommandBridgeUtils.dispatchTownCommand(plugin, player, "invite " + action, inviter);
    }

    private static int usage(CommandSender sender) {
        sender.sendMessage(USAGE);
        return Command.SINGLE_SUCCESS;
    }

}
