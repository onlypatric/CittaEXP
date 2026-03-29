package it.patric.cittaexp.challenges;

import it.patric.cittaexp.utils.PluginConfigUtils;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class ChallengeProgressBossBarService {

    private final Plugin plugin;
    private final PluginConfigUtils configUtils;
    private final ChallengeTextCatalog textCatalog;
    private final ConcurrentMap<UUID, ActiveBar> activeBars = new ConcurrentHashMap<>();

    public ChallengeProgressBossBarService(Plugin plugin, ChallengeTextCatalog textCatalog) {
        this.plugin = plugin;
        this.configUtils = new PluginConfigUtils(plugin);
        this.textCatalog = textCatalog;
    }

    public void clear() {
        if (Bukkit.isPrimaryThread()) {
            clearInternal();
            return;
        }
        if (!plugin.isEnabled()) {
            activeBars.clear();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, this::clearInternal);
    }

    public void showProgress(
            UUID playerId,
            String challengeName,
            ChallengeObjectiveType objectiveType,
            int progress,
            int target,
            int excellenceTarget
    ) {
        if (playerId == null || target <= 0) {
            return;
        }
        if (!plugin.isEnabled()) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            render(playerId, challengeName, objectiveType, progress, target, excellenceTarget);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!plugin.isEnabled()) {
                return;
            }
            render(playerId, challengeName, objectiveType, progress, target, excellenceTarget);
        });
    }

    private void render(
            UUID playerId,
            String challengeName,
            ChallengeObjectiveType objectiveType,
            int progress,
            int target,
            int excellenceTarget
    ) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        ChallengeLoreFormatter.ProgressPhase phase = ChallengeLoreFormatter.phase(progress, target, excellenceTarget, 20);
        float percent = Math.max(0.0F, Math.min(1.0F, (float) phase.percent() / 100.0F));
        String objectiveName = ChallengeObjectivePresentation.objectiveLabel(textCatalog, objectiveType);
        int percentInt = Math.round(percent * 100.0F);
        String phaseLabel = phase.excellencePhase() ? "Eccellenza" : "Base";
        Component title = configUtils.msg(
                "city.challenges.progress_bossbar.title",
                "<gold>Sfida:</gold> <white>{challenge}</white> <gray>|</gray> <yellow>{objective}</yellow> <gray>| {phase} {progress}/{target} ({percent}%)</gray>",
                Placeholder.unparsed("challenge", challengeName == null ? "-" : challengeName),
                Placeholder.unparsed("objective", objectiveName),
                Placeholder.unparsed("phase", phaseLabel),
                Placeholder.unparsed("progress", Integer.toString(Math.max(0, phase.current()))),
                Placeholder.unparsed("target", Integer.toString(Math.max(1, phase.total()))),
                Placeholder.unparsed("percent", Integer.toString(percentInt))
        );
        BossBar.Color color;
        if (progress >= Math.max(target, excellenceTarget)) {
            color = BossBar.Color.GREEN;
        } else if (phase.excellencePhase()) {
            color = percent < 0.90F ? BossBar.Color.YELLOW : BossBar.Color.RED;
        } else if (percent < 0.50F) {
            color = BossBar.Color.BLUE;
        } else if (percent < 0.90F) {
            color = BossBar.Color.YELLOW;
        } else {
            color = BossBar.Color.GREEN;
        }
        BossBar.Overlay overlay = BossBar.Overlay.PROGRESS;

        ActiveBar existing = activeBars.get(playerId);
        BossBar bar;
        if (existing == null) {
            bar = BossBar.bossBar(title, percent, color, overlay);
            player.showBossBar(bar);
        } else {
            if (existing.removeTask != null) {
                existing.removeTask.cancel();
            }
            bar = existing.bar;
            bar.name(title);
            bar.progress(percent);
            bar.color(color);
        }

        BukkitTask removeTask = null;
        if (plugin.isEnabled()) {
            removeTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player online = Bukkit.getPlayer(playerId);
                if (online != null) {
                    online.hideBossBar(bar);
                }
                activeBars.remove(playerId);
            }, 20L * 30L);
        }
        activeBars.put(playerId, new ActiveBar(playerId, bar, removeTask));
    }

    private void clearInternal() {
        for (ActiveBar value : activeBars.values()) {
            if (value.removeTask != null) {
                value.removeTask.cancel();
            }
            Player player = Bukkit.getPlayer(value.playerId);
            if (player != null) {
                player.hideBossBar(value.bar);
            }
        }
        activeBars.clear();
    }

    private static final class ActiveBar {
        private final UUID playerId;
        private final BossBar bar;
        private final BukkitTask removeTask;

        private ActiveBar(UUID playerId, BossBar bar, BukkitTask removeTask) {
            this.playerId = playerId;
            this.bar = bar;
            this.removeTask = removeTask;
        }
    }
}
