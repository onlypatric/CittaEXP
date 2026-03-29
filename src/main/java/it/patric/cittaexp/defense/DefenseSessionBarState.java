package it.patric.cittaexp.defense;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.bossbar.BossBar;

final class DefenseSessionBarState {

    private final Set<UUID> viewerIds;
    private volatile BossBar bossBar;

    DefenseSessionBarState() {
        this.viewerIds = ConcurrentHashMap.newKeySet();
        this.bossBar = null;
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
}
