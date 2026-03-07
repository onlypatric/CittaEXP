package it.patric.cittaexp.runtime.integration;

import java.util.Objects;

public record AdapterStatus(
        String name,
        AdapterState state,
        String version,
        String reason
) {

    public AdapterStatus {
        name = requireText(name, "name");
        state = Objects.requireNonNull(state, "state");
        version = version == null || version.isBlank() ? "n/a" : version;
        reason = reason == null || reason.isBlank() ? "-" : reason;
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
