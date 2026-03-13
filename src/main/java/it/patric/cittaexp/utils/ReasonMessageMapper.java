package it.patric.cittaexp.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class ReasonMessageMapper {

    private ReasonMessageMapper() {
    }

    public static String text(
            PluginConfigUtils cfg,
            String keyPrefix,
            String reasonCode
    ) {
        return PlainTextComponentSerializer.plainText().serialize(component(cfg, keyPrefix, reasonCode));
    }

    public static Component component(
            PluginConfigUtils cfg,
            String keyPrefix,
            String reasonCode,
            TagResolver... resolvers
    ) {
        String normalizedReason = normalize(reasonCode);
        return cfg.msg(keyPrefix + normalizedReason, humanize(normalizedReason), resolvers);
    }

    private static String normalize(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            return "unknown";
        }
        return reasonCode.trim();
    }

    private static String humanize(String reasonCode) {
        return reasonCode.replace('-', ' ').replace('_', ' ');
    }
}
