package it.patric.cittaexp.challenges;

import java.time.Instant;

public final class SqliteChallengeBackend implements ChallengeBackend {

    private final CityChallengeRepository repository;

    public SqliteChallengeBackend(CityChallengeRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean enabled() {
        return repository != null;
    }

    @Override
    public void initialize() {
        if (repository != null) {
            repository.initialize();
        }
    }

    @Override
    public void appendSyncLog(String opId, String opType, String payload, Instant createdAt) {
        // SQLite runtime is already persisted directly via repository mutations.
        // This backend is used as durable fallback and does not require an extra sync log write.
    }
}

