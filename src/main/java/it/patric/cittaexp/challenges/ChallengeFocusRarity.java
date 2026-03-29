package it.patric.cittaexp.challenges;

public enum ChallengeFocusRarity {
    COMMON,
    UNCOMMON,
    RARE;

    public static ChallengeFocusRarity fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return COMMON;
        }
        try {
            return ChallengeFocusRarity.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return COMMON;
        }
    }
}
