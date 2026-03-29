package it.patric.cittaexp.utils;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

public final class CommandSuggestionUtils {

    private CommandSuggestionUtils() {
    }

    public static void suggest(SuggestionsBuilder builder, String value, String tooltip) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (tooltip == null || tooltip.isBlank()) {
            builder.suggest(value);
            return;
        }
        builder.suggest(value, new LiteralMessage(tooltip));
    }
}
