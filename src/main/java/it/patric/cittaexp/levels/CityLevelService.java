package it.patric.cittaexp.levels;

import it.patric.cittaexp.challenges.ChallengeBroadcastFormatter;
import it.patric.cittaexp.defense.CityDefenseService;
import it.patric.cittaexp.defense.CityDefenseTier;
import it.patric.cittaexp.economy.EconomyBalanceCategory;
import it.patric.cittaexp.economy.EconomyValueService;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.text.MiniMessageHelper;
import it.patric.cittaexp.text.TimedTitleHelper;
import it.patric.cittaexp.utils.PluginConfigUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntConsumer;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.william278.husktowns.town.Member;
import net.william278.husktowns.town.Town;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class CityLevelService {
    private static final int REGNO_EXTRA_CLAIM_HARD_MAX_LEVEL = 250;

    public record LevelStatus(
            int townId,
            String townName,
            long xpScaled,
            double xp,
            int level,
            int earnedLevel,
            TownStage stage,
            PrincipalStage principalStage,
            SubTier subTier,
            int stageSealsEarned,
            int stageSealsRequired,
            int stageClaimCap,
            int adminClaimBonus,
            int claimCap,
            int memberCap,
            int spawnerCap,
            int shopCap,
            double currentBalance,
            TownStage nextStage,
            int nextRequiredLevel,
            double requiredBalance,
            double upgradeCost,
            double nextMonthlyTax,
            boolean staffApprovalRequired,
            double nextRequiredXp
    ) {
    }

    public record UpgradeAttempt(boolean success, String reason, TownStage upgradedStage, LevelStatus status) {
    }

    public record LevelUnlockCheck(
            int townId,
            String townName,
            int currentLevel,
            int earnedLevel,
            int targetLevel,
            double currentXp,
            double requiredXp,
            double currentBalance,
            boolean checkpoint,
            TownStage checkpointStage,
            boolean confirmationRequired,
            boolean staffApprovalRequired,
            double requiredBalance,
            double upgradeCost,
            double monthlyTax,
            boolean spawnRequired,
            boolean spawnPresent,
            CityDefenseTier requiredDefenseTier,
            boolean defenseTierCompleted,
            boolean xpReached,
            boolean bankBalanceSatisfied,
            boolean canRequestStaffApproval,
            boolean canUnlockNow,
            String blockingReason
    ) {
    }

    public record SpawnPrivacyToggleResult(boolean success, String reason, Boolean isPublic) {
    }

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final CityLevelStore store;
    private final CityLevelSettings settings;
    private final CityXpScanService scanService;
    private final EconomyValueService economyValueService;
    private final PluginConfigUtils configUtils;
    private final AdminClaimBonusService adminClaimBonusService;
    private CityDefenseService cityDefenseService;
    private IntConsumer upgradeAvailabilityListener;

    public CityLevelService(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            CityLevelStore store,
            CityLevelSettings settings,
            CityXpScanService scanService,
            EconomyValueService economyValueService,
            AdminClaimBonusService adminClaimBonusService
    ) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.store = store;
        this.settings = settings;
        this.scanService = scanService;
        this.economyValueService = economyValueService;
        this.configUtils = new PluginConfigUtils(plugin);
        this.adminClaimBonusService = adminClaimBonusService;
    }

    public void setDefenseService(CityDefenseService cityDefenseService) {
        this.cityDefenseService = cityDefenseService;
    }

    public Optional<LevelStatus> statusForPlayer(Player player, boolean runScan) {
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(statusForTown(member.get().town().getId(), runScan));
    }

    public LevelStatus statusForTown(int townId, boolean runScan) {
        if (runScan) {
            applyScanAndNotify(townId);
        }
        TownProgression progression = store.getOrCreateProgression(townId);
        int earnedLevel = earnedLevelForXp(progression.xpScaled());
        Optional<Town> town = huskTownsApiHook.getTownById(townId);
        String townName = town.map(Town::getName).orElse("Sconosciuta");
        double currentBalance = town.map(value -> value.getMoney().doubleValue()).orElse(0.0D);
        int stageClaimCap = resolveClaimCap(progression.stage(), progression.cityLevel());
        int adminClaimBonus = adminClaimBonusService.getBonus(townId);
        int claimCap = stageClaimCap + adminClaimBonus;
        int memberCap = resolveMemberCap(progression.stage());
        int spawnerCap = resolveSpawnerCap(progression.stage());
        int shopCap = resolveShopCap(progression.stage());
        int nextLevel = progression.cityLevel() >= settings.levelCap() ? 0 : progression.cityLevel() + 1;
        TownStage next = checkpointStageAtLevel(nextLevel);
        CityLevelSettings.StageSpec nextSpec = next == null ? null : settings.spec(next);
        double requiredBalance = next == null ? 0.0D : effectiveRequiredBalance(next);
        double nextRequiredXp = nextLevel <= 0 ? toXp(progression.xpScaled()) : toXp(CityLevelCurve.requiredXpScaledForLevel(
                nextLevel,
                settings.xpPerLevel(),
                settings.xpScale(),
                settings.curveExponent()
        ));
        return new LevelStatus(
                townId,
                townName,
                progression.xpScaled(),
                toXp(progression.xpScaled()),
                progression.cityLevel(),
                earnedLevel,
                progression.stage(),
                progression.stage().principalStage(),
                progression.stage().subTier(),
                progression.stageSealsEarned(),
                progression.stageSealsRequired(),
                stageClaimCap,
                adminClaimBonus,
                claimCap,
                memberCap,
                spawnerCap,
                shopCap,
                currentBalance,
                next,
                nextSpec == null ? 0 : nextSpec.requiredLevel(),
                requiredBalance,
                next == null ? 0.0D : effectiveUpgradeCost(next),
                next == null ? 0.0D : effectiveMonthlyTax(next),
                nextSpec != null && nextSpec.staffApprovalRequired(),
                nextRequiredXp
        );
    }

    public UpgradeAttempt tryUpgrade(Player player) {
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty()) {
            return new UpgradeAttempt(false, "no-town", null, null);
        }
        Town town = member.get().town();
        if (!town.getMayor().equals(player.getUniqueId())) {
            return new UpgradeAttempt(false, "not-mayor", null, null);
        }

        applyScanAndNotify(town.getId());
        LevelUnlockCheck check = nextUnlockCheck(town.getId(), false);
        LevelStatus status = statusForTown(town.getId(), false);
        if (check.targetLevel() <= 0) {
            return new UpgradeAttempt(false, check.blockingReason(), null, status);
        }
        if (check.staffApprovalRequired()) {
            return new UpgradeAttempt(false, "staff-approval-required", null, status);
        }
        if (!check.canUnlockNow()) {
            return new UpgradeAttempt(false, check.blockingReason(), null, status);
        }
        if (check.checkpoint() && check.checkpointStage() != null) {
            return applyStageUpgrade(player, town.getId(), check.checkpointStage(), "manual-upgrade");
        }
        store.unlockLevel(town.getId(), check.targetLevel());
        syncTownCaps(town.getId());
        enforceSpawnPrivacyPolicy(town.getId());
        broadcastLevelUps(town.getId(), check.currentLevel(), check.targetLevel());
        notifyUpgradeAvailabilityChanged(town.getId());
        return new UpgradeAttempt(true, "upgraded", null, statusForTown(town.getId(), false));
    }

    public UpgradeAttempt applyStageUpgrade(Player actor, int townId, TownStage targetStage, String source) {
        Optional<Town> townOptional = huskTownsApiHook.getTownById(townId);
        if (townOptional.isEmpty()) {
            return new UpgradeAttempt(false, "town-not-found", null, null);
        }
        Town town = townOptional.get();

        TownProgression progression = store.getOrCreateProgression(townId);
        int targetLevel = settings.spec(targetStage).requiredLevel();
        if (progression.cityLevel() + 1 != targetLevel || checkpointStageAtLevel(targetLevel) != targetStage) {
            return new UpgradeAttempt(false, "invalid-stage-transition", null, statusForTown(townId, false));
        }
        LevelUnlockCheck check = checkLevelUnlock(townId, targetLevel, false);
        if (!check.checkpoint()) {
            return new UpgradeAttempt(false, "invalid-stage-transition", null, statusForTown(townId, false));
        }
        if (!check.xpReached()) {
            return new UpgradeAttempt(false, "level-too-low", null, statusForTown(townId, false));
        }
        if (!check.spawnPresent()) {
            return new UpgradeAttempt(false, "town-spawn-required", null, statusForTown(townId, false));
        }
        if (!check.defenseTierCompleted()) {
            return new UpgradeAttempt(false, "defense-tier-required", null, statusForTown(townId, false));
        }
        if (!check.bankBalanceSatisfied()) {
            return new UpgradeAttempt(false, "insufficient-balance-requirement", null, statusForTown(townId, false));
        }

        try {
            huskTownsApiHook.editTown(actor, townId, mutableTown -> {
                BigDecimal money = mutableTown.getMoney();
                BigDecimal cost = BigDecimal.valueOf(check.upgradeCost());
                if (money.compareTo(BigDecimal.valueOf(check.requiredBalance())) < 0) {
                    throw new IllegalStateException("insufficient-balance-requirement");
                }
                mutableTown.setMoney(money.subtract(cost));
                mutableTown.setLevel(settings.spec(targetStage).huskTownLevel());
                int targetClaimCap = resolveClaimCap(targetStage, targetLevel);
                int targetMemberCap = resolveMemberCap(targetStage);
                applyTownCaps(mutableTown, targetClaimCap, targetMemberCap);
            });
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("[levels] upgrade failed town=" + townId
                    + " target=" + targetStage.name()
                    + " source=" + source
                    + " reason=" + exception.getMessage());
            return new UpgradeAttempt(false, "town-update-failed", null, statusForTown(townId, false));
        }

        store.unlockLevelAndStage(townId, targetLevel, targetStage);
        syncTownCaps(townId);
        enforceSpawnPrivacyPolicy(townId);
        broadcastLevelUps(townId, progression.cityLevel(), targetLevel);
        broadcastStageUpgrade(townId, targetStage);
        notifyUpgradeAvailabilityChanged(townId);
        return new UpgradeAttempt(true, "upgraded", targetStage, statusForTown(townId, false));
    }

    public CityScanResult applyScanAndNotify(int townId) {
        CityScanResult scan = scanService.scanTown(townId);
        if (scan.error() != null) {
            return scan;
        }
        syncTownCaps(townId);
        enforceSpawnPrivacyPolicy(townId);
        notifyUpgradeAvailabilityChanged(townId);
        return scan;
    }

    public CityScanResult grantXpBonus(int townId, long deltaXpScaled, UUID actor, String reason) {
        if (deltaXpScaled <= 0L) {
            TownProgression progression = store.getOrCreateProgression(townId);
            return CityScanResult.noChange(townId, progression.cityLevel(), progression.xpScaled());
        }
        CityScanResult result = store.applyScanAward(
                townId,
                deltaXpScaled,
                Map.of(),
                settings.xpPerLevel() * settings.xpScale(),
                settings.levelCap(),
                null,
                "xp_bonus",
                actor,
                reason == null ? "staff-xp-bonus" : reason
        );
        notifyUpgradeAvailabilityChanged(townId);
        return result;
    }

    public LevelStatus setTownXp(int townId, long targetXpScaled, UUID actor, String reason) {
        TownProgression before = store.getOrCreateProgression(townId);
        TownProgression updated = store.setXpScaled(
                townId,
                targetXpScaled,
                "xp_admin_set",
                actor,
                reason == null ? "admin-xp-set" : reason
        );
        syncTownCaps(townId);
        enforceSpawnPrivacyPolicy(townId);
        notifyUpgradeAvailabilityChanged(townId);
        return statusForTown(townId, false);
    }

    public LevelStatus addTownXp(int townId, long deltaXpScaled, UUID actor, String reason) {
        grantXpBonus(townId, deltaXpScaled, actor, reason == null ? "admin-xp-add" : reason);
        return statusForTown(townId, false);
    }

    public LevelStatus takeTownXp(int townId, long deltaXpScaled, UUID actor, String reason) {
        TownProgression current = store.getOrCreateProgression(townId);
        long target = Math.max(0L, current.xpScaled() - Math.max(0L, deltaXpScaled));
        return setTownXp(townId, target, actor, reason == null ? "admin-xp-take" : reason);
    }

    public LevelUnlockCheck nextUnlockCheck(int townId, boolean runScan) {
        LevelStatus status = statusForTown(townId, runScan);
        if (status.level() >= settings.levelCap()) {
            return new LevelUnlockCheck(
                    status.townId(),
                    status.townName(),
                    status.level(),
                    status.earnedLevel(),
                    0,
                    status.xp(),
                    status.nextRequiredXp(),
                    status.currentBalance(),
                    false,
                    null,
                    false,
                    false,
                    0.0D,
                    0.0D,
                    0.0D,
                    false,
                    true,
                    null,
                    true,
                    true,
                    true,
                    false,
                    false,
                    "already-max-level"
            );
        }
        return checkLevelUnlock(townId, status.level() + 1, false);
    }

    public LevelUnlockCheck checkLevelUnlock(int townId, int targetLevel, boolean runScan) {
        LevelStatus status = statusForTown(townId, runScan);
        Optional<Town> townOptional = huskTownsApiHook.getTownById(townId);
        double currentBalance = townOptional.map(value -> value.getMoney().doubleValue()).orElse(0.0D);
        if (targetLevel <= 0 || targetLevel > settings.levelCap()) {
            return new LevelUnlockCheck(
                    status.townId(), status.townName(), status.level(), status.earnedLevel(), targetLevel,
                    status.xp(), 0.0D, currentBalance, false, null, false, false,
                    0.0D, 0.0D, 0.0D, false, true, null, true, false, false, false, false, "invalid-level-target"
            );
        }
        if (targetLevel != status.level() + 1) {
            return new LevelUnlockCheck(
                    status.townId(), status.townName(), status.level(), status.earnedLevel(), targetLevel,
                    status.xp(), requiredXpForLevel(targetLevel), currentBalance, checkpointStageAtLevel(targetLevel) != null,
                    checkpointStageAtLevel(targetLevel), true, false, 0.0D, 0.0D, 0.0D, false, true, null,
                    status.earnedLevel() >= targetLevel, true, false, false, false, "unlock-previous-level-first"
            );
        }

        TownStage checkpointStage = checkpointStageAtLevel(targetLevel);
        CityLevelSettings.StageSpec stageSpec = checkpointStage == null ? null : settings.spec(checkpointStage);
        CityLevelSettings.LevelSpec levelSpec = settings.levelSpec(targetLevel);
        double requiredXp = requiredXpForLevel(targetLevel);
        boolean xpReached = status.earnedLevel() >= targetLevel;

        double requiredBalance = checkpointStage == null ? 0.0D : effectiveRequiredBalance(checkpointStage);
        boolean spawnRequired = checkpointStage != null && requiresTownSpawn(checkpointStage);
        boolean staffApprovalRequired = checkpointStage != null && stageSpec.staffApprovalRequired();
        CityDefenseTier requiredDefenseTier = checkpointStage == null ? null : stageSpec.requiredDefenseTier();

        for (CityLevelSettings.LevelRequirementSpec requirement : levelSpec.requirements()) {
            if (requirement == null || !requirement.required()) {
                continue;
            }
            switch (requirement.type()) {
                case BANK_BALANCE_MIN -> requiredBalance = Math.max(requiredBalance, requirement.amount());
                case TOWN_SPAWN_REQUIRED -> spawnRequired = true;
                case DEFENSE_TIER_COMPLETED -> {
                    if (requiredDefenseTier == null && requirement.defenseTier() != null) {
                        requiredDefenseTier = requirement.defenseTier();
                    }
                }
                case STAFF_APPROVAL_REQUIRED -> staffApprovalRequired = true;
                case XP_REACHED -> {
                    // always enforced
                }
            }
        }

        boolean spawnPresent = !spawnRequired || townOptional.flatMap(Town::getSpawn).isPresent();
        boolean defenseCompleted = requiredDefenseTier == null
                || (cityDefenseService != null && cityDefenseService.hasCompletedTier(townId, requiredDefenseTier));
        boolean bankSatisfied = currentBalance >= requiredBalance;
        String blockingReason = null;
        if (!xpReached) {
            blockingReason = "level-too-low";
        } else if (!spawnPresent) {
            blockingReason = "town-spawn-required";
        } else if (!defenseCompleted) {
            blockingReason = "defense-tier-required";
        } else if (!bankSatisfied) {
            blockingReason = "insufficient-balance-requirement";
        }

        boolean canRequestStaffApproval = blockingReason == null && staffApprovalRequired;
        boolean canUnlockNow = blockingReason == null && !staffApprovalRequired;

        return new LevelUnlockCheck(
                status.townId(),
                status.townName(),
                status.level(),
                status.earnedLevel(),
                targetLevel,
                status.xp(),
                requiredXp,
                currentBalance,
                checkpointStage != null,
                checkpointStage,
                levelSpec.confirmationRequired(),
                staffApprovalRequired,
                requiredBalance,
                checkpointStage == null ? 0.0D : effectiveUpgradeCost(checkpointStage),
                checkpointStage == null ? 0.0D : effectiveMonthlyTax(checkpointStage),
                spawnRequired,
                spawnPresent,
                requiredDefenseTier,
                defenseCompleted,
                xpReached,
                bankSatisfied,
                canRequestStaffApproval,
                canUnlockNow,
                blockingReason
        );
    }

    public double effectiveUpgradeCost(TownStage stage) {
        if (stage == null) {
            return 0.0D;
        }
        CityLevelSettings.StageSpec spec = settings.spec(stage);
        return economyValueService.effectiveAmount(EconomyBalanceCategory.TOWN_STAGE_UPGRADE, spec.upgradeCost());
    }

    public double effectiveMonthlyTax(TownStage stage) {
        if (stage == null) {
            return 0.0D;
        }
        CityLevelSettings.StageSpec spec = settings.spec(stage);
        return economyValueService.effectiveAmount(EconomyBalanceCategory.TOWN_MONTHLY_TAX, spec.monthlyTax());
    }

    public double effectiveRequiredBalance(TownStage stage) {
        if (stage == null) {
            return 0.0D;
        }
        double nominal = settings.spec(stage).upgradeCost() * settings.upgradeBalanceMultiplier();
        return economyValueService.effectiveAmount(EconomyBalanceCategory.TOWN_STAGE_REQUIRED_BALANCE, nominal);
    }

    public int claimCapForTown(int townId) {
        TownProgression progression = store.getOrCreateProgression(townId);
        return resolveClaimCap(progression.stage(), progression.cityLevel()) + adminClaimBonusService.getBonus(townId);
    }

    public int baseClaimCapForTown(int townId) {
        TownProgression progression = store.getOrCreateProgression(townId);
        return resolveClaimCap(progression.stage(), progression.cityLevel());
    }

    public int adminClaimBonusForTown(int townId) {
        return adminClaimBonusService.getBonus(townId);
    }

    public Optional<ClaimCapSnapshot> claimCapSnapshot(int townId) {
        Optional<Town> town = huskTownsApiHook.getTownById(townId);
        if (town.isEmpty()) {
            return Optional.empty();
        }
        LevelStatus status = statusForTown(townId, false);
        return Optional.of(new ClaimCapSnapshot(
                townId,
                town.get().getName(),
                town.get().getClaimCount(),
                status.stageClaimCap(),
                status.adminClaimBonus(),
                status.claimCap()
        ));
    }

    public Optional<ClaimCapSnapshot> setAdminClaimBonus(int townId, int amount) {
        adminClaimBonusService.setBonus(townId, amount);
        syncTownCaps(townId);
        return claimCapSnapshot(townId);
    }

    public Optional<ClaimCapSnapshot> giveAdminClaimBonus(int townId, int amount) {
        adminClaimBonusService.addBonus(townId, amount);
        syncTownCaps(townId);
        return claimCapSnapshot(townId);
    }

    public Optional<ClaimCapSnapshot> takeAdminClaimBonus(int townId, int amount) {
        adminClaimBonusService.takeBonus(townId, amount);
        syncTownCaps(townId);
        return claimCapSnapshot(townId);
    }

    public void syncAllTownCaps() {
        for (Town town : huskTownsApiHook.getTowns()) {
            syncTownCaps(town.getId());
        }
    }

    public int memberCapForTown(int townId) {
        TownProgression progression = store.getOrCreateProgression(townId);
        return resolveMemberCap(progression.stage());
    }

    public int spawnerCapForTown(int townId) {
        TownProgression progression = store.getOrCreateProgression(townId);
        return resolveSpawnerCap(progression.stage());
    }

    public int shopCapForTown(int townId) {
        TownProgression progression = store.getOrCreateProgression(townId);
        return resolveShopCap(progression.stage());
    }

    public boolean canToggleSpawnPrivacy(int townId) {
        return false;
    }

    public boolean shouldForcePublicSpawnPrivacy(int townId) {
        return true;
    }

    public SpawnPrivacyToggleResult toggleSpawnPrivacy(Player actor) {
        Optional<Member> member = huskTownsApiHook.getUserTown(actor);
        if (member.isEmpty()) {
            return new SpawnPrivacyToggleResult(false, "no-town", null);
        }
        if (member.get().town().getSpawn().isEmpty()) {
            return new SpawnPrivacyToggleResult(false, "town-spawn-required", null);
        }
        int townId = member.get().town().getId();
        if (member.get().town().getSpawn().get().isPublic()) {
            return new SpawnPrivacyToggleResult(true, "already-public", true);
        }
        try {
            huskTownsApiHook.setSpawnPrivacy(actor, true);
            return new SpawnPrivacyToggleResult(true, "updated", true);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("[levels] spawn privacy toggle failed town=" + townId
                    + " actor=" + actor.getUniqueId()
                    + " reason=" + ex.getMessage());
            return new SpawnPrivacyToggleResult(false, "town-update-failed", null);
        }
    }

    public boolean enforceSpawnPrivacyPolicy(int townId) {
        if (!shouldForcePublicSpawnPrivacy(townId)) {
            return false;
        }
        Optional<Town> townOptional = huskTownsApiHook.getTownById(townId);
        if (townOptional.isEmpty()) {
            return false;
        }
        Town town = townOptional.get();
        if (town.getSpawn().isEmpty() || town.getSpawn().get().isPublic()) {
            return false;
        }
        Optional<UUID> actorId = resolveActor(town);
        if (actorId.isEmpty()) {
            return false;
        }
        try {
            huskTownsApiHook.editTown(actorId.get(), townId, mutableTown -> mutableTown.getSpawn().ifPresent(spawn -> {
                if (!spawn.isPublic()) {
                    spawn.setPublic(true);
                    mutableTown.setSpawn(spawn);
                }
            }));
            return true;
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("[levels] spawn privacy enforcement failed town=" + townId
                    + " reason=" + ex.getMessage());
            return false;
        }
    }

    private void syncTownCaps(int townId) {
        Optional<Town> townOptional = huskTownsApiHook.getTownById(townId);
        if (townOptional.isEmpty()) {
            return;
        }
        Town town = townOptional.get();
        TownProgression progression = store.getOrCreateProgression(townId);
        int targetClaimCap = resolveClaimCap(progression.stage(), progression.cityLevel()) + adminClaimBonusService.getBonus(townId);
        int targetMemberCap = resolveMemberCap(progression.stage());
        int currentClaimCap = town.getMaxClaims(huskTownsApiHook.plugin());
        int currentMemberCap = town.getMaxMembers(huskTownsApiHook.plugin());
        if (currentClaimCap == targetClaimCap && currentMemberCap == targetMemberCap) {
            return;
        }
        Optional<UUID> actorId = resolveActor(town);
        try {
            if (actorId.isPresent()) {
                huskTownsApiHook.editTown(actorId.get(), townId, mutableTown -> applyTownCaps(mutableTown, targetClaimCap, targetMemberCap));
            } else {
                applyTownCaps(town, targetClaimCap, targetMemberCap);
                huskTownsApiHook.updateTownDirect(town);
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("[levels] cap sync failed town=" + townId + " reason=" + exception.getMessage());
        }
    }

    private void applyTownCaps(Town town, int targetClaimCap, int targetMemberCap) {
        town.setBonusClaims(0);
        town.setBonusMembers(0);
        int baseClaims = town.getMaxClaims(huskTownsApiHook.plugin());
        int baseMembers = town.getMaxMembers(huskTownsApiHook.plugin());
        town.setBonusClaims(Math.max(0, targetClaimCap - baseClaims));
        town.setBonusMembers(Math.max(0, targetMemberCap - baseMembers));
    }

    private int resolveClaimCap(TownStage stage, int cityLevel) {
        CityLevelSettings.StageSpec spec = settings.spec(stage);
        if (!(stage.principalStage() == PrincipalStage.REGNO && stage.subTier() == SubTier.III)) {
            return spec.claimCap();
        }
        int extraUntilLevel = Math.min(
                settings.levelCap(),
                Math.min(settings.regnoExtraClaimMaxLevel(), REGNO_EXTRA_CLAIM_HARD_MAX_LEVEL)
        );
        int extraLevels = Math.max(0, Math.min(cityLevel, extraUntilLevel) - TownStage.REGNO_III.requiredLevel());
        return spec.claimCap() + extraLevels;
    }

    private int resolveMemberCap(TownStage stage) {
        return settings.spec(stage).memberCap();
    }

    private int resolveSpawnerCap(TownStage stage) {
        return settings.spec(stage).spawnerCap();
    }

    private int resolveShopCap(TownStage stage) {
        return settings.spec(stage).shopCap();
    }

    private int earnedLevelForXp(long xpScaled) {
        return CityLevelCurve.levelFromXpScaled(
                xpScaled,
                settings.levelCap(),
                settings.xpPerLevel(),
                settings.xpScale(),
                settings.curveExponent()
        );
    }

    private double requiredXpForLevel(int level) {
        return toXp(CityLevelCurve.requiredXpScaledForLevel(
                level,
                settings.xpPerLevel(),
                settings.xpScale(),
                settings.curveExponent()
        ));
    }

    private TownStage checkpointStageAtLevel(int level) {
        if (level <= 0) {
            return null;
        }
        for (TownStage stage : TownStage.values()) {
            if (settings.spec(stage).requiredLevel() == level) {
                return stage;
            }
        }
        return null;
    }

    private boolean requiresTownSpawn(TownStage targetStage) {
        return targetStage.principalStage() == PrincipalStage.VILLAGGIO && targetStage.subTier() == SubTier.I;
    }

    public boolean grantSealMilestone(int townId, String milestoneKey) {
        if (milestoneKey == null || milestoneKey.isBlank()) {
            return false;
        }
        if (store.hasSealMilestone(townId, milestoneKey)) {
            return false;
        }
        TownProgression before = store.getOrCreateProgression(townId);
        TownProgression updated = store.addStageSeals(townId, 1, milestoneKey);
        if (updated.stageSealsEarned() > before.stageSealsEarned()) {
            syncTownCaps(townId);
            enforceSpawnPrivacyPolicy(townId);
            return true;
        }
        return false;
    }

    public TownProgression syncStageSealsFromAtlas(int townId, int seals) {
        TownProgression before = store.getOrCreateProgression(townId);
        TownProgression updated = store.setStageSeals(townId, seals, true);
        if (updated.stageSealsEarned() != before.stageSealsEarned()) {
            syncTownCaps(townId);
            enforceSpawnPrivacyPolicy(townId);
            notifyUpgradeAvailabilityChanged(townId);
        }
        return updated;
    }

    private void broadcastLevelUps(int townId, int fromExclusive, int toInclusive) {
        Optional<Town> town = huskTownsApiHook.getTownById(townId);
        if (town.isEmpty()) {
            return;
        }
        String townName = town.get().getName();

        for (int level = fromExclusive + 1; level <= toInclusive; level++) {
            for (UUID memberId : town.get().getMembers().keySet()) {
                Player online = Bukkit.getPlayer(memberId);
                if (online == null) {
                    continue;
                }
                online.sendMessage(MiniMessageHelper.parse(
                        ChallengeBroadcastFormatter.renderCityLevelUp(townName, level)
                ));
                TimedTitleHelper.show(
                        online,
                        configUtils.msg(
                                "city.level.notifications.level_up.title",
                                "<gold><bold>Citta salita di livello</bold></gold>"
                        ),
                        configUtils.msg(
                                "city.level.notifications.level_up.subtitle",
                                "<yellow>Nuovo livello: {level}</yellow>",
                                Placeholder.unparsed("level", Integer.toString(level))
                        )
                );
            }
        }
    }

    private void broadcastStageUpgrade(int townId, TownStage stage) {
        Optional<Town> town = huskTownsApiHook.getTownById(townId);
        if (town.isEmpty()) {
            return;
        }
        String townName = town.get().getName();
        for (UUID memberId : town.get().getMembers().keySet()) {
            Player online = Bukkit.getPlayer(memberId);
            if (online == null) {
                continue;
            }
            online.sendMessage(MiniMessageHelper.parse(
                    ChallengeBroadcastFormatter.renderStageUpgrade(townName, stage)
            ));
        }
    }

    public double toXp(long xpScaled) {
        return BigDecimal.valueOf(xpScaled)
                .divide(BigDecimal.valueOf(settings.xpScale()), 4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public long toScaledXp(double xp) {
        if (xp <= 0.0D) {
            return 0L;
        }
        return BigDecimal.valueOf(xp)
                .multiply(BigDecimal.valueOf(settings.xpScale()))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    public CityLevelSettings settings() {
        return settings;
    }

    public void setUpgradeAvailabilityListener(IntConsumer upgradeAvailabilityListener) {
        this.upgradeAvailabilityListener = upgradeAvailabilityListener;
    }

    public record ClaimCapSnapshot(
            int townId,
            String townName,
            int currentClaims,
            int stageClaimCap,
            int adminClaimBonus,
            int effectiveClaimCap
    ) {
    }

    private Optional<UUID> resolveActor(Town town) {
        for (UUID memberId : town.getMembers().keySet()) {
            Player online = Bukkit.getPlayer(memberId);
            if (online != null) {
                return Optional.of(memberId);
            }
        }
        return Bukkit.getOnlinePlayers().stream()
                .findFirst()
                .map(Player::getUniqueId);
    }

    private void notifyUpgradeAvailabilityChanged(int townId) {
        IntConsumer listener = this.upgradeAvailabilityListener;
        if (listener != null && townId > 0) {
            listener.accept(townId);
        }
    }
}
