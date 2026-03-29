package it.patric.cittaexp.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import it.patric.cittaexp.citylistgui.CityTownListGuiService;
import it.patric.cittaexp.chat.TownChatCommand;
import it.patric.cittaexp.createtown.CityCreateDialogFlow;
import it.patric.cittaexp.cityhubgui.CityHubGuiService;
import it.patric.cittaexp.discord.DiscordCommandService;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.manageclaim.CitySectionMapGuiService;
import it.patric.cittaexp.manageclaim.SectionCommand;
import it.patric.cittaexp.membership.InviteCommand;
import it.patric.cittaexp.trust.CityChunkTrustCommandService;
import it.patric.cittaexp.utils.CommandGuards;
import it.patric.cittaexp.vault.CityItemVaultService;
import it.patric.cittaexp.wiki.CityWikiDialogService;
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
            CityHubGuiService cityHubGuiService,
            CityLevelService cityLevelService,
            HuskTownsApiHook huskTownsApiHook,
            CitySectionMapGuiService citySectionMapGuiService,
            CityTownListGuiService cityTownListGuiService,
            DiscordCommandService discordCommandService,
            CityWikiDialogService cityWikiDialogService,
            CityChunkTrustCommandService cityChunkTrustCommandService,
            CityItemVaultService cityItemVaultService
    ) {
        CommandDescriptionRegistry descriptions = new CommandDescriptionRegistry(plugin);
        return Commands.literal("city")
                .executes(ctx -> openHubRoot(ctx, cityHubGuiService))
                .then(Commands.literal("help")
                        .executes(ctx -> sendHelp(ctx, cityWikiDialogService)))
                .then(Commands.literal("create")
                        .executes(ctx -> openCreateDialog(ctx, createDialogFlow, discordCommandService)))
                .then(Commands.literal("list")
                        .executes(ctx -> openTownList(ctx, cityTownListGuiService)))
                .then(InviteCommand.create(plugin, huskTownsApiHook, cityLevelService, discordCommandService))
                .then(cityChunkTrustCommandService.createCommand())
                .then(cityChunkTrustCommandService.createUntrustCommand())
                .then(SectionCommand.create(citySectionMapGuiService))
                .then(Commands.literal("redeem")
                        .executes(ctx -> redeemPendingRewards(ctx, cityItemVaultService)))
                .then(TownChatCommand.create(plugin, huskTownsApiHook))
                .then(WarpCommand.create(plugin, huskTownsApiHook, descriptions))
                .then(SetWarpCommand.create(plugin, huskTownsApiHook, cityLevelService))
                .then(Commands.literal("discord")
                        .then(Commands.literal("link")
                                .executes(ctx -> discordLink(ctx, discordCommandService)))
                        .then(Commands.literal("status")
                                .executes(ctx -> discordStatus(ctx, discordCommandService)))
                        .then(Commands.literal("unlink")
                                .executes(ctx -> discordUnlink(ctx, discordCommandService))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> createVerifyCommand(
            DiscordCommandService discordCommandService
    ) {
        return Commands.literal("verify")
                .executes(ctx -> discordVerify(ctx, discordCommandService));
    }

    private static int sendHelp(
            CommandContext<CommandSourceStack> ctx,
            CityWikiDialogService cityWikiDialogService
    ) {
        Player player = CommandGuards.cityPlayer(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        cityWikiDialogService.openRoot(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int openHubRoot(
            CommandContext<CommandSourceStack> ctx,
            CityHubGuiService cityHubGuiService
    ) {
        Player player = CommandGuards.cityPlayer(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        cityHubGuiService.openForOwnTown(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int openCreateDialog(
            CommandContext<CommandSourceStack> ctx,
            CityCreateDialogFlow createDialogFlow,
            DiscordCommandService discordCommandService
    ) {
        Player player = CommandGuards.cityPlayer(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        if (!discordCommandService.requireVerified(player, "/city create")) {
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

    private static int redeemPendingRewards(
            CommandContext<CommandSourceStack> ctx,
            CityItemVaultService cityItemVaultService
    ) {
        Player player = CommandGuards.cityPlayer(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        return cityItemVaultService.redeemPendingRewardsCommand(player);
    }

    private static int discordLink(
            CommandContext<CommandSourceStack> ctx,
            DiscordCommandService discordCommandService
    ) {
        Player player = CommandGuards.cityPlayer(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        discordCommandService.openLink(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int discordVerify(
            CommandContext<CommandSourceStack> ctx,
            DiscordCommandService discordCommandService
    ) {
        Player player = CommandGuards.cityPlayer(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        discordCommandService.openVerify(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int discordStatus(
            CommandContext<CommandSourceStack> ctx,
            DiscordCommandService discordCommandService
    ) {
        Player player = CommandGuards.cityPlayer(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        discordCommandService.showStatus(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int discordUnlink(
            CommandContext<CommandSourceStack> ctx,
            DiscordCommandService discordCommandService
    ) {
        Player player = CommandGuards.cityPlayer(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        discordCommandService.unlink(player);
        return Command.SINGLE_SUCCESS;
    }
}
