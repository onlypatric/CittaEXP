package it.patric.cittaexp.discord;

import org.bukkit.plugin.Plugin;

public final class DiscordLogHelper {

    private final Plugin plugin;
    private final boolean verbose;

    public DiscordLogHelper(Plugin plugin, boolean verbose) {
        this.plugin = plugin;
        this.verbose = verbose;
    }

    public void debug(String message) {
        if (!verbose) {
            return;
        }
        plugin.getLogger().info("[discord] " + message);
    }

    public void info(String message) {
        plugin.getLogger().info("[discord] " + message);
    }

    public void warn(String message) {
        plugin.getLogger().warning("[discord] " + message);
    }

    public void warn(String message, Throwable throwable) {
        plugin.getLogger().warning("[discord] " + message + " reason=" + (throwable == null ? "unknown" : throwable.getMessage()));
    }

    public void error(String message, Throwable throwable) {
        plugin.getLogger().severe("[discord] " + message + " reason=" + (throwable == null ? "unknown" : throwable.getMessage()));
    }
}
