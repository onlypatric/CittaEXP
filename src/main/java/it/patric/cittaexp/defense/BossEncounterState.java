package it.patric.cittaexp.defense;

import it.patric.cittaexp.custommobs.CustomMobBossRuntimeSnapshot;
import it.patric.cittaexp.custommobs.CustomMobLocalBroadcasts;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.bossbar.BossBar;

final class BossEncounterState {

    private final UUID bossUuid;
    private final int townId;
    private final String sessionId;
    private final int waveNumber;
    private final Set<UUID> viewerIds;

    private volatile BossBar bossBar;
    private volatile int phaseNumber;
    private volatile String phaseId;
    private volatile String phaseLabel;
    private volatile String bossName;
    private volatile String mobId;
    private volatile CustomMobLocalBroadcasts localBroadcasts;

    BossEncounterState(UUID bossUuid, int townId, String sessionId, int waveNumber) {
        this.bossUuid = bossUuid;
        this.townId = townId;
        this.sessionId = sessionId;
        this.waveNumber = waveNumber;
        this.viewerIds = ConcurrentHashMap.newKeySet();
        this.bossBar = null;
        this.phaseNumber = 0;
        this.phaseId = null;
        this.phaseLabel = null;
        this.bossName = null;
        this.mobId = null;
        this.localBroadcasts = CustomMobLocalBroadcasts.EMPTY;
    }

    UUID bossUuid() {
        return bossUuid;
    }

    int townId() {
        return townId;
    }

    String sessionId() {
        return sessionId;
    }

    int waveNumber() {
        return waveNumber;
    }

    Set<UUID> viewerIds() {
        return viewerIds;
    }

    BossBar bossBar() {
        return bossBar;
    }

    void bossBar(BossBar bossBar) {
        this.bossBar = bossBar;
    }

    int phaseNumber() {
        return phaseNumber;
    }

    String phaseId() {
        return phaseId;
    }

    String phaseLabel() {
        return phaseLabel;
    }

    String bossName() {
        return bossName;
    }

    String mobId() {
        return mobId;
    }

    CustomMobLocalBroadcasts localBroadcasts() {
        return localBroadcasts;
    }

    void updateFromSnapshot(CustomMobBossRuntimeSnapshot snapshot) {
        this.phaseNumber = snapshot.phaseNumber();
        this.phaseId = snapshot.phaseId();
        this.phaseLabel = snapshot.phaseLabel();
        this.bossName = snapshot.displayName();
        this.mobId = snapshot.mobId();
        this.localBroadcasts = snapshot.localBroadcasts();
    }
}
