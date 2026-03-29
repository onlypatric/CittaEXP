package it.patric.cittaexp.challenges;

public enum StoryModeProofType {
    STOCKPILE,
    CONSTRUCTION,
    DEFENSE,
    SUPPLY,
    EXPANSION,
    COUNCIL,
    GRAND_PROJECT;

    public static StoryModeProofType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return STOCKPILE;
        }
        try {
            return StoryModeProofType.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return STOCKPILE;
        }
    }
}
