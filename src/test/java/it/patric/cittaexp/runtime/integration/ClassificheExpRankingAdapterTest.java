package it.patric.cittaexp.runtime.integration;

import it.patric.classificheexp.api.LeaderboardApi;
import it.patric.classificheexp.api.LeaderboardApiProvider;
import it.patric.classificheexp.domain.LeaderboardEntry;
import it.patric.classificheexp.domain.LeaderboardId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClassificheExpRankingAdapterTest {

    @AfterEach
    void cleanupProvider() {
        LeaderboardApiProvider.unregister();
    }

    @Test
    void bindReturnsUnavailableWhenApiIsNotRegistered() {
        LeaderboardApiProvider.unregister();

        ClassificheExpRankingAdapter.Binding binding = ClassificheExpRankingAdapter.bind(
                mockPlugin("0.2.0"),
                100,
                Logger.getLogger("test")
        );

        assertTrue(!binding.port().available());
        assertEquals(AdapterState.UNAVAILABLE, binding.status().state());
    }

    @Test
    void parseCityKeyAcceptsCanonicalPattern() {
        UUID cityId = UUID.randomUUID();

        Optional<UUID> parsed = ClassificheExpRankingAdapter.parseCityKey("city:" + cityId);

        assertTrue(parsed.isPresent());
        assertEquals(cityId, parsed.orElseThrow());
    }

    @Test
    void findByCityIdReturnsOptionalEmptyWhenMissing() {
        UUID found = UUID.randomUUID();
        LeaderboardApi api = apiWithEntries(List.of(
                new LeaderboardEntry(LeaderboardId.GLOBAL, "city:" + found, 100),
                new LeaderboardEntry(LeaderboardId.GLOBAL, "legacy-key", 90)
        ));

        ClassificheExpRankingAdapter adapter = new ClassificheExpRankingAdapter(
                api,
                100,
                "0.2.0",
                Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC),
                Logger.getLogger("test")
        );

        assertTrue(adapter.findByCityId(UUID.randomUUID()).isEmpty());
    }

    @Test
    void topKingdomSkipsInvalidKeys() {
        UUID kingdom = UUID.randomUUID();
        LeaderboardApi api = apiWithEntries(List.of(
                new LeaderboardEntry(LeaderboardId.GLOBAL, "invalid", 500),
                new LeaderboardEntry(LeaderboardId.GLOBAL, "city:not-a-uuid", 400),
                new LeaderboardEntry(LeaderboardId.GLOBAL, "city:" + kingdom, 350)
        ));

        ClassificheExpRankingAdapter adapter = new ClassificheExpRankingAdapter(
                api,
                100,
                "0.2.0",
                Clock.fixed(Instant.ofEpochMilli(2_000L), ZoneOffset.UTC),
                Logger.getLogger("test")
        );

        assertEquals(kingdom, adapter.topKingdom().orElseThrow().cityId());
        RankingScanStats stats = adapter.scanStats();
        assertEquals(3, stats.scannedEntries());
        assertEquals(1, stats.validCityKeys());
        assertEquals(2, stats.invalidKeys());
    }

    private static LeaderboardApi apiWithEntries(List<LeaderboardEntry> entries) {
        LeaderboardApi api = mock(LeaderboardApi.class);
        when(api.getTop(100)).thenReturn(entries);
        return api;
    }

    private static Plugin mockPlugin(String version) {
        Plugin plugin = mock(Plugin.class);
        PluginDescriptionFile description = mock(PluginDescriptionFile.class);
        when(description.getVersion()).thenReturn(version);
        when(plugin.getDescription()).thenReturn(description);
        return plugin;
    }

}
