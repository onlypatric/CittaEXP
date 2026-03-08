package it.patric.cittaexp.economy.config;

import java.time.LocalTime;
import java.util.Objects;

public record EconomySettings(
        boolean enabled,
        ScheduleSettings schedule,
        TaxSettings tax,
        CapitalSettings capital
) {

    public EconomySettings {
        schedule = Objects.requireNonNull(schedule, "schedule");
        tax = Objects.requireNonNull(tax, "tax");
        capital = Objects.requireNonNull(capital, "capital");
    }

    public record ScheduleSettings(
            String timezone,
            LocalTime runTime,
            boolean catchUpEnabled,
            boolean skipCreationMonth,
            int tickIntervalSeconds
    ) {

        public ScheduleSettings {
            timezone = requireText(timezone, "timezone");
            runTime = Objects.requireNonNull(runTime, "runTime");
            if (tickIntervalSeconds < 5) {
                throw new IllegalArgumentException("tickIntervalSeconds must be >= 5");
            }
        }
    }

    public record TaxSettings(
            boolean enabled,
            long borgoMonthlyCost,
            long villaggioMonthlyCost,
            long regnoMonthlyCost,
            long regnoShopMonthlyExtraCost,
            int dueDayOfMonth
    ) {

        public TaxSettings {
            if (borgoMonthlyCost < 0L
                    || villaggioMonthlyCost < 0L
                    || regnoMonthlyCost < 0L
                    || regnoShopMonthlyExtraCost < 0L) {
                throw new IllegalArgumentException("tax costs must be >= 0");
            }
            if (dueDayOfMonth < 1 || dueDayOfMonth > 28) {
                throw new IllegalArgumentException("dueDayOfMonth must be between 1 and 28");
            }
        }
    }

    public record CapitalSettings(
            long monthlyBonus,
            String slotKey
    ) {

        public CapitalSettings {
            if (monthlyBonus < 0L) {
                throw new IllegalArgumentException("monthlyBonus must be >= 0");
            }
            slotKey = requireText(slotKey, "slotKey");
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
