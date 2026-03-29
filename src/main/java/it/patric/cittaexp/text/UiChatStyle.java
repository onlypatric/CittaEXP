package it.patric.cittaexp.text;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class UiChatStyle {

    private static final String HEADER_PREFIX = "<dark_gray><bold>——————< </bold></dark_gray><gold><bold>";
    private static final String HEADER_SUFFIX = "</bold></gold><dark_gray><bold> >———————</bold></dark_gray>";
    private static final String BORDER = "<dark_gray><bold>—————————————</bold></dark_gray>";
    private static final String BULLET_PREFIX = "<gold><bold>➥</bold></gold> <white>";

    private UiChatStyle() {
    }

    public static String render(String title, String line1, String line2, String extra) {
        return render(title, line1, line2, detailLines(extra));
    }

    public static String render(String title, String line1, String line2, List<DetailLine> details) {
        String safeTitle = UiLoreStyle.sanitizeContent(title);
        StringBuilder builder = new StringBuilder();
        builder.append(HEADER_PREFIX).append(safeTitle.isBlank() ? "INFO" : safeTitle).append(HEADER_SUFFIX);
        if (hasText(line1)) {
            builder.append('\n').append(BULLET_PREFIX).append(UiLoreStyle.sanitizeContent(line1)).append("</white>");
        }
        if (hasText(line2)) {
            builder.append('\n').append(BULLET_PREFIX).append(UiLoreStyle.sanitizeContent(line2)).append("</white>");
        }
        for (DetailLine detail : normalizedDetails(details)) {
            builder.append('\n').append(renderDetail(detail));
        }
        builder.append('\n').append(BORDER);
        return builder.toString();
    }

    public static Component renderComponent(String title, String line1, String line2, String extra, TagResolver... resolvers) {
        return MiniMessageHelper.parse(render(title, line1, line2, extra), resolvers);
    }

    public static Component renderComponent(String title, String line1, String line2, List<DetailLine> details, TagResolver... resolvers) {
        return MiniMessageHelper.parse(render(title, line1, line2, details), resolvers);
    }

    public enum DetailType {
        CONTEXT,
        REWARD,
        STATUS,
        GOAL,
        SPECIAL,
        NOTE,
        WARNING
    }

    public record DetailLine(DetailType type, String text) {
        public DetailLine {
            type = type == null ? DetailType.NOTE : type;
            text = text == null ? "" : text;
        }
    }

    public static DetailLine detail(DetailType type, String text) {
        return new DetailLine(type, text);
    }

    private static boolean hasText(String raw) {
        return raw != null && !UiLoreStyle.sanitizeContent(raw).isBlank();
    }

    private static List<DetailLine> detailLines(String raw) {
        if (!hasText(raw)) {
            return List.of();
        }
        String normalized = raw
                .replace("<newline/>", "\n")
                .replace("<newline />", "\n")
                .replace("<newline>", "\n")
                .replace("<br/>", "\n")
                .replace("<br />", "\n")
                .replace("<br>", "\n")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        List<DetailLine> lines = new ArrayList<>();
        for (String line : normalized.split("\n")) {
            String sanitized = UiLoreStyle.sanitizeContent(line);
            if (!sanitized.isBlank()) {
                lines.add(new DetailLine(inferType(sanitized), sanitized));
            }
        }
        return List.copyOf(lines);
    }

    private static List<DetailLine> normalizedDetails(List<DetailLine> details) {
        if (details == null || details.isEmpty()) {
            return List.of();
        }
        List<DetailLine> lines = new ArrayList<>();
        for (DetailLine detail : details) {
            if (detail == null || !hasText(detail.text())) {
                continue;
            }
            lines.add(new DetailLine(detail.type(), UiLoreStyle.sanitizeContent(detail.text())));
        }
        return List.copyOf(lines);
    }

    private static String renderDetail(DetailLine detail) {
        DetailType type = detail.type() == null ? inferType(detail.text()) : detail.type();
        String text = UiLoreStyle.sanitizeContent(detail.text());
        if (text.isBlank()) {
            return "";
        }
        String prefix = type == DetailType.CONTEXT || type == DetailType.NOTE
                ? ""
                : "<dark_gray>·</dark_gray> ";
        return prefix + colorOpen(type) + text + colorClose(type);
    }

    private static DetailType inferType(String text) {
        String lower = UiLoreStyle.sanitizeContent(text).toLowerCase();
        if (lower.startsWith("citta'")
                || lower.startsWith("citta:")
                || lower.startsWith("stagione:")
                || lower.startsWith("season:")
                || lower.startsWith("ciclo:")
                || lower.startsWith("obiettivo:")) {
            return lower.startsWith("obiettivo:") ? DetailType.GOAL : DetailType.CONTEXT;
        }
        if (lower.startsWith("+")
                || lower.matches("^\\d+x .+")
                || lower.contains(" monete")
                || lower.contains(" xp")
                || lower.contains("material")
                || lower.contains("vault")) {
            return DetailType.REWARD;
        }
        if (lower.contains("manca")
                || lower.contains("blocc")
                || lower.contains("rifiutat")
                || lower.contains("non ")) {
            return DetailType.WARNING;
        }
        if (lower.contains("stato")
                || lower.contains("classifica")
                || lower.contains("attesa")
                || lower.contains("aperta")
                || lower.contains("chiusa")
                || lower.contains("completat")) {
            return DetailType.STATUS;
        }
        return DetailType.NOTE;
    }

    private static String colorOpen(DetailType type) {
        return switch (type == null ? DetailType.NOTE : type) {
            case CONTEXT -> "<white>";
            case REWARD -> "<green>";
            case STATUS -> "<aqua>";
            case GOAL -> "<yellow>";
            case SPECIAL -> "<light_purple>";
            case WARNING -> "<red>";
            case NOTE -> "<gray>";
        };
    }

    private static String colorClose(DetailType type) {
        return switch (type == null ? DetailType.NOTE : type) {
            case CONTEXT -> "</white>";
            case REWARD -> "</green>";
            case STATUS -> "</aqua>";
            case GOAL -> "</yellow>";
            case SPECIAL -> "</light_purple>";
            case WARNING -> "</red>";
            case NOTE -> "</gray>";
        };
    }
}
