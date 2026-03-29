package it.patric.cittaexp.activity;

import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.playersgui.HuskTownsRoleLadderService;
import it.patric.cittaexp.text.MiniMessageHelper;
import it.patric.cittaexp.utils.PluginConfigUtils;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.william278.husktowns.audit.Action;
import net.william278.husktowns.town.Town;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class InactiveTownMemberPruneService {

    private static final long DEFAULT_CHECK_INTERVAL_TICKS = 20L * 60L * 10L;

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final PlayerActivityService playerActivityService;
    private final HuskTownsRoleLadderService roleLadderService;
    private final PluginConfigUtils configUtils;
    private int taskId = -1;

    public InactiveTownMemberPruneService(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            PlayerActivityService playerActivityService,
            HuskTownsRoleLadderService roleLadderService
    ) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.playerActivityService = playerActivityService;
        this.roleLadderService = roleLadderService;
        this.configUtils = new PluginConfigUtils(plugin);
    }

    public void start() {
        if (taskId >= 0 || !configUtils.cfgBool("city.players.inactivity_cleanup.enabled", true)) {
            return;
        }
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                plugin,
                this::pruneInactiveMembers,
                DEFAULT_CHECK_INTERVAL_TICKS,
                DEFAULT_CHECK_INTERVAL_TICKS
        );
    }

    public void stop() {
        if (taskId >= 0) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    private void pruneInactiveMembers() {
        int maxOfflineDays = Math.max(1, configUtils.cfgInt("city.players.inactivity_cleanup.max_offline_days", 30));
        long cutoffMillis = System.currentTimeMillis() - Duration.ofDays(maxOfflineDays).toMillis();
        OptionalInt thresholdWeight = inactiveRemovalThreshold();
        if (thresholdWeight.isEmpty()) {
            return;
        }

        for (Town town : huskTownsApiHook.getPlayableTowns()) {
            try {
                pruneTown(town, thresholdWeight.getAsInt(), cutoffMillis, maxOfflineDays);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING,
                        "[inactive-prune] errore durante la pulizia della citta " + town.getName(),
                        exception);
            }
        }
    }

    private void pruneTown(Town town, int thresholdWeight, long cutoffMillis, int maxOfflineDays) {
        List<RemovalCandidate> removals = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : town.getMembers().entrySet()) {
            UUID memberId = entry.getKey();
            int roleWeight = entry.getValue() == null ? 0 : entry.getValue();
            if (town.getMayor().equals(memberId) || roleWeight >= thresholdWeight) {
                continue;
            }
            Player online = Bukkit.getPlayer(memberId);
            if (online != null && online.isOnline()) {
                continue;
            }
            OptionalLong lastSeen = playerActivityService.lastSeenMillis(memberId);
            if (lastSeen.isEmpty() || lastSeen.getAsLong() > cutoffMillis) {
                continue;
            }
            removals.add(new RemovalCandidate(memberId, resolvePlayerName(memberId), lastSeen.getAsLong()));
        }
        if (removals.isEmpty()) {
            return;
        }

        for (RemovalCandidate removal : removals) {
            town.removeMember(removal.playerId());
            long offlineDays = Math.max(1L, Duration.ofMillis(System.currentTimeMillis() - removal.lastSeenAt()).toDays());
            town.getLog().log(Action.of(Action.Type.EVICT,
                    removal.playerName() + " (auto-inactive " + offlineDays + "d)"));
        }
        huskTownsApiHook.updateTownDirect(town);

        Component message = MiniMessageHelper.parse(
                "<red><bold>Pulizia inattivita</bold></red><newline><gray>Rimossi dalla citta dopo oltre </gray><white>"
                        + maxOfflineDays
                        + " giorni</white><gray> offline:</gray><newline><white>"
                        + removals.stream().map(RemovalCandidate::playerName).collect(Collectors.joining(", "))
                        + "</white>"
        );
        huskTownsApiHook.plugin().getManager().sendTownMessage(town, message);
    }

    private OptionalInt inactiveRemovalThreshold() {
        HuskTownsRoleLadderService.RoleLadderSnapshot snapshot = roleLadderService.snapshot();
        OptionalInt resolved = snapshot.weightForRoleNameContains("funzionario", "ufficiale");
        if (resolved.isEmpty()) {
            plugin.getLogger().warning("[inactive-prune] impossibile trovare il ruolo Funzionario/Ufficiale nella ladder. Pulizia inattivita disattivata.");
        }
        return resolved;
    }

    private String resolvePlayerName(UUID playerId) {
        Player online = Bukkit.getPlayer(playerId);
        if (online != null && online.isOnline()) {
            return online.getName();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
        String name = offline.getName();
        return name == null || name.isBlank() ? playerId.toString().substring(0, 8).toLowerCase(Locale.ROOT) : name;
    }

    private record RemovalCandidate(UUID playerId, String playerName, long lastSeenAt) {
    }
}
