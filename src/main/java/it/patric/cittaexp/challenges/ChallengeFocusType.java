package it.patric.cittaexp.challenges;

public enum ChallengeFocusType {
    NONE,
    MATERIAL,
    ENTITY_TYPE;

    public static ChallengeFocusType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        try {
            return ChallengeFocusType.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
