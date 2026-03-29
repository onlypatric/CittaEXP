package it.patric.cittaexp.cityoverlaytest;

import java.util.List;
import org.bukkit.Material;

public record OverlayMissionCard(
        String key,
        String cycleLabel,
        String title,
        String objective,
        Material icon,
        OverlayMissionStatus status,
        int progressPercent,
        int remainingMinutes,
        int rewardXp,
        List<String> rewards
) {
}
