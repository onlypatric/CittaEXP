package it.patric.cittaexp.trust;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.text.MiniMessageHelper;
import it.patric.cittaexp.utils.CommandGuards;
import it.patric.cittaexp.utils.DialogConfirmHelper;
import it.patric.cittaexp.utils.FeedbackUtils;
import it.patric.cittaexp.utils.PluginConfigUtils;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.william278.husktowns.claim.ChunkTrustPreset;
import net.william278.husktowns.claim.TownClaim;
import net.william278.husktowns.town.Member;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class CityChunkTrustCommandService {

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final PluginConfigUtils cfg;

    public CityChunkTrustCommandService(Plugin plugin, HuskTownsApiHook huskTownsApiHook) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.cfg = new PluginConfigUtils(plugin);
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("trust")
                .then(Commands.literal("list")
                        .executes(this::listTrust))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(this::suggestPlayers)
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .suggests(this::suggestPresets)
                                .executes(this::assignTrust)));
    }

    public LiteralArgumentBuilder<CommandSourceStack> createUntrustCommand() {
        return Commands.literal("untrust")
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(this::suggestPlayers)
                        .executes(this::removeTrust));
    }

    private int assignTrust(CommandContext<CommandSourceStack> ctx) {
        Player player = CommandGuards.cityPlayer(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<Member> mayor = requireMayorInOwnClaim(player);
        if (mayor.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }

        String targetName = StringArgumentType.getString(ctx, "player").trim();
        Optional<ChunkTrustPreset> preset = parsePreset(StringArgumentType.getString(ctx, "preset"));
        if (targetName.isBlank() || preset.isEmpty()) {
            FeedbackUtils.send(player, cfg,
                    "city.trust.errors.invalid_syntax",
                    "<red>Usa /city trust <player> <access|build|full>.</red>");
            return Command.SINGLE_SUCCESS;
        }

        Chunk chunk = player.getChunk();
        World world = player.getWorld();
        TownClaim claim = huskTownsApiHook.getClaimAt(chunk).orElseThrow();
        Component body = MiniMessageHelper.parse(
                "<gray>Stai per dare trust a</gray> <white><player></white><gray>.</gray>\n"
                        + "<gray>Preset:</gray> <gold><preset></gold>\n"
                        + "<gray>Citta:</gray> <white><town></white>\n"
                        + "<gray>Chunk attuale:</gray> <white><chunk_x>, <chunk_z></white>\n"
                        + "<white>Il trust verra assegnato sul chunk dove ti trovi ora.</white>",
                Placeholder.unparsed("player", targetName),
                Placeholder.unparsed("preset", presetLabel(preset.get())),
                Placeholder.unparsed("town", claim.town().getName()),
                Placeholder.unparsed("chunk_x", Integer.toString(chunk.getX())),
                Placeholder.unparsed("chunk_z", Integer.toString(chunk.getZ()))
        );
        DialogConfirmHelper.open(
                plugin,
                player,
                cfg.msg("city.trust.dialog.title", "<gold>Conferma trust chunk</gold>"),
                body,
                cfg.msg("city.trust.dialog.confirm", "<green>Conferma</green>"),
                cfg.msg("city.trust.dialog.cancel", "<red>Annulla</red>"),
                confirmed -> {
                    huskTownsApiHook.setChunkTrust(confirmed, chunk, world, targetName, preset.get());
                    FeedbackUtils.send(
                            confirmed,
                            cfg,
                            "city.trust.success.assigned",
                            "<green>Trust assegnato.</green> <gray><player> ora ha <preset> su questo chunk.</gray>",
                            Placeholder.unparsed("player", targetName),
                            Placeholder.unparsed("preset", presetLabel(preset.get()))
                    );
                },
                cfg.msg("city.trust.errors.dialog_unavailable", "<red>Dialog non disponibile su questo server.</red>")
        );
        return Command.SINGLE_SUCCESS;
    }

    private int removeTrust(CommandContext<CommandSourceStack> ctx) {
        Player player = CommandGuards.cityPlayer(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        if (requireMayorInOwnClaim(player).isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }

        String targetName = StringArgumentType.getString(ctx, "player").trim();
        if (targetName.isBlank()) {
            FeedbackUtils.send(player, cfg,
                    "city.trust.errors.invalid_syntax",
                    "<red>Usa /city untrust <player>.</red>");
            return Command.SINGLE_SUCCESS;
        }
        huskTownsApiHook.removeChunkTrust(player, player.getChunk(), player.getWorld(), targetName);
        FeedbackUtils.send(
                player,
                cfg,
                "city.trust.success.removed",
                "<green>Trust rimosso.</green> <gray><player> non ha piu trust su questo chunk.</gray>",
                Placeholder.unparsed("player", targetName)
        );
        return Command.SINGLE_SUCCESS;
    }

    private int listTrust(CommandContext<CommandSourceStack> ctx) {
        Player player = CommandGuards.cityPlayer(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        if (requireMayorInOwnClaim(player).isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }

        Map<UUID, ChunkTrustPreset> trusts = huskTownsApiHook.listChunkTrust(player.getChunk(), player.getWorld());
        player.sendMessage(cfg.chatFrame(
                "city.trust.list.header",
                "TRUST CHUNK",
                "Chunk attuale: " + player.getChunk().getX() + ", " + player.getChunk().getZ(),
                "Vedi chi puo usare questo chunk in modo speciale.",
                trusts.isEmpty() ? "Nessun trust attivo." : ""
        ));
        trusts.entrySet().stream()
                .sorted(Comparator.comparing(entry -> displayName(entry.getKey()), String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> player.sendMessage(MiniMessageHelper.parse(
                        "<gold><bold>➥</bold></gold> <white><player></white> <gray>-></gray> <gold><preset></gold>",
                        Placeholder.unparsed("player", displayName(entry.getKey())),
                        Placeholder.unparsed("preset", presetLabel(entry.getValue()))
                )));
        return Command.SINGLE_SUCCESS;
    }

    private Optional<Member> requireMayorInOwnClaim(Player player) {
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty()) {
            FeedbackUtils.send(player, cfg,
                    "city.trust.errors.no_town",
                    "<red>Devi appartenere a una citta per usare questo comando.</red>");
            return Optional.empty();
        }
        if (!member.get().town().getMayor().equals(player.getUniqueId())) {
            FeedbackUtils.send(player, cfg,
                    "city.trust.errors.not_mayor",
                    "<red>Solo il Capo puo gestire i trust dei chunk.</red>");
            return Optional.empty();
        }
        Optional<TownClaim> claim = huskTownsApiHook.getClaimAt(player.getChunk());
        if (claim.isEmpty() || claim.get().isAdminClaim(huskTownsApiHook.plugin())
                || claim.get().town().getId() != member.get().town().getId()) {
            FeedbackUtils.send(player, cfg,
                    "city.trust.errors.not_own_claim",
                    "<red>Devi stare dentro un chunk della tua citta.</red>");
            return Optional.empty();
        }
        return member;
    }

    private CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        Player player = CommandGuards.cityPlayer(ctx);
        if (player == null) {
            return builder.buildFuture();
        }
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        Bukkit.getOnlinePlayers().stream()
                .filter(online -> !online.getUniqueId().equals(player.getUniqueId()))
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(remaining))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestPresets(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (ChunkTrustPreset preset : ChunkTrustPreset.values()) {
            String id = preset.id();
            if (id.startsWith(remaining)) {
                builder.suggest(id);
            }
        }
        return builder.buildFuture();
    }

    private Optional<ChunkTrustPreset> parsePreset(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ChunkTrustPreset.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private String presetLabel(ChunkTrustPreset preset) {
        return switch (preset) {
            case ACCESS -> "Access";
            case BUILD -> "Build";
            case FULL -> "Full";
        };
    }

    private String displayName(UUID playerId) {
        String name = Bukkit.getOfflinePlayer(playerId).getName();
        return name == null || name.isBlank() ? playerId.toString() : name;
    }
}
