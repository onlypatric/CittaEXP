package it.patric.cittaexp.levels;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import it.patric.cittaexp.utils.CommandGuards;
import it.patric.cittaexp.utils.PluginConfigUtils;
import it.patric.cittaexp.utils.ReasonMessageMapper;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class CityLevelCommand {

    private CityLevelCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create(
            Plugin plugin,
            CityLevelService cityLevelService,
            CityLevelRequestService requestService
    ) {
        PluginConfigUtils cfg = new PluginConfigUtils(plugin);
        return Commands.literal("level")
                .executes(ctx -> status(ctx, cityLevelService, cfg))
                .then(Commands.literal("upgrade")
                        .executes(ctx -> upgrade(ctx, cityLevelService, cfg)))
                .then(Commands.literal("request")
                        .executes(ctx -> request(ctx, requestService, cfg)));
    }

    private static int status(
            CommandContext<CommandSourceStack> ctx,
            CityLevelService cityLevelService,
            PluginConfigUtils cfg
    ) {
        Player player = CommandGuards.cityPlayer(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }

        Optional<CityLevelService.LevelStatus> status = cityLevelService.statusForPlayer(player, true);
        if (status.isEmpty()) {
            player.sendMessage(cfg.msg("city.level.errors.no_town", "<red>Devi essere membro attivo di una citta.</red>"));
            return Command.SINGLE_SUCCESS;
        }

        CityLevelService.LevelStatus s = status.get();
        player.sendMessage(cfg.msg(
                "city.level.status.header",
                "<gold>Livello Citta:</gold> <yellow>{town}</yellow>",
                Placeholder.unparsed("town", s.townName())
        ));
        player.sendMessage(cfg.msg(
                "city.level.status.current",
                "<gray>Livello:</gray> <white>{level}</white> <gray>| XP:</gray> <white>{xp}</white> <gray>| Stage:</gray> <yellow>{stage}</yellow>",
                Placeholder.unparsed("level", Integer.toString(s.level())),
                Placeholder.unparsed("xp", formatMoney(s.xp())),
                Placeholder.unparsed("stage", s.stage().displayName())
        ));
        if (s.nextStage() == null) {
            player.sendMessage(cfg.msg("city.level.status.max", "<green>Hai raggiunto lo stage massimo disponibile.</green>"));
            return Command.SINGLE_SUCCESS;
        }
        player.sendMessage(cfg.msg(
                "city.level.status.next",
                "<gray>Prossimo stage:</gray> <yellow>{stage}</yellow> <gray>(L{required_level})</gray>",
                Placeholder.unparsed("stage", s.nextStage().displayName()),
                Placeholder.unparsed("required_level", Integer.toString(s.nextRequiredLevel()))
        ));
        player.sendMessage(cfg.msg(
                "city.level.status.requirement",
                "<gray>Requisito banca:</gray> <white>{required_balance}</white> <gray>| Costo upgrade:</gray> <white>{upgrade_cost}</white>",
                Placeholder.unparsed("required_balance", formatMoney(s.requiredBalance())),
                Placeholder.unparsed("upgrade_cost", formatMoney(s.upgradeCost()))
        ));
        if (s.nextStage() == TownStage.VILLAGGIO_I) {
            player.sendMessage(cfg.msg(
                    "city.level.status.spawn_requirement",
                    "<gray>Prerequisito:</gray> <white>warp citta impostato</white> <gray>(/city setwarp)</gray>"
            ));
        }
        if (s.staffApprovalRequired()) {
            player.sendMessage(cfg.msg(
                    "city.level.status.staff_required",
                    "<gold>Questo upgrade richiede approvazione staff: usa</gold> <yellow>/city level request</yellow>."
            ));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int upgrade(
            CommandContext<CommandSourceStack> ctx,
            CityLevelService cityLevelService,
            PluginConfigUtils cfg
    ) {
        Player player = CommandGuards.cityPlayer(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }

        CityLevelService.UpgradeAttempt attempt = cityLevelService.tryUpgrade(player);
        if (!attempt.success()) {
            player.sendMessage(cfg.msg(
                    "city.level.upgrade.failed",
                    "<red>Upgrade non riuscito:</red> <gray>{reason}</gray>",
                    Placeholder.unparsed("reason", mapReason(attempt.reason(), cfg))
            ));
            return Command.SINGLE_SUCCESS;
        }

        player.sendMessage(cfg.msg(
                "city.level.upgrade.success",
                "<green>Upgrade completato:</green> <yellow>{stage}</yellow>",
                Placeholder.unparsed("stage", attempt.upgradedStage().displayName())
        ));
        return Command.SINGLE_SUCCESS;
    }

    private static int request(
            CommandContext<CommandSourceStack> ctx,
            CityLevelRequestService requestService,
            PluginConfigUtils cfg
    ) {
        Player player = CommandGuards.cityPlayer(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }

        CityLevelRequestService.RequestResult result = requestService.createRequest(player);
        if (!result.success()) {
            player.sendMessage(cfg.msg(
                    "city.level.request.failed",
                    "<red>Richiesta non creata:</red> <gray>{reason}</gray>",
                    Placeholder.unparsed("reason", mapReason(result.reason(), cfg))
            ));
            return Command.SINGLE_SUCCESS;
        }

        TownLevelRequest request = result.request();
        player.sendMessage(cfg.msg(
                "city.level.request.success",
                "<green>Richiesta creata:</green> <yellow>#{id}</yellow> <gray>per</gray> <white>{stage}</white>",
                Placeholder.unparsed("id", Long.toString(request.id())),
                Placeholder.unparsed("stage", request.targetStage().displayName())
        ));
        return Command.SINGLE_SUCCESS;
    }

    private static String mapReason(String reason, PluginConfigUtils cfg) {
        return ReasonMessageMapper.text(cfg, "city.level.reasons.", reason);
    }

    private static String formatMoney(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
