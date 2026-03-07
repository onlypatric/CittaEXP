package it.patric.cittaexp.core.model;

import java.util.Objects;

public record MemberClaimPermissions(
        boolean access,
        boolean container,
        boolean build
) {

    public static MemberClaimPermissions none() {
        return new MemberClaimPermissions(false, false, false);
    }

    public static MemberClaimPermissions memberDefault() {
        return new MemberClaimPermissions(true, false, false);
    }

    public static MemberClaimPermissions viceOrLeader() {
        return new MemberClaimPermissions(true, true, true);
    }

    public static MemberClaimPermissions parse(String key) {
        String normalized = Objects.requireNonNullElse(key, "").trim().toLowerCase();
        return switch (normalized) {
            case "access" -> new MemberClaimPermissions(true, false, false);
            case "container" -> new MemberClaimPermissions(true, true, false);
            case "build" -> new MemberClaimPermissions(true, true, true);
            default -> throw new IllegalStateException("claim-permission-invalid");
        };
    }
}
