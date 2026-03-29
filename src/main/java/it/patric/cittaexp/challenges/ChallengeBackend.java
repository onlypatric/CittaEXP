package it.patric.cittaexp.challenges;

import java.time.Instant;

public interface ChallengeBackend {

    boolean enabled();

    void initialize() throws Exception;

    void appendSyncLog(String opId, String opType, String payload, Instant createdAt) throws Exception;
}

