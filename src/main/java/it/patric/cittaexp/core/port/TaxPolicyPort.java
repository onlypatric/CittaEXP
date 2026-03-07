package it.patric.cittaexp.core.port;

import it.patric.cittaexp.persistence.domain.TaxPolicyRecord;
import it.patric.cittaexp.persistence.domain.PersistenceWriteOutcome;
import java.util.Optional;

public interface TaxPolicyPort {

    Optional<TaxPolicyRecord> findTaxPolicy(String policyId);

    PersistenceWriteOutcome upsertTaxPolicy(TaxPolicyRecord record);
}
