package it.patric.cittaexp.warp;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.utils.CommandGuards;
import it.patric.cittaexp.utils.ExceptionUtils;
import it.patric.cittaexp.utils.FeedbackUtils;
import it.patric.cittaexp.utils.PluginConfigUtils;
import it.patric.cittaexp.utils.TownMemberGuards;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Optional;
import net.william278.husktowns.claim.TownClaim;
import net.william278.husktowns.town.Member;
import net.william278.husktowns.town.Privilege;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class SetWarpCommand {

    private SetWarpCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            CityLevelService cityLevelService
    ) {
        PluginConfigUtils cfg = new PluginConfigUtils(plugin);
        return Commands.literal("setwarp")
                .executes(ctx -> setWarp(ctx, huskTownsApiHook, cityLevelService, cfg));
    }

    private static int setWarp(
            CommandContext<CommandSourceStack> ctx,
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
                Privilege.SET_SPAWN,
                "city.setwarp.errors.no_town",
                "<red>Devi essere membro attivo di una citta.</red>",
                "city.setwarp.errors.no_permission",
                "<red>Non hai i permessi per impostare lo spawn della citta.</red>"
        );
        if (member.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }

        Optional<TownClaim> claimAt = huskTownsApiHook.getClaimAt(player.getLocation());
        if (claimAt.isEmpty() || claimAt.get().town().getId() != member.get().town().getId()) {
            FeedbackUtils.send(
                    player,
                    cfg,
                    "city.setwarp.errors.outside_claim",
                    "<red>Devi essere dentro un claim della tua citta per impostare lo spawn.</red>"
            );
            return Command.SINGLE_SUCCESS;
        }

        try {
            huskTownsApiHook.setTownSpawn(player, member.get().town().getId(), player.getLocation());
            cityLevelService.enforceSpawnPrivacyPolicy(member.get().town().getId());
            player.sendMessage(cfg.msg("city.setwarp.success", "<green>Warp citta impostato con successo.</green>"));
        } catch (RuntimeException exception) {
            player.sendMessage(cfg.msg(
                    "city.setwarp.errors.failed",
                    "<red>Impostazione warp fallita:</red> <gray>{reason}</gray>",
                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed(
                            "reason",
                            ExceptionUtils.safeMessage(exception, "errore sconosciuto")
                    )
            ));
        }
        return Command.SINGLE_SUCCESS;
    }

}
