package it.patric.cittaexp.challenges;

import java.util.List;
import org.bukkit.Material;

public record ChallengeRewardPreview(
        double xpCity,
        double moneyCity,
        double personalXpCityBonus,
        int commandCount,
        List<String> commandKeys,
        List<RewardItem> rewardItems,
        int vaultItemTypes,
        int vaultItemAmount
) {
    public static final ChallengeRewardPreview EMPTY = new ChallengeRewardPreview(
            0.0D,
            0.0D,
            0.0D,
            0,
            List.of(),
            List.of(),
            0,
            0
    );

    public record RewardItem(Material material, int amount) {
    }
}
