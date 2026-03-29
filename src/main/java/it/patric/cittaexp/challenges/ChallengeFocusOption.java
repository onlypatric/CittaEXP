package it.patric.cittaexp.challenges;

import java.util.Locale;

public record ChallengeFocusOption(
        String key,
        String label,
        ChallengeFocusRarity rarity
) {

    public ChallengeFocusOption {
        key = normalizeKey(key);
        label = (label == null || label.isBlank()) ? key : label.trim();
        rarity = rarity == null ? ChallengeFocusRarity.COMMON : rarity;
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return key.trim().toUpperCase(Locale.ROOT);
    }
}
