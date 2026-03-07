package it.patric.cittaexp.runtime.integration;

import it.patric.cittaexp.core.port.RankingPort;
import it.patric.classificheexp.api.LeaderboardApi;
import it.patric.classificheexp.api.LeaderboardApiProvider;
import it.patric.classificheexp.domain.LeaderboardEntry;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

public final class ClassificheExpRankingAdapter implements RankingPort {

    private static final String CITY_PREFIX = "city:";

    private final LeaderboardApi api;
    private final int scanLimit;
    private final String sourceVersion;
    private final Clock clock;
    private final Logger logger;
    private final AtomicReference<RankingScanStats> stats = new AtomicReference<>(RankingScanStats.empty());
    private final AtomicReference<ScanCache> cache = new AtomicReference<>(ScanCache.empty());

    ClassificheExpRankingAdapter(
            LeaderboardApi api,
            int scanLimit,
            String sourceVersion,
            Clock clock,
            Logger logger
    ) {
        this.api = Objects.requireNonNull(api, "api");
        this.scanLimit = scanLimit;
        this.sourceVersion = Objects.requireNonNull(sourceVersion, "sourceVersion");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public static Binding bind(Plugin plugin, int scanLimit, Logger logger) {
        Optional<LeaderboardApi> maybeApi = LeaderboardApiProvider.get();
        if (maybeApi.isEmpty()) {
            RankingPort unavailable = new UnavailableRankingPort();
            return new Binding(
                    unavailable,
                    new AdapterStatus(
                            "ClassificheExp",
                            AdapterState.UNAVAILABLE,
                            safeVersion(plugin),
                            IntegrationErrorCode.DEPENDENCY_UNAVAILABLE + ":api-missing:LeaderboardApiProvider"
                    ),
                    RankingScanStats.empty()
            );
        }

        ClassificheExpRankingAdapter adapter = new ClassificheExpRankingAdapter(
                maybeApi.get(),
                scanLimit,
                safeVersion(plugin),
                Clock.systemUTC(),
                logger
        );
        adapter.refreshScan();
        return new Binding(
                adapter,
                new AdapterStatus(
                        "ClassificheExp",
                        AdapterState.AVAILABLE,
                        safeVersion(plugin),
                        "api-registered"
                ),
                adapter.scanStats()
        );
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public Optional<CityRankingEntry> findByCityId(UUID cityId) {
        if (cityId == null) {
            return Optional.empty();
        }
        ScanCache current = getOrRefresh();
        return current.entries().stream()
                .filter(entry -> entry.cityId().equals(cityId))
                .findFirst();
    }

    @Override
    public Optional<CityRankingEntry> topKingdom() {
        ScanCache current = getOrRefresh();
        return current.entries().stream().findFirst();
    }

    public RankingScanStats scanStats() {
        return stats.get();
    }

    private ScanCache getOrRefresh() {
        ScanCache current = cache.get();
        long now = clock.millis();
        if (current.expiresAtEpochMilli() > now) {
            return current;
        }
        return refreshScan();
    }

    private synchronized ScanCache refreshScan() {
        long now = clock.millis();
        ScanCache current = cache.get();
        if (current.expiresAtEpochMilli() > now) {
            return current;
        }

        List<LeaderboardEntry> top;
        try {
            top = api.getTop(scanLimit);
        } catch (RuntimeException ex) {
            logger.warning(
                    "[CittaEXP] ClassificheExp scan failed error="
                            + IntegrationErrorCode.EXTERNAL_INTEGRATION_ERROR
                            + ":"
                            + ex.getClass().getSimpleName()
            );
            RankingScanStats snapshot = new RankingScanStats(0, 0, 0, now);
            stats.set(snapshot);
            ScanCache failed = new ScanCache(List.of(), now + 2_000L);
            cache.set(failed);
            return failed;
        }

        List<CityRankingEntry> cityEntries = new ArrayList<>();
        int invalidKeys = 0;
        int rank = 1;
        for (LeaderboardEntry entry : top) {
            Optional<UUID> cityId = parseCityKey(entry.name());
            if (cityId.isEmpty()) {
                invalidKeys++;
                rank++;
                continue;
            }
            cityEntries.add(new CityRankingEntry(
                    cityId.get(),
                    rank,
                    Math.max(0L, entry.score()),
                    sourceVersion,
                    now
            ));
            rank++;
        }

        RankingScanStats snapshot = new RankingScanStats(top.size(), cityEntries.size(), invalidKeys, now);
        stats.set(snapshot);
        ScanCache refreshed = new ScanCache(List.copyOf(cityEntries), now + 5_000L);
        cache.set(refreshed);
        return refreshed;
    }

    static Optional<UUID> parseCityKey(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase();
        if (!normalized.startsWith(CITY_PREFIX)) {
            return Optional.empty();
        }
        String payload = normalized.substring(CITY_PREFIX.length());
        try {
            return Optional.of(UUID.fromString(payload));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static String safeVersion(Plugin plugin) {
        if (plugin == null || plugin.getDescription() == null || plugin.getDescription().getVersion() == null) {
            return "unknown";
        }
        return plugin.getDescription().getVersion();
    }

    public record Binding(RankingPort port, AdapterStatus status, RankingScanStats initialStats) {
    }

    private record ScanCache(List<CityRankingEntry> entries, long expiresAtEpochMilli) {

        private static ScanCache empty() {
            return new ScanCache(List.of(), 0L);
        }
    }

    private static final class UnavailableRankingPort implements RankingPort {

        @Override
        public boolean available() {
            return false;
        }

        @Override
        public Optional<CityRankingEntry> findByCityId(UUID cityId) {
            return Optional.empty();
        }

        @Override
        public Optional<CityRankingEntry> topKingdom() {
            return Optional.empty();
        }
    }
}
