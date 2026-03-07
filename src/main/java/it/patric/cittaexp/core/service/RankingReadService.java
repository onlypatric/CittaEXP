package it.patric.cittaexp.core.service;

import it.patric.cittaexp.core.model.RankingSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RankingReadService {

    Optional<RankingSnapshot> citySnapshot(UUID cityId);

    Optional<RankingSnapshot> topKingdom();

    List<RankingSnapshot> top(int limit);
}
