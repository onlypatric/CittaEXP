package it.patric.cittaexp.challenges;

import it.patric.cittaexp.economy.EconomyBalanceCategory;
import it.patric.cittaexp.economy.EconomyValueService;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.levels.PrincipalStage;
import it.patric.cittaexp.vault.CityItemVaultService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class CityAtlasService {

    public record AtlasTierSnapshot(
            AtlasTier tier,
            int target,
            long progress,
            boolean completed,
            boolean claimable,
            boolean claimed,
            ChallengeRewardSpec reward
    ) {
    }

    public record AtlasFamilySnapshot(
            String familyId,
            String displayName,
            AtlasChapter chapter,
            ChallengeObjectiveType objectiveType,
            PrincipalStage minStage,
            boolean unlockedForStage,
            long progress,
            List<AtlasTierSnapshot> tiers
    ) {
    }

    public record AtlasChapterSnapshot(
            AtlasChapter chapter,
            String displayName,
            org.bukkit.Material icon,
            int completedFamilies,
            int totalFamilies,
            int requiredForSeal,
            boolean sealed,
            List<AtlasFamilySnapshot> families
    ) {
    }

    public record AtlasSealSnapshot(
            PrincipalStage stage,
            AtlasTier requiredTier,
            int earned,
            int required,
            Map<AtlasChapter, Boolean> chapterState
    ) {
    }

    public record ClaimResult(boolean success, String reason) {
    }

    private static final int CHAPTER_SEAL_REQUIRED_PERCENT = 60;
    private static final int STAGE_SEAL_CAP = 6;

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final CityLevelService cityLevelService;
    private final CityItemVaultService cityItemVaultService;
    private final ChallengeStore store;
    private final AtlasActivityLedgerService activityLedgerService;
    private final CityAtlasSettings settings;
    private final EconomyValueService economyValueService;
    private final ConcurrentMap<Integer, Map<String, Long>> progressByTown;
    private final Set<String> rewardGrantKeys;
    private final Map<ChallengeObjectiveType, List<AtlasFamilyDefinition>> familiesByObjective;

    public CityAtlasService(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            CityLevelService cityLevelService,
            CityItemVaultService cityItemVaultService,
            ChallengeStore store,
            CityAtlasSettings settings,
            EconomyValueService economyValueService
    ) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.cityLevelService = cityLevelService;
        this.cityItemVaultService = cityItemVaultService;
        this.store = store;
        this.activityLedgerService = new AtlasActivityLedgerService(store);
        this.settings = settings;
        this.economyValueService = economyValueService;
        this.progressByTown = new ConcurrentHashMap<>();
        this.rewardGrantKeys = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.familiesByObjective = indexFamiliesByObjective(settings.families());
    }

    public void bootstrap() {
        progressByTown.clear();
        Map<Integer, Map<String, Long>> loadedProgress = store.loadAtlasProgressByTown();
        for (Map.Entry<Integer, Map<String, Long>> entry : loadedProgress.entrySet()) {
            progressByTown.put(entry.getKey(), new ConcurrentHashMap<>(entry.getValue()));
        }
        rewardGrantKeys.clear();
        rewardGrantKeys.addAll(store.loadAtlasRewardGrantKeys());

        if (!settings.enabled()) {
            return;
        }

        String cutoverFlag = store.getRuntimeState(settings.cutoverFlagKey()).orElse("");
        if (cutoverFlag.isBlank()) {
            plugin.getLogger().info("[atlas] cutover M3/M4: reset atlas progress + seals.");
            store.clearAtlasState();
            progressByTown.clear();
            rewardGrantKeys.clear();
            for (net.william278.husktowns.town.Town town : huskTownsApiHook.getPlayableTowns()) {
                cityLevelService.syncStageSealsFromAtlas(town.getId(), 0);
            }
            store.setRuntimeState(settings.cutoverFlagKey(), Instant.now().toString());
        }

        for (net.william278.husktowns.town.Town town : huskTownsApiHook.getPlayableTowns()) {
            syncSealsForTown(town.getId());
        }
    }

    public boolean interimSealFallbackEnabled() {
        return settings.interimSealFallbackEnabled();
    }

    public boolean enabled() {
        return settings.enabled();
    }

    public void recordObjective(
            int townId,
            UUID actorId,
            ChallengeObjectiveType objectiveType,
            int amount,
            ChallengeObjectiveContext context
    ) {
        if (!settings.enabled() || townId <= 0 || objectiveType == null || amount <= 0) {
            return;
        }
        List<AtlasFamilyDefinition> candidates = familiesByObjective.get(objectiveType);
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        PrincipalStage stage = cityLevelService.statusForTown(townId, false).principalStage();
        boolean changed = false;
        Map<String, Long> familyProgress = progressByTown.computeIfAbsent(townId, ignored -> new ConcurrentHashMap<>());
        for (AtlasFamilyDefinition family : candidates) {
            if (!family.enabled()) {
                continue;
            }
            if (!unlockedByStage(family, stage)) {
                continue;
            }
            if (!matchesFocus(family, context)) {
                continue;
            }
            long previous = Math.max(0L, familyProgress.getOrDefault(family.id(), 0L));
            long next = previous + Math.max(0, amount);
            familyProgress.put(family.id(), next);
            Instant now = Instant.now();
            store.upsertAtlasFamilyProgress(townId, family.id(), next, now);
            activityLedgerService.appendDelta(townId, family.chapter(), family.id(), Math.max(0L, next - previous), now);
            changed = true;
        }
        if (changed) {
            syncSealsForTown(townId);
        }
    }

    public AtlasSealSnapshot sealsSnapshot(int townId) {
        PrincipalStage stage = cityLevelService.statusForTown(townId, false).principalStage();
        AtlasTier requiredTier = requiredTierForStage(stage);
        Map<AtlasChapter, Boolean> chapterState = new EnumMap<>(AtlasChapter.class);
        int seals = 0;
        for (AtlasChapter chapter : AtlasChapter.values()) {
            ChapterEval eval = evaluateChapter(townId, chapter, stage, requiredTier);
            chapterState.put(chapter, eval.sealed());
            if (eval.sealed()) {
                seals++;
            }
        }
        return new AtlasSealSnapshot(stage, requiredTier, Math.min(STAGE_SEAL_CAP, seals), STAGE_SEAL_CAP, Map.copyOf(chapterState));
    }

    public List<AtlasChapterSnapshot> chapterSnapshots(int townId) {
        PrincipalStage stage = cityLevelService.statusForTown(townId, false).principalStage();
        AtlasTier requiredTier = requiredTierForStage(stage);
        List<AtlasChapterSnapshot> snapshots = new ArrayList<>();
        for (AtlasChapter chapter : AtlasChapter.values()) {
            ChapterEval eval = evaluateChapter(townId, chapter, stage, requiredTier);
            CityAtlasSettings.ChapterMeta meta = settings.chapterMeta().getOrDefault(
                    chapter,
                    new CityAtlasSettings.ChapterMeta(chapter.defaultDisplayName(), chapter.defaultIcon())
            );
            snapshots.add(new AtlasChapterSnapshot(
                    chapter,
                    meta.displayName(),
                    meta.icon(),
                    eval.completedFamilies(),
                    eval.totalFamilies(),
                    eval.requiredForSeal(),
                    eval.sealed(),
                    familySnapshots(townId, chapter, stage)
            ));
        }
        snapshots.sort(Comparator.comparingInt((AtlasChapterSnapshot snapshot) -> snapshot.chapter().ordinal()));
        return snapshots;
    }

    public List<AtlasFamilySnapshot> familySnapshots(int townId, AtlasChapter chapter, PrincipalStage stage) {
        if (chapter == null) {
            return List.of();
        }
        Map<String, Long> familyProgress = progressByTown.getOrDefault(townId, Map.of());
        List<AtlasFamilySnapshot> rows = new ArrayList<>();
        for (AtlasFamilyDefinition definition : settings.families().values()) {
            if (!definition.enabled() || definition.chapter() != chapter) {
                continue;
            }
            long progress = Math.max(0L, familyProgress.getOrDefault(definition.id(), 0L));
            boolean unlocked = unlockedByStage(definition, stage);
            List<AtlasTierSnapshot> tiers = new ArrayList<>();
            for (AtlasTier tier : AtlasTier.values()) {
                if (tier == AtlasTier.IV_ELITE && !settings.tierIvEnabled()) {
                    continue;
                }
                int target = definition.target(tier);
                if (target <= 0) {
                    continue;
                }
                String grantKey = atlasGrantKey(townId, definition.id(), tier);
                boolean claimed = rewardGrantKeys.contains(grantKey);
                boolean completed = progress >= target;
                tiers.add(new AtlasTierSnapshot(
                        tier,
                        target,
                        progress,
                        completed,
                        unlocked && completed && !claimed,
                        claimed,
                        economyValueService.effectiveRewardSpec(definition.reward(tier), EconomyBalanceCategory.MILESTONE_MONEY_REWARD)
                ));
            }
            rows.add(new AtlasFamilySnapshot(
                    definition.id(),
                    definition.displayName(),
                    definition.chapter(),
                    definition.objectiveType(),
                    definition.minStage(),
                    unlocked,
                    progress,
                    List.copyOf(tiers)
            ));
        }
        rows.sort(Comparator.comparing(AtlasFamilySnapshot::displayName, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    public ClaimResult claimTierReward(Player actor, int townId, String familyId, AtlasTier tier) {
        if (!settings.enabled()) {
            return new ClaimResult(false, "atlas-disabled");
        }
        if (actor == null || townId <= 0 || familyId == null || familyId.isBlank() || tier == null) {
            return new ClaimResult(false, "invalid-request");
        }
        AtlasFamilyDefinition family = settings.families().get(familyId);
        if (family == null || !family.enabled()) {
            return new ClaimResult(false, "family-not-found");
        }
        if (tier == AtlasTier.IV_ELITE && !settings.tierIvEnabled()) {
            return new ClaimResult(false, "tier-disabled");
        }
        PrincipalStage stage = cityLevelService.statusForTown(townId, false).principalStage();
        if (!unlockedByStage(family, stage)) {
            return new ClaimResult(false, "stage-locked");
        }
        int target = family.target(tier);
        if (target <= 0) {
            return new ClaimResult(false, "tier-unavailable");
        }
        long progress = progressByTown.getOrDefault(townId, Map.of()).getOrDefault(family.id(), 0L);
        if (progress < target) {
            return new ClaimResult(false, "tier-not-completed");
        }
        String grantKey = atlasGrantKey(townId, family.id(), tier);
        if (rewardGrantKeys.contains(grantKey)) {
            return new ClaimResult(false, "already-claimed");
        }
        ChallengeRewardSpec reward = family.reward(tier);
        reward = economyValueService.effectiveRewardSpec(reward, EconomyBalanceCategory.MILESTONE_MONEY_REWARD);
        if (reward == null || reward.empty()) {
            rewardGrantKeys.add(grantKey);
            store.markAtlasRewardGrant(grantKey, townId, family.id(), tier, ChallengeRewardType.XP_CITY, "empty", Instant.now());
            return new ClaimResult(true, "claimed");
        }

        if (reward.xpCity() > 0.0D) {
            long scaled = Math.max(1L, Math.round(reward.xpCity() * cityLevelService.settings().xpScale()));
            if (store.markAtlasRewardGrant(grantKey + ":xp", townId, family.id(), tier, ChallengeRewardType.XP_CITY,
                    Double.toString(reward.xpCity()), Instant.now())) {
                cityLevelService.grantXpBonus(townId, scaled, actor.getUniqueId(), "atlas-" + family.id() + ':' + tier.name());
            }
        }
        if (reward.vaultItems() != null && !reward.vaultItems().isEmpty()) {
            if (store.markAtlasRewardGrant(grantKey + ":vault", townId, family.id(), tier, ChallengeRewardType.ITEM_VAULT,
                    "items=" + reward.vaultItems().size(), Instant.now())) {
                cityItemVaultService.depositRewardItems(townId, reward.vaultItems(), "atlas-" + family.id() + ':' + tier.name());
            }
        }
        if (reward.consoleCommands() != null && !reward.consoleCommands().isEmpty()) {
            int index = 0;
            for (String command : reward.consoleCommands()) {
                String trimmed = command == null ? "" : command.trim();
                if (trimmed.isBlank()) {
                    continue;
                }
                String key = grantKey + ":cmd:" + index++;
                if (!store.markAtlasRewardGrant(key, townId, family.id(), tier, ChallengeRewardType.COMMAND_CONSOLE,
                        trimmed, Instant.now())) {
                    continue;
                }
                String rendered = renderCommand(trimmed, actor, townId, family.id(), tier.name());
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rendered));
            }
        }
        rewardGrantKeys.add(grantKey);
        store.markAtlasRewardGrant(grantKey, townId, family.id(), tier, ChallengeRewardType.XP_CITY, "claimed", Instant.now());
        return new ClaimResult(true, "claimed");
    }

    public void syncSealsForTown(int townId) {
        if (!settings.enabled() || townId <= 0) {
            return;
        }
        AtlasSealSnapshot snapshot = sealsSnapshot(townId);
        cityLevelService.syncStageSealsFromAtlas(townId, snapshot.earned());
    }

    public void syncAllTownSeals() {
        if (!settings.enabled()) {
            return;
        }
        for (net.william278.husktowns.town.Town town : huskTownsApiHook.getPlayableTowns()) {
            syncSealsForTown(town.getId());
        }
    }

    public Optional<AtlasChapter> leastCompletedChapter(int townId) {
        if (!settings.enabled() || townId <= 0) {
            return Optional.empty();
        }
        List<AtlasChapterSnapshot> chapters = chapterSnapshots(townId);
        AtlasChapter best = null;
        double bestRatio = Double.MAX_VALUE;
        int bestTotal = Integer.MAX_VALUE;
        for (AtlasChapterSnapshot snapshot : chapters) {
            if (snapshot == null || snapshot.totalFamilies() <= 0) {
                continue;
            }
            double ratio = (double) Math.max(0, snapshot.completedFamilies()) / (double) Math.max(1, snapshot.totalFamilies());
            if (best == null
                    || ratio < bestRatio
                    || (Double.compare(ratio, bestRatio) == 0 && snapshot.totalFamilies() < bestTotal)
                    || (Double.compare(ratio, bestRatio) == 0 && snapshot.totalFamilies() == bestTotal
                    && snapshot.chapter().ordinal() < best.ordinal())) {
                best = snapshot.chapter();
                bestRatio = ratio;
                bestTotal = snapshot.totalFamilies();
            }
        }
        return Optional.ofNullable(best);
    }

    public Optional<AtlasChapter> mostActiveChapterLastDays(int townId, int days) {
        if (!settings.enabled() || townId <= 0) {
            return Optional.empty();
        }
        return activityLedgerService.mostActiveChapterLastDays(townId, days);
    }

    public Optional<AtlasChapter> primaryChapterForObjective(ChallengeObjectiveType objectiveType) {
        if (objectiveType == null) {
            return Optional.empty();
        }
        List<AtlasFamilyDefinition> families = familiesByObjective.get(objectiveType);
        if (families == null || families.isEmpty()) {
            return Optional.empty();
        }
        Map<AtlasChapter, Integer> counts = new EnumMap<>(AtlasChapter.class);
        for (AtlasFamilyDefinition family : families) {
            if (family == null || family.chapter() == null) {
                continue;
            }
            counts.compute(family.chapter(), (ignored, existing) -> Integer.valueOf(Math.max(0, existing == null ? 0 : existing) + 1));
        }
        AtlasChapter best = null;
        int bestCount = -1;
        for (Map.Entry<AtlasChapter, Integer> entry : counts.entrySet()) {
            int current = entry.getValue() == null ? 0 : entry.getValue();
            if (best == null || current > bestCount
                    || (current == bestCount && entry.getKey().ordinal() < best.ordinal())) {
                best = entry.getKey();
                bestCount = current;
            }
        }
        return Optional.ofNullable(best);
    }

    public Set<String> atlasFamilyIdsForObjective(ChallengeObjectiveType objectiveType) {
        List<AtlasFamilyDefinition> families = familiesByObjective.get(objectiveType);
        if (families == null || families.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (AtlasFamilyDefinition family : families) {
            if (family != null && family.id() != null && !family.id().isBlank()) {
                result.add(family.id().trim().toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(result);
    }

    private ChapterEval evaluateChapter(int townId, AtlasChapter chapter, PrincipalStage stage, AtlasTier requiredTier) {
        List<AtlasFamilySnapshot> families = familySnapshots(townId, chapter, stage);
        int total = 0;
        int completed = 0;
        for (AtlasFamilySnapshot family : families) {
            if (!family.unlockedForStage()) {
                continue;
            }
            AtlasTierSnapshot tierSnapshot = family.tiers().stream()
                    .filter(tier -> tier.tier() == requiredTier)
                    .findFirst()
                    .orElse(null);
            if (tierSnapshot == null) {
                continue;
            }
            total++;
            if (tierSnapshot.completed()) {
                completed++;
            }
        }
        int required = total <= 0 ? 0 : (int) Math.ceil(total * (CHAPTER_SEAL_REQUIRED_PERCENT / 100.0D));
        boolean sealed = total > 0 && completed >= required;
        return new ChapterEval(completed, total, required, sealed);
    }

    private String renderCommand(String command, Player actor, int townId, String familyId, String tier) {
        String townName = huskTownsApiHook.getTownById(townId).map(net.william278.husktowns.town.Town::getName)
                .orElse("Town-" + townId);
        String playerName = actor == null ? "-" : actor.getName();
        String playerUuid = actor == null ? "-" : actor.getUniqueId().toString();
        return command
                .replace("{town_id}", Integer.toString(townId))
                .replace("{town_name}", townName)
                .replace("{player_name}", playerName)
                .replace("{player_uuid}", playerUuid)
                .replace("{atlas_family}", familyId)
                .replace("{atlas_tier}", tier);
    }

    private static Map<ChallengeObjectiveType, List<AtlasFamilyDefinition>> indexFamiliesByObjective(
            Map<String, AtlasFamilyDefinition> source
    ) {
        Map<ChallengeObjectiveType, List<AtlasFamilyDefinition>> map = new EnumMap<>(ChallengeObjectiveType.class);
        for (AtlasFamilyDefinition definition : source.values()) {
            if (definition == null || !definition.enabled() || definition.objectiveType() == null) {
                continue;
            }
            map.computeIfAbsent(definition.objectiveType(), ignored -> new ArrayList<>()).add(definition);
        }
        for (Map.Entry<ChallengeObjectiveType, List<AtlasFamilyDefinition>> entry : map.entrySet()) {
            entry.setValue(List.copyOf(entry.getValue()));
        }
        return Map.copyOf(map);
    }

    private static boolean unlockedByStage(AtlasFamilyDefinition family, PrincipalStage stage) {
        if (family == null || stage == null) {
            return false;
        }
        return stage.ordinal() >= family.minStage().ordinal();
    }

    private static boolean matchesFocus(AtlasFamilyDefinition family, ChallengeObjectiveContext context) {
        if (family == null) {
            return false;
        }
        if (family.focusType() == null || family.focusType() == ChallengeFocusType.NONE) {
            return true;
        }
        if (family.focusKeys() == null || family.focusKeys().isEmpty()) {
            return true;
        }
        if (context == null) {
            return false;
        }
        String key = switch (family.focusType()) {
            case MATERIAL -> context.materialKey();
            case ENTITY_TYPE -> context.entityTypeKey();
            case NONE -> null;
        };
        if (key == null || key.isBlank()) {
            return false;
        }
        if (key.indexOf('|') < 0) {
            return family.focusKeys().contains(key.toUpperCase(Locale.ROOT));
        }
        for (String token : key.split("\\|")) {
            if (family.focusKeys().contains(token.trim().toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static AtlasTier requiredTierForStage(PrincipalStage stage) {
        if (stage == null) {
            return AtlasTier.I;
        }
        return switch (stage) {
            case AVAMPOSTO, BORGO -> AtlasTier.I;
            case VILLAGGIO, CITTADINA -> AtlasTier.II;
            case CITTA, REGNO -> AtlasTier.III;
        };
    }

    private static String atlasGrantKey(int townId, String familyId, AtlasTier tier) {
        return "atlas:" + townId + ':' + familyId + ':' + (tier == null ? "-" : tier.name().toLowerCase(Locale.ROOT));
    }

    private record ChapterEval(int completedFamilies, int totalFamilies, int requiredForSeal, boolean sealed) {
    }
}
