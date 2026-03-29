package it.patric.cittaexp.levels;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import it.patric.cittaexp.command.CommandDescriptionRegistry;
import it.patric.cittaexp.utils.CommandSuggestionUtils;
import it.patric.cittaexp.utils.PluginConfigUtils;
import it.patric.cittaexp.utils.ReasonMessageMapper;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class StaffLevelCommand {

    private static final String STAFF_PERMISSION = "cittaexp.staff.level";

    private StaffLevelCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create(
            Plugin plugin,
            CityLevelRequestService requestService,
            PluginConfigUtils cfg,
            CommandDescriptionRegistry descriptions
    ) {
        return Commands.literal("level")
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx, requestService, cfg))
                        .then(Commands.argument("status", StringArgumentType.word())
                                .suggests(statusSuggestions(descriptions))
                                .executes(ctx -> list(ctx, requestService, cfg))))
                .then(Commands.literal("approve")
                        .then(Commands.argument("requestId", LongArgumentType.longArg(1))
                                .executes(ctx -> approve(ctx, requestService, cfg, ""))
                                .then(Commands.argument("note", StringArgumentType.greedyString())
                                        .executes(ctx -> approve(ctx, requestService, cfg,
                                                StringArgumentType.getString(ctx, "note"))))))
                .then(Commands.literal("reject")
                        .then(Commands.argument("requestId", LongArgumentType.longArg(1))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> reject(ctx, requestService, cfg,
                                                StringArgumentType.getString(ctx, "reason"))))));
    }

    private static SuggestionProvider<CommandSourceStack> statusSuggestions(CommandDescriptionRegistry descriptions) {
        return (ctx, builder) -> {
            suggestStatus(builder, descriptions, "pending", "status_pending", "Richieste in attesa di review");
            suggestStatus(builder, descriptions, "approved", "status_approved", "Richieste approvate");
            suggestStatus(builder, descriptions, "rejected", "status_rejected", "Richieste rifiutate");
            suggestStatus(builder, descriptions, "cancelled", "status_cancelled", "Richieste annullate");
            return builder.buildFuture();
        };
    }

    private static void suggestStatus(
            SuggestionsBuilder builder,
            CommandDescriptionRegistry descriptions,
            String value,
            String tooltipKey,
            String fallback
    ) {
        if (!value.startsWith(builder.getRemainingLowerCase())) {
            return;
        }
        CommandSuggestionUtils.suggest(builder, value, descriptions.suggestionTooltip(tooltipKey, fallback));
    }

    private static int list(
            CommandContext<CommandSourceStack> ctx,
            CityLevelRequestService requestService,
            PluginConfigUtils cfg
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(STAFF_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + STAFF_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }

        Optional<CityLevelRepository.RequestStatus> status = Optional.empty();
        try {
            String raw = StringArgumentType.getString(ctx, "status");
            if (raw != null && !raw.isBlank()) {
                status = Optional.of(CityLevelRepository.RequestStatus.valueOf(raw.toUpperCase(Locale.ROOT)));
            }
        } catch (IllegalArgumentException ignored) {
            sender.sendMessage(cfg.msg("cittaexp.staff.level.invalid_status", "<red>Status non valido.</red>"));
            return Command.SINGLE_SUCCESS;
        } catch (Exception ignored) {
            // no argument provided.
        }

        List<TownLevelRequest> requests = requestService.listRequests(status);
        sender.sendMessage(cfg.msg(
                "cittaexp.staff.level.list.header",
                "<gold>Level Requests:</gold> <white>{count}</white>",
                Placeholder.unparsed("count", Integer.toString(requests.size()))
        ));
        int limit = Math.min(20, requests.size());
        for (int i = 0; i < limit; i++) {
            TownLevelRequest request = requests.get(i);
            sender.sendMessage(cfg.msg(
                    "cittaexp.staff.level.list.row",
                    "<gray>#{id}</gray> <yellow>town={town_id}</yellow> <white>{stage}</white> <gray>{status}</gray>",
                    Placeholder.unparsed("id", Long.toString(request.id())),
                    Placeholder.unparsed("town_id", Integer.toString(request.townId())),
                    Placeholder.unparsed("stage", request.targetStage().displayName()),
                    Placeholder.unparsed("status", request.status().name())
            ));
        }
        if (requests.size() > limit) {
            sender.sendMessage(cfg.msg(
                    "cittaexp.staff.level.list.more",
                    "<gray>... e altre {count} richieste</gray>",
                    Placeholder.unparsed("count", Integer.toString(requests.size() - limit))
            ));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int approve(
            CommandContext<CommandSourceStack> ctx,
            CityLevelRequestService requestService,
            PluginConfigUtils cfg,
            String note
    ) {
        Player player = requireStaffPlayer(ctx.getSource().getSender());
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        long requestId = LongArgumentType.getLong(ctx, "requestId");
        CityLevelRequestService.RequestResult result = requestService.approve(player, requestId, note);
        if (!result.success()) {
            player.sendMessage(cfg.msg(
                    "cittaexp.staff.level.approve.failed",
                    "<red>Approve fallita:</red> <gray>{reason}</gray>",
                    Placeholder.unparsed("reason", mapReason(result.reason(), cfg))
            ));
            return Command.SINGLE_SUCCESS;
        }
        player.sendMessage(cfg.msg(
                "cittaexp.staff.level.approve.success",
                "<green>Richiesta approvata:</green> <yellow>#{id}</yellow>",
                Placeholder.unparsed("id", Long.toString(requestId))
        ));
        return Command.SINGLE_SUCCESS;
    }

    private static int reject(
            CommandContext<CommandSourceStack> ctx,
            CityLevelRequestService requestService,
            PluginConfigUtils cfg,
            String reason
    ) {
        Player player = requireStaffPlayer(ctx.getSource().getSender());
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        if (reason == null || reason.isBlank()) {
            player.sendMessage(cfg.msg(
                    "cittaexp.staff.level.reject.reason_required",
                    "<red>Specifica un motivo per il reject.</red>"
            ));
            return Command.SINGLE_SUCCESS;
        }
        long requestId = LongArgumentType.getLong(ctx, "requestId");
        CityLevelRequestService.RequestResult result = requestService.reject(player, requestId, reason);
        if (!result.success()) {
            player.sendMessage(cfg.msg(
                    "cittaexp.staff.level.reject.failed",
                    "<red>Reject fallita:</red> <gray>{reason}</gray>",
                    Placeholder.unparsed("reason", mapReason(result.reason(), cfg))
            ));
            return Command.SINGLE_SUCCESS;
        }
        player.sendMessage(cfg.msg(
                "cittaexp.staff.level.reject.success",
                "<yellow>Richiesta rifiutata:</yellow> <white>#{id}</white>",
                Placeholder.unparsed("id", Long.toString(requestId))
        ));
        return Command.SINGLE_SUCCESS;
    }

    private static Player requireStaffPlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("[CittaEXP] Questo comando puo essere eseguito solo da un player staff.");
            return null;
        }
        if (!sender.hasPermission(STAFF_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + STAFF_PERMISSION);
            return null;
        }
        return player;
    }

    private static String mapReason(String reason, PluginConfigUtils cfg) {
        return ReasonMessageMapper.text(cfg, "cittaexp.staff.level.reasons.", reason);
    }
}
