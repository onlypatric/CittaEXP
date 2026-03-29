package it.patric.cittaexp.challenges;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public final class ObjectiveDedupeStore {

    private final ChallengeStore repository;

    public ObjectiveDedupeStore(ChallengeStore repository) {
        this.repository = repository;
    }

    public boolean tryMarkProcessed(
            String dedupeKey,
            String instanceId,
            int townId,
            UUID playerId,
            ChallengeObjectiveType objectiveType,
            Instant createdAt
    ) {
        if (dedupeKey == null || dedupeKey.isBlank() || instanceId == null || instanceId.isBlank()
                || objectiveType == null || townId <= 0) {
            return false;
        }
        String normalized = dedupeKey.trim().toLowerCase(Locale.ROOT);
        return repository.tryInsertObjectiveDedupe(
                normalized,
                instanceId,
                townId,
                playerId,
                objectiveType.id(),
                createdAt == null ? Instant.now() : createdAt
        );
    }
}

