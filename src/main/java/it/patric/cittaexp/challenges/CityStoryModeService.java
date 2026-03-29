package it.patric.cittaexp.challenges;

import it.patric.cittaexp.economy.EconomyBalanceCategory;
import it.patric.cittaexp.economy.EconomyValueService;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.vault.CityItemVaultService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class CityStoryModeService {

    public enum ChapterState {
        LOCKED,
        UNLOCKED,
        IN_PROGRESS,
        COMPLETED
    }

    public enum TaskState {
        LOCKED,
        AVAILABLE,
        IN_PROGRESS,
        COMPLETED
    }

    public record StoryModeTaskSnapshot(
            String templateId,
            String title,
            String description,
            StoryModeStepType stepType,
            StoryModeProofType proofType,
            int progressPercent,
            boolean completed,
            TaskState state,
            String progressLabel
    ) {
    }

    public record StoryModeChapterSnapshot(
            String chapterId,
            int order,
            String title,
            String act,
            boolean elite,
            int minCityLevel,
            AtlasChapter primaryChapter,
            ChapterState state,
            int progressPercent,
            boolean unlocked,
            boolean completed,
            boolean rewardGranted,
            String narrativeBeat,
            String signatureGoal,
            String chapterProof,
            String completionFantasy,
            List<AtlasChapter> taskFamilies,
            List<StoryModeTaskSnapshot> tasks,
            ChallengeRewardPreview rewardPreview
    ) {
    }

    public record StoryModeDiagnostics(
            boolean atlasEnabled,
            boolean storyModeConfigEnabled,
            boolean storyModeEnabled,
            int baseChapterCount,
            int eliteChapterCount,
            int renderedChapterCount,
            String reason
    ) {
    }

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final CityLevelService cityLevelService;
    private final CityItemVaultService cityItemVaultService;
    private final ChallengeStore store;
    private final CityAtlasService atlasService;
    private final CityAtlasSettings settings;
    private final EconomyValueService economyValueService;
    private final Set<String> rewardGrantKeys;

    public CityStoryModeService(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            CityLevelService cityLevelService,
            CityItemVaultService cityItemVaultService,
            ChallengeStore store,
            CityAtlasService atlasService,
            CityAtlasSettings settings,
            EconomyValueService economyValueService
    ) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.cityLevelService = cityLevelService;
        this.cityItemVaultService = cityItemVaultService;
        this.store = store;
        this.atlasService = atlasService;
        this.settings = settings;
        this.economyValueService = economyValueService;
        this.rewardGrantKeys = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    public void bootstrap() {
        rewardGrantKeys.clear();
        for (String grantKey : store.loadRewardGrantKeys()) {
            if (grantKey != null && grantKey.startsWith("story-mode:")) {
                rewardGrantKeys.add(grantKey);
            }
        }
    }

    public boolean enabled() {
        return atlasService.enabled() && settings.storyMode().enabled();
    }

    public int maxPage(int pageSize) {
        return settings.storyMode().maxPage(pageSize);
    }

    public List<StoryModeChapterSnapshot> chapterSnapshots(int townId, UUID actorId) {
        if (!enabled() || townId <= 0) {
            return List.of();
        }
        syncTownRewards(townId, actorId);
        return buildSnapshots(townId);
    }

    public StoryModeChapterSnapshot chapterSnapshot(int townId, String chapterId, UUID actorId) {
        if (chapterId == null || chapterId.isBlank()) {
            return null;
        }
        for (StoryModeChapterSnapshot snapshot : chapterSnapshots(townId, actorId)) {
            if (snapshot.chapterId().equalsIgnoreCase(chapterId)) {
                return snapshot;
            }
        }
        return null;
    }

    public StoryModeDiagnostics diagnostics(int townId) {
        int baseCount = settings.storyMode().baseChapters().size();
        int eliteCount = settings.storyMode().eliteChapters().size();
        boolean atlasEnabled = atlasService.enabled();
        boolean configEnabled = settings.storyMode().enabled();
        boolean enabled = atlasEnabled && configEnabled;
        if (!configEnabled) {
            return new StoryModeDiagnostics(atlasEnabled, false, false, baseCount, eliteCount, 0,
                    "Story Mode disabilitato nel catalogo.");
        }
        if (!atlasEnabled) {
            return new StoryModeDiagnostics(false, true, false, baseCount, eliteCount, 0,
                    "Atlas backend non attivo.");
        }
        if (baseCount + eliteCount <= 0) {
            return new StoryModeDiagnostics(true, true, true, 0, 0, 0,
                    "Catalogo Story Mode vuoto.");
        }
        int rendered = townId > 0 ? buildSnapshots(townId).size() : 0;
        if (rendered <= 0) {
            return new StoryModeDiagnostics(true, true, true, baseCount, eliteCount, 0,
                    "Nessun capitolo generato per la città.");
        }
        return new StoryModeDiagnostics(true, true, true, baseCount, eliteCount, rendered, "ok");
    }

    public void syncTownRewards(int townId, UUID actorId) {
        if (!enabled() || townId <= 0) {
            return;
        }
        for (StoryModeChapterSnapshot snapshot : buildSnapshots(townId)) {
            if (!snapshot.completed() || snapshot.rewardGranted()) {
                continue;
            }
            StoryModeChapterDefinition definition = settings.storyMode().chapter(snapshot.chapterId());
            if (definition == null) {
                continue;
            }
            grantChapterReward(townId, definition, actorId);
        }
    }

    private List<StoryModeChapterSnapshot> buildSnapshots(int townId) {
        CityLevelService.LevelStatus level = cityLevelService.statusForTown(townId, false);
        Map<String, CityAtlasService.AtlasFamilySnapshot> families = indexFamilies(townId);
        Set<String> successfulDefenseTiers = store.loadSuccessfulDefenseTiers(townId);
        List<StoryModeChapterSnapshot> snapshots = new ArrayList<>();
        boolean previousGateReached = true;
        int gatePercent = Math.max(1, Math.min(100, settings.storyMode().completionGatePercent()));
        for (StoryModeChapterDefinition definition : settings.storyMode().allChapters()) {
            boolean levelGate = level.level() >= definition.minCityLevel();
            boolean unlocked = levelGate && previousGateReached;
            List<StoryModeTaskSnapshot> taskSnapshots = buildTaskSnapshots(definition, unlocked, families, successfulDefenseTiers);
            boolean rawCompleted = !taskSnapshots.isEmpty() && taskSnapshots.stream().allMatch(StoryModeTaskSnapshot::completed);
            boolean completed = unlocked && rawCompleted;
            int progressPercent = taskSnapshots.isEmpty()
                    ? 0
                    : (int) Math.round(taskSnapshots.stream().mapToInt(StoryModeTaskSnapshot::progressPercent).average().orElse(0.0D));
            ChapterState state = completed
                    ? ChapterState.COMPLETED
                    : unlocked
                    ? (progressPercent > 0 ? ChapterState.IN_PROGRESS : ChapterState.UNLOCKED)
                    : ChapterState.LOCKED;
            boolean rewardGranted = rewardGrantKeys.contains(summaryGrantKey(townId, definition.id()));
            snapshots.add(new StoryModeChapterSnapshot(
                    definition.id(),
                    definition.order(),
                    definition.title(),
                    definition.act(),
                    definition.elite(),
                    definition.minCityLevel(),
                    definition.primaryChapter(),
                    state,
                    clampPercent(progressPercent),
                    unlocked,
                    completed,
                    rewardGranted,
                    definition.narrativeBeat(),
                    definition.signatureGoal(),
                    definition.chapterProof(),
                    definition.completionFantasy(),
                    definition.taskFamilies(),
                    List.copyOf(taskSnapshots),
                    toRewardPreview(definition.reward())
            ));
            previousGateReached = unlocked && progressPercent >= gatePercent;
        }
        return List.copyOf(snapshots);
    }

    private List<StoryModeTaskSnapshot> buildTaskSnapshots(
            StoryModeChapterDefinition definition,
            boolean chapterUnlocked,
            Map<String, CityAtlasService.AtlasFamilySnapshot> families,
            Set<String> successfulDefenseTiers
    ) {
        List<StoryModeTaskSnapshot> tasks = new ArrayList<>();
        for (String templateId : definition.taskTemplateIds()) {
            StoryModeTaskTemplate template = settings.storyMode().templates().get(templateId);
            if (template != null) {
                tasks.add(toTaskSnapshot(template, chapterUnlocked, families, successfulDefenseTiers));
            }
        }
        StoryModeTaskTemplate milestone = settings.storyMode().templates().get(definition.milestoneTemplateId());
        if (milestone != null) {
            tasks.add(toTaskSnapshot(milestone, chapterUnlocked, families, successfulDefenseTiers));
        }
        StoryModeTaskTemplate proof = settings.storyMode().templates().get(definition.proofTemplateId());
        if (proof != null) {
            tasks.add(toTaskSnapshot(proof, chapterUnlocked, families, successfulDefenseTiers));
        }
        return List.copyOf(tasks);
    }

    private StoryModeTaskSnapshot toTaskSnapshot(
            StoryModeTaskTemplate template,
            boolean chapterUnlocked,
            Map<String, CityAtlasService.AtlasFamilySnapshot> families,
            Set<String> successfulDefenseTiers
    ) {
        int progressPercent = 0;
        String progressLabel = "0%";
        boolean completed = false;
        if (template.usesAtlasProgress()) {
            CityAtlasService.AtlasFamilySnapshot family = families.get(template.atlasFamilyId());
            CityAtlasService.AtlasTierSnapshot tier = family == null ? null : family.tiers().stream()
                    .filter(value -> value.tier() == template.atlasTier())
                    .findFirst()
                    .orElse(null);
            if (tier != null) {
                progressPercent = tier.target() <= 0 ? 0 : clampPercent((int) Math.round((tier.progress() * 100.0D) / Math.max(1, tier.target())));
                progressLabel = Math.max(0L, tier.progress()) + "/" + Math.max(1, tier.target());
                completed = tier.completed();
            }
        } else if (template.usesDefenseProgress()) {
            completed = successfulDefenseTiers.contains(template.defenseTier().toUpperCase(Locale.ROOT));
            progressPercent = completed ? 100 : 0;
            progressLabel = completed ? "Vittoria registrata" : "Serve difesa " + template.defenseTier();
        }
        TaskState state = completed
                ? TaskState.COMPLETED
                : chapterUnlocked
                ? (progressPercent > 0 ? TaskState.IN_PROGRESS : TaskState.AVAILABLE)
                : TaskState.LOCKED;
        return new StoryModeTaskSnapshot(
                template.id(),
                template.title(),
                template.description(),
                template.stepType(),
                template.proofType(),
                clampPercent(progressPercent),
                completed,
                state,
                progressLabel
        );
    }

    private Map<String, CityAtlasService.AtlasFamilySnapshot> indexFamilies(int townId) {
        Map<String, CityAtlasService.AtlasFamilySnapshot> result = new HashMap<>();
        List<CityAtlasService.AtlasChapterSnapshot> chapters = atlasService.chapterSnapshots(townId);
        for (CityAtlasService.AtlasChapterSnapshot chapter : chapters) {
            for (CityAtlasService.AtlasFamilySnapshot family : chapter.families()) {
                result.put(family.familyId(), family);
            }
        }
        return result;
    }

    private void grantChapterReward(int townId, StoryModeChapterDefinition definition, UUID actorId) {
        String summaryKey = summaryGrantKey(townId, definition.id());
        if (rewardGrantKeys.contains(summaryKey)) {
            return;
        }
        ChallengeRewardSpec reward = definition.reward() == null ? ChallengeRewardSpec.EMPTY : definition.reward();
        reward = economyValueService.effectiveRewardSpec(reward, EconomyBalanceCategory.MILESTONE_MONEY_REWARD);
        Instant now = Instant.now();
        String syntheticInstanceId = "story-mode:" + definition.id();
        if (reward.xpCity() > 0.0D) {
            String grantKey = summaryKey + ":xp";
            if (store.markRewardGrant(grantKey, townId, syntheticInstanceId, ChallengeRewardType.XP_CITY,
                    Double.toString(reward.xpCity()), now)) {
                cityLevelService.grantXpBonus(townId, cityLevelService.toScaledXp(reward.xpCity()), actorId, "story-mode:" + definition.id());
                rewardGrantKeys.add(grantKey);
            }
        }
        if (reward.vaultItems() != null && !reward.vaultItems().isEmpty()) {
            String grantKey = summaryKey + ":vault";
            int totalItems = reward.vaultItems().values().stream().mapToInt(Integer::intValue).sum();
            if (store.markRewardGrant(grantKey, townId, syntheticInstanceId, ChallengeRewardType.ITEM_VAULT,
                    Integer.toString(Math.max(0, totalItems)), now)) {
                cityItemVaultService.depositRewardItems(townId, reward.vaultItems(), "story-mode:" + definition.id());
                rewardGrantKeys.add(grantKey);
            }
        }
        if (reward.consoleCommands() != null && !reward.consoleCommands().isEmpty()) {
            int index = 0;
            for (String command : reward.consoleCommands()) {
                if (command == null || command.isBlank()) {
                    continue;
                }
                String grantKey = summaryKey + ":cmd:" + index++;
                if (!store.markRewardGrant(grantKey, townId, syntheticInstanceId, ChallengeRewardType.COMMAND_CONSOLE, command.trim(), now)) {
                    continue;
                }
                rewardGrantKeys.add(grantKey);
                String rendered = renderCommand(command.trim(), townId, actorId, definition);
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rendered));
            }
        }
        if (store.markRewardGrant(summaryKey, townId, syntheticInstanceId, ChallengeRewardType.XP_CITY, "claimed", now)) {
            rewardGrantKeys.add(summaryKey);
        }
    }

    private String renderCommand(String command, int townId, UUID actorId, StoryModeChapterDefinition definition) {
        String townName = huskTownsApiHook.getTownById(townId)
                .map(net.william278.husktowns.town.Town::getName)
                .orElse("Town-" + townId);
        Player actor = actorId == null ? null : Bukkit.getPlayer(actorId);
        String playerName = actor == null ? "-" : actor.getName();
        String playerUuid = actorId == null ? "-" : actorId.toString();
        return command
                .replace("{town_id}", Integer.toString(townId))
                .replace("{town_name}", townName)
                .replace("{player_name}", playerName)
                .replace("{player_uuid}", playerUuid)
                .replace("{story_chapter}", definition.id())
                .replace("{story_chapter_title}", definition.title());
    }

    private ChallengeRewardPreview toRewardPreview(ChallengeRewardSpec spec) {
        if (spec == null || spec.empty()) {
            return ChallengeRewardPreview.EMPTY;
        }
        spec = economyValueService.effectiveRewardSpec(spec, EconomyBalanceCategory.MILESTONE_MONEY_REWARD);
        int commandCount = spec.consoleCommands() == null ? 0 : (int) spec.consoleCommands().stream()
                .filter(value -> value != null && !value.isBlank())
                .count();
        List<String> commandKeys = spec.commandKeys() == null ? List.of() : spec.commandKeys().stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
        List<ChallengeRewardPreview.RewardItem> rewardItems = spec.vaultItems() == null
                ? List.of()
                : spec.vaultItems().entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0)
                .sorted(Comparator.comparing(entry -> entry.getKey().name()))
                .map(entry -> new ChallengeRewardPreview.RewardItem(entry.getKey(), entry.getValue()))
                .toList();
        int itemAmount = rewardItems.stream().mapToInt(ChallengeRewardPreview.RewardItem::amount).sum();
        return new ChallengeRewardPreview(
                Math.max(0.0D, spec.xpCity()),
                Math.max(0.0D, spec.moneyCity()),
                0.0D,
                commandCount,
                List.copyOf(commandKeys),
                List.copyOf(rewardItems),
                rewardItems.size(),
                Math.max(0, itemAmount)
        );
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static String summaryGrantKey(int townId, String chapterId) {
        return "story-mode:" + townId + ':' + chapterId + ":claimed";
    }
}
