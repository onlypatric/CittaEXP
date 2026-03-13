package it.patric.cittaexp.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import java.util.regex.Pattern;

public final class MiniMessageHelper {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Pattern LEGACY_PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_.-]+)}");

    private MiniMessageHelper() {
    }

    public static Component parse(String input) {
        return MINI_MESSAGE.deserialize(normalize(input));
    }

    public static Component parse(String input, TagResolver... resolvers) {
        return MINI_MESSAGE.deserialize(normalize(input), resolvers);
    }

    private static String normalize(String input) {
        String raw = input == null ? "" : input;
        if (raw.indexOf('{') < 0) {
            return raw;
        }
        return LEGACY_PLACEHOLDER.matcher(raw).replaceAll("<$1>");
    }
}
