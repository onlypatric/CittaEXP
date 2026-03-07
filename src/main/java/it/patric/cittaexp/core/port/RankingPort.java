package it.patric.cittaexp.core.port;

import java.util.Optional;
import java.util.UUID;

public interface RankingPort {

    boolean available();

    Optional<CityRankingEntry> findByCityId(UUID cityId);

    Optional<CityRankingEntry> topKingdom();

    record CityRankingEntry(UUID cityId, int rank, long score, String sourceVersion, long fetchedAtEpochMilli) {
    }
}
