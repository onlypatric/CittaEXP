package it.patric.cittaexp.utils;

import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import java.util.Optional;
import net.william278.husktowns.town.Member;
import net.william278.husktowns.town.Privilege;
import org.bukkit.entity.Player;

public final class TownMemberGuards {

    private TownMemberGuards() {
    }

    public static Optional<Member> requireMember(
            Player player,
            HuskTownsApiHook huskTownsApiHook,
            PluginConfigUtils cfg,
            String noTownPath,
            String noTownFallback
    ) {
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isPresent()) {
            return member;
        }
        player.sendMessage(cfg.msg(noTownPath, noTownFallback));
        return Optional.empty();
    }

    public static Optional<Member> requirePrivilege(
            Player player,
            HuskTownsApiHook huskTownsApiHook,
            PluginConfigUtils cfg,
            Privilege privilege,
            String noTownPath,
            String noTownFallback,
            String noPermissionPath,
            String noPermissionFallback
    ) {
        Optional<Member> member = requireMember(player, huskTownsApiHook, cfg, noTownPath, noTownFallback);
        if (member.isEmpty()) {
            return Optional.empty();
        }
        if (huskTownsApiHook.hasPrivilege(player, privilege)) {
            return member;
        }
        player.sendMessage(cfg.msg(noPermissionPath, noPermissionFallback));
        return Optional.empty();
    }
}
