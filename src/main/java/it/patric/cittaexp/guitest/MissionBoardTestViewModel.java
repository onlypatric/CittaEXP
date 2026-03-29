package it.patric.cittaexp.guitest;

import org.bukkit.Material;

public record MissionBoardTestViewModel(
        String groupId,
        String title,
        Material icon,
        MissionBoardStatus status,
        int progressPercent,
        int remainingMinutes,
        int rewardXp,
        String rewardItem,
        int priorityScore
) {
}
