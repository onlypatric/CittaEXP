package it.patric.cittaexp.challenges;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

public final class ChallengeSignatureService {

    public record Decision(boolean blocked, String reasonCode) {
        public static final Decision ALLOW = new Decision(false, "-");
    }

    private final long townWindowSeconds;
    private final long globalWindowSeconds;

    public ChallengeSignatureService(int townWindowDays, int globalWindowDays) {
        this.townWindowSeconds = Math.max(0L, townWindowDays) * 86_400L;
        this.globalWindowSeconds = Math.max(0L, globalWindowDays) * 86_400L;
    }

    public Decision evaluate(
            List<CityChallengeRepository.SelectionHistoryEntry> history,
            int townId,
            String signatureKey,
            Instant now
    ) {
        if (signatureKey == null || signatureKey.isBlank() || now == null || history == null || history.isEmpty()) {
            return Decision.ALLOW;
        }
        String signature = normalize(signatureKey);
        long nowSeconds = now.getEpochSecond();
        long townThreshold = nowSeconds - townWindowSeconds;
        long globalThreshold = nowSeconds - globalWindowSeconds;
        for (CityChallengeRepository.SelectionHistoryEntry entry : history) {
            if (entry == null || entry.selectedAt() == null) {
                continue;
            }
            if (!normalize(entry.signatureKey()).equals(signature)) {
                continue;
            }
            long selectedAt = entry.selectedAt().getEpochSecond();
            if (globalWindowSeconds > 0L && selectedAt >= globalThreshold) {
                return new Decision(true, "signature-global-window");
            }
            if (townWindowSeconds > 0L && entry.townId() != null && entry.townId() == townId && selectedAt >= townThreshold) {
                return new Decision(true, "signature-town-window");
            }
        }
        return Decision.ALLOW;
    }

    public static String buildSignature(
            ChallengeMode mode,
            String objectiveFamily,
            ChallengeObjectiveType objectiveType,
            String focusKey,
            String biomeKey,
            String dimensionKey
    ) {
        String biomeToken = ChallengeContextResolver.biomeFamilyKey(biomeKey);
        if (biomeToken == null || biomeToken.isBlank()) {
            biomeToken = biomeKey;
        }
        return normalizeToken(mode == null ? "-" : mode.name())
                + "|fam:" + normalizeToken(objectiveFamily)
                + "|obj:" + normalizeToken(objectiveType == null ? "-" : objectiveType.id())
                + "|focus:" + normalizeToken(focusKey)
                + "|biome:" + normalizeToken(biomeToken)
                + "|dim:" + normalizeToken(dimensionKey);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeToken(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
