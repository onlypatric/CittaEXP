package it.patric.cittaexp.challenges;

public record ChallengeObjectiveContext(
        String blockKey,
        String mobSpawnReason,
        boolean sampled,
        boolean markActive,
        String materialKey,
        String entityTypeKey,
        String biomeKey,
        String dimensionKey,
        String dedupeKey
) {

    public static final ChallengeObjectiveContext DEFAULT_ACTION =
            new ChallengeObjectiveContext(null, null, false, true, null, null, null, null, null);
    public static final ChallengeObjectiveContext SAMPLED =
            new ChallengeObjectiveContext(null, null, true, false, null, null, null, null, null);

    public static ChallengeObjectiveContext blockBreak(
            String blockKey,
            String materialKey,
            String biomeKey,
            String dimensionKey
    ) {
        return new ChallengeObjectiveContext(
                blockKey,
                null,
                false,
                true,
                normalize(materialKey),
                null,
                normalize(biomeKey),
                normalize(dimensionKey),
                null
        );
    }

    public static ChallengeObjectiveContext mobKill(
            String spawnReason,
            String entityTypeKey,
            String biomeKey,
            String dimensionKey
    ) {
        return new ChallengeObjectiveContext(
                null,
                spawnReason,
                false,
                true,
                null,
                normalize(entityTypeKey),
                normalize(biomeKey),
                normalize(dimensionKey),
                null
        );
    }

    public static ChallengeObjectiveContext materialAction(
            String materialKey,
            String biomeKey,
            String dimensionKey
    ) {
        return new ChallengeObjectiveContext(
                null,
                null,
                false,
                true,
                normalize(materialKey),
                null,
                normalize(biomeKey),
                normalize(dimensionKey),
                null
        );
    }

    public static ChallengeObjectiveContext entityAction(
            String entityTypeKey,
            String biomeKey,
            String dimensionKey
    ) {
        return new ChallengeObjectiveContext(
                null,
                null,
                false,
                true,
                null,
                normalize(entityTypeKey),
                normalize(biomeKey),
                normalize(dimensionKey),
                null
        );
    }

    public static ChallengeObjectiveContext withDedupe(String dedupeKey) {
        return new ChallengeObjectiveContext(null, null, false, true, null, null, null, null, normalizeDedupe(dedupeKey));
    }

    public ChallengeObjectiveContext withLocationContext(String biomeKey, String dimensionKey) {
        return new ChallengeObjectiveContext(
                blockKey,
                mobSpawnReason,
                sampled,
                markActive,
                materialKey,
                entityTypeKey,
                normalize(biomeKey),
                normalize(dimensionKey),
                dedupeKey
        );
    }

    public ChallengeObjectiveContext withDedupeKey(String dedupeKey) {
        return new ChallengeObjectiveContext(
                blockKey,
                mobSpawnReason,
                sampled,
                markActive,
                materialKey,
                entityTypeKey,
                biomeKey,
                dimensionKey,
                normalizeDedupe(dedupeKey)
        );
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static String normalizeDedupe(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
