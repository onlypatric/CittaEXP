package it.patric.cittaexp.utils;

import it.patric.cittaexp.text.MiniMessageHelper;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.plugin.Plugin;

public final class PluginConfigUtils {

    private final Plugin plugin;

    public PluginConfigUtils(Plugin plugin) {
        this.plugin = plugin;
    }

    public String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    public Component msg(String path, String fallback, TagResolver... resolvers) {
        String configured = missingKeyFallback(path, fallback);
        try {
            return MiniMessageHelper.parse(configured, resolvers);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("[i18n] Messaggio non valido per key '" + path + "', uso fallback. reason=" + exception.getMessage());
            return MiniMessageHelper.parse(fallback, resolvers);
        }
    }

    public List<Component> msgList(String path, List<String> fallbackLines, TagResolver... resolvers) {
        List<String> lines = plugin.getConfig().getStringList(path);
        if (lines == null || lines.isEmpty()) {
            lines = fallbackLines;
        }
        return lines.stream().map(line -> {
            try {
                return MiniMessageHelper.parse(line, resolvers);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("[i18n] Riga messaggio non valida per key '" + path + "', uso riga vuota. reason=" + exception.getMessage());
                return Component.empty();
            }
        }).toList();
    }

    public int cfgInt(String path, int fallback) {
        return plugin.getConfig().getInt(path, fallback);
    }

    public long cfgLong(String path, long fallback) {
        return plugin.getConfig().getLong(path, fallback);
    }

    public double cfgDouble(String path, double fallback) {
        return plugin.getConfig().getDouble(path, fallback);
    }

    public boolean cfgBool(String path, boolean fallback) {
        return plugin.getConfig().getBoolean(path, fallback);
    }

    public String cfgString(String path, String fallback) {
        return plugin.getConfig().getString(path, fallback);
    }

    public String missingKeyFallback(String path, String fallback) {
        String configured = plugin.getConfig().getString(path);
        if (configured == null || configured.isBlank()) {
            return fallback;
        }
        return configured;
    }

    public Integer cfgPositiveIntOrDefault(String path, int fallback) {
        int value = plugin.getConfig().getInt(path, -1);
        return value > 0 ? value : fallback;
    }
}
