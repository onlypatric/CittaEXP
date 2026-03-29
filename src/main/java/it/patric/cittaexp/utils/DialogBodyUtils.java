package it.patric.cittaexp.utils;

import io.papermc.paper.registry.data.dialog.body.DialogBody;
import it.patric.cittaexp.text.MiniMessageHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class DialogBodyUtils {

    private static final Pattern CLOSING_TAG = Pattern.compile("</([^>]+)>");

    private DialogBodyUtils() {
    }

    public static List<DialogBody> plainMessages(
            PluginConfigUtils configUtils,
            String path,
            String fallback,
            TagResolver... resolvers
    ) {
        String configured = configUtils.missingKeyFallback(path, fallback);
        String normalized = configured
                .replace("<newline/>", "\n")
                .replace("<newline />", "\n")
                .replace("<newline>", "\n")
                .replace("<br/>", "\n")
                .replace("<br />", "\n")
                .replace("<br>", "\n")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        List<DialogBody> bodies = new ArrayList<>(lines.length);
        for (String line : lines) {
            Component component;
            if (line == null || line.isBlank()) {
                component = Component.space();
            } else {
                try {
                    component = MiniMessageHelper.parse(stripLineScopedClosers(line), resolvers);
                } catch (IllegalArgumentException exception) {
                    component = MiniMessageHelper.parse(fallback, resolvers);
                }
            }
            bodies.add(DialogBody.plainMessage(component));
        }
        return List.copyOf(bodies);
    }

    private static String stripLineScopedClosers(String line) {
        Matcher matcher = CLOSING_TAG.matcher(line);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String rawTag = matcher.group(1);
            if (hasOpeningTag(line, rawTag)) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            matcher.appendReplacement(buffer, "");
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static boolean hasOpeningTag(String line, String rawTag) {
        if (rawTag == null || rawTag.isBlank()) {
            return false;
        }
        if (rawTag.startsWith("#")) {
            return line.contains("<" + rawTag + ">");
        }
        int colon = rawTag.indexOf(':');
        String tagName = colon >= 0 ? rawTag.substring(0, colon) : rawTag;
        return line.matches(".*<" + Pattern.quote(tagName) + "(?::|>).*");
    }
}
