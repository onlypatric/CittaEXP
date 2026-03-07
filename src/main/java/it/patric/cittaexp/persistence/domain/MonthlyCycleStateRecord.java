package it.patric.cittaexp.persistence.domain;

import java.util.Objects;

public record MonthlyCycleStateRecord(
        String cycleKey,
        String lastProcessedMonth,
        long lastRunAtEpochMilli,
        String lastStatus,
        String lastError
) {

    public MonthlyCycleStateRecord {
        cycleKey = requireText(cycleKey, "cycleKey");
        lastProcessedMonth = requireText(lastProcessedMonth, "lastProcessedMonth");
        lastStatus = requireText(lastStatus, "lastStatus");
        lastError = lastError == null ? "" : lastError;
        if (lastRunAtEpochMilli < 0L) {
            throw new IllegalArgumentException("lastRunAtEpochMilli must be >= 0");
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
