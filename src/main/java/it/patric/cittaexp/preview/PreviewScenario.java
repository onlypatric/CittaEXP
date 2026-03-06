package it.patric.cittaexp.preview;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum PreviewScenario {
    DEFAULT,
    FREEZE,
    CAPITAL,
    LOWFUNDS,
    KINGDOM;

    public static Optional<PreviewScenario> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(value -> value.name().equals(normalized))
                .findFirst();
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
