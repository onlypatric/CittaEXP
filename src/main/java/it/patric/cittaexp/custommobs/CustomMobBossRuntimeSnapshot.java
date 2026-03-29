package it.patric.cittaexp.custommobs;

import java.util.UUID;

public record CustomMobBossRuntimeSnapshot(
        UUID entityId,
        String mobId,
        String displayName,
        int phaseNumber,
        String phaseId,
        String phaseLabel,
        boolean bossbarEnabled,
        CustomMobBossbarSpec bossbarSpec,
        CustomMobLocalBroadcasts localBroadcasts
) {
}
