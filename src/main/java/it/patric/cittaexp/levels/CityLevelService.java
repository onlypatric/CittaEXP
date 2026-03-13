package it.patric.cittaexp.levels;

import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.utils.PluginConfigUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
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
            TownStage stage,
            int claimCap,
            int memberCap,
            int spawnerCap,
            TownStage nextStage,
            int nextRequiredLevel,
            double requiredBalance,
            double upgradeCost,
            boolean staffApprovalRequired
    ) {
    }

    public record UpgradeAttempt(boolean success, String reason, TownStage upgradedStage, LevelStatus status) {
    }

    public record SpawnPrivacyToggleResult(boolean success, String reason, Boolean isPublic) {
    }

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final CityLevelStore store;
    private final CityLevelSettings settings;
    private final CityXpScanService scanService;
    private final PluginConfigUtils configUtils;

    public CityLevelService(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            CityLevelStore store,
            CityLevelSettings settings,
            CityXpScanService scanService
    ) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.store = store;
        this.settings = settings;
        this.scanService = scanService;
        this.configUtils = new PluginConfigUtils(plugin);
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
        Optional<Town> town = huskTownsApiHook.getTownById(townId);
        String townName = town.map(Town::getName).orElse("Sconosciuta");
        int claimCap = resolveClaimCap(progression.stage(), progression.cityLevel());
        int memberCap = resolveMemberCap(progression.stage());
        int spawnerCap = resolveSpawnerCap(progression.stage());
        TownStage next = progression.stage().next().orElse(null);
        CityLevelSettings.StageSpec nextSpec = next == null ? null : settings.spec(next);
        double requiredBalance = nextSpec == null ? 0.0D : nextSpec.upgradeCost() * settings.upgradeBalanceMultiplier();
        return new LevelStatus(
                townId,
                townName,
                progression.xpScaled(),
                toXp(progression.xpScaled()),
                progression.cityLevel(),
                progression.stage(),
                claimCap,
                memberCap,
                spawnerCap,
                next,
                nextSpec == null ? 0 : nextSpec.requiredLevel(),
                requiredBalance,
                nextSpec == null ? 0.0D : nextSpec.upgradeCost(),
                nextSpec != null && nextSpec.staffApprovalRequired()
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
        LevelStatus status = statusForTown(town.getId(), false);
        TownStage next = status.nextStage();
        if (next == null) {
            return new UpgradeAttempt(false, "already-max-stage", null, status);
        }
        CityLevelSettings.StageSpec nextSpec = settings.spec(next);
        if (status.level() < nextSpec.requiredLevel()) {
            return new UpgradeAttempt(false, "level-too-low", null, status);
        }
        if (requiresTownSpawn(next) && town.getSpawn().isEmpty()) {
            return new UpgradeAttempt(false, "town-spawn-required", null, status);
        }

        double townMoney = town.getMoney().doubleValue();
        if (townMoney < status.requiredBalance()) {
            return new UpgradeAttempt(false, "insufficient-balance-requirement", null, status);
        }
        if (nextSpec.staffApprovalRequired()) {
            return new UpgradeAttempt(false, "staff-approval-required", null, status);
        }

        UpgradeAttempt applied = applyStageUpgrade(player, town.getId(), next, "manual-upgrade");
        if (!applied.success()) {
            return applied;
        }
        return new UpgradeAttempt(true, "upgraded", next, statusForTown(town.getId(), false));
    }

    public UpgradeAttempt applyStageUpgrade(Player actor, int townId, TownStage targetStage, String source) {
        Optional<Town> townOptional = huskTownsApiHook.getTownById(townId);
        if (townOptional.isEmpty()) {
            return new UpgradeAttempt(false, "town-not-found", null, null);
        }
        Town town = townOptional.get();

        TownProgression progression = store.getOrCreateProgression(townId);
        Optional<TownStage> expectedNext = progression.stage().next();
        if (expectedNext.isEmpty() || expectedNext.get() != targetStage) {
            return new UpgradeAttempt(false, "invalid-stage-transition", null, statusForTown(townId, false));
        }
        CityLevelSettings.StageSpec targetSpec = settings.spec(targetStage);
        if (progression.cityLevel() < targetSpec.requiredLevel()) {
            return new UpgradeAttempt(false, "level-too-low", null, statusForTown(townId, false));
        }
        if (requiresTownSpawn(targetStage) && town.getSpawn().isEmpty()) {
            return new UpgradeAttempt(false, "town-spawn-required", null, statusForTown(townId, false));
        }

        double requiredBalance = targetSpec.upgradeCost() * settings.upgradeBalanceMultiplier();
        double currentMoney = town.getMoney().doubleValue();
        if (currentMoney < requiredBalance) {
            return new UpgradeAttempt(false, "insufficient-balance-requirement", null, statusForTown(townId, false));
        }

        try {
            huskTownsApiHook.editTown(actor, townId, mutableTown -> {
                BigDecimal money = mutableTown.getMoney();
                BigDecimal cost = BigDecimal.valueOf(targetSpec.upgradeCost());
                if (money.compareTo(BigDecimal.valueOf(requiredBalance)) < 0) {
                    throw new IllegalStateException("insufficient-balance-requirement");
                }
                mutableTown.setMoney(money.subtract(cost));
                mutableTown.setLevel(targetSpec.huskTownLevel());
                int targetClaimCap = resolveClaimCap(targetStage, progression.cityLevel());
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

        store.updateStage(townId, targetStage);
        syncTownCaps(townId);
        enforceSpawnPrivacyPolicy(townId);
        broadcastStageUpgrade(townId, targetStage);
        return new UpgradeAttempt(true, "upgraded", targetStage, statusForTown(townId, false));
    }

    public CityScanResult applyScanAndNotify(int townId) {
        CityScanResult scan = scanService.scanTown(townId);
        if (scan.error() != null) {
            return scan;
        }
        syncTownCaps(townId);
        enforceSpawnPrivacyPolicy(townId);
        if (scan.currentLevel() > scan.previousLevel()) {
            broadcastLevelUps(townId, scan.previousLevel(), scan.currentLevel());
        }
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
        if (result.currentLevel() > result.previousLevel()) {
            broadcastLevelUps(townId, result.previousLevel(), result.currentLevel());
        }
        return result;
    }

    public int claimCapForTown(int townId) {
        TownProgression progression = store.getOrCreateProgression(townId);
        return resolveClaimCap(progression.stage(), progression.cityLevel());
    }

    public int memberCapForTown(int townId) {
        TownProgression progression = store.getOrCreateProgression(townId);
        return resolveMemberCap(progression.stage());
    }

    public int spawnerCapForTown(int townId) {
        TownProgression progression = store.getOrCreateProgression(townId);
        return resolveSpawnerCap(progression.stage());
    }

    public boolean canToggleSpawnPrivacy(int townId) {
        CityLevelSettings.SpawnPrivacyPolicy policy = settings.spawnPrivacyPolicy();
        if (!policy.enabled()) {
            return true;
        }
        if (!policy.allowBorgoToggle()) {
            return false;
        }
        TownStage stage = store.getOrCreateProgression(townId).stage();
        return stage.ordinal() < policy.forcePublicFromStage().ordinal();
    }

    public boolean shouldForcePublicSpawnPrivacy(int townId) {
        CityLevelSettings.SpawnPrivacyPolicy policy = settings.spawnPrivacyPolicy();
        if (!policy.enabled()) {
            return false;
        }
        TownStage stage = store.getOrCreateProgression(townId).stage();
        return stage.ordinal() >= policy.forcePublicFromStage().ordinal();
    }

    public SpawnPrivacyToggleResult toggleSpawnPrivacy(Player actor) {
        Optional<Member> member = huskTownsApiHook.getUserTown(actor);
        if (member.isEmpty()) {
            return new SpawnPrivacyToggleResult(false, "no-town", null);
        }
        int townId = member.get().town().getId();
        if (shouldForcePublicSpawnPrivacy(townId) || !canToggleSpawnPrivacy(townId)) {
            return new SpawnPrivacyToggleResult(false, "privacy-policy-locked", true);
        }
        if (member.get().town().getSpawn().isEmpty()) {
            return new SpawnPrivacyToggleResult(false, "town-spawn-required", null);
        }
        if (!huskTownsApiHook.hasPrivilege(actor, net.william278.husktowns.town.Privilege.SPAWN_PRIVACY)) {
            return new SpawnPrivacyToggleResult(false, "no-privacy-permission", null);
        }
        boolean target = !member.get().town().getSpawn().get().isPublic();
        try {
            huskTownsApiHook.setSpawnPrivacy(actor, target);
            return new SpawnPrivacyToggleResult(true, "updated", target);
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
        int targetClaimCap = resolveClaimCap(progression.stage(), progression.cityLevel());
        int targetMemberCap = resolveMemberCap(progression.stage());
        int currentClaimCap = town.getMaxClaims(huskTownsApiHook.plugin());
        int currentMemberCap = town.getMaxMembers(huskTownsApiHook.plugin());
        if (currentClaimCap == targetClaimCap && currentMemberCap == targetMemberCap) {
            return;
        }
        Optional<UUID> actorId = resolveActor(town);
        if (actorId.isEmpty()) {
            return;
        }
        try {
            huskTownsApiHook.editTown(actorId.get(), townId, mutableTown -> applyTownCaps(mutableTown, targetClaimCap, targetMemberCap));
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
        if (stage != TownStage.REGNO_I) {
            return spec.claimCap();
        }
        int extraUntilLevel = Math.min(
                settings.levelCap(),
                Math.min(settings.regnoExtraClaimMaxLevel(), REGNO_EXTRA_CLAIM_HARD_MAX_LEVEL)
        );
        int extraLevels = Math.max(0, Math.min(cityLevel, extraUntilLevel) - TownStage.REGNO_I.requiredLevel());
        return spec.claimCap() + extraLevels;
    }

    private int resolveMemberCap(TownStage stage) {
        return settings.spec(stage).memberCap();
    }

    private int resolveSpawnerCap(TownStage stage) {
        return settings.spec(stage).spawnerCap();
    }

    private boolean requiresTownSpawn(TownStage targetStage) {
        return targetStage == TownStage.VILLAGGIO_I;
    }

    private void broadcastLevelUps(int townId, int fromExclusive, int toInclusive) {
        Optional<Town> town = huskTownsApiHook.getTownById(townId);
        if (town.isEmpty()) {
            return;
        }

        for (int level = fromExclusive + 1; level <= toInclusive; level++) {
            for (UUID memberId : town.get().getMembers().keySet()) {
                Player online = Bukkit.getPlayer(memberId);
                if (online == null) {
                    continue;
                }
                online.sendMessage(configUtils.msg(
                        "city.level.notifications.level_up",
                        "<green>La tua citta e salita al livello</green> <yellow>{level}</yellow>.",
                        Placeholder.unparsed("level", Integer.toString(level))
                ));
                if (level % 5 == 0) {
                    Title title = Title.title(
                            configUtils.msg(
                                    "city.level.notifications.title_every_5.title",
                                    "<gold>Citta Livello {level}</gold>",
                                    Placeholder.unparsed("level", Integer.toString(level))
                            ),
                            configUtils.msg(
                                    "city.level.notifications.title_every_5.subtitle",
                                    "<yellow>Traguardo raggiunto!</yellow>",
                                    Placeholder.unparsed("level", Integer.toString(level))
                            )
                    );
                    online.showTitle(title);
                }
            }
        }
    }

    private void broadcastStageUpgrade(int townId, TownStage stage) {
        Optional<Town> town = huskTownsApiHook.getTownById(townId);
        if (town.isEmpty()) {
            return;
        }
        for (UUID memberId : town.get().getMembers().keySet()) {
            Player online = Bukkit.getPlayer(memberId);
            if (online == null) {
                continue;
            }
            online.sendMessage(configUtils.msg(
                    "city.level.notifications.stage_upgraded",
                    "<gold>Upgrade citta completato:</gold> <yellow>{stage}</yellow>",
                    Placeholder.unparsed("stage", stage.displayName())
            ));
        }
    }

    public double toXp(long xpScaled) {
        return BigDecimal.valueOf(xpScaled)
                .divide(BigDecimal.valueOf(settings.xpScale()), 4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public CityLevelSettings settings() {
        return settings;
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
}
