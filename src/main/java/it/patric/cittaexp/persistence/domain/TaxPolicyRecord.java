package it.patric.cittaexp.persistence.domain;

import java.util.Objects;

public record TaxPolicyRecord(
        String policyId,
        long borgoMonthlyCost,
        long villaggioMonthlyCost,
        long regnoMonthlyCost,
        long regnoShopMonthlyExtra,
        long capitaleMonthlyBonus,
        int dueDayOfMonth,
        String timezoneId,
        long updatedAtEpochMilli
) {

    public TaxPolicyRecord {
        policyId = requireText(policyId, "policyId");
        timezoneId = requireText(timezoneId, "timezoneId");
        if (borgoMonthlyCost < 0L
                || villaggioMonthlyCost < 0L
                || regnoMonthlyCost < 0L
                || regnoShopMonthlyExtra < 0L
                || capitaleMonthlyBonus < 0L) {
            throw new IllegalArgumentException("tax policy amounts must be >= 0");
        }
        if (dueDayOfMonth < 1 || dueDayOfMonth > 28) {
            throw new IllegalArgumentException("dueDayOfMonth must be between 1 and 28");
        }
        if (updatedAtEpochMilli < 0L) {
            throw new IllegalArgumentException("updatedAtEpochMilli must be >= 0");
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
