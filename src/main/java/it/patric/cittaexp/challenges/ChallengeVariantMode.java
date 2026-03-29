package it.patric.cittaexp.challenges;

public enum ChallengeVariantMode {
    GLOBAL_ONLY,
    SPECIFIC_ONLY,
    MIXED;

    public static ChallengeVariantMode fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return GLOBAL_ONLY;
        }
        try {
            return ChallengeVariantMode.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return GLOBAL_ONLY;
        }
    }
}
