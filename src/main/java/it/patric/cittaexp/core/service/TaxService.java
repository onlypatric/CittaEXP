package it.patric.cittaexp.core.service;

import it.patric.cittaexp.core.model.CityTreasuryLedgerEntry;
import it.patric.cittaexp.core.model.TaxPolicy;
import java.util.List;

public interface TaxService {

    TaxPolicy currentPolicy();

    void updatePolicy(TaxPolicy policy, String reason);

    List<CityTreasuryLedgerEntry> runMonthlyCycle();
}
