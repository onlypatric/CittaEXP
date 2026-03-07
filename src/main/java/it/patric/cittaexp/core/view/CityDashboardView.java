package it.patric.cittaexp.core.view;

import it.patric.cittaexp.core.model.CityStatus;
import it.patric.cittaexp.core.model.CityTier;
import java.util.UUID;

public record CityDashboardView(
        UUID cityId,
        String cityName,
        String cityTag,
        CityTier tier,
        CityStatus status,
        boolean capital,
        int rank,
        long score,
        long treasuryBalance,
        int membersOnline,
        int membersMax,
        String nextTaxDue,
        String monthlyTaxLabel
) {
}
