package it.patric.cittaexp.challenges;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ChallengeAntiAbuseService {

    public record Decision(
            boolean allowed,
            int acceptedAmount,
            String reasonCode,
            boolean clamped
    ) {
        public static Decision allow(int amount) {
            return new Decision(true, amount, "ok", false);
        }

        public static Decision allowWithReason(int amount, String reasonCode) {
            return new Decision(true, amount, reasonCode == null || reasonCode.isBlank() ? "ok" : reasonCode, false);
        }

        public static Decision clamp(int amount, String reasonCode) {
            return new Decision(true, Math.max(0, amount), reasonCode == null || reasonCode.isBlank() ? "abuse:suspicion-clamp" : reasonCode, true);
        }

        public static Decision deny(String reasonCode) {
            return new Decision(false, 0, reasonCode, false);
        }
    }

    public record Diagnostics(
            Map<String, Long> droppedByReason,
            long throttleHits,
            long capHits,
            long afkHits,
            long suspicionWarningHits,
            long suspicionClampHits,
            long suspicionReviewFlags,
            int placedRegistrySize,
            String lastError
    ) {
    }

    private static final Set<ChallengeObjectiveType> MOB_OBJECTIVES = EnumSet.of(
            ChallengeObjectiveType.MOB_KILL,
            ChallengeObjectiveType.RARE_MOB_KILL,
            ChallengeObjectiveType.BOSS_KILL
    );
    private static final Set<ChallengeObjectiveType> AFK_GATED_OBJECTIVES = EnumSet.of(
            ChallengeObjectiveType.PLAYTIME_MINUTES,
            ChallengeObjectiveType.TRANSPORT_DISTANCE
    );

    private final CityChallengeSettings.AntiAbuseSettings settings;
    private final PlacedBlockTracker placedBlockTracker;
    private final PlayerContributionCapStore contributionCapStore;
    private final TownDailyCategoryCapStore dailyCategoryCapStore;
    private final PlayerActivityTracker activityTracker;
    private final SuspicionScoreStore suspicionScoreStore;
    private final ConcurrentHashMap<String, Long> throttleState = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> droppedByReason = new ConcurrentHashMap<>();

    private final AtomicLong throttleHits = new AtomicLong(0L);
    private final AtomicLong capHits = new AtomicLong(0L);
    private final AtomicLong afkHits = new AtomicLong(0L);
    private final AtomicLong suspicionWarningHits = new AtomicLong(0L);
    private final AtomicLong suspicionClampHits = new AtomicLong(0L);
    private final AtomicLong suspicionReviewFlags = new AtomicLong(0L);

    private volatile String lastError = "-";

    public ChallengeAntiAbuseService(
            CityChallengeSettings.AntiAbuseSettings settings,
            PlacedBlockTracker placedBlockTracker,
            PlayerContributionCapStore contributionCapStore,
            TownDailyCategoryCapStore dailyCategoryCapStore,
            PlayerActivityTracker activityTracker,
            SuspicionScoreStore suspicionScoreStore
    ) {
        this.settings = settings;
        this.placedBlockTracker = placedBlockTracker;
        this.contributionCapStore = contributionCapStore;
        this.dailyCategoryCapStore = dailyCategoryCapStore;
        this.activityTracker = activityTracker;
        this.suspicionScoreStore = suspicionScoreStore;
    }

    public Decision evaluate(
            ChallengeInstance instance,
            int townId,
            UUID playerId,
            ChallengeObjectiveType objectiveType,
            int amount,
            ChallengeObjectiveContext context,
            String dailyCycleKey,
            int dailyBucketTarget
    ) {
        try {
            if (instance == null || objectiveType == null || amount <= 0 || townId <= 0) {
                return deny("abuse:invalid-context");
            }
            ChallengeObjectiveContext safeContext = context == null ? ChallengeObjectiveContext.DEFAULT_ACTION : context;
            if (requiresPlayer(objectiveType) && playerId == null) {
                return deny("abuse:invalid-context");
            }
            if (safeContext.sampled() && !settings.isSampledAllowed(objectiveType)) {
                return deny("abuse:invalid-context");
            }

            CityChallengeSettings.ObjectiveFamilyProfile profile = settings.profileForFamily(familyForObjective(objectiveType));
            Instant now = Instant.now();

            if (objectiveType == ChallengeObjectiveType.BLOCK_MINE
                    && safeContext.blockKey() != null
                    && placedBlockTracker.isTrackedActive(safeContext.blockKey(), now, settings.placedBlockTtlHours())) {
                bumpSuspicion(townId, playerId, instance.cycleKey(), profile.suspicionOnDedupe(), now);
                return deny("abuse:block-placed");
            }

            if (MOB_OBJECTIVES.contains(objectiveType) && safeContext.mobSpawnReason() != null) {
                String normalized = safeContext.mobSpawnReason().toUpperCase(Locale.ROOT);
                if (settings.denyMobSpawnReasons().contains(normalized) || profile.denySources().contains(normalized)) {
                    bumpSuspicion(townId, playerId, instance.cycleKey(), profile.suspicionOnDenySource(), now);
                    return deny("abuse:mob-spawn-reason");
                }
            }

            int appliedAmount = amount;
            String resultReason = "ok";
            boolean clamped = false;

            int throttleWindow = Math.max(0, profile.throttleMs());
            if (playerId != null && throttleWindow > 0) {
                String throttleKey = throttleKey(playerId, instance.instanceId(), objectiveType);
                long nowMillis = System.currentTimeMillis();
                Long previous = throttleState.putIfAbsent(throttleKey, nowMillis);
                if (previous != null) {
                    if (nowMillis - previous < throttleWindow) {
                        throttleHits.incrementAndGet();
                        bumpSuspicion(townId, playerId, instance.cycleKey(), profile.suspicionOnThrottle(), now);
                        return deny("abuse:player-throttle");
                    }
                    throttleState.put(throttleKey, nowMillis);
                }
            }

            if (playerId != null && safeContext.sampled() && AFK_GATED_OBJECTIVES.contains(objectiveType) && settings.afkIdleSeconds() > 0
                    && activityTracker.isAfk(playerId, now, settings.afkIdleSeconds())) {
                afkHits.incrementAndGet();
                return deny("abuse:player-afk");
            }

            if (playerId != null) {
                int current = contributionCapStore.get(instance.instanceId(), townId, playerId);
                int contributors = contributionCapStore.contributorsCount(instance.instanceId(), townId);
                // Applica cap individuale solo quando ci sono almeno 3 contributori reali nell'istanza:
                // evita blocchi innaturali nei town piccoli/solo-player.
                if (contributors >= 3) {
                    int hardCap = Math.max(1, (int) Math.floor(instance.target() * Math.max(0.05D, profile.hardCapRatio())));
                    int softCap = Math.max(1, (int) Math.floor(instance.target() * Math.max(0.05D, Math.min(profile.softCapRatio(), profile.hardCapRatio()))));
                    if (current >= hardCap) {
                        capHits.incrementAndGet();
                        bumpSuspicion(townId, playerId, instance.cycleKey(), profile.suspicionOnCap(), now);
                        return deny("abuse:player-cap-reached");
                    }
                    if (current + appliedAmount > softCap) {
                        int allowed = Math.max(0, softCap - current);
                        if (allowed <= 0) {
                            capHits.incrementAndGet();
                            bumpSuspicion(townId, playerId, instance.cycleKey(), profile.suspicionOnCap(), now);
                            return deny("abuse:player-cap-reached");
                        }
                        appliedAmount = Math.min(appliedAmount, allowed);
                        clamped = true;
                        resultReason = "abuse:suspicion-clamp";
                        capHits.incrementAndGet();
                        bumpSuspicion(townId, playerId, instance.cycleKey(), profile.suspicionOnSoftClamp(), now);
                    }
                }
            }

            if (settings.dailyTownCategoryCapEnabled() && instance.cycleType() == ChallengeCycleType.DAILY) {
                String dayKey = safeDayKey(instance.cycleKey(), dailyCycleKey);
                String bucket = bucketForCategory(instance.category());
                int cap = (int) Math.floor(Math.max(0.0D, settings.dailyTownCategoryCapRatio()) * Math.max(0, dailyBucketTarget));
                if (cap > 0) {
                    int consumed = dailyCategoryCapStore.get(dayKey, townId, bucket);
                    if (consumed + appliedAmount > cap) {
                        capHits.incrementAndGet();
                        return deny("abuse:town-daily-cap");
                    }
                }
            }

            if (playerId != null && settings.suspicion() != null && settings.suspicion().enabled()) {
                SuspicionScoreStore.SuspicionState suspicion = suspicionScoreStore.get(townId, playerId, instance.cycleKey());
                int score = suspicion.score();
                if (score >= settings.suspicion().reviewThreshold()) {
                    suspicionReviewFlags.incrementAndGet();
                    resultReason = "abuse:suspicion-review";
                }
                if (score >= settings.suspicion().clampThreshold() && appliedAmount > 1) {
                    int before = appliedAmount;
                    int reduced = Math.max(1, (int) Math.floor(before * Math.max(0.05D, profile.clampRatio())));
                    if (reduced < before) {
                        appliedAmount = reduced;
                        clamped = true;
                        suspicionClampHits.incrementAndGet();
                        if (!"abuse:suspicion-review".equals(resultReason)) {
                            resultReason = "abuse:suspicion-clamp";
                        }
                    }
                } else if (score >= settings.suspicion().warningThreshold()) {
                    suspicionWarningHits.incrementAndGet();
                    if ("ok".equals(resultReason)) {
                        resultReason = "abuse:suspicion-warning";
                    }
                }
            }

            if (appliedAmount <= 0) {
                return deny("abuse:player-cap-reached");
            }
            if (clamped) {
                return Decision.clamp(appliedAmount, resultReason);
            }
            if (!"ok".equals(resultReason)) {
                return Decision.allowWithReason(appliedAmount, resultReason);
            }
            return Decision.allow(appliedAmount);
        } catch (Exception exception) {
            lastError = normalizeError(exception);
            return settings.denyOnGuardError() ? deny("abuse:invalid-context") : Decision.allow(amount);
        }
    }

    public void onAccepted(
            ChallengeInstance instance,
            int townId,
            UUID playerId,
            int acceptedAmount,
            String dailyCycleKey
    ) {
        if (acceptedAmount <= 0 || instance == null || townId <= 0) {
            return;
        }
        if (playerId != null) {
            contributionCapStore.increment(instance.instanceId(), townId, playerId, acceptedAmount);
        }
        if (settings.dailyTownCategoryCapEnabled() && instance.cycleType() == ChallengeCycleType.DAILY) {
            String dayKey = safeDayKey(instance.cycleKey(), dailyCycleKey);
            dailyCategoryCapStore.increment(dayKey, townId, bucketForCategory(instance.category()), acceptedAmount);
        }
    }

    public void trackPlacedBlock(String blockKey, Instant placedAt) {
        placedBlockTracker.track(blockKey, placedAt);
    }

    public void prune(Instant now) {
        try {
            Instant safeNow = now == null ? Instant.now() : now;
            placedBlockTracker.prune(safeNow, settings.placedBlockTtlHours(), settings.placedBlockMaxEntries());
            if (settings.suspicion() != null && settings.suspicion().enabled()) {
                suspicionScoreStore.decay(
                        safeNow,
                        settings.suspicion().decayIntervalMinutes(),
                        settings.suspicion().decayAmount()
                );
            }
        } catch (Exception exception) {
            lastError = normalizeError(exception);
        }
    }

    public void markActive(UUID playerId, Instant at) {
        activityTracker.markActive(playerId, at);
    }

    public void markInactive(UUID playerId) {
        activityTracker.markInactive(playerId);
    }

    public String bucketForCategory(int category) {
        return switch (category) {
            case 1, 2, 17 -> "mining";
            case 3, 5, 10 -> "mob";
            case 4, 13, 14, 15 -> "farming";
            case 7, 8, 9, 11, 12 -> "exploration";
            case 6, 16 -> "economy";
            default -> "utility";
        };
    }

    public Diagnostics diagnostics() {
        Map<String, Long> dropped = new LinkedHashMap<>();
        droppedByReason.forEach((reason, counter) -> dropped.put(reason, counter.get()));
        return new Diagnostics(
                Map.copyOf(dropped),
                throttleHits.get(),
                capHits.get(),
                afkHits.get(),
                suspicionWarningHits.get(),
                suspicionClampHits.get(),
                suspicionReviewFlags.get(),
                placedBlockTracker.size(),
                lastError
        );
    }

    private Decision deny(String reasonCode) {
        droppedByReason.computeIfAbsent(reasonCode, ignored -> new AtomicLong()).incrementAndGet();
        return Decision.deny(reasonCode);
    }

    private void bumpSuspicion(
            int townId,
            UUID playerId,
            String cycleKey,
            int delta,
            Instant now
    ) {
        if (playerId == null || delta <= 0 || settings.suspicion() == null || !settings.suspicion().enabled()) {
            return;
        }
        suspicionScoreStore.increment(
                townId,
                playerId,
                cycleKey,
                delta,
                now,
                settings.suspicion().reviewThreshold()
        );
    }

    private static String familyForObjective(ChallengeObjectiveType objectiveType) {
        if (objectiveType == null) {
            return "social_coop";
        }
        return switch (objectiveType) {
            case BLOCK_MINE, RESOURCE_CONTRIBUTION, VAULT_DELIVERY, CROP_HARVEST, FARMING_ECOSYSTEM, XP_PICKUP, FOOD_CRAFT, BREW_POTION, ITEM_CRAFT, PLACE_BLOCK -> "resource_grind";
            case MOB_KILL, RARE_MOB_KILL, BOSS_KILL, RAID_WIN, NETHER_ACTIVITY, OCEAN_ACTIVITY -> "mob_combat";
            case STRUCTURE_LOOT, TRIAL_VAULT_OPEN, STRUCTURE_DISCOVERY, DIMENSION_TRAVEL, ARCHAEOLOGY_BRUSH, SECRET_QUEST -> "structure_loot";
            case TEAM_PHASE_QUEST -> "social_coop";
            case PLAYTIME_MINUTES, TRANSPORT_DISTANCE -> "sampled_passive";
            default -> "social_coop";
        };
    }

    private static String safeDayKey(String instanceCycleKey, String fallbackCycleKey) {
        if (instanceCycleKey != null && !instanceCycleKey.isBlank()) {
            return instanceCycleKey;
        }
        if (fallbackCycleKey != null && !fallbackCycleKey.isBlank()) {
            return fallbackCycleKey;
        }
        return "unknown";
    }

    private static boolean requiresPlayer(ChallengeObjectiveType objectiveType) {
        return objectiveType != ChallengeObjectiveType.RESOURCE_CONTRIBUTION
                && objectiveType != ChallengeObjectiveType.VAULT_DELIVERY;
    }

    private static String throttleKey(UUID playerId, String instanceId, ChallengeObjectiveType objectiveType) {
        return playerId + "|" + (instanceId == null ? "-" : instanceId) + "|" + objectiveType.id();
    }

    private static String normalizeError(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message;
    }
}
