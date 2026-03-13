package it.patric.cittaexp.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import it.patric.cittaexp.citylistgui.CityTownListGuiService;
import it.patric.cittaexp.chat.TownChatCommand;
import it.patric.cittaexp.createtown.CityCreateDialogFlow;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.levels.CityLevelCommand;
import it.patric.cittaexp.levels.CityLevelRequestService;
import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.manageclaim.CitySectionMapGuiService;
import it.patric.cittaexp.manageclaim.SectionCommand;
import it.patric.cittaexp.membership.InviteCommand;
import it.patric.cittaexp.playersgui.CityPlayersGuiService;
import it.patric.cittaexp.utils.CommandGuards;
import it.patric.cittaexp.warp.SetWarpCommand;
import it.patric.cittaexp.warp.WarpCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class CityCommandTree {

    private CityCommandTree() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create(
            Plugin plugin,
            CityCreateDialogFlow createDialogFlow,
            CityPlayersGuiService cityPlayersGuiService,
            CityLevelService cityLevelService,
            CityLevelRequestService cityLevelRequestService,
            HuskTownsApiHook huskTownsApiHook,
            CitySectionMapGuiService citySectionMapGuiService,
            CityTownListGuiService cityTownListGuiService
    ) {
        return Commands.literal("city")
                .executes(ctx -> openPlayersRoot(ctx, cityPlayersGuiService))
                .then(Commands.literal("create")
                        .executes(ctx -> openCreateDialog(ctx, createDialogFlow)))
                .then(Commands.literal("list")
                        .executes(ctx -> openTownList(ctx, cityTownListGuiService)))
                .then(InviteCommand.create(plugin, huskTownsApiHook, cityLevelService))
                .then(SectionCommand.create(citySectionMapGuiService))
                .then(TownChatCommand.create(plugin, huskTownsApiHook))
                .then(CityLevelCommand.create(plugin, cityLevelService, cityLevelRequestService))
                .then(WarpCommand.create(plugin, huskTownsApiHook))
                .then(SetWarpCommand.create(plugin, huskTownsApiHook, cityLevelService));
    }

    private static int openPlayersRoot(
            CommandContext<CommandSourceStack> ctx,
            CityPlayersGuiService cityPlayersGuiService
    ) {
        Player player = CommandGuards.cityPlayer(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        cityPlayersGuiService.openForOwnTown(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int openCreateDialog(CommandContext<CommandSourceStack> ctx, CityCreateDialogFlow createDialogFlow) {
        Player player = CommandGuards.cityPlayer(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        createDialogFlow.openIntro(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int openTownList(
            CommandContext<CommandSourceStack> ctx,
            CityTownListGuiService cityTownListGuiService
    ) {
        Player player = CommandGuards.cityPlayer(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        cityTownListGuiService.open(player);
        return Command.SINGLE_SUCCESS;
    }
}
