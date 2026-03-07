package it.patric.cittaexp.persistence.runtime;

public record PersistenceStatusSnapshot(
        PersistenceRuntimeMode mode,
        boolean mysqlReachable,
        boolean fallbackEnabled,
        long outboxPending,
        long outboxConflicts,
        long lastModeSwitchEpochMilli,
        String lastSwitchReason
) {
}
