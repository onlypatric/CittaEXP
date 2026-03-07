package it.patric.cittaexp.runtime.dependency;

import java.util.Objects;

public record ExternalDependencyStatus(
        String name,
        DependencyState state,
        String version,
        String reason
) {

    public ExternalDependencyStatus {
        name = requireText(name, "name");
        state = Objects.requireNonNull(state, "state");
        version = version == null ? "n/a" : version;
        reason = reason == null ? "" : reason;
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
