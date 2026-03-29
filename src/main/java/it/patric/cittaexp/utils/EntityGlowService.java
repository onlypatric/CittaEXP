package it.patric.cittaexp.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class EntityGlowService {

    private static final String RED_GLOW_TEAM = "cittaexp_glow_red";
    private static final String MANAGED_GLOW_TAG = "cittaexp-glow-red";

    private final Plugin plugin;

    public EntityGlowService(Plugin plugin) {
        this.plugin = plugin;
    }

    public void applyRedGlow(Entity entity) {
        if (entity == null) {
            return;
        }
        Team team = ensureRedGlowTeam();
        if (team == null) {
            return;
        }
        String entry = entry(entity);
        team.addEntry(entry);
        entity.addScoreboardTag(MANAGED_GLOW_TAG);
        entity.setGlowing(true);
    }

    public void clearGlow(Entity entity) {
        if (entity == null) {
            return;
        }
        Team team = ensureRedGlowTeam();
        if (team != null) {
            team.removeEntry(entry(entity));
        }
        entity.removeScoreboardTag(MANAGED_GLOW_TAG);
        entity.setGlowing(false);
    }

    public boolean isManagedGlow(Entity entity) {
        return entity != null && entity.getScoreboardTags().contains(MANAGED_GLOW_TAG);
    }

    private Team ensureRedGlowTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager() == null
                ? null
                : Bukkit.getScoreboardManager().getMainScoreboard();
        if (scoreboard == null) {
            plugin.getLogger().warning("[glow] scoreboard manager unavailable");
            return null;
        }
        Team team = scoreboard.getTeam(RED_GLOW_TEAM);
        if (team == null) {
            team = scoreboard.registerNewTeam(RED_GLOW_TEAM);
        }
        team.setColor(ChatColor.RED);
        return team;
    }

    private static String entry(Entity entity) {
        return entity.getUniqueId().toString();
    }
}
