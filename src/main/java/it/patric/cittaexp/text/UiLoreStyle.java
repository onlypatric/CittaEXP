package it.patric.cittaexp.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class UiLoreStyle {

    private static final String BORDER = "<dark_gray><bold>—————————————</bold></dark_gray>";
    private static final String BULLET_PREFIX = "<gold><bold>➥</bold></gold> ";
    private static final String SPACER_TOKEN = "__CITTAEXP_SPACER__";
    private static final int WRAP_COLUMN = 100;
    private static final Pattern MINI_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern SECTION_LEGACY_CODE = Pattern.compile("(?i)§[0-9A-FK-ORX]");
    private static final Pattern BORDER_ONLY = Pattern.compile("^[\\s\\-—_═─╭╮╰╯┏┓┗┛┃|•·✦➜❖☩]+$");
    private static final Pattern LEADING_MARKERS = Pattern.compile("^(?:[\\s\\-—_]+|[•·|]+\\s*|[✦➜❖☩]+\\s*)+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private UiLoreStyle() {
    }

    public enum LineType {
        INFO,
        FOCUS,
        STATUS,
        REWARD,
        WARNING,
        ACTION,
        TIME,
        SPECIAL,
        NEUTRAL
    }

    public record StyledLine(LineType type, String text) {
    }

    public static StyledLine line(LineType type, String text) {
        return new StyledLine(type == null ? LineType.INFO : type, text == null ? "" : text);
    }

    public static StyledLine spacer() {
        return new StyledLine(LineType.NEUTRAL, SPACER_TOKEN);
    }

    public static Content fromLegacyLines(List<String> rawLines) {
        if (rawLines == null || rawLines.isEmpty()) {
            return new Content("", "", List.of());
        }
        List<String> cleaned = new ArrayList<>();
        for (String rawLine : rawLines) {
            String normalized = normalizeLegacyContent(rawLine);
            if (!normalized.isBlank()) {
                cleaned.add(normalized);
            }
        }
        String line1 = cleaned.size() > 0 ? cleaned.get(0) : "";
        String line2 = cleaned.size() > 1 ? cleaned.get(1) : "";
        List<String> extraLines = cleaned.size() > 2 ? List.copyOf(cleaned.subList(2, cleaned.size())) : List.of();
        return new Content(line1, line2, extraLines);
    }

    public static List<String> renderLines(String line1, String line2, String extra) {
        return renderLines(line1, line2, hasText(extra) ? List.of(extra) : List.of());
    }

    public static List<String> renderLines(String line1, String line2, List<String> extraLines) {
        return renderLines(
                inferStyledLine(line1, true),
                inferStyledLine(line2, true),
                inferStyledLines(extraLines, false)
        );
    }

    public static List<String> renderLines(StyledLine line1, StyledLine line2, List<StyledLine> extraLines) {
        List<String> lines = new ArrayList<>();
        lines.add(BORDER);
        appendWrappedLine(lines, line1, true);
        appendWrappedLine(lines, line2, true);
        if (extraLines != null) {
            for (StyledLine extraLine : extraLines) {
                appendWrappedLine(lines, extraLine, false);
            }
        }
        lines.add(BORDER);
        return List.copyOf(lines);
    }

    public static List<Component> renderComponents(String line1, String line2, String extra, TagResolver... resolvers) {
        return renderComponents(line1, line2, hasText(extra) ? List.of(extra) : List.of(), resolvers);
    }

    public static List<Component> renderComponents(String line1, String line2, List<String> extraLines, TagResolver... resolvers) {
        return renderComponents(
                inferStyledLine(line1, true),
                inferStyledLine(line2, true),
                inferStyledLines(extraLines, false),
                resolvers
        );
    }

    public static List<Component> renderComponents(StyledLine line1, StyledLine line2, List<StyledLine> extraLines, TagResolver... resolvers) {
        return renderLines(line1, line2, extraLines).stream()
                .map(line -> MiniMessageHelper.parse(line, resolvers))
                .toList();
    }

    public static List<Component> renderFramedComponents(List<StyledLine> lines, TagResolver... resolvers) {
        List<String> rendered = new ArrayList<>();
        rendered.add(BORDER);
        if (lines != null) {
            for (StyledLine line : lines) {
                appendWrappedLine(rendered, line, false, true);
            }
        }
        rendered.add(BORDER);
        return rendered.stream()
                .map(line -> MiniMessageHelper.parse(line, resolvers))
                .toList();
    }

    public static List<Component> renderComponents(String line1, String line2, String[] extraLines, TagResolver... resolvers) {
        List<String> extras = extraLines == null ? List.of() : List.of(extraLines);
        return renderComponents(line1, line2, extras, resolvers);
    }

    public static List<Component> renderLooseComponents(String... lines) {
        return renderLooseComponents(lines == null ? List.of() : List.of(lines));
    }

    public static List<Component> renderLooseComponents(List<String> lines, TagResolver... resolvers) {
        List<Component> rendered = new ArrayList<>();
        if (lines == null) {
            return List.of();
        }
        for (StyledLine line : inferStyledLines(lines, false)) {
            for (String renderedLine : renderStandaloneLines(line)) {
                rendered.add(MiniMessageHelper.parse(renderedLine, resolvers));
            }
        }
        return List.copyOf(rendered);
    }

    public static List<Component> renderLooseStyledComponents(List<StyledLine> lines, TagResolver... resolvers) {
        List<Component> rendered = new ArrayList<>();
        if (lines == null) {
            return List.of();
        }
        for (StyledLine line : lines) {
            for (String renderedLine : renderStandaloneLines(line)) {
                rendered.add(MiniMessageHelper.parse(renderedLine, resolvers));
            }
        }
        return List.copyOf(rendered);
    }

    public static List<Component> renderLegacyComponents(List<String> rawLines, TagResolver... resolvers) {
        Content content = fromLegacyLines(rawLines);
        return renderComponents(content.line1(), content.line2(), content.extraLines(), resolvers);
    }

    public static String normalizeLegacyContent(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            return "";
        }
        String plain = MINI_TAG.matcher(rawLine).replaceAll(" ");
        plain = SECTION_LEGACY_CODE.matcher(plain).replaceAll("");
        plain = plain.replace('\n', ' ').replace('\r', ' ');
        plain = WHITESPACE.matcher(plain).replaceAll(" ").trim();
        if (plain.isBlank() || BORDER_ONLY.matcher(plain).matches()) {
            return "";
        }
        plain = LEADING_MARKERS.matcher(plain).replaceFirst("").trim();
        if (plain.startsWith("LMB:")) {
            plain = plain.substring(4).trim();
        }
        if (plain.startsWith("RMB:")) {
            plain = plain.substring(4).trim();
        }
        return plain;
    }

    public static String sanitizeContent(String raw) {
        return normalizeLegacyContent(raw)
                .replace("<", "[")
                .replace(">", "]");
    }

    public static List<String> wrapSanitizedContent(String raw) {
        return wrapContent(raw);
    }

    private static void appendWrappedLine(List<String> out, StyledLine styledLine, boolean bullet) {
        appendWrappedLine(out, styledLine, bullet, false);
    }

    private static void appendWrappedLine(List<String> out, StyledLine styledLine, boolean bullet, boolean preserveMarkers) {
        if (styledLine == null) {
            return;
        }
        if (SPACER_TOKEN.equals(styledLine.text())) {
            out.add("");
            return;
        }
        List<String> wrapped = wrapContent(styledLine.text(), preserveMarkers);
        if (wrapped.isEmpty()) {
            return;
        }
        String colorOpen = colorOpen(styledLine.type());
        String colorClose = colorClose(styledLine.type());
        if (bullet) {
            out.add(BULLET_PREFIX + colorOpen + wrapped.getFirst() + colorClose);
        } else {
            out.add(colorOpen + wrapped.getFirst() + colorClose);
        }
        for (int i = 1; i < wrapped.size(); i++) {
            out.add(colorOpen + wrapped.get(i) + colorClose);
        }
    }

    private static List<String> renderStandaloneLines(StyledLine styledLine) {
        if (styledLine == null) {
            return List.of();
        }
        if (SPACER_TOKEN.equals(styledLine.text())) {
            return List.of("");
        }
        List<String> wrapped = wrapContent(styledLine.text());
        if (wrapped.isEmpty()) {
            return List.of();
        }
        String colorOpen = colorOpen(styledLine.type());
        String colorClose = colorClose(styledLine.type());
        List<String> rendered = new ArrayList<>();
        for (String line : wrapped) {
            rendered.add(colorOpen + line + colorClose);
        }
        return List.copyOf(rendered);
    }

    private static List<StyledLine> inferStyledLines(List<String> rawLines, boolean primary) {
        if (rawLines == null || rawLines.isEmpty()) {
            return List.of();
        }
        List<StyledLine> lines = new ArrayList<>();
        for (String rawLine : rawLines) {
            StyledLine styled = inferStyledLine(rawLine, primary);
            if (styled != null && hasText(styled.text())) {
                lines.add(styled);
            }
        }
        return List.copyOf(lines);
    }

    private static StyledLine inferStyledLine(String raw, boolean primary) {
        String sanitized = sanitizeContent(raw);
        if (sanitized.isBlank()) {
            return new StyledLine(primary ? LineType.FOCUS : LineType.INFO, "");
        }
        return new StyledLine(inferLineType(sanitized, primary), sanitized);
    }

    private static LineType inferLineType(String sanitized, boolean primary) {
        String lower = sanitized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("xp richiesta:")) {
            return LineType.STATUS;
        }
        if (lower.startsWith("ricompensa:")
                || lower.startsWith("bottino")
                || lower.startsWith("premio")
                || lower.startsWith("$")
                || lower.startsWith("bonus ")
                || lower.startsWith("+")
                || lower.contains(" xp citta")
                || lower.matches("^\\d+x .+")) {
            return LineType.REWARD;
        }
        if (lower.startsWith("tempo:")
                || lower.startsWith("durata:")
                || lower.startsWith("ripresa:")
                || lower.contains("scade")
                || lower.contains("cooldown")
                || lower.contains("attesa")
                || lower.contains("reset")) {
            return LineType.TIME;
        }
        if (lower.startsWith("progresso:")
                || lower.startsWith("stato:")
                || lower.startsWith("online")
                || lower.startsWith("offline")
                || lower.startsWith("pagina ")
                || lower.startsWith("relazione")
                || lower.startsWith("loro verso")
                || lower.contains("in corso")
                || lower.contains("completata")
                || lower.contains("disponibile")
                || lower.contains("attiva")) {
            return LineType.STATUS;
        }
        if (lower.startsWith("indizio:")
                || lower.startsWith("eccellenza:")
                || lower.startsWith("prima vittoria:")
                || lower.startsWith("primo completamento")
                || lower.contains("segreta")
                || lower.contains("speciale")) {
            return LineType.SPECIAL;
        }
        if (lower.startsWith("clicca")
                || lower.startsWith("click")
                || lower.startsWith("vai ")
                || lower.startsWith("apri ")
                || lower.startsWith("usa ")
                || lower.startsWith("scorri ")
                || lower.startsWith("muoviti ")
                || lower.startsWith("puoi ")) {
            return LineType.ACTION;
        }
        if (lower.startsWith("mancano ")
                || lower.contains("non disponibile")
                || lower.contains("blocc")
                || lower.contains("solo il capo")
                || lower.contains("manca")
                || lower.contains("non riesco")
                || lower.contains("non e")) {
            return LineType.WARNING;
        }
        if (lower.startsWith("obiettivo:")
                || lower.startsWith("dettaglio:")
                || lower.startsWith("ruolo:")
                || lower.startsWith("claim:")
                || lower.startsWith("membri ")
                || lower.startsWith("capo ")
                || lower.startsWith("nome:")
                || lower.startsWith("richiede ")) {
            return LineType.FOCUS;
        }
        return primary ? LineType.FOCUS : LineType.INFO;
    }

    private static String colorOpen(LineType type) {
        return switch (type == null ? LineType.INFO : type) {
            case FOCUS -> "<yellow>";
            case STATUS -> "<aqua>";
            case REWARD, ACTION -> "<green>";
            case WARNING -> "<red>";
            case TIME -> "<gold>";
            case SPECIAL -> "<light_purple>";
            case NEUTRAL -> "<white>";
            case INFO -> "<gray>";
        };
    }

    private static String colorClose(LineType type) {
        return switch (type == null ? LineType.INFO : type) {
            case FOCUS -> "</yellow>";
            case STATUS -> "</aqua>";
            case REWARD, ACTION -> "</green>";
            case WARNING -> "</red>";
            case TIME -> "</gold>";
            case SPECIAL -> "</light_purple>";
            case NEUTRAL -> "</white>";
            case INFO -> "</gray>";
        };
    }

    private static List<String> wrapContent(String raw) {
        return wrapContent(raw, false);
    }

    private static List<String> wrapContent(String raw, boolean preserveMarkers) {
        String sanitized = preserveMarkers ? sanitizeContentPreserveMarkers(raw) : sanitizeContent(raw);
        if (sanitized.isBlank()) {
            return List.of();
        }
        if (sanitized.length() <= WRAP_COLUMN) {
            return List.of(sanitized);
        }
        List<String> wrapped = new ArrayList<>();
        String[] words = sanitized.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (word == null || word.isBlank()) {
                continue;
            }
            if (current.isEmpty()) {
                current.append(word);
                continue;
            }
            if (current.length() + 1 + word.length() > WRAP_COLUMN) {
                wrapped.add(current.toString());
                current.setLength(0);
                current.append(word);
                continue;
            }
            current.append(' ').append(word);
        }
        if (!current.isEmpty()) {
            wrapped.add(current.toString());
        }
        return wrapped.isEmpty() ? List.of(sanitized) : List.copyOf(wrapped);
    }

    private static String sanitizeContentPreserveMarkers(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String plain = MINI_TAG.matcher(raw).replaceAll(" ");
        plain = SECTION_LEGACY_CODE.matcher(plain).replaceAll("");
        plain = plain.replace('\n', ' ').replace('\r', ' ');
        plain = WHITESPACE.matcher(plain).replaceAll(" ").trim();
        if (plain.isBlank() || BORDER_ONLY.matcher(plain).matches()) {
            return "";
        }
        return plain.replace("<", "[").replace(">", "]");
    }

    private static boolean hasText(String raw) {
        return raw != null && !sanitizeContent(raw).isBlank();
    }

    public record Content(String line1, String line2, List<String> extraLines) {
    }
}
