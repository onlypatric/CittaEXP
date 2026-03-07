package it.patric.cittaexp.core.port;

import it.patric.cittaexp.persistence.domain.CapitalStateRecord;
import it.patric.cittaexp.persistence.domain.MonthlyCycleStateRecord;
import it.patric.cittaexp.persistence.domain.PersistenceWriteOutcome;
import it.patric.cittaexp.persistence.domain.RankingSnapshotRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EconomyStatePort {

    Optional<CapitalStateRecord> findCapitalState(String capitalSlot);

    PersistenceWriteOutcome upsertCapitalState(CapitalStateRecord record);

    PersistenceWriteOutcome clearCapitalState(String capitalSlot);

    Optional<RankingSnapshotRecord> findRankingSnapshot(UUID cityId);

    List<RankingSnapshotRecord> listRankingSnapshots(int limit);

    PersistenceWriteOutcome upsertRankingSnapshot(RankingSnapshotRecord record);

    PersistenceWriteOutcome deleteRankingSnapshot(UUID cityId);

    Optional<MonthlyCycleStateRecord> findMonthlyCycleState(String cycleKey);

    PersistenceWriteOutcome upsertMonthlyCycleState(MonthlyCycleStateRecord record);
}
