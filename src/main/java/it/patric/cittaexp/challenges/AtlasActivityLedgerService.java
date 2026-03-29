package it.patric.cittaexp.challenges;

import java.time.Instant;
import java.util.Optional;

public final class AtlasActivityLedgerService {

    private final ChallengeStore store;

    public AtlasActivityLedgerService(ChallengeStore store) {
        this.store = store;
    }

    public void appendDelta(
            int townId,
            AtlasChapter chapter,
            String familyId,
            long deltaProgress,
            Instant createdAt
    ) {
        if (store == null || townId <= 0 || chapter == null || familyId == null || familyId.isBlank() || deltaProgress <= 0L) {
            return;
        }
        store.appendAtlasActivity(townId, chapter, familyId, deltaProgress, createdAt == null ? Instant.now() : createdAt);
    }

    public Optional<AtlasChapter> mostActiveChapterLastDays(int townId, int days) {
        if (store == null || townId <= 0) {
            return Optional.empty();
        }
        Instant since = Instant.now().minusSeconds(Math.max(1, days) * 86_400L);
        return store.loadMostActiveAtlasChapterSince(townId, since);
    }
}
