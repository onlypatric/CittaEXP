package it.patric.cittaexp.core.view;

import java.util.UUID;

public record TaxesView(
        UUID cityId,
        long monthlyCost,
        long shopExtraCost,
        long capitalBonus,
        String dueDate,
        boolean frozen,
        String freezeReason,
        long treasuryBalance
) {
}
