package it.patric.cittaexp.challenges;

import java.util.Locale;

public enum ChallengeProgressionRole {
    CORE,
    SUPPORT;

    public static ChallengeProgressionRole fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ChallengeProgressionRole.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
