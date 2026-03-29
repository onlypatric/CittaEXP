package it.patric.cittaexp.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import it.patric.cittaexp.integration.huskclaims.HuskClaimsHookStateService;
import it.patric.cittaexp.integration.huskclaims.HuskClaimsTownFallbackGuardListener;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.integration.husktowns.HuskTownsClaimWorldSanityService;
import it.patric.cittaexp.challenges.ChallengeMode;
import it.patric.cittaexp.challenges.CityChallengeService;
import it.patric.cittaexp.challenges.StaffEvent;
import it.patric.cittaexp.challenges.StaffEventService;
import it.patric.cittaexp.challenges.StaffEventStatus;
import it.patric.cittaexp.CittaExpPlugin;
import it.patric.cittaexp.cityhubgui.CityHubGuiService;
import it.patric.cittaexp.custommobs.CustomMobSubsystem;
import it.patric.cittaexp.defense.CityDefenseService;
import it.patric.cittaexp.defense.CityDefenseTier;
import it.patric.cittaexp.discord.DiscordCommandService;
import it.patric.cittaexp.economy.EconomyInflationService;
import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.levels.CityLevelRepository;
import it.patric.cittaexp.levels.CityLevelStore;
import it.patric.cittaexp.levels.CityTaxService;
import it.patric.cittaexp.levels.GlobalHopperChunkCapListener;
import it.patric.cittaexp.levels.StaffLevelCommand;
import it.patric.cittaexp.playersgui.CityPlayersGuiService;
import it.patric.cittaexp.relationsgui.CityRelationsGuiService;
import it.patric.cittaexp.utils.CommandSuggestionUtils;
import it.patric.cittaexp.utils.PluginConfigUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import net.william278.husktowns.town.Town;

public final class CittaExpCommandTree {

    private static final String PROBE_PERMISSION = "cittaexp.admin.probe";
    private static final String CHALLENGES_REGENERATE_PERMISSION = "cittaexp.staff.level";
    private static final String CUSTOM_MOBS_PERMISSION = "cittaexp.staff.custommobs";
    private static final String DEFENSE_PERMISSION = "cittaexp.staff.defense";
    private static final String ECONOMY_PERMISSION = "cittaexp.staff.economy";
    private static final String DISCORD_PERMISSION = "cittaexp.staff.discord";
    private static final String HOPPER_PERMISSION = "cittaexp.staff.hopper";
    private static final String CLAIMS_PERMISSION = "cittaexp.staff.claims";
    private static final String RELOAD_PERMISSION = "cittaexp.admin.reload";
    private static final String STAFF_EVENTS_MANAGE_PERMISSION = "cittaexp.staff.events.manage";
    private static final String STAFF_EVENTS_REVIEW_PERMISSION = "cittaexp.staff.events.review";
    private static final String STAFF_EVENTS_REWARD_PERMISSION = "cittaexp.staff.events.reward";

    private CittaExpCommandTree() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create(
            Plugin plugin,
            StaffLevelCommandContext staffContext,
            CityHubGuiService cityHubGuiService,
            CityPlayersGuiService cityPlayersGuiService,
            CityRelationsGuiService cityRelationsGuiService,
            CityLevelService cityLevelService,
            HuskTownsApiHook huskTownsApiHook,
            HuskTownsClaimWorldSanityService huskTownsClaimWorldSanityService,
            DiscordCommandService discordCommandService
    ) {
        PluginConfigUtils cfg = new PluginConfigUtils(plugin);
        CommandDescriptionRegistry descriptions = new CommandDescriptionRegistry(plugin);
        return Commands.literal("cittaexp")
                .executes(ctx -> sendUsage(ctx.getSource().getSender()))
                .then(Commands.literal("help").executes(ctx -> sendHelp(ctx, descriptions)))
                .then(Commands.literal("reload").executes(ctx -> reloadPlugin(ctx, plugin)))
                .then(Commands.literal("who")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> lookupDiscordLink(
                                        ctx,
                                        discordCommandService,
                                        StringArgumentType.getString(ctx, "player")
                                ))))
                .then(Commands.literal("probe").executes(ctx -> probe(ctx, plugin, staffContext)))
                .then(createAdministrativeCommands(
                        "staff",
                        plugin,
                        staffContext,
                        cfg,
                        descriptions,
                        cityHubGuiService,
                        cityPlayersGuiService,
                        cityRelationsGuiService,
                        cityLevelService,
                        huskTownsApiHook,
                        huskTownsClaimWorldSanityService,
                        discordCommandService
                ))
                .then(createAdministrativeCommands(
                        "admin",
                        plugin,
                        staffContext,
                        cfg,
                        descriptions,
                        cityHubGuiService,
                        cityPlayersGuiService,
                        cityRelationsGuiService,
                        cityLevelService,
                        huskTownsApiHook,
                        huskTownsClaimWorldSanityService,
                        discordCommandService
                ));
    }

    private static int sendHelp(
            CommandContext<CommandSourceStack> ctx,
            CommandDescriptionRegistry descriptions
    ) {
        descriptions.sendHelp(ctx.getSource().getSender(), CommandDescriptionRegistry.Scope.CITTAEXP);
        return Command.SINGLE_SUCCESS;
    }

    private static int sendUsage(CommandSender sender) {
        sender.sendMessage("[CittaEXP] Uso: /cittaexp <help|reload|who <player>|probe|staff ...|admin ...>");
        return Command.SINGLE_SUCCESS;
    }

    private static int reloadPlugin(CommandContext<CommandSourceStack> ctx, Plugin plugin) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(RELOAD_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + RELOAD_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        if (!(plugin instanceof CittaExpPlugin cittaExpPlugin)) {
            sender.sendMessage("[CittaEXP] Reload non disponibile in questo runtime.");
            return Command.SINGLE_SUCCESS;
        }
        cittaExpPlugin.scheduleFullReload(sender);
        return Command.SINGLE_SUCCESS;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createAdministrativeCommands(
            String rootLiteral,
            Plugin plugin,
            StaffLevelCommandContext staffContext,
            PluginConfigUtils cfg,
            CommandDescriptionRegistry descriptions,
            CityHubGuiService cityHubGuiService,
            CityPlayersGuiService cityPlayersGuiService,
            CityRelationsGuiService cityRelationsGuiService,
            CityLevelService cityLevelService,
            HuskTownsApiHook huskTownsApiHook,
            HuskTownsClaimWorldSanityService huskTownsClaimWorldSanityService,
            DiscordCommandService discordCommandService
    ) {
        return Commands.literal(rootLiteral)
                .executes(ctx -> sendAdminUsage(ctx.getSource().getSender(), rootLiteral))
                .then(Commands.literal("help").executes(ctx -> sendAdminUsage(ctx.getSource().getSender(), rootLiteral)))
                .then(createTownAdminCommands(cityPlayersGuiService, cityLevelService, huskTownsApiHook, rootLiteral))
                .then(createClaimsCommands(cityLevelService, huskTownsApiHook, huskTownsClaimWorldSanityService))
                .then(createBankCommands(huskTownsApiHook))
                .then(createVaultCommands(cityPlayersGuiService, huskTownsApiHook))
                .then(createXpCommands(cityLevelService, huskTownsApiHook))
                .then(StaffLevelCommand.create(plugin, staffContext.requestService(), cfg, descriptions))
                .then(Commands.literal("challenges")
                        .then(Commands.literal("regenerate")
                                .executes(ctx -> regenerateChallenges(ctx, plugin, staffContext)))
                        .then(Commands.literal("race")
                                .then(Commands.literal("1830")
                                        .executes(ctx -> forceDailyRace(ctx, staffContext, ChallengeMode.DAILY_SPRINT_1830)))
                                .then(Commands.literal("2130")
                                        .executes(ctx -> forceDailyRace(ctx, staffContext, ChallengeMode.DAILY_SPRINT_2130)))
                                .then(Commands.literal("both")
                                        .executes(ctx -> forceBothDailyRaces(ctx, staffContext))))
                        .then(createChallengeEventsCommands(staffContext, huskTownsApiHook)))
                .then(createDefenseCommands(staffContext, cityHubGuiService))
                .then(createWarCommands(cityHubGuiService))
                .then(createRelationsCommands(cityRelationsGuiService))
                .then(createDiscordCommands(discordCommandService))
                .then(createHopperCommands(plugin))
                .then(createEconomyCommands(staffContext))
                .then(createCustomMobsCommands(staffContext));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createClaimsCommands(
            CityLevelService cityLevelService,
            HuskTownsApiHook huskTownsApiHook,
            HuskTownsClaimWorldSanityService huskTownsClaimWorldSanityService
    ) {
        return Commands.literal("claims")
                .then(Commands.literal("get")
                        .then(Commands.argument("town", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestTownNames(builder, huskTownsApiHook))
                                .executes(ctx -> getAdminClaimBonus(
                                        ctx,
                                        cityLevelService,
                                        huskTownsApiHook,
                                        StringArgumentType.getString(ctx, "town")
                                ))))
                .then(Commands.literal("give")
                        .then(Commands.argument("town", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestTownNames(builder, huskTownsApiHook))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(ctx -> mutateAdminClaimBonus(
                                                ctx,
                                                cityLevelService,
                                                huskTownsApiHook,
                                                ClaimBonusMutation.GIVE,
                                                StringArgumentType.getString(ctx, "town"),
                                                IntegerArgumentType.getInteger(ctx, "amount")
                                        )))))
                .then(Commands.literal("take")
                        .then(Commands.argument("town", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestTownNames(builder, huskTownsApiHook))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(ctx -> mutateAdminClaimBonus(
                                                ctx,
                                                cityLevelService,
                                                huskTownsApiHook,
                                                ClaimBonusMutation.TAKE,
                                                StringArgumentType.getString(ctx, "town"),
                                                IntegerArgumentType.getInteger(ctx, "amount")
                                        )))))
                .then(Commands.literal("set")
                        .then(Commands.argument("town", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestTownNames(builder, huskTownsApiHook))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(ctx -> mutateAdminClaimBonus(
                                                ctx,
                                                cityLevelService,
                                                huskTownsApiHook,
                                                ClaimBonusMutation.SET,
                                                StringArgumentType.getString(ctx, "town"),
                                                IntegerArgumentType.getInteger(ctx, "amount")
                                        )))))
                .then(Commands.literal("doctor")
                        .executes(ctx -> doctorClaims(ctx, huskTownsClaimWorldSanityService)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createTownAdminCommands(
            CityPlayersGuiService cityPlayersGuiService,
            CityLevelService cityLevelService,
            HuskTownsApiHook huskTownsApiHook,
            String rootLiteral
    ) {
        return Commands.literal("town")
                .executes(ctx -> sendTownAdminUsage(ctx.getSource().getSender(), rootLiteral))
                .then(Commands.literal("help").executes(ctx -> sendTownAdminUsage(ctx.getSource().getSender(), rootLiteral)))
                .then(Commands.literal("info")
                        .then(Commands.argument("town", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestTownNames(builder, huskTownsApiHook))
                                .executes(ctx -> sendTownInfo(
                                        ctx.getSource().getSender(),
                                        cityLevelService,
                                        huskTownsApiHook,
                                        StringArgumentType.getString(ctx, "town")
                                ))))
                .then(Commands.literal("players")
                        .then(Commands.argument("town", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> suggestTownNames(builder, huskTownsApiHook))
                                .executes(ctx -> openTownPlayers(
                                        ctx,
                                        cityPlayersGuiService,
                                        StringArgumentType.getString(ctx, "town")
                                ))))
                .then(createForwardedAdminTownArgsCommand("claim"))
                .then(createForwardedAdminTownArgsCommand("unclaim"))
                .then(Commands.literal("ignoreclaims")
                        .executes(ctx -> dispatchCommand(ctx.getSource().getSender(), "admintown ignoreclaims", CLAIMS_PERMISSION)))
                .then(Commands.literal("chatspy")
                        .executes(ctx -> dispatchCommand(ctx.getSource().getSender(), "admintown chatspy", CLAIMS_PERMISSION)))
                .then(Commands.literal("delete")
                        .then(Commands.argument("town", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestTownNames(builder, huskTownsApiHook))
                                .executes(ctx -> dispatchCommand(
                                        ctx.getSource().getSender(),
                                        "admintown delete " + StringArgumentType.getString(ctx, "town"),
                                        CLAIMS_PERMISSION
                                ))))
                .then(Commands.literal("takeover")
                        .then(Commands.argument("town", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestTownNames(builder, huskTownsApiHook))
                                .executes(ctx -> dispatchCommand(
                                        ctx.getSource().getSender(),
                                        "admintown takeover " + StringArgumentType.getString(ctx, "town"),
                                        CLAIMS_PERMISSION
                                ))))
                .then(Commands.literal("fulltakeover")
                        .then(Commands.argument("town", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestTownNames(builder, huskTownsApiHook))
                                .executes(ctx -> dispatchCommand(
                                        ctx.getSource().getSender(),
                                        "admintown takeover " + StringArgumentType.getString(ctx, "town"),
                                        CLAIMS_PERMISSION
                                ))))
                .then(Commands.literal("balance")
                        .then(Commands.literal("get")
                                .then(Commands.argument("town", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestTownNames(builder, huskTownsApiHook))
                                        .executes(ctx -> sendTownBalance(
                                                ctx.getSource().getSender(),
                                                huskTownsApiHook,
                                                StringArgumentType.getString(ctx, "town")
                                        ))))
                        .then(Commands.argument("town", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestTownNames(builder, huskTownsApiHook))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                                .executes(ctx -> dispatchCommand(
                                                        ctx.getSource().getSender(),
                                                        "admintown balance "
                                                                + StringArgumentType.getString(ctx, "town")
                                                                + " set "
                                                                + DoubleArgumentType.getDouble(ctx, "amount"),
                                                        ECONOMY_PERMISSION
                                                ))))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                                .executes(ctx -> dispatchCommand(
                                                        ctx.getSource().getSender(),
                                                        "admintown balance "
                                                                + StringArgumentType.getString(ctx, "town")
                                                                + " add "
                                                                + DoubleArgumentType.getDouble(ctx, "amount"),
                                                        ECONOMY_PERMISSION
                                                ))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                                .executes(ctx -> dispatchCommand(
                                                        ctx.getSource().getSender(),
                                                        "admintown balance "
                                                                + StringArgumentType.getString(ctx, "town")
                                                                + " remove "
                                                                + DoubleArgumentType.getDouble(ctx, "amount"),
                                                        ECONOMY_PERMISSION
                                                ))))))
                .then(Commands.literal("setlevel")
                        .then(Commands.argument("town", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestTownNames(builder, huskTownsApiHook))
                                .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                        .executes(ctx -> dispatchCommand(
                                                ctx.getSource().getSender(),
                                                "admintown setlevel "
                                                        + StringArgumentType.getString(ctx, "town")
                                                        + " "
                                                        + IntegerArgumentType.getInteger(ctx, "level"),
                                                CHALLENGES_REGENERATE_PERMISSION
                                        )))))
                .then(createForwardedAdminTownArgsCommand("prune"))
                .then(Commands.literal("advancements")
                        .then(Commands.literal("list")
                                .executes(ctx -> dispatchCommand(ctx.getSource().getSender(), "admintown advancements list", CLAIMS_PERMISSION))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(ctx -> dispatchCommand(
                                                ctx.getSource().getSender(),
                                                "admintown advancements list " + StringArgumentType.getString(ctx, "player"),
                                                CLAIMS_PERMISSION
                                        ))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(ctx -> dispatchCommand(
                                                ctx.getSource().getSender(),
                                                "admintown advancements reset " + StringArgumentType.getString(ctx, "player"),
                                                CLAIMS_PERMISSION
                                        )))))
                .then(Commands.literal("bonus")
                        .then(Commands.literal("get")
                                .then(Commands.argument("town", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestTownNames(builder, huskTownsApiHook))
                                        .executes(ctx -> dispatchCommand(
                                                ctx.getSource().getSender(),
                                                "admintown bonus town " + StringArgumentType.getString(ctx, "town"),
                                                CLAIMS_PERMISSION
                                        ))))
                        .then(createTownBonusMutationCommand("set", huskTownsApiHook))
                        .then(createTownBonusMutationCommand("add", huskTownsApiHook))
                        .then(createTownBonusMutationCommand("remove", huskTownsApiHook))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("town", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestTownNames(builder, huskTownsApiHook))
                                .then(Commands.argument("bonus", StringArgumentType.word())
                                                .suggests((ctx, builder) -> suggestTownBonuses(builder))
                                                .executes(ctx -> dispatchCommand(
                                                        ctx.getSource().getSender(),
                                                        "admintown bonus town "
                                                                + StringArgumentType.getString(ctx, "town")
                                                                + " clear "
                                                                + StringArgumentType.getString(ctx, "bonus"),
                                                        CLAIMS_PERMISSION
                                                ))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createBankCommands(
            HuskTownsApiHook huskTownsApiHook
    ) {
        return Commands.literal("bank")
                .then(Commands.literal("get")
                        .then(Commands.argument("town", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestTownNames(builder, huskTownsApiHook))
                                .executes(ctx -> sendTownBalance(
                                        ctx.getSource().getSender(),
                                        huskTownsApiHook,
                                        StringArgumentType.getString(ctx, "town")
                                ))))
                .then(createBankMutationCommand("set", "set", huskTownsApiHook))
                .then(createBankMutationCommand("add", "add", huskTownsApiHook))
                .then(createBankMutationCommand("take", "remove", huskTownsApiHook));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createChallengeEventsCommands(
            StaffLevelCommandContext staffContext,
            HuskTownsApiHook huskTownsApiHook
    ) {
        return Commands.literal("events")
                .then(Commands.literal("list")
                        .executes(ctx -> listStaffEvents(ctx, staffContext)))
                .then(Commands.literal("info")
                        .then(Commands.argument("eventId", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestEventIds(builder, staffContext.staffEventService()))
                                .executes(ctx -> sendStaffEventInfo(
                                        ctx,
                                        staffContext,
                                        StringArgumentType.getString(ctx, "eventId")
                                ))))
                .then(Commands.literal("create")
                        .then(Commands.literal("auto")
                                .executes(ctx -> openCreateAutoEventDialog(ctx, staffContext)))
                        .then(Commands.literal("judged")
                                .executes(ctx -> openCreateJudgedEventDialog(ctx, staffContext))))
                .then(Commands.literal("publish")
                        .then(Commands.argument("eventId", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestEventIds(builder, staffContext.staffEventService()))
                                .executes(ctx -> mutateStaffEvent(
                                        ctx,
                                        staffContext,
                                        StringArgumentType.getString(ctx, "eventId"),
                                        StaffEventMutation.PUBLISH
                                ))))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("eventId", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestEventIds(builder, staffContext.staffEventService()))
                                .executes(ctx -> mutateStaffEvent(
                                        ctx,
                                        staffContext,
                                        StringArgumentType.getString(ctx, "eventId"),
                                        StaffEventMutation.CANCEL
                                ))))
                .then(Commands.literal("archive")
                        .then(Commands.argument("eventId", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestEventIds(builder, staffContext.staffEventService()))
                                .executes(ctx -> mutateStaffEvent(
                                        ctx,
                                        staffContext,
                                        StringArgumentType.getString(ctx, "eventId"),
                                        StaffEventMutation.ARCHIVE
                                ))))
                .then(Commands.literal("start")
                        .then(Commands.argument("eventId", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestEventIds(builder, staffContext.staffEventService()))
                                .executes(ctx -> mutateStaffEvent(
                                        ctx,
                                        staffContext,
                                        StringArgumentType.getString(ctx, "eventId"),
                                        StaffEventMutation.START
                                ))))
                .then(Commands.literal("close")
                        .then(Commands.argument("eventId", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestEventIds(builder, staffContext.staffEventService()))
                                .executes(ctx -> mutateStaffEvent(
                                        ctx,
                                        staffContext,
                                        StringArgumentType.getString(ctx, "eventId"),
                                        StaffEventMutation.CLOSE
                                ))))
                .then(Commands.literal("submissions")
                        .then(Commands.argument("eventId", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestEventIds(builder, staffContext.staffEventService()))
                                .executes(ctx -> listEventSubmissions(
                                        ctx,
                                        staffContext,
                                        StringArgumentType.getString(ctx, "eventId")
                                ))))
                .then(Commands.literal("review")
                        .then(Commands.argument("eventId", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestEventIds(builder, staffContext.staffEventService()))
                                .then(Commands.argument("town", StringArgumentType.greedyString())
                                        .suggests((ctx, builder) -> suggestTownNames(builder, huskTownsApiHook))
                                        .executes(ctx -> openReviewSubmissionDialog(
                                                ctx,
                                                staffContext,
                                                huskTownsApiHook,
                                                StringArgumentType.getString(ctx, "eventId"),
                                                StringArgumentType.getString(ctx, "town")
                                        )))))
                .then(Commands.literal("place")
                        .then(Commands.argument("eventId", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestEventIds(builder, staffContext.staffEventService()))
                                .then(Commands.argument("town", StringArgumentType.greedyString())
                                        .suggests((ctx, builder) -> suggestTownNames(builder, huskTownsApiHook))
                                        .then(Commands.argument("placement", IntegerArgumentType.integer(1))
                                                .executes(ctx -> assignSubmissionPlacement(
                                                        ctx,
                                                        staffContext,
                                                        huskTownsApiHook,
                                                        StringArgumentType.getString(ctx, "eventId"),
                                                        StringArgumentType.getString(ctx, "town"),
                                                        IntegerArgumentType.getInteger(ctx, "placement")
                                                ))))))
                .then(Commands.literal("reward")
                        .then(Commands.argument("eventId", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestEventIds(builder, staffContext.staffEventService()))
                                .then(Commands.argument("town", StringArgumentType.greedyString())
                                        .suggests((ctx, builder) -> suggestTownNames(builder, huskTownsApiHook))
                                        .executes(ctx -> openRewardSubmissionDialog(
                                                ctx,
                                                staffContext,
                                                huskTownsApiHook,
                                                StringArgumentType.getString(ctx, "eventId"),
                                                StringArgumentType.getString(ctx, "town")
                                        )))))
                .then(Commands.literal("publish-results")
                        .then(Commands.argument("eventId", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestEventIds(builder, staffContext.staffEventService()))
                                .executes(ctx -> mutateStaffEvent(
                                        ctx,
                                        staffContext,
                                        StringArgumentType.getString(ctx, "eventId"),
                                        StaffEventMutation.PUBLISH_RESULTS
                                ))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createBankMutationCommand(
            String literal,
            String forwardedAction,
            HuskTownsApiHook huskTownsApiHook
    ) {
        return Commands.literal(literal)
                .then(Commands.argument("town", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestTownNames(builder, huskTownsApiHook))
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                .executes(ctx -> dispatchCommand(
                                        ctx.getSource().getSender(),
                                        "admintown balance "
                                                + StringArgumentType.getString(ctx, "town")
                                                + " "
                                                + forwardedAction
                                                + " "
                                                + DoubleArgumentType.getDouble(ctx, "amount"),
                                        ECONOMY_PERMISSION
                                ))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createVaultCommands(
            CityPlayersGuiService cityPlayersGuiService,
            HuskTownsApiHook huskTownsApiHook
    ) {
        return Commands.literal("vault")
                .then(Commands.literal("open")
                        .then(Commands.argument("town", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> suggestTownNames(builder, huskTownsApiHook))
                                .executes(ctx -> openAdminVault(
                                        ctx,
                                        cityPlayersGuiService,
                                        StringArgumentType.getString(ctx, "town")
                                ))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createXpCommands(
            CityLevelService cityLevelService,
            HuskTownsApiHook huskTownsApiHook
    ) {
        return Commands.literal("xp")
                .then(Commands.literal("get")
                        .then(Commands.argument("town", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestTownNames(builder, huskTownsApiHook))
                                .executes(ctx -> getTownXp(
                                        ctx,
                                        cityLevelService,
                                        huskTownsApiHook,
                                        StringArgumentType.getString(ctx, "town")
                                ))))
                .then(Commands.literal("add")
                        .then(Commands.argument("town", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestTownNames(builder, huskTownsApiHook))
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(ctx -> mutateTownXp(
                                                ctx,
                                                cityLevelService,
                                                huskTownsApiHook,
                                                XpMutation.ADD,
                                                StringArgumentType.getString(ctx, "town"),
                                                DoubleArgumentType.getDouble(ctx, "amount")
                                        )))))
                .then(Commands.literal("take")
                        .then(Commands.argument("town", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestTownNames(builder, huskTownsApiHook))
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(ctx -> mutateTownXp(
                                                ctx,
                                                cityLevelService,
                                                huskTownsApiHook,
                                                XpMutation.TAKE,
                                                StringArgumentType.getString(ctx, "town"),
                                                DoubleArgumentType.getDouble(ctx, "amount")
                                        )))))
                .then(Commands.literal("set")
                        .then(Commands.argument("town", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestTownNames(builder, huskTownsApiHook))
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(ctx -> mutateTownXp(
                                                ctx,
                                                cityLevelService,
                                                huskTownsApiHook,
                                                XpMutation.SET,
                                                StringArgumentType.getString(ctx, "town"),
                                                DoubleArgumentType.getDouble(ctx, "amount")
                                        )))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createForwardedAdminTownArgsCommand(String subcommand) {
        return Commands.literal(subcommand)
                .executes(ctx -> dispatchCommand(ctx.getSource().getSender(), "admintown " + subcommand, CLAIMS_PERMISSION))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes(ctx -> dispatchCommand(
                                ctx.getSource().getSender(),
                                "admintown " + subcommand + " " + StringArgumentType.getString(ctx, "args").trim(),
                                CLAIMS_PERMISSION
                        )));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createTownBonusMutationCommand(
            String operation,
            HuskTownsApiHook huskTownsApiHook
    ) {
        return Commands.literal(operation)
                .then(Commands.argument("town", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestTownNames(builder, huskTownsApiHook))
                        .then(Commands.argument("bonus", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestTownBonuses(builder))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(ctx -> dispatchCommand(
                                                ctx.getSource().getSender(),
                                                "admintown bonus town "
                                                        + StringArgumentType.getString(ctx, "town")
                                                        + " " + operation + " "
                                                        + StringArgumentType.getString(ctx, "bonus")
                                                        + " "
                                                        + IntegerArgumentType.getInteger(ctx, "amount"),
                                                CLAIMS_PERMISSION
                                        )))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createDefenseCommands(
            StaffLevelCommandContext staffContext,
            CityHubGuiService cityHubGuiService
    ) {
        return Commands.literal("defense")
                .then(Commands.literal("force-start")
                        .then(Commands.argument("tier", StringArgumentType.word())
                                .suggests(defenseTierSuggestions())
                                .executes(ctx -> forceStartDefense(
                                        ctx,
                                        staffContext,
                                        StringArgumentType.getString(ctx, "tier")
                                ))))
                .then(Commands.literal("live-sample")
                        .executes(ctx -> openDefenseLiveSample(ctx, cityHubGuiService)))
                .then(Commands.literal("force-stop")
                        .executes(ctx -> forceStopDefense(ctx, staffContext)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createWarCommands(
            CityHubGuiService cityHubGuiService
    ) {
        return Commands.literal("war")
                .then(Commands.literal("sample")
                        .then(Commands.literal("list")
                                .executes(ctx -> openWarSample(ctx, cityHubGuiService, CityHubGuiService.WarSampleState.LIST)))
                        .then(Commands.literal("live")
                                .executes(ctx -> openWarSample(ctx, cityHubGuiService, CityHubGuiService.WarSampleState.LIVE)))
                        .then(Commands.literal("incoming")
                                .executes(ctx -> openWarSample(ctx, cityHubGuiService, CityHubGuiService.WarSampleState.INCOMING)))
                        .then(Commands.literal("busy")
                                .executes(ctx -> openWarSample(ctx, cityHubGuiService, CityHubGuiService.WarSampleState.BUSY)))
                        .then(Commands.literal("unavailable")
                                .executes(ctx -> openWarSample(ctx, cityHubGuiService, CityHubGuiService.WarSampleState.UNAVAILABLE)))
                        .then(Commands.literal("ally-warning")
                                .executes(ctx -> openWarSample(ctx, cityHubGuiService, CityHubGuiService.WarSampleState.ALLY_WARNING)))
                        .then(Commands.literal("declare")
                                .executes(ctx -> openWarSample(ctx, cityHubGuiService, CityHubGuiService.WarSampleState.DECLARE))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createRelationsCommands(
            CityRelationsGuiService cityRelationsGuiService
    ) {
        return Commands.literal("relations")
                .then(Commands.literal("sample")
                        .executes(ctx -> openRelationsSample(ctx, cityRelationsGuiService)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createDiscordCommands(
            DiscordCommandService discordCommandService
    ) {
        return Commands.literal("discord")
                .then(Commands.literal("resync")
                        .then(Commands.literal("all")
                                .executes(ctx -> resyncDiscordAll(ctx, discordCommandService)))
                        .then(Commands.argument("town", StringArgumentType.greedyString())
                                .executes(ctx -> resyncDiscordTown(
                                        ctx,
                                        discordCommandService,
                                        StringArgumentType.getString(ctx, "town")
                                ))))
                .then(Commands.literal("lookup")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> lookupDiscordLink(
                                        ctx,
                                        discordCommandService,
                                        StringArgumentType.getString(ctx, "player")
                                ))))
                .then(Commands.literal("unlink")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> unlinkDiscordLink(
                                        ctx,
                                        discordCommandService,
                                        StringArgumentType.getString(ctx, "player")
                                ))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createHopperCommands(Plugin plugin) {
        return Commands.literal("hopper")
                .then(Commands.literal("here")
                        .executes(ctx -> probeHopperHere(ctx, plugin)))
                .then(Commands.literal("chunk")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(ctx -> probeHopperChunk(
                                                ctx,
                                                plugin,
                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                IntegerArgumentType.getInteger(ctx, "z")
                                        )))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createCustomMobsCommands(
            StaffLevelCommandContext staffContext
    ) {
        return Commands.literal("custommobs")
                .then(Commands.literal("probe")
                        .executes(ctx -> probeCustomMobs(ctx, staffContext)))
                .then(Commands.literal("lint")
                        .executes(ctx -> lintCustomMobs(ctx, staffContext)))
                .then(Commands.literal("reload")
                        .executes(ctx -> reloadCustomMobs(ctx, staffContext)))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("mobId", StringArgumentType.word())
                                .suggests(mobIdSuggestions(staffContext))
                                .executes(ctx -> spawnCustomMob(ctx, staffContext, StringArgumentType.getString(ctx, "mobId")))))
                .then(Commands.literal("dump")
                        .then(Commands.argument("uuid", StringArgumentType.word())
                                .executes(ctx -> dumpCustomMob(ctx, staffContext, StringArgumentType.getString(ctx, "uuid")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createEconomyCommands(
            StaffLevelCommandContext staffContext
    ) {
        return Commands.literal("economy")
                .then(Commands.literal("probe")
                        .executes(ctx -> probeEconomy(ctx, staffContext)))
                .then(Commands.literal("recalc")
                        .executes(ctx -> recalcEconomy(ctx, staffContext)))
                .then(Commands.literal("freeze")
                        .executes(ctx -> freezeEconomy(ctx, staffContext)))
                .then(Commands.literal("unfreeze")
                        .executes(ctx -> unfreezeEconomy(ctx, staffContext)))
                .then(Commands.literal("set-bias")
                        .then(Commands.argument("percent", DoubleArgumentType.doubleArg(-90.0D, 200.0D))
                                .executes(ctx -> setEconomyBias(ctx, staffContext, DoubleArgumentType.getDouble(ctx, "percent")))))
                .then(Commands.literal("reset-bias")
                        .executes(ctx -> resetEconomyBias(ctx, staffContext)));
    }

    private static SuggestionProvider<CommandSourceStack> mobIdSuggestions(StaffLevelCommandContext staffContext) {
        return (ctx, builder) -> {
            String remaining = builder.getRemainingLowerCase();
            List<String> mobIds = staffContext.customMobSubsystem().mobRegistry().all().keySet().stream()
                    .sorted()
                    .toList();
            for (String mobId : mobIds) {
                if (!mobId.toLowerCase(java.util.Locale.ROOT).startsWith(remaining)) {
                    continue;
                }
                CommandSuggestionUtils.suggest(builder, mobId, "Custom mob registrato");
            }
            return builder.buildFuture();
        };
    }

    private static SuggestionProvider<CommandSourceStack> defenseTierSuggestions() {
        return (ctx, builder) -> {
            String remaining = builder.getRemainingLowerCase();
            for (CityDefenseTier tier : CityDefenseTier.values()) {
                String token = tier.name().toLowerCase(java.util.Locale.ROOT);
                if (!token.startsWith(remaining) && !tier.name().toLowerCase(java.util.Locale.ROOT).startsWith(remaining)) {
                    continue;
                }
                CommandSuggestionUtils.suggest(builder, token, "Tier difesa");
            }
            return builder.buildFuture();
        };
    }

    private static int sendAdminUsage(CommandSender sender, String rootLiteral) {
        sender.sendMessage("[CittaEXP] Uso: /cittaexp " + rootLiteral + " <town|claims|bank|vault|xp|level|discord|economy|defense|war|relations|challenges|custommobs|hopper>");
        return Command.SINGLE_SUCCESS;
    }

    private static int sendTownAdminUsage(CommandSender sender, String rootLiteral) {
        sender.sendMessage("[CittaEXP] Uso: /cittaexp " + rootLiteral + " town <help|info|players|claim|unclaim|ignoreclaims|chatspy|delete|takeover|fulltakeover|balance|setlevel|prune|advancements|bonus>");
        return Command.SINGLE_SUCCESS;
    }

    private static int dispatchCommand(CommandSender sender, String commandLine, String permission) {
        if (permission != null && !permission.isBlank() && !sender.hasPermission(permission)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + permission);
            return Command.SINGLE_SUCCESS;
        }
        String trimmed = commandLine == null ? "" : commandLine.trim();
        if (trimmed.isBlank()) {
            sender.sendMessage("[CittaEXP] Comando vuoto.");
            return Command.SINGLE_SUCCESS;
        }
        boolean dispatched = Bukkit.dispatchCommand(sender, trimmed);
        if (!dispatched) {
            sender.sendMessage("[CittaEXP] Dispatch fallita verso: /" + trimmed);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestTownNames(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder,
            HuskTownsApiHook huskTownsApiHook
    ) {
        String remaining = builder.getRemainingLowerCase();
        huskTownsApiHook.getTowns().stream()
                .map(Town::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .filter(name -> name.toLowerCase(java.util.Locale.ROOT).startsWith(remaining))
                .forEach(name -> CommandSuggestionUtils.suggest(builder, name, "Citta"));
        return builder.buildFuture();
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestTownBonuses(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        String remaining = builder.getRemainingLowerCase();
        for (Town.Bonus bonus : Town.Bonus.values()) {
            String token = bonus.name().toLowerCase(java.util.Locale.ROOT);
            if (!token.startsWith(remaining)) {
                continue;
            }
            CommandSuggestionUtils.suggest(builder, token, "Bonus citta");
        }
        return builder.buildFuture();
    }

    private static int openTownPlayers(
            CommandContext<CommandSourceStack> ctx,
            CityPlayersGuiService cityPlayersGuiService,
            String townName
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage("[CittaEXP] Questo comando puo essere eseguito solo in-game.");
            return Command.SINGLE_SUCCESS;
        }
        if (!player.hasPermission("cittaexp.city.players.staff")) {
            player.sendMessage("[CittaEXP] Permesso mancante: cittaexp.city.players.staff");
            return Command.SINGLE_SUCCESS;
        }
        String normalized = townName == null ? "" : townName.trim();
        if (normalized.isBlank()) {
            player.sendMessage("[CittaEXP] Specifica una citta.");
            return Command.SINGLE_SUCCESS;
        }
        cityPlayersGuiService.openForTargetTown(player, normalized);
        return Command.SINGLE_SUCCESS;
    }

    private static int openAdminVault(
            CommandContext<CommandSourceStack> ctx,
            CityPlayersGuiService cityPlayersGuiService,
            String townName
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage("[CittaEXP] Questo comando puo essere eseguito solo in-game.");
            return Command.SINGLE_SUCCESS;
        }
        String normalized = townName == null ? "" : townName.trim();
        if (normalized.isBlank()) {
            player.sendMessage("[CittaEXP] Specifica una citta.");
            return Command.SINGLE_SUCCESS;
        }
        cityPlayersGuiService.openAdminVaultForTargetTown(player, normalized);
        return Command.SINGLE_SUCCESS;
    }

    private static int sendTownBalance(
            CommandSender sender,
            HuskTownsApiHook huskTownsApiHook,
            String townName
    ) {
        if (!sender.hasPermission(ECONOMY_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + ECONOMY_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        String normalized = townName == null ? "" : townName.trim();
        if (normalized.isBlank()) {
            sender.sendMessage("[CittaEXP] Specifica una citta.");
            return Command.SINGLE_SUCCESS;
        }
        Optional<Town> town = huskTownsApiHook.getTownByName(normalized);
        if (town.isEmpty()) {
            sender.sendMessage("[CittaEXP] Citta non trovata: " + normalized);
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage("[CittaEXP] Tesoro " + town.get().getName() + " -> saldo=" + town.get().getMoney().toPlainString());
        return Command.SINGLE_SUCCESS;
    }

    private static int sendTownInfo(
            CommandSender sender,
            CityLevelService cityLevelService,
            HuskTownsApiHook huskTownsApiHook,
            String townName
    ) {
        if (!sender.hasPermission(CLAIMS_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + CLAIMS_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        Optional<Town> town = resolveTown(sender, huskTownsApiHook, townName);
        if (town.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        CityLevelService.LevelStatus status = cityLevelService.statusForTown(town.get().getId(), false);
        String mayorName = Optional.ofNullable(Bukkit.getOfflinePlayer(town.get().getMayor()).getName()).orElse(town.get().getMayor().toString());
        String warpState = town.get().getSpawn().isPresent() ? "presente" : "assente";
        sender.sendMessage("[CittaEXP] Citta " + town.get().getName()
                + " -> id=" + town.get().getId()
                + " sindaco=" + mayorName
                + " membri=" + town.get().getMembers().size()
                + " claim=" + town.get().getClaimCount() + "/" + status.claimCap()
                + " saldo=" + town.get().getMoney().toPlainString()
                + " level=" + status.level()
                + " stage=" + status.stage().name()
                + " xp=" + formatXp(status.xp())
                + " shops=" + status.shopCap()
                + " warp=" + warpState);
        return Command.SINGLE_SUCCESS;
    }

    private static int getAdminClaimBonus(
            CommandContext<CommandSourceStack> ctx,
            CityLevelService cityLevelService,
            HuskTownsApiHook huskTownsApiHook,
            String townName
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(CLAIMS_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + CLAIMS_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        String normalized = townName == null ? "" : townName.trim();
        if (normalized.isBlank()) {
            sender.sendMessage("[CittaEXP] Specifica una citta.");
            return Command.SINGLE_SUCCESS;
        }
        Optional<Town> town = huskTownsApiHook.getTownByName(normalized);
        if (town.isEmpty()) {
            sender.sendMessage("[CittaEXP] Citta non trovata: " + normalized);
            return Command.SINGLE_SUCCESS;
        }
        Optional<CityLevelService.ClaimCapSnapshot> snapshot = cityLevelService.claimCapSnapshot(town.get().getId());
        if (snapshot.isEmpty()) {
            sender.sendMessage("[CittaEXP] Impossibile leggere il cap claim di " + town.get().getName());
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage("[CittaEXP] Claim cap " + snapshot.get().townName()
                + " -> stage=" + snapshot.get().stageClaimCap()
                + " admin_bonus=" + snapshot.get().adminClaimBonus()
                + " totale=" + snapshot.get().effectiveClaimCap()
                + " usati=" + snapshot.get().currentClaims());
        return Command.SINGLE_SUCCESS;
    }

    private static int mutateAdminClaimBonus(
            CommandContext<CommandSourceStack> ctx,
            CityLevelService cityLevelService,
            HuskTownsApiHook huskTownsApiHook,
            ClaimBonusMutation mutation,
            String townName,
            int amount
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(CLAIMS_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + CLAIMS_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        String normalized = townName == null ? "" : townName.trim();
        if (normalized.isBlank()) {
            sender.sendMessage("[CittaEXP] Specifica una citta.");
            return Command.SINGLE_SUCCESS;
        }
        Optional<Town> town = huskTownsApiHook.getTownByName(normalized);
        if (town.isEmpty()) {
            sender.sendMessage("[CittaEXP] Citta non trovata: " + normalized);
            return Command.SINGLE_SUCCESS;
        }
        Optional<CityLevelService.ClaimCapSnapshot> updated = switch (mutation) {
            case GIVE -> cityLevelService.giveAdminClaimBonus(town.get().getId(), amount);
            case TAKE -> cityLevelService.takeAdminClaimBonus(town.get().getId(), amount);
            case SET -> cityLevelService.setAdminClaimBonus(town.get().getId(), amount);
        };
        if (updated.isEmpty()) {
            sender.sendMessage("[CittaEXP] Aggiornamento claim bonus fallito per " + town.get().getName());
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage("[CittaEXP] Claim bonus aggiornato " + updated.get().townName()
                + " -> stage=" + updated.get().stageClaimCap()
                + " admin_bonus=" + updated.get().adminClaimBonus()
                + " totale=" + updated.get().effectiveClaimCap()
                + " usati=" + updated.get().currentClaims());
        return Command.SINGLE_SUCCESS;
    }

    private static int getTownXp(
            CommandContext<CommandSourceStack> ctx,
            CityLevelService cityLevelService,
            HuskTownsApiHook huskTownsApiHook,
            String townName
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(CHALLENGES_REGENERATE_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + CHALLENGES_REGENERATE_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        Optional<Town> town = resolveTown(sender, huskTownsApiHook, townName);
        if (town.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        CityLevelService.LevelStatus status = cityLevelService.statusForTown(town.get().getId(), false);
        sender.sendMessage("[CittaEXP] XP citta " + status.townName()
                + " -> xp=" + formatXp(status.xp())
                + " xp_scaled=" + status.xpScaled()
                + " level=" + status.level()
                + " stage=" + status.stage().name());
        return Command.SINGLE_SUCCESS;
    }

    private static int mutateTownXp(
            CommandContext<CommandSourceStack> ctx,
            CityLevelService cityLevelService,
            HuskTownsApiHook huskTownsApiHook,
            XpMutation mutation,
            String townName,
            double amount
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(CHALLENGES_REGENERATE_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + CHALLENGES_REGENERATE_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        Optional<Town> town = resolveTown(sender, huskTownsApiHook, townName);
        if (town.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        long scaled = cityLevelService.toScaledXp(amount);
        UUID actorId = sender instanceof Entity entity ? entity.getUniqueId() : null;
        CityLevelService.LevelStatus status = switch (mutation) {
            case ADD -> cityLevelService.addTownXp(town.get().getId(), scaled, actorId, "admin-xp-add");
            case TAKE -> cityLevelService.takeTownXp(town.get().getId(), scaled, actorId, "admin-xp-take");
            case SET -> cityLevelService.setTownXp(town.get().getId(), scaled, actorId, "admin-xp-set");
        };
        sender.sendMessage("[CittaEXP] XP aggiornata " + status.townName()
                + " -> xp=" + formatXp(status.xp())
                + " xp_scaled=" + status.xpScaled()
                + " level=" + status.level()
                + " stage=" + status.stage().name());
        return Command.SINGLE_SUCCESS;
    }

    private static Optional<Town> resolveTown(CommandSender sender, HuskTownsApiHook huskTownsApiHook, String townName) {
        String normalized = townName == null ? "" : townName.trim();
        if (normalized.isBlank()) {
            sender.sendMessage("[CittaEXP] Specifica una citta.");
            return Optional.empty();
        }
        Optional<Town> town = huskTownsApiHook.getTownByName(normalized);
        if (town.isEmpty()) {
            sender.sendMessage("[CittaEXP] Citta non trovata: " + normalized);
        }
        return town;
    }

    private static String formatXp(double xp) {
        return String.format(java.util.Locale.ROOT, "%.2f", xp);
    }

    private static int probeHopperHere(CommandContext<CommandSourceStack> ctx, Plugin plugin) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(HOPPER_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + HOPPER_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("[CittaEXP] Questo comando richiede un player.");
            return Command.SINGLE_SUCCESS;
        }
        return sendHopperChunkProbe(plugin, player, player.getChunk());
    }

    private static int probeHopperChunk(CommandContext<CommandSourceStack> ctx, Plugin plugin, int chunkX, int chunkZ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(HOPPER_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + HOPPER_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("[CittaEXP] Questo comando richiede un player.");
            return Command.SINGLE_SUCCESS;
        }
        return sendHopperChunkProbe(plugin, player, player.getWorld().getChunkAt(chunkX, chunkZ));
    }

    private static int sendHopperChunkProbe(Plugin plugin, Player player, Chunk chunk) {
        int cap = GlobalHopperChunkCapListener.configuredLimit(plugin);
        long current = GlobalHopperChunkCapListener.countHoppersInChunk(chunk);
        long remaining = Math.max(0L, cap - current);
        String status;
        if (cap <= 0) {
            status = "DISABILITATO";
        } else if (current >= cap) {
            status = "PIENO";
        } else {
            status = "OK";
        }
        player.sendMessage("[CittaEXP] Hopper chunk probe -> mondo=" + chunk.getWorld().getName()
                + " chunk=" + chunk.getX() + "," + chunk.getZ()
                + " hopper=" + current
                + " cap=" + cap
                + " residui=" + remaining
                + " stato=" + status);
        return Command.SINGLE_SUCCESS;
    }

    private enum ClaimBonusMutation {
        GIVE,
        TAKE,
        SET
    }

    private enum XpMutation {
        ADD,
        TAKE,
        SET
    }

    private static int probeCustomMobs(
            CommandContext<CommandSourceStack> ctx,
            StaffLevelCommandContext staffContext
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(CUSTOM_MOBS_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + CUSTOM_MOBS_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        sendCustomMobsDiagnostics(sender, staffContext.customMobSubsystem());
        return Command.SINGLE_SUCCESS;
    }

    private static int probeEconomy(
            CommandContext<CommandSourceStack> ctx,
            StaffLevelCommandContext staffContext
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(ECONOMY_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + ECONOMY_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        sendEconomyDiagnostics(sender, staffContext.economyInflationService());
        return Command.SINGLE_SUCCESS;
    }

    private static int recalcEconomy(
            CommandContext<CommandSourceStack> ctx,
            StaffLevelCommandContext staffContext
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(ECONOMY_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + ECONOMY_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        staffContext.economyInflationService().recalculate();
        sendEconomyDiagnostics(sender, staffContext.economyInflationService());
        return Command.SINGLE_SUCCESS;
    }

    private static int freezeEconomy(
            CommandContext<CommandSourceStack> ctx,
            StaffLevelCommandContext staffContext
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(ECONOMY_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + ECONOMY_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        staffContext.economyInflationService().freeze();
        sender.sendMessage("[CittaEXP] inflation frozen.");
        sendEconomyDiagnostics(sender, staffContext.economyInflationService());
        return Command.SINGLE_SUCCESS;
    }

    private static int unfreezeEconomy(
            CommandContext<CommandSourceStack> ctx,
            StaffLevelCommandContext staffContext
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(ECONOMY_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + ECONOMY_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        staffContext.economyInflationService().unfreeze();
        sender.sendMessage("[CittaEXP] inflation unfrozen.");
        sendEconomyDiagnostics(sender, staffContext.economyInflationService());
        return Command.SINGLE_SUCCESS;
    }

    private static int setEconomyBias(
            CommandContext<CommandSourceStack> ctx,
            StaffLevelCommandContext staffContext,
            double percent
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(ECONOMY_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + ECONOMY_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        staffContext.economyInflationService().setManualBiasPercent(percent);
        sender.sendMessage("[CittaEXP] inflation bias impostato a " + String.format(java.util.Locale.ROOT, "%.2f", percent) + "%.");
        sendEconomyDiagnostics(sender, staffContext.economyInflationService());
        return Command.SINGLE_SUCCESS;
    }

    private static int resyncDiscordAll(
            CommandContext<CommandSourceStack> ctx,
            DiscordCommandService discordCommandService
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(DISCORD_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + DISCORD_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        discordCommandService.resyncAll(sender);
        return Command.SINGLE_SUCCESS;
    }

    private static int resyncDiscordTown(
            CommandContext<CommandSourceStack> ctx,
            DiscordCommandService discordCommandService,
            String townName
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(DISCORD_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + DISCORD_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        discordCommandService.resyncTown(sender, townName);
        return Command.SINGLE_SUCCESS;
    }

    private static int lookupDiscordLink(
            CommandContext<CommandSourceStack> ctx,
            DiscordCommandService discordCommandService,
            String playerName
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(DISCORD_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + DISCORD_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        discordCommandService.lookup(sender, playerName);
        return Command.SINGLE_SUCCESS;
    }

    private static int unlinkDiscordLink(
            CommandContext<CommandSourceStack> ctx,
            DiscordCommandService discordCommandService,
            String playerName
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(DISCORD_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + DISCORD_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        discordCommandService.staffUnlink(sender, playerName);
        return Command.SINGLE_SUCCESS;
    }

    private static int doctorClaims(
            CommandContext<CommandSourceStack> ctx,
            HuskTownsClaimWorldSanityService huskTownsClaimWorldSanityService
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(CLAIMS_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + CLAIMS_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        HuskTownsClaimWorldSanityService.ClaimWorldDiagnostics diagnostics = huskTownsClaimWorldSanityService.analyzeNow();
        sender.sendMessage("[CittaEXP] Claims doctor -> healthy=" + diagnostics.healthy()
                + " runtimeTownClaims=" + diagnostics.runtimeTownClaims()
                + " databaseTownClaims=" + diagnostics.databaseTownClaims()
                + " databaseAdminClaims=" + diagnostics.databaseAdminClaims());
        if (diagnostics.errorMessage() != null && !diagnostics.errorMessage().isBlank()) {
            sender.sendMessage("[CittaEXP] Claims doctor error: " + diagnostics.errorMessage());
            return Command.SINGLE_SUCCESS;
        }
        if (diagnostics.duplicatesFound()) {
            for (HuskTownsClaimWorldSanityService.DuplicateClaimWorld duplicate : diagnostics.duplicates()) {
                for (HuskTownsClaimWorldSanityService.ClaimWorldRow row : duplicate.rows()) {
                    sender.sendMessage("[CittaEXP] DUPLICATE claim_world -> id=" + row.id()
                            + " server=" + row.serverName()
                            + " world=" + row.worldName()
                            + " uuid=" + row.worldUuid()
                            + " env=" + row.worldEnvironment()
                            + " blob=" + row.claimsBlobBytes()
                            + " townClaims=" + row.townClaims()
                            + " adminClaims=" + row.adminClaims());
                }
            }
        }
        if (diagnostics.runtimeClaimMismatch()) {
            sender.sendMessage("[CittaEXP] Claims doctor mismatch: runtimeTownClaims="
                    + diagnostics.runtimeTownClaims() + " databaseTownClaims=" + diagnostics.databaseTownClaims());
        }
        if (!diagnostics.duplicatesFound() && !diagnostics.runtimeClaimMismatch()) {
            sender.sendMessage("[CittaEXP] Claims doctor: nessuna anomalia rilevata.");
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int resetEconomyBias(
            CommandContext<CommandSourceStack> ctx,
            StaffLevelCommandContext staffContext
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(ECONOMY_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + ECONOMY_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        staffContext.economyInflationService().resetManualBias();
        sender.sendMessage("[CittaEXP] inflation bias resettato.");
        sendEconomyDiagnostics(sender, staffContext.economyInflationService());
        return Command.SINGLE_SUCCESS;
    }

    private static int forceStartDefense(
            CommandContext<CommandSourceStack> ctx,
            StaffLevelCommandContext staffContext,
            String rawTier
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(DEFENSE_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + DEFENSE_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("[CittaEXP] Questo comando e disponibile solo in-game.");
            return Command.SINGLE_SUCCESS;
        }
        CityDefenseTier tier;
        try {
            tier = CityDefenseTier.valueOf(rawTier.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            sender.sendMessage("[CittaEXP] Tier difesa non valido: " + rawTier);
            return Command.SINGLE_SUCCESS;
        }
        CityDefenseService.StartResult result = staffContext.defenseService().forceStart(player, tier);
        if (!result.success()) {
            sender.sendMessage("[CittaEXP] Force-start difesa fallito: " + result.reason());
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage("[CittaEXP] Force-start difesa completato: tier=" + tier.name());
        return Command.SINGLE_SUCCESS;
    }

    private static int forceStopDefense(
            CommandContext<CommandSourceStack> ctx,
            StaffLevelCommandContext staffContext
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(DEFENSE_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + DEFENSE_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("[CittaEXP] Questo comando e disponibile solo in-game.");
            return Command.SINGLE_SUCCESS;
        }
        CityDefenseService.StartResult result = staffContext.defenseService().forceStop(player);
        if (!result.success()) {
            sender.sendMessage("[CittaEXP] Force-stop difesa fallito: " + result.reason());
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage("[CittaEXP] Force-stop difesa completato.");
        return Command.SINGLE_SUCCESS;
    }

    private static int openDefenseLiveSample(
            CommandContext<CommandSourceStack> ctx,
            CityHubGuiService cityHubGuiService
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(DEFENSE_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + DEFENSE_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("[CittaEXP] Questo comando e disponibile solo in-game.");
            return Command.SINGLE_SUCCESS;
        }
        cityHubGuiService.openDefenseLiveSampleForOwnTown(player);
        sender.sendMessage("[CittaEXP] Preview live GUI difesa aperta.");
        return Command.SINGLE_SUCCESS;
    }

    private static int openWarSample(
            CommandContext<CommandSourceStack> ctx,
            CityHubGuiService cityHubGuiService,
            CityHubGuiService.WarSampleState state
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(DEFENSE_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + DEFENSE_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("[CittaEXP] Questo comando e disponibile solo in-game.");
            return Command.SINGLE_SUCCESS;
        }
        cityHubGuiService.openWarSampleForOwnTown(player, state);
        sender.sendMessage("[CittaEXP] Preview war sample aperta: " + state.name().toLowerCase(java.util.Locale.ROOT));
        return Command.SINGLE_SUCCESS;
    }

    private static int openRelationsSample(
            CommandContext<CommandSourceStack> ctx,
            CityRelationsGuiService cityRelationsGuiService
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(DEFENSE_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + DEFENSE_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("[CittaEXP] Questo comando e disponibile solo in-game.");
            return Command.SINGLE_SUCCESS;
        }
        cityRelationsGuiService.openSamplePreview(player);
        sender.sendMessage("[CittaEXP] Preview del registro diplomatico aperta.");
        return Command.SINGLE_SUCCESS;
    }

    private static int lintCustomMobs(
            CommandContext<CommandSourceStack> ctx,
            StaffLevelCommandContext staffContext
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(CUSTOM_MOBS_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + CUSTOM_MOBS_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage("[CittaEXP] Lint custom mobs in corso...");
        CustomMobSubsystem.LoadReport report = staffContext.customMobSubsystem().lint();
        sendCustomMobsLoadReport(sender, "lint", report);
        return Command.SINGLE_SUCCESS;
    }

    private static int reloadCustomMobs(
            CommandContext<CommandSourceStack> ctx,
            StaffLevelCommandContext staffContext
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(CUSTOM_MOBS_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + CUSTOM_MOBS_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage("[CittaEXP] Reload custom mobs in corso...");
        CustomMobSubsystem.LoadReport report = staffContext.customMobSubsystem().reload();
        sendCustomMobsLoadReport(sender, "reload", report);
        return Command.SINGLE_SUCCESS;
    }

    private static int spawnCustomMob(
            CommandContext<CommandSourceStack> ctx,
            StaffLevelCommandContext staffContext,
            String mobId
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(CUSTOM_MOBS_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + CUSTOM_MOBS_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("[CittaEXP] Questo comando e disponibile solo in-game.");
            return Command.SINGLE_SUCCESS;
        }
        String normalizedMobId = mobId == null ? "" : mobId.trim();
        if (normalizedMobId.isBlank()) {
            sender.sendMessage("[CittaEXP] mobId mancante.");
            return Command.SINGLE_SUCCESS;
        }
        if (staffContext.customMobSubsystem().mobRegistry().get(normalizedMobId).isEmpty()) {
            sender.sendMessage("[CittaEXP] Custom mob sconosciuto: " + normalizedMobId);
            return Command.SINGLE_SUCCESS;
        }
        try {
            Entity entity = staffContext.customMobSubsystem().spawnService().spawn(
                    normalizedMobId,
                    player.getLocation(),
                    it.patric.cittaexp.custommobs.CustomMobSpawnContext.simple("staff_command")
            );
            sender.sendMessage("[CittaEXP] Spawn completato: mobId=" + normalizedMobId
                    + ", uuid=" + entity.getUniqueId()
                    + ", type=" + entity.getType().name());
        } catch (Exception exception) {
            sender.sendMessage("[CittaEXP] Spawn fallito: " + exception.getMessage());
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int dumpCustomMob(
            CommandContext<CommandSourceStack> ctx,
            StaffLevelCommandContext staffContext,
            String rawUuid
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(CUSTOM_MOBS_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + CUSTOM_MOBS_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        UUID entityId;
        try {
            entityId = UUID.fromString(rawUuid.trim());
        } catch (IllegalArgumentException exception) {
            sender.sendMessage("[CittaEXP] UUID non valido: " + rawUuid);
            return Command.SINGLE_SUCCESS;
        }
        var dump = staffContext.customMobSubsystem().dumpEntity(entityId);
        if (dump.isEmpty()) {
            sender.sendMessage("[CittaEXP] Nessun runtime/entity trovato per uuid=" + entityId);
            return Command.SINGLE_SUCCESS;
        }
        sendCustomMobDump(sender, dump.get());
        return Command.SINGLE_SUCCESS;
    }

    private static int regenerateChallenges(
            CommandContext<CommandSourceStack> ctx,
            Plugin plugin,
            StaffLevelCommandContext staffContext
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(CHALLENGES_REGENERATE_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + CHALLENGES_REGENERATE_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage("[CittaEXP] Rigenerazione sfide in corso...");
        staffContext.challengeService().regenerateAllNow().whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (throwable != null) {
                sender.sendMessage("[CittaEXP] Rigenerazione sfide fallita: " + throwable.getMessage());
                return;
            }
            if (result == null || !result.success()) {
                sender.sendMessage("[CittaEXP] Rigenerazione sfide fallita: " + (result == null ? "-" : result.error()));
                return;
            }
            sender.sendMessage("[CittaEXP] Rigenerazione completata: active=" + result.activeCount()
                    + ", daily=" + result.dailyCount()
                    + ", weekly=" + result.weeklyCount()
                    + ", monthly=" + result.monthlyCount()
                    + ", seasonal=" + result.seasonalCount()
                    + ", events=" + result.eventsCount());
        }));
        return Command.SINGLE_SUCCESS;
    }

    private static int forceDailyRace(
            CommandContext<CommandSourceStack> ctx,
            StaffLevelCommandContext staffContext,
            ChallengeMode mode
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(CHALLENGES_REGENERATE_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + CHALLENGES_REGENERATE_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        CityChallengeService.DailySprintForceResult result = staffContext.challengeService().forceDailySprintToday(mode);
        if (!result.success()) {
            sender.sendMessage("[CittaEXP] Force race fallita: mode=" + (mode == null ? "-" : mode.name())
                    + ", reason=" + result.reason());
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage("[CittaEXP] Force race completata: mode=" + mode.name()
                + ", instance=" + (result.instanceId() == null ? "-" : result.instanceId()));
        return Command.SINGLE_SUCCESS;
    }

    private static int forceBothDailyRaces(
            CommandContext<CommandSourceStack> ctx,
            StaffLevelCommandContext staffContext
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(CHALLENGES_REGENERATE_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + CHALLENGES_REGENERATE_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        CityChallengeService.DailySprintForceResult first = staffContext.challengeService().forceDailySprintToday(ChallengeMode.DAILY_SPRINT_1830);
        CityChallengeService.DailySprintForceResult second = staffContext.challengeService().forceDailySprintToday(ChallengeMode.DAILY_SPRINT_2130);
        sender.sendMessage("[CittaEXP] Force race 1830 -> success=" + first.success()
                + ", reason=" + first.reason()
                + ", instance=" + (first.instanceId() == null ? "-" : first.instanceId()));
        sender.sendMessage("[CittaEXP] Force race 2130 -> success=" + second.success()
                + ", reason=" + second.reason()
                + ", instance=" + (second.instanceId() == null ? "-" : second.instanceId()));
        return Command.SINGLE_SUCCESS;
    }

    private static int probe(
            CommandContext<CommandSourceStack> ctx,
            Plugin plugin,
            StaffLevelCommandContext staffContext
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(PROBE_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + PROBE_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }

        Plugin huskTowns = plugin.getServer().getPluginManager().getPlugin("HuskTowns");
        String status = huskTowns != null && huskTowns.isEnabled() ? "available" : "missing";
        String version = huskTowns != null ? huskTowns.getDescription().getVersion() : "-";
        sender.sendMessage("[CittaEXP] probe: mode=bootstrap, core=" + status + ", version=" + version);
        CityLevelStore.StoreDiagnostics diagnostics = staffContext.store().diagnostics();
        CityLevelRepository.ScanStats scanStats = staffContext.store().scanStats();
        CityTaxService.TaxDiagnostics taxDiagnostics = staffContext.taxService().diagnostics();
        sender.sendMessage("[CittaEXP] levels: towns=" + scanStats.townCount()
                + ", xpScaled=" + scanStats.totalXpScaled()
                + ", pendingRequests=" + staffContext.store().countPendingRequests());
        sender.sendMessage("[CittaEXP] store: mode=" + diagnostics.storageMode()
                + ", cacheProgression=" + diagnostics.progressionCacheSize()
                + ", cacheApprovals=" + diagnostics.approvalCacheSize()
                + ", dirty=" + diagnostics.dirtyEntries()
                + ", outboxPending=" + diagnostics.outboxPending()
                + ", outboxFailed=" + diagnostics.outboxFailed());
        sender.sendMessage("[CittaEXP] store-mysql: lastSuccess=" + diagnostics.lastMySqlSuccessAt()
                + ", lastError=" + diagnostics.lastMySqlError()
                + ", lastErrorAt=" + diagnostics.lastMySqlErrorAt());
        sender.sendMessage("[CittaEXP] tax: lastMonth=" + taxDiagnostics.lastRunMonth()
                + ", lastRunAt=" + (taxDiagnostics.lastRunAt() == null ? "-" : taxDiagnostics.lastRunAt())
                + ", lastError=" + (taxDiagnostics.lastError() == null ? "-" : taxDiagnostics.lastError()));
        CityChallengeService.Diagnostics challengeDiagnostics = staffContext.challengeService().diagnostics();
        String droppedByReason = formatDroppedReasons(plugin, challengeDiagnostics.droppedByReason());
        String boardSignals = formatDroppedReasons(plugin, challengeDiagnostics.boardSignalHits());
        String boardRules = formatDroppedReasons(plugin, challengeDiagnostics.boardHardRuleHits());
        sender.sendMessage("[CittaEXP] challenges: enabled=" + challengeDiagnostics.enabled()
                + ", daily=" + challengeDiagnostics.dailyCycleKey()
                + ", weekly=" + challengeDiagnostics.weeklyCycleKey()
                + ", monthly=" + challengeDiagnostics.monthlyCycleKey()
                + ", storeMode=" + challengeDiagnostics.storeMode()
                + ", mysqlUp=" + challengeDiagnostics.storeMySqlUp()
                + ", outboxPending=" + challengeDiagnostics.storeOutboxPending()
                + ", outboxFailed=" + challengeDiagnostics.storeOutboxFailed()
                + ", active=" + challengeDiagnostics.activeCount()
                + ", races=" + challengeDiagnostics.raceCount()
                + ", dailyCount=" + challengeDiagnostics.dailyCount()
                + ", weeklyCount=" + challengeDiagnostics.weeklyCount()
                + ", monthlyCount=" + challengeDiagnostics.monthlyCount()
                + ", seasonalCount=" + challengeDiagnostics.seasonalCount()
                + ", eventsCount=" + challengeDiagnostics.eventsCount()
                + ", storyBase=" + challengeDiagnostics.storyModeBaseCount()
                + ", storyElite=" + challengeDiagnostics.storyModeEliteCount()
                + ", rewardLedger=" + challengeDiagnostics.rewardLedgerSize()
                + ", lastError=" + challengeDiagnostics.lastError());
        sender.sendMessage("[CittaEXP] challenges-pools: counts=" + formatCandidateCounts(challengeDiagnostics.candidateCounts())
                + ", blocked=" + formatBlockedReasons(challengeDiagnostics.generationBlockedReasons()));
        sender.sendMessage("[CittaEXP] challenges-m5: signals=" + boardSignals
                + ", hardRules=" + boardRules
                + ", lastFallback=" + challengeDiagnostics.boardLastFallbackReason());
        sender.sendMessage("[CittaEXP] challenges-antiabuse: placedRegistry=" + challengeDiagnostics.placedRegistrySize()
                + ", throttleHits=" + challengeDiagnostics.throttleHits()
                + ", capHits=" + challengeDiagnostics.capHits()
                + ", afkHits=" + challengeDiagnostics.afkHits()
                + ", suspicionWarningHits=" + challengeDiagnostics.suspicionWarningHits()
                + ", suspicionClampHits=" + challengeDiagnostics.suspicionClampHits()
                + ", suspicionReviewFlags=" + challengeDiagnostics.suspicionReviewFlags()
                + ", dedupeDrops=" + challengeDiagnostics.dedupeDrops()
                + ", structureLootValidHits=" + challengeDiagnostics.structureLootValidHits()
                + ", explorationValidHits=" + challengeDiagnostics.explorationValidHits()
                + ", lastError=" + challengeDiagnostics.lastAntiAbuseError()
                + ", droppedByReason=" + droppedByReason);
        sender.sendMessage("[CittaEXP] challenges-streak: activeCities=" + challengeDiagnostics.streakActiveCities()
                + ", resets=" + challengeDiagnostics.streakResets()
                + ", max=" + challengeDiagnostics.streakMax());
        sender.sendMessage("[CittaEXP] challenges-events: windowActive=" + challengeDiagnostics.eventWindowActive()
                + ", leaderboardSize=" + challengeDiagnostics.eventLeaderboardSize()
                + ", rewardGrants=" + challengeDiagnostics.eventRewardGrants()
                + ", lastFinalizeError=" + challengeDiagnostics.lastEventFinalizeError());
        sender.sendMessage("[CittaEXP] challenges-m9: fairnessFallbackHits=" + challengeDiagnostics.fairnessFallbackHits()
                + ", personalRewardGrants=" + challengeDiagnostics.personalRewardGrantCount()
                + ", personalRewardSkips=" + challengeDiagnostics.personalRewardSkipCount());
        sender.sendMessage("[CittaEXP] challenges-daily-first: rewardGrants=" + challengeDiagnostics.dailyFirstRewardGrantCount()
                + ", rewardSkips=" + challengeDiagnostics.dailyFirstRewardSkipCount());
        sender.sendMessage("[CittaEXP] challenges-m6: weeklyFirstRewardGrants=" + challengeDiagnostics.weeklyFirstRewardGrantCount()
                + ", weeklyFirstRewardSkips=" + challengeDiagnostics.weeklyFirstRewardSkipCount());
        CityDefenseService.Diagnostics defenseDiagnostics = staffContext.defenseService().diagnostics();
        sender.sendMessage("[CittaEXP] defense: activeGlobal=" + defenseDiagnostics.activeGlobal()
                + ", activeByCity=" + defenseDiagnostics.activeCities()
                + ", activeBossEncounters=" + defenseDiagnostics.activeBossEncounters()
                + ", abortedOnRestart=" + defenseDiagnostics.abortedOnRestart()
                + ", rejectedByCap=" + defenseDiagnostics.rejectedByCap()
                + ", rejectedByCooldown=" + defenseDiagnostics.rejectedByCooldown()
                + ", lastError=" + defenseDiagnostics.lastError());
        sendEconomyDiagnostics(sender, staffContext.economyInflationService());
        CustomMobSubsystem.Diagnostics customMobDiagnostics = staffContext.customMobSubsystem().diagnostics();
        CustomMobSubsystem.LoadReport customMobReport = staffContext.customMobSubsystem().report();
        sender.sendMessage("[CittaEXP] custom-mobs: mobs=" + customMobDiagnostics.mobsLoaded()
                + ", abilities=" + customMobDiagnostics.abilitiesLoaded()
                + ", traits=" + customMobDiagnostics.traitsLoaded()
                + ", variants=" + customMobDiagnostics.variantsLoaded()
                + ", skipped=" + customMobReport.skippedFiles()
                + ", warnings=" + customMobReport.warnings().size()
                + ", activeRuntime=" + customMobDiagnostics.activeRuntimeMobs()
                + ", activeProjectiles=" + customMobDiagnostics.activeProjectileRuntimes()
                + ", activeBossRuntime=" + customMobDiagnostics.activeBossRuntimeMobs());
        sender.sendMessage("[CittaEXP] challenges-m11: lastSyncOk=" + challengeDiagnostics.storeLastSyncOk()
                + ", lastSyncError=" + challengeDiagnostics.storeLastSyncError()
                + ", replayLagSeconds=" + challengeDiagnostics.storeReplayLagSeconds()
                + ", completionRate=" + pct(challengeDiagnostics.completionRate())
                + ", abandonmentRate=" + pct(challengeDiagnostics.abandonmentRate())
                + ", antiAbuseHitRate=" + pct(challengeDiagnostics.antiAbuseHitRate())
                + ", winnerDistribution=" + pct(challengeDiagnostics.winnerDistribution())
                + ", duplicateGrantRate=" + pct(challengeDiagnostics.duplicateGrantRate())
                + ", contributionSpread=" + pct(challengeDiagnostics.contributionSpread())
                + ", topCarryRatio=" + pct(challengeDiagnostics.topCarryRatio())
                + ", rerollUsageRate=" + pct(challengeDiagnostics.rerollUsageRate())
                + ", vetoUsageRate=" + pct(challengeDiagnostics.vetoUsageRate()));

        HuskClaimsHookStateService.Diagnostics claimsDiagnostics = staffContext.huskClaimsHookState().diagnostics();
        sender.sendMessage(format(
                plugin,
                "city.huskclaims.guard.probe.summary",
                "[CittaEXP] confine_guard: mode={mode}, hookEnabled={hookEnabled}, reason={reason}",
                "{mode}", claimsDiagnostics.mode().probeValue(),
                "{hookEnabled}", Boolean.toString(claimsDiagnostics.huskTownsHookEnabled()),
                "{reason}", claimsDiagnostics.reasonCode()
        ));
        if (claimsDiagnostics.mode() == HuskClaimsHookStateService.GuardMode.FALLBACK_GUARD_ACTIVE) {
            HuskClaimsTownFallbackGuardListener fallbackListener = staffContext.huskClaimsFallbackListener();
            long cancelled = fallbackListener != null ? fallbackListener.cancelledCount() : 0L;
            long errors = fallbackListener != null ? fallbackListener.errorCount() : 0L;
            String lastError = fallbackListener != null ? fallbackListener.lastError() : "-";
            sender.sendMessage(format(
                    plugin,
                    "city.huskclaims.guard.probe.fallback_stats",
                    "[CittaEXP] confine_guard(fallback): cancelled={cancelled}, errors={errors}, lastError={lastError}",
                    "{cancelled}", Long.toString(cancelled),
                    "{errors}", Long.toString(errors),
                    "{lastError}", lastError
            ));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static void sendCustomMobsDiagnostics(
            CommandSender sender,
            CustomMobSubsystem subsystem
    ) {
        CustomMobSubsystem.Diagnostics diagnostics = subsystem.diagnostics();
        CustomMobSubsystem.LoadReport report = subsystem.report();
        sender.sendMessage("[CittaEXP] custom-mobs: mobs=" + diagnostics.mobsLoaded()
                + ", abilities=" + diagnostics.abilitiesLoaded()
                + ", traits=" + diagnostics.traitsLoaded()
                + ", variants=" + diagnostics.variantsLoaded()
                + ", skipped=" + diagnostics.skippedFiles()
                + ", warnings=" + diagnostics.warningCount()
                + ", activeRuntime=" + diagnostics.activeRuntimeMobs()
                + ", activeProjectiles=" + diagnostics.activeProjectileRuntimes()
                + ", activeBossRuntime=" + diagnostics.activeBossRuntimeMobs());
        if (!report.warnings().isEmpty()) {
            sender.sendMessage("[CittaEXP] custom-mobs warnings(sample): "
                    + String.join(" | ", report.warnings().stream().limit(3).toList()));
        }
    }

    private static void sendEconomyDiagnostics(
            CommandSender sender,
            EconomyInflationService service
    ) {
        var snapshot = service.snapshot();
        sender.sendMessage("[CittaEXP] economy: frozen=" + snapshot.frozen()
                + ", telemetryReliable=" + snapshot.telemetryReliable()
                + ", bias=" + String.format(java.util.Locale.ROOT, "%.2f%%", snapshot.manualBiasPercent())
                + ", effectiveIndex=" + String.format(java.util.Locale.ROOT, "%.3f", snapshot.effectiveIndex())
                + ", costMultiplier=" + String.format(java.util.Locale.ROOT, "%.3f", snapshot.costMultiplier())
                + ", rewardMultiplier=" + String.format(java.util.Locale.ROOT, "%.3f", snapshot.rewardMultiplier()));
        sender.sendMessage("[CittaEXP] economy-metrics: towns=" + snapshot.townCount()
                + ", treasuryTotal=" + String.format(java.util.Locale.ROOT, "%.2f", snapshot.totalTownTreasury())
                + ", treasuryAvg=" + String.format(java.util.Locale.ROOT, "%.2f", snapshot.averageTownTreasury())
                + ", recentEmission=" + String.format(java.util.Locale.ROOT, "%.2f", snapshot.recentRewardEmission())
                + ", observedIndex=" + String.format(java.util.Locale.ROOT, "%.3f", snapshot.observedIndex())
                + ", timeDriftIndex=" + String.format(java.util.Locale.ROOT, "%.3f", snapshot.timeDriftIndex()));
    }

    private static void sendCustomMobsLoadReport(
            CommandSender sender,
            String verb,
            CustomMobSubsystem.LoadReport report
    ) {
        sender.sendMessage("[CittaEXP] custom-mobs " + verb
                + ": mobs=" + report.mobsLoaded()
                + ", abilities=" + report.abilitiesLoaded()
                + ", traits=" + report.traitsLoaded()
                + ", variants=" + report.variantsLoaded()
                + ", skipped=" + report.skippedFiles()
                + ", warnings=" + report.warnings().size());
        List<String> warnings = report.warnings().stream().limit(10).toList();
        for (String warning : warnings) {
            sender.sendMessage("[CittaEXP] custom-mobs warning: " + warning);
        }
        if (report.warnings().size() > warnings.size()) {
            sender.sendMessage("[CittaEXP] custom-mobs: altre " + (report.warnings().size() - warnings.size()) + " warning omesse.");
        }
    }

    private static void sendCustomMobDump(
            CommandSender sender,
            CustomMobSubsystem.EntityDump dump
    ) {
        sender.sendMessage("[CittaEXP] custom-mobs dump: uuid=" + dump.entityId()
                + ", entityType=" + dump.entityType()
                + ", world=" + dump.worldName()
                + ", location=" + dump.locationSummary()
                + ", valid=" + dump.valid()
                + ", dead=" + dump.dead());
        sender.sendMessage("[CittaEXP] custom-mobs dump-markers: mobId=" + dump.mobId()
                + ", variant=" + dump.variantId()
                + ", trait=" + dump.traitId()
                + ", spawnSource=" + dump.spawnSource()
                + ", ownerTownId=" + (dump.ownerTownId() == null ? "-" : dump.ownerTownId())
                + ", session=" + dump.sessionId()
                + ", encounter=" + dump.encounterId()
                + ", wave=" + dump.waveId()
                + ", defenseTier=" + dump.defenseTier()
                + ", phase=" + dump.phaseId());
        sender.sendMessage("[CittaEXP] custom-mobs dump-state: custom=" + dump.customMob()
                + ", boss=" + dump.boss()
                + ", summoned=" + dump.summoned()
                + ", parent=" + (dump.parentMobUuid() == null ? "-" : dump.parentMobUuid())
                + ", ownedProjectiles=" + dump.ownedProjectileCount());
        dump.runtimeSnapshot().ifPresent(snapshot -> sender.sendMessage("[CittaEXP] custom-mobs runtime: phase="
                + snapshot.phaseNumber() + "/" + snapshot.phaseId()
                + ", label=" + snapshot.phaseLabel()
                + ", abilities=" + String.join(",", snapshot.activeAbilityIds())
                + ", tags=" + String.join(",", snapshot.tags())
                + ", flags=" + String.join(",", snapshot.activeFlags())));
        dump.bossSnapshot().ifPresent(snapshot -> sender.sendMessage("[CittaEXP] custom-mobs boss: display="
                + snapshot.displayName()
                + ", phase=" + snapshot.phaseNumber() + "/" + snapshot.phaseId()
                + ", label=" + snapshot.phaseLabel()
                + ", bossbar=" + snapshot.bossbarEnabled()));
    }

    private static String format(
            Plugin plugin,
            String path,
            String fallback,
            String... replacements
    ) {
        String message = plugin.getConfig().getString(path, fallback);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        return message;
    }

    private static String formatDroppedReasons(Plugin plugin, Map<String, Long> droppedByReason) {
        if (droppedByReason == null || droppedByReason.isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Long> entry : droppedByReason.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            first = false;
            builder.append(localizedReasonLabel(plugin, entry.getKey())).append('=').append(entry.getValue());
        }
        builder.append('}');
        return builder.toString();
    }

    private static String localizedReasonLabel(Plugin plugin, String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            return "-";
        }
        String key = "city.challenges.anti_abuse.reason_labels." + reasonCode.replace(':', '_');
        return plugin.getConfig().getString(key, reasonCode);
    }

    private static String formatCandidateCounts(Map<String, Integer> values) {
        if (values == null || values.isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            first = false;
            builder.append(entry.getKey()).append('=').append(Math.max(0, entry.getValue() == null ? 0 : entry.getValue()));
        }
        builder.append('}');
        return builder.toString();
    }

    private static String formatBlockedReasons(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            first = false;
            builder.append(entry.getKey()).append('=')
                    .append(entry.getValue() == null || entry.getValue().isBlank() ? "-" : entry.getValue());
        }
        builder.append('}');
        return builder.toString();
    }

    private static int listStaffEvents(
            CommandContext<CommandSourceStack> ctx,
            StaffLevelCommandContext staffContext
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(STAFF_EVENTS_MANAGE_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + STAFF_EVENTS_MANAGE_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        List<StaffEvent> events = staffContext.staffEventService().listAll();
        sender.sendMessage("[CittaEXP] Staff events: " + events.size());
        for (StaffEventStatus status : StaffEventStatus.values()) {
            long count = events.stream().filter(event -> event.status() == status).count();
            if (count > 0L) {
                sender.sendMessage(" - " + status.name() + ": " + count);
            }
        }
        for (StaffEvent event : events.stream().limit(10).toList()) {
            sender.sendMessage(" • " + event.eventId() + " [" + event.kind().name() + "/" + event.status().name() + "] "
                    + event.title());
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int sendStaffEventInfo(
            CommandContext<CommandSourceStack> ctx,
            StaffLevelCommandContext staffContext,
            String eventId
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(STAFF_EVENTS_MANAGE_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + STAFF_EVENTS_MANAGE_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        Optional<StaffEvent> event = staffContext.staffEventService().findEvent(eventId);
        if (event.isEmpty()) {
            sender.sendMessage("[CittaEXP] Staff event non trovato: " + eventId);
            return Command.SINGLE_SUCCESS;
        }
        StaffEvent value = event.get();
        sender.sendMessage("[CittaEXP] Event " + value.eventId());
        sender.sendMessage(" - kind: " + value.kind().name());
        sender.sendMessage(" - status: " + value.status().name());
        sender.sendMessage(" - title: " + value.title());
        sender.sendMessage(" - start: " + value.startAt());
        sender.sendMessage(" - end: " + value.endAt());
        sender.sendMessage(" - linkedChallenge: " + (value.linkedChallengeInstanceId() == null ? "-" : value.linkedChallengeInstanceId()));
        sender.sendMessage(" - visible: " + value.visible());
        return Command.SINGLE_SUCCESS;
    }

    private static int openCreateAutoEventDialog(
            CommandContext<CommandSourceStack> ctx,
            StaffLevelCommandContext staffContext
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(STAFF_EVENTS_MANAGE_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + STAFF_EVENTS_MANAGE_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("[CittaEXP] Questo comando e disponibile solo in-game.");
            return Command.SINGLE_SUCCESS;
        }
        staffContext.staffEventService().openCreateAutoDialog(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int openCreateJudgedEventDialog(
            CommandContext<CommandSourceStack> ctx,
            StaffLevelCommandContext staffContext
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(STAFF_EVENTS_MANAGE_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + STAFF_EVENTS_MANAGE_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("[CittaEXP] Questo comando e disponibile solo in-game.");
            return Command.SINGLE_SUCCESS;
        }
        staffContext.staffEventService().openCreateJudgedDialog(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int mutateStaffEvent(
            CommandContext<CommandSourceStack> ctx,
            StaffLevelCommandContext staffContext,
            String eventId,
            StaffEventMutation mutation
    ) {
        CommandSender sender = ctx.getSource().getSender();
        String permission = mutation == StaffEventMutation.PUBLISH_RESULTS ? STAFF_EVENTS_REVIEW_PERMISSION : STAFF_EVENTS_MANAGE_PERMISSION;
        if (!sender.hasPermission(permission)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + permission);
            return Command.SINGLE_SUCCESS;
        }
        UUID actorId = sender instanceof Player player ? player.getUniqueId() : null;
        StaffEventService.TransitionResult result = switch (mutation) {
            case PUBLISH -> staffContext.staffEventService().publish(eventId, actorId);
            case CANCEL -> staffContext.staffEventService().cancel(eventId, actorId);
            case ARCHIVE -> staffContext.staffEventService().archive(eventId, actorId);
            case START -> staffContext.staffEventService().forceStart(eventId, actorId);
            case CLOSE -> staffContext.staffEventService().forceClose(eventId, actorId);
            case PUBLISH_RESULTS -> staffContext.staffEventService().publishResults(eventId, actorId);
        };
        sender.sendMessage("[CittaEXP] staff-event " + mutation.name().toLowerCase(java.util.Locale.ROOT)
                + ": " + result.reason()
                + (result.event() == null ? "" : " [" + result.event().status().name() + "]"));
        return Command.SINGLE_SUCCESS;
    }

    private static int listEventSubmissions(
            CommandContext<CommandSourceStack> ctx,
            StaffLevelCommandContext staffContext,
            String eventId
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(STAFF_EVENTS_REVIEW_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + STAFF_EVENTS_REVIEW_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        List<it.patric.cittaexp.challenges.StaffEventSubmission> submissions = staffContext.staffEventService().listSubmissions(eventId);
        sender.sendMessage("[CittaEXP] Submissions " + eventId + ": " + submissions.size());
        for (it.patric.cittaexp.challenges.StaffEventSubmission submission : submissions) {
            sender.sendMessage(" • town=" + submission.townId()
                    + " status=" + submission.reviewStatus().name()
                    + " placement=" + (submission.placement() == null ? "-" : submission.placement())
                    + " at=" + submission.submittedAt());
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int openReviewSubmissionDialog(
            CommandContext<CommandSourceStack> ctx,
            StaffLevelCommandContext staffContext,
            HuskTownsApiHook huskTownsApiHook,
            String eventId,
            String townName
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(STAFF_EVENTS_REVIEW_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + STAFF_EVENTS_REVIEW_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("[CittaEXP] Questo comando e disponibile solo in-game.");
            return Command.SINGLE_SUCCESS;
        }
        Optional<Town> town = huskTownsApiHook.getTownByName(townName);
        if (town.isEmpty()) {
            sender.sendMessage("[CittaEXP] Citta non trovata: " + townName);
            return Command.SINGLE_SUCCESS;
        }
        staffContext.staffEventService().openReviewDialog(player, eventId, town.get().getId());
        return Command.SINGLE_SUCCESS;
    }

    private static int assignSubmissionPlacement(
            CommandContext<CommandSourceStack> ctx,
            StaffLevelCommandContext staffContext,
            HuskTownsApiHook huskTownsApiHook,
            String eventId,
            String townName,
            int placement
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(STAFF_EVENTS_REVIEW_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + STAFF_EVENTS_REVIEW_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        Optional<Town> town = huskTownsApiHook.getTownByName(townName);
        if (town.isEmpty()) {
            sender.sendMessage("[CittaEXP] Citta non trovata: " + townName);
            return Command.SINGLE_SUCCESS;
        }
        UUID actorId = sender instanceof Player player ? player.getUniqueId() : null;
        it.patric.cittaexp.challenges.StaffEventService.SubmitResult result = staffContext.staffEventService()
                .assignPlacement(eventId, town.get().getId(), placement, actorId);
        sender.sendMessage("[CittaEXP] placement: " + result.reason());
        return Command.SINGLE_SUCCESS;
    }

    private static int openRewardSubmissionDialog(
            CommandContext<CommandSourceStack> ctx,
            StaffLevelCommandContext staffContext,
            HuskTownsApiHook huskTownsApiHook,
            String eventId,
            String townName
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(STAFF_EVENTS_REWARD_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + STAFF_EVENTS_REWARD_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("[CittaEXP] Questo comando e disponibile solo in-game.");
            return Command.SINGLE_SUCCESS;
        }
        Optional<Town> town = huskTownsApiHook.getTownByName(townName);
        if (town.isEmpty()) {
            sender.sendMessage("[CittaEXP] Citta non trovata: " + townName);
            return Command.SINGLE_SUCCESS;
        }
        staffContext.staffEventService().openRewardDialog(player, eventId, town.get().getId());
        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestEventIds(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder,
            StaffEventService staffEventService
    ) {
        for (String eventId : staffEventService.eventIds()) {
            if (eventId != null && !eventId.isBlank()) {
                builder.suggest(eventId);
            }
        }
        return builder.buildFuture();
    }

    private enum StaffEventMutation {
        PUBLISH,
        CANCEL,
        ARCHIVE,
        START,
        CLOSE,
        PUBLISH_RESULTS
    }

    private static String pct(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f%%", Math.max(0.0D, value) * 100.0D);
    }

    public record StaffLevelCommandContext(
            it.patric.cittaexp.levels.CityLevelRequestService requestService,
            CityLevelStore store,
            CityTaxService taxService,
            HuskClaimsHookStateService huskClaimsHookState,
            @Nullable HuskClaimsTownFallbackGuardListener huskClaimsFallbackListener,
            CityChallengeService challengeService,
            StaffEventService staffEventService,
            CityDefenseService defenseService,
            CustomMobSubsystem customMobSubsystem,
            EconomyInflationService economyInflationService
    ) {
    }
}
