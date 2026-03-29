package it.patric.cittaexp.warp;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import it.patric.cittaexp.command.CommandDescriptionRegistry;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.utils.CommandSuggestionUtils;
import it.patric.cittaexp.utils.CommandGuards;
import it.patric.cittaexp.utils.ExceptionUtils;
import it.patric.cittaexp.utils.PluginConfigUtils;
import it.patric.cittaexp.utils.TownMemberGuards;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class WarpCommand {

    private WarpCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            CommandDescriptionRegistry descriptions
    ) {
        PluginConfigUtils cfg = new PluginConfigUtils(plugin);
        return Commands.literal("warp")
                .executes(ctx -> execute(ctx, huskTownsApiHook, cfg, null))
                .then(Commands.argument("town", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> suggestTownNames(builder, huskTownsApiHook, descriptions))
                        .executes(ctx -> execute(ctx, huskTownsApiHook, cfg, StringArgumentType.getString(ctx, "town"))));
    }

    private static int execute(
            CommandContext<CommandSourceStack> ctx,
            HuskTownsApiHook huskTownsApiHook,
            PluginConfigUtils cfg,
            String targetTown
    ) {
        Player player = CommandGuards.cityPlayer(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }

        String normalizedTarget = cfg.normalizeText(targetTown);
        if (normalizedTarget.isBlank()) {
            if (TownMemberGuards.requireMember(
                    player,
                    huskTownsApiHook,
                    cfg,
                    "city.warp.errors.no_town",
                    "<red>Devi essere membro attivo di una citta.</red>"
            ).isEmpty()) {
                return Command.SINGLE_SUCCESS;
            }
        } else if (huskTownsApiHook.getTownByName(normalizedTarget).isEmpty()) {
            player.sendMessage(cfg.msg(
                    "city.warp.errors.town_not_found",
                    "<red>Citta non trovata:</red> <yellow>{town}</yellow>",
                    Placeholder.unparsed("town", normalizedTarget)
            ));
            return Command.SINGLE_SUCCESS;
        }

        try {
            huskTownsApiHook.teleportToTownSpawn(player, normalizedTarget.isBlank() ? null : normalizedTarget);
        } catch (RuntimeException exception) {
            player.sendMessage(cfg.msg(
                    "city.warp.errors.failed",
                    "<red>Teleport warp fallito:</red> <gray>{reason}</gray>",
                    Placeholder.unparsed("reason", ExceptionUtils.safeMessage(exception, "errore sconosciuto"))
            ));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Suggestions> suggestTownNames(
            SuggestionsBuilder builder,
            HuskTownsApiHook huskTownsApiHook,
            CommandDescriptionRegistry descriptions
    ) {
        String remaining = builder.getRemainingLowerCase();
        String tooltip = descriptions.suggestionTooltip("town_arg", "Nome citta target");
        huskTownsApiHook.api().getTowns().stream()
                .map(town -> town.getName().toLowerCase(Locale.ROOT))
                .filter(name -> name.startsWith(remaining))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(name -> CommandSuggestionUtils.suggest(builder, name, tooltip));
        return builder.buildFuture();
    }

}
