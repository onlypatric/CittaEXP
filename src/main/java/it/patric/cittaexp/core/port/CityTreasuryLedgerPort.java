package it.patric.cittaexp.core.port;

import it.patric.cittaexp.core.model.LedgerEntryType;
import it.patric.cittaexp.persistence.domain.CityTreasuryLedgerRecord;
import it.patric.cittaexp.persistence.domain.PersistenceWriteOutcome;
import java.util.List;
import java.util.UUID;

public interface CityTreasuryLedgerPort {

    PersistenceWriteOutcome appendLedger(CityTreasuryLedgerRecord record);

    boolean existsLedgerEntry(UUID cityId, LedgerEntryType entryType, String reason);

    long countLedgerByType(LedgerEntryType entryType);

    List<CityTreasuryLedgerRecord> listLedger(UUID cityId, int limit);
}
