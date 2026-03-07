package it.patric.cittaexp.core.service;

import it.patric.cittaexp.core.model.CityTreasuryLedgerEntry;
import it.patric.cittaexp.core.model.TaxPolicy;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public interface TaxService {

    TaxPolicy currentPolicy();

    void updatePolicy(TaxPolicy policy, String reason);

    MonthlyCycleReport runMonthlyCycle(YearMonth triggerMonth, TriggerType triggerType);

    CityTaxPreview previewCityTax(UUID cityId, YearMonth month);

    enum TriggerType {
        SCHEDULED,
        STARTUP_CATCHUP,
        MANUAL
    }

    record CityTaxPreview(
            UUID cityId,
            YearMonth month,
            long totalTaxAmount,
            long treasuryContribution,
            long leaderContribution,
            long unpaidResidual,
            boolean skippedForCreationMonth
    ) {
    }

    record MonthlyCycleReport(
            YearMonth targetMonth,
            int processedCities,
            int successfulCities,
            int frozenCities,
            int failedCities,
            int skippedCities,
            List<CityTreasuryLedgerEntry> ledgerEntries
    ) {
    }
}
