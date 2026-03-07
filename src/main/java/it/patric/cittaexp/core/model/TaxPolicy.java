package it.patric.cittaexp.core.model;

import java.util.Objects;

public record TaxPolicy(
        long borgoMonthlyCost,
        long villaggioMonthlyCost,
        long regnoMonthlyCost,
        long regnoShopMonthlyExtraCost,
        long capitaleMonthlyBonus,
        int dueDayOfMonth,
        String timezoneId
) {

    public TaxPolicy {
        if (borgoMonthlyCost < 0 || villaggioMonthlyCost < 0 || regnoMonthlyCost < 0 || regnoShopMonthlyExtraCost < 0) {
            throw new IllegalArgumentException("tax costs must be >= 0");
        }
        if (capitaleMonthlyBonus < 0) {
            throw new IllegalArgumentException("capitaleMonthlyBonus must be >= 0");
        }
        if (dueDayOfMonth < 1 || dueDayOfMonth > 28) {
            throw new IllegalArgumentException("dueDayOfMonth must be between 1 and 28");
        }
        timezoneId = Objects.requireNonNull(timezoneId, "timezoneId").trim();
        if (timezoneId.isEmpty()) {
            throw new IllegalArgumentException("timezoneId must not be blank");
        }
    }
}
