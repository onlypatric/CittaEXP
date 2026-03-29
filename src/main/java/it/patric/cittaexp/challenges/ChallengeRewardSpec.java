package it.patric.cittaexp.challenges;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;

public record ChallengeRewardSpec(
        double xpCity,
        double moneyCity,
        List<String> consoleCommands,
        List<String> commandKeys,
        Map<Material, Integer> vaultItems,
        double personalXpCityBonus
) {
    public static final ChallengeRewardSpec EMPTY = new ChallengeRewardSpec(
            0.0D,
            0.0D,
            List.of(),
            List.of(),
            Map.of(),
            0.0D
    );

    public boolean empty() {
        return xpCity <= 0.0D
                && moneyCity <= 0.0D
                && (consoleCommands == null || consoleCommands.isEmpty())
                && (commandKeys == null || commandKeys.isEmpty())
                && (vaultItems == null || vaultItems.isEmpty())
                && personalXpCityBonus <= 0.0D;
    }

    public boolean hasPersonalRewards() {
        return personalXpCityBonus > 0.0D;
    }
}
