package it.patric.cittaexp.playersgui;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

public final class StaffCityPlayersCommand {

    private static final String STAFF_PERMISSION = "cittaexp.city.players.staff";

    private StaffCityPlayersCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create(
            CityPlayersGuiService cityPlayersGuiService,
            HuskTownsApiHook huskTownsApiHook
    ) {
        return Commands.literal("city")
                .then(Commands.literal("players")
                        .then(Commands.argument("town", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> suggestTownNames(ctx, builder, huskTownsApiHook))
                                .executes(ctx -> openTarget(ctx, cityPlayersGuiService))));
    }

    private static int openTarget(
            CommandContext<CommandSourceStack> ctx,
            CityPlayersGuiService cityPlayersGuiService
    ) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendMessage("[CittaEXP] Questo comando puo essere eseguito solo da un player staff.");
            return Command.SINGLE_SUCCESS;
        }
        if (!player.hasPermission(STAFF_PERMISSION)) {
            player.sendMessage("[CittaEXP] Permesso mancante: " + STAFF_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }
        String townName = StringArgumentType.getString(ctx, "town").trim();
        if (townName.isBlank()) {
            player.sendMessage("[CittaEXP] Uso: /cittaexp staff city players <citta>");
            return Command.SINGLE_SUCCESS;
        }
        cityPlayersGuiService.openForTargetTown(player, townName);
        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Suggestions> suggestTownNames(
            CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder,
            HuskTownsApiHook huskTownsApiHook
    ) {
        if (!(ctx.getSource().getSender() instanceof Player player) || !player.hasPermission(STAFF_PERMISSION)) {
            return builder.buildFuture();
        }
        String remaining = builder.getRemainingLowerCase();
        huskTownsApiHook.api().getTowns().stream()
                .map(town -> town.getName().toLowerCase(Locale.ROOT))
                .filter(name -> name.startsWith(remaining))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(builder::suggest);
        return builder.buildFuture();
    }
}
