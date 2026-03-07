package it.patric.cittaexp.core.service;

import java.util.UUID;

public interface EconomyDiagnosticsService {

    Snapshot snapshot();

    record Snapshot(
            boolean enabled,
            String lastProcessedMonth,
            int backlogMonths,
            long taxFreezeActiveCount,
            UUID capitalCityId,
            long capitalBonusEntries,
            long lastRunAtEpochMilli,
            String lastStatus,
            String lastError
    ) {
    }
}
