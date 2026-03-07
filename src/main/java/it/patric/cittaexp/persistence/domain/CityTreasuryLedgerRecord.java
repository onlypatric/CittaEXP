package it.patric.cittaexp.persistence.domain;

import it.patric.cittaexp.core.model.LedgerEntryType;
import java.util.Objects;
import java.util.UUID;

public record CityTreasuryLedgerRecord(
        UUID entryId,
        UUID cityId,
        LedgerEntryType entryType,
        long amount,
        long resultingBalance,
        String reason,
        UUID actorUuid,
        long occurredAtEpochMilli
) {

    public CityTreasuryLedgerRecord {
        entryId = Objects.requireNonNull(entryId, "entryId");
        cityId = Objects.requireNonNull(cityId, "cityId");
        entryType = Objects.requireNonNull(entryType, "entryType");
        reason = requireText(reason, "reason");
        if (occurredAtEpochMilli < 0L) {
            throw new IllegalArgumentException("occurredAtEpochMilli must be >= 0");
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
