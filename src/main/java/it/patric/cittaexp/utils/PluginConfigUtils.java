package it.patric.cittaexp.utils;

import it.patric.cittaexp.text.MiniMessageHelper;
import it.patric.cittaexp.text.UiChatStyle;
import it.patric.cittaexp.text.UiLoreStyle;
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

    public List<Component> lore(String path, List<String> fallbackLines, TagResolver... resolvers) {
        String line1 = plugin.getConfig().getString(path + ".line_1");
        String line2 = plugin.getConfig().getString(path + ".line_2");
        String extra = plugin.getConfig().getString(path + ".extra");
        List<String> extraLines = plugin.getConfig().getStringList(path + ".extra_lines");
        if (line1 != null || line2 != null || extra != null || (extraLines != null && !extraLines.isEmpty())) {
            List<String> resolvedExtraLines = new java.util.ArrayList<>();
            if (extra != null && !extra.isBlank()) {
                resolvedExtraLines.add(extra);
            }
            if (extraLines != null && !extraLines.isEmpty()) {
                resolvedExtraLines.addAll(extraLines);
            }
            return UiLoreStyle.renderComponents(
                    line1 == null ? "" : line1,
                    line2 == null ? "" : line2,
                    resolvedExtraLines,
                    resolvers
            );
        }
        List<String> lines = plugin.getConfig().getStringList(path);
        if (lines == null || lines.isEmpty()) {
            lines = fallbackLines;
        }
        return UiLoreStyle.renderLegacyComponents(lines, resolvers);
    }

    public Component chatFrame(String path, String fallbackTitle, String fallbackLine1, String fallbackLine2, String fallbackExtra, TagResolver... resolvers) {
        String title = plugin.getConfig().getString(path + ".title");
        String line1 = plugin.getConfig().getString(path + ".line_1");
        String line2 = plugin.getConfig().getString(path + ".line_2");
        String extra = plugin.getConfig().getString(path + ".extra");
        List<String> details = plugin.getConfig().getStringList(path + ".details");
        if (title != null || line1 != null || line2 != null || extra != null || (details != null && !details.isEmpty())) {
            List<UiChatStyle.DetailLine> renderedDetails = details == null || details.isEmpty()
                    ? List.of()
                    : details.stream()
                            .filter(line -> line != null && !line.isBlank())
                            .map(line -> UiChatStyle.detail(null, line))
                            .toList();
            if (!renderedDetails.isEmpty()) {
                return UiChatStyle.renderComponent(
                        title == null ? fallbackTitle : title,
                        line1 == null ? fallbackLine1 : line1,
                        line2 == null ? fallbackLine2 : line2,
                        renderedDetails,
                        resolvers
                );
            }
            return UiChatStyle.renderComponent(
                    title == null ? fallbackTitle : title,
                    line1 == null ? fallbackLine1 : line1,
                    line2 == null ? fallbackLine2 : line2,
                    extra == null ? fallbackExtra : extra,
                    resolvers
            );
        }
        String legacyScalar = plugin.getConfig().getString(path);
        if (legacyScalar != null && !legacyScalar.isBlank()) {
            return UiChatStyle.renderComponent(fallbackTitle, legacyScalar, fallbackLine2, fallbackExtra, resolvers);
        }
        return UiChatStyle.renderComponent(fallbackTitle, fallbackLine1, fallbackLine2, fallbackExtra, resolvers);
    }

    public Component chatFrame(
            String path,
            String fallbackTitle,
            String fallbackLine1,
            String fallbackLine2,
            List<UiChatStyle.DetailLine> fallbackDetails,
            TagResolver... resolvers
    ) {
        String title = plugin.getConfig().getString(path + ".title");
        String line1 = plugin.getConfig().getString(path + ".line_1");
        String line2 = plugin.getConfig().getString(path + ".line_2");
        List<String> details = plugin.getConfig().getStringList(path + ".details");
        if (title != null || line1 != null || line2 != null || (details != null && !details.isEmpty())) {
            List<UiChatStyle.DetailLine> renderedDetails = details == null || details.isEmpty()
                    ? fallbackDetails
                    : details.stream()
                            .filter(line -> line != null && !line.isBlank())
                            .map(line -> UiChatStyle.detail(null, line))
                            .toList();
            return UiChatStyle.renderComponent(
                    title == null ? fallbackTitle : title,
                    line1 == null ? fallbackLine1 : line1,
                    line2 == null ? fallbackLine2 : line2,
                    renderedDetails,
                    resolvers
            );
        }
        return UiChatStyle.renderComponent(
                fallbackTitle,
                fallbackLine1,
                fallbackLine2,
                fallbackDetails == null ? List.of() : fallbackDetails,
                resolvers
        );
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
