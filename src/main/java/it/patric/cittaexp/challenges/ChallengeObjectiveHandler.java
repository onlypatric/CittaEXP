package it.patric.cittaexp.challenges;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface ChallengeObjectiveHandler {
    void handle(Player player, int amount, ChallengeObjectiveContext context);
}
