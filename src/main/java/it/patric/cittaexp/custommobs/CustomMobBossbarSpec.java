package it.patric.cittaexp.custommobs;

import net.kyori.adventure.bossbar.BossBar;

public record CustomMobBossbarSpec(
        boolean enabled,
        String titleTemplate,
        BossBar.Color color,
        BossBar.Overlay overlay
) {
    public static final CustomMobBossbarSpec DISABLED = new CustomMobBossbarSpec(
            false,
            "<dark_red>{boss}</dark_red> <gray>{phase}</gray>",
            BossBar.Color.RED,
            BossBar.Overlay.PROGRESS
    );
}
