package it.patric.cittaexp.challenges;

import java.util.EnumMap;
import java.util.Map;
import org.bukkit.entity.Player;

public final class ChallengeObjectiveHandlerRegistry {

    private final Map<ChallengeObjectiveType, ChallengeObjectiveHandler> handlers;
    private final CityChallengeService challengeService;

    public ChallengeObjectiveHandlerRegistry(CityChallengeService challengeService) {
        this.challengeService = challengeService;
        this.handlers = new EnumMap<>(ChallengeObjectiveType.class);
        registerDefaults();
    }

    public void register(ChallengeObjectiveType type, ChallengeObjectiveHandler handler) {
        if (type == null || handler == null) {
            return;
        }
        handlers.put(type, handler);
    }

    public void record(Player player, ChallengeObjectiveType type, int amount) {
        record(player, type, amount, ChallengeObjectiveContext.DEFAULT_ACTION);
    }

    public void record(Player player, ChallengeObjectiveType type, int amount, ChallengeObjectiveContext context) {
        if (player == null || type == null || amount <= 0) {
            return;
        }
        ChallengeObjectiveContext safeContext = context == null ? ChallengeObjectiveContext.DEFAULT_ACTION : context;
        ChallengeObjectiveHandler handler = handlers.get(type);
        if (handler == null) {
            challengeService.recordObjective(player, type, amount, safeContext);
            return;
        }
        handler.handle(player, amount, safeContext);
    }

    private void registerDefaults() {
        for (ChallengeObjectiveType type : ChallengeObjectiveType.values()) {
            register(type, (player, amount, context) -> challengeService.recordObjective(player, type, amount, context));
        }
    }
}
