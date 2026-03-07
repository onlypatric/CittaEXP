package it.patric.cittaexp.persistence.domain;

import java.util.Objects;

public record PersistenceWriteOutcome(boolean success, boolean conflict, String message) {

    public static PersistenceWriteOutcome success(String message) {
        return new PersistenceWriteOutcome(true, false, normalize(message));
    }

    public static PersistenceWriteOutcome conflict(String message) {
        return new PersistenceWriteOutcome(false, true, normalize(message));
    }

    public static PersistenceWriteOutcome failure(String message) {
        return new PersistenceWriteOutcome(false, false, normalize(message));
    }

    public PersistenceWriteOutcome {
        message = normalize(message);
    }

    private static String normalize(String value) {
        String normalized = Objects.requireNonNullElse(value, "").trim();
        return normalized.isEmpty() ? "n/a" : normalized;
    }
}
