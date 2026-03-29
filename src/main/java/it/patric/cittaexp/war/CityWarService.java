package it.patric.cittaexp.war;

import dev.lone.itemsadder.api.CustomStack;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.utils.PluginConfigUtils;
import it.patric.cittaexp.vault.CityItemVaultService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import net.william278.husktowns.events.TownWarCreateEvent;
import net.william278.husktowns.events.TownWarEndEvent;
import net.william278.husktowns.town.Privilege;
import net.william278.husktowns.town.Town;
import net.william278.husktowns.war.Declaration;
import net.william278.husktowns.war.War;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class CityWarService implements Listener {

    public enum ViewerAccess {
        WARS_DISABLED,
        SERVER_BUSY,
        LIVE,
        INCOMING_DECLARATION,
        LIST
    }

    public record ActionResult(boolean success, String reason) {
    }

    public record ViewerDecision(
            ViewerAccess access,
            WarOverview activeWar,
            IncomingDeclarationView incomingDeclaration
    ) {
    }

    public record WarOverview(
            int attackerTownId,
            String attackerTownName,
            int defenderTownId,
            String defenderTownName,
            BigDecimal wager,
            long warZoneRadius,
            OffsetDateTime startTime,
            int aliveAttackers,
            int aliveDefenders,
            WarMoneyRewardPreview attackerVictoryMoney,
            WarMoneyRewardPreview defenderVictoryMoney,
            WarXpRewardPreview attackerVictoryXp,
            WarXpRewardPreview defenderVictoryXp,
            boolean viewerIsAttacker,
            boolean viewerIsDefender
    ) {
        public boolean involvesTown(int townId) {
            return attackerTownId == townId || defenderTownId == townId;
        }
    }

    public record IncomingDeclarationView(
            int attackerTownId,
            String attackerTownName,
            int defenderTownId,
            String defenderTownName,
            BigDecimal wager,
            OffsetDateTime expiryTime,
            long declarationExpiryMinutes,
            boolean relationForcedEnemy
    ) {
    }

    public record WarTargetView(
            int townId,
            String townName,
            String townTag,
            Town.Relation relation,
            Town.Relation reciprocalRelation,
            int cityLevel,
            boolean eligible,
            String stateReason,
            boolean warningAllyBreak,
            boolean needsEnemyNormalization,
            boolean sourceHasDeclarePrivilege,
            boolean sourceHasSpawn,
            boolean targetHasSpawn,
            boolean attackerCanAffordMinimum,
            boolean defenderCanAffordMinimum,
            boolean sourceOnCooldown,
            boolean targetOnCooldown,
            WarMoneyRewardPreview attackerVictoryMoney,
            WarXpRewardPreview attackerVictoryXp
    ) {
    }

    public record DeclarationPreview(
            WarTargetView target,
            BigDecimal minimumWager,
            long cooldownHours,
            long declarationExpiryMinutes,
            long warZoneRadius,
            double requiredOnlineMembership,
            String relationOutcome,
            ActionResult validation,
            WarMoneyRewardPreview attackerVictoryMoney,
            WarXpRewardPreview attackerVictoryXp
    ) {
    }

    public record WarMoneyRewardPreview(
            boolean enabled,
            BigDecimal grossPayout,
            BigDecimal feeAmount,
            BigDecimal netPayout
    ) {
    }

    public record WarXpRewardPreview(
            boolean enabled,
            double baseXp,
            double wagerBonusXp,
            double totalXp,
            long scaledXp
    ) {
    }

    public record CityWarRuntime(
            War war,
            int attackerTownId,
            String attackerTownName,
            int defenderTownId,
            String defenderTownName,
            BigDecimal wager,
            long warZoneRadius,
            OffsetDateTime startTime,
            Location defenderSpawn
    ) {
        public boolean involvesTown(int townId) {
            return attackerTownId == townId || defenderTownId == townId;
        }
    }

    private static final Key CITY_TAG_KEY = Key.key("cittaexp", "tag");
    private static final DateTimeFormatter TROPHY_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final CityLevelService cityLevelService;
    private final CityItemVaultService cityItemVaultService;
    private final CityWarSettings settings;
    private final PluginConfigUtils cfg;
    private final WarBlockRollbackService rollbackService;
    private volatile CityWarRuntime activeRuntime;

    public CityWarService(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            CityLevelService cityLevelService,
            CityItemVaultService cityItemVaultService,
            CityWarSettings settings
    ) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.cityLevelService = cityLevelService;
        this.cityItemVaultService = cityItemVaultService;
        this.settings = settings;
        this.cfg = new PluginConfigUtils(plugin);
        this.rollbackService = new WarBlockRollbackService(plugin, huskTownsApiHook, settings);
    }

    public void start() {
        this.activeRuntime = huskTownsApiHook.getActiveWars().stream()
                .findFirst()
                .flatMap(this::runtimeFromWar)
                .orElse(null);
        if (activeRuntime != null) {
            rollbackService.beginTracking(activeRuntime);
        }
    }

    public void stop() {
        rollbackService.clearRuntime();
        activeRuntime = null;
    }

    public WarBlockRollbackService rollbackService() {
        return rollbackService;
    }

    public boolean warsConfiguredAndEnabled() {
        return settings.enabled() && huskTownsApiHook.isRelationsEnabled() && huskTownsApiHook.warsEnabled();
    }

    public boolean hasSingleActiveWar() {
        return !huskTownsApiHook.getActiveWars().isEmpty();
    }

    public Optional<WarOverview> activeWarOverview() {
        Optional<War> active = huskTownsApiHook.getActiveWars().stream().findFirst();
        return active.map(war -> overview(war, -1));
    }

    public Optional<WarOverview> activeWarForTown(int townId) {
        return huskTownsApiHook.getActiveWars().stream()
                .filter(war -> war.getAttacking() == townId || war.getDefending() == townId)
                .findFirst()
                .map(war -> overview(war, townId));
    }

    public ViewerDecision evaluateViewer(int townId) {
        if (!warsConfiguredAndEnabled()) {
            return new ViewerDecision(ViewerAccess.WARS_DISABLED, null, null);
        }
        Optional<WarOverview> active = activeWarOverview();
        if (active.isPresent()) {
            WarOverview overview = overview(active.get(), townId);
            if (overview.involvesTown(townId)) {
                return new ViewerDecision(ViewerAccess.LIVE, overview, null);
            }
            return new ViewerDecision(ViewerAccess.SERVER_BUSY, overview, null);
        }
        Optional<IncomingDeclarationView> incoming = incomingDeclarationForTown(townId);
        if (incoming.isPresent()) {
            return new ViewerDecision(ViewerAccess.INCOMING_DECLARATION, null, incoming.get());
        }
        return new ViewerDecision(ViewerAccess.LIST, null, null);
    }

    public Optional<IncomingDeclarationView> incomingDeclarationForTown(int townId) {
        return huskTownsApiHook.pendingDeclarationForTown(townId).flatMap(this::incomingFromDeclaration);
    }

    public List<WarTargetView> targetViewsForTown(Player viewer, int sourceTownId) {
        Optional<Town> sourceTown = huskTownsApiHook.getTownById(sourceTownId);
        if (sourceTown.isEmpty()) {
            return List.of();
        }
        BigDecimal minimumWager = minimumWager();
        int sourceLevel = cityLevelService.statusForTown(sourceTownId, false).level();
        boolean sourceHasDeclarePrivilege = huskTownsApiHook.hasPrivilege(viewer, Privilege.DECLARE_WAR);
        return huskTownsApiHook.getPlayableTowns().stream()
                .filter(town -> town.getId() != sourceTownId)
                .map(targetTown -> buildTargetView(sourceTown.get(), sourceLevel, sourceHasDeclarePrivilege, targetTown, minimumWager))
                .sorted(Comparator
                        .comparing((WarTargetView view) -> !view.eligible())
                        .thenComparing((WarTargetView view) -> relationOrder(view.relation()))
                        .thenComparing(view -> view.townName().toLowerCase()))
                .toList();
    }

    public Optional<DeclarationPreview> declarationPreview(Player actor, int sourceTownId, int targetTownId) {
        Optional<Town> sourceTown = huskTownsApiHook.getTownById(sourceTownId);
        Optional<Town> targetTown = huskTownsApiHook.getTownById(targetTownId);
        if (sourceTown.isEmpty() || targetTown.isEmpty()) {
            return Optional.empty();
        }
        int sourceLevel = cityLevelService.statusForTown(sourceTownId, false).level();
        WarTargetView targetView = buildTargetView(
                sourceTown.get(),
                sourceLevel,
                huskTownsApiHook.hasPrivilege(actor, Privilege.DECLARE_WAR),
                targetTown.get(),
                minimumWager()
        );
        String relationOutcome = targetView.warningAllyBreak() || targetView.needsEnemyNormalization()
                ? "Nemici"
                : relationLabel(targetView.relation());
        return Optional.of(new DeclarationPreview(
                targetView,
                minimumWager(),
                huskTownsApiHook.warSettings().cooldownHours(),
                huskTownsApiHook.warSettings().declarationExpiryMinutes(),
                huskTownsApiHook.warSettings().warZoneRadius(),
                huskTownsApiHook.warSettings().requiredOnlineMembership(),
                relationOutcome,
                validateDeclaration(actor, sourceTown.get(), targetTown.get(), minimumWager()),
                attackerVictoryMoneyPreview(minimumWager()),
                attackerVictoryPreview(minimumWager())
        ));
    }

    public CompletableFuture<ActionResult> declareWar(
            Player actor,
            int sourceTownId,
            int targetTownId,
            BigDecimal wager,
            boolean allowAllyBreak
    ) {
        Optional<Town> sourceTown = huskTownsApiHook.getTownById(sourceTownId);
        Optional<Town> targetTown = huskTownsApiHook.getTownById(targetTownId);
        if (sourceTown.isEmpty() || targetTown.isEmpty()) {
            return CompletableFuture.completedFuture(new ActionResult(false, "town-not-found"));
        }
        ActionResult validation = validateDeclaration(actor, sourceTown.get(), targetTown.get(), wager);
        if (!validation.success()) {
            return CompletableFuture.completedFuture(validation);
        }
        Town.Relation relation = sourceTown.get().getRelationWith(targetTown.get());
        Town.Relation reciprocal = targetTown.get().getRelationWith(sourceTown.get());
        boolean warningAllyBreak = relation == Town.Relation.ALLY || reciprocal == Town.Relation.ALLY;
        if (warningAllyBreak && !allowAllyBreak) {
            return CompletableFuture.completedFuture(new ActionResult(false, "ally-warning-required"));
        }
        boolean needsNormalization = !sourceTown.get().areRelationsBilateral(targetTown.get(), Town.Relation.ENEMY);
        CompletableFuture<Boolean> relationFuture = needsNormalization
                ? huskTownsApiHook.forceTownRelationBilateral(actor.getUniqueId(), sourceTownId, targetTownId, Town.Relation.ENEMY)
                : CompletableFuture.completedFuture(true);
        return relationFuture.thenApply(normalized -> {
            if (!normalized) {
                return new ActionResult(false, "relation-normalize-failed");
            }
            boolean dispatched = huskTownsApiHook.sendWarDeclaration(actor, targetTown.get().getName(), wager);
            return dispatched
                    ? new ActionResult(true, "declared")
                    : new ActionResult(false, "declare-dispatch-failed");
        });
    }

    public ActionResult acceptIncoming(Player actor, int defendingTownId) {
        if (!warsConfiguredAndEnabled()) {
            return new ActionResult(false, "native-wars-disabled");
        }
        Optional<Town> defendingTown = huskTownsApiHook.getTownById(defendingTownId);
        if (defendingTown.isEmpty()) {
            return new ActionResult(false, "town-not-found");
        }
        if (!huskTownsApiHook.hasPrivilege(actor, Privilege.DECLARE_WAR)) {
            return new ActionResult(false, "no-declare-privilege");
        }
        if (settings.singleActiveWarGlobal() && hasSingleActiveWar()) {
            return new ActionResult(false, "active-war-global");
        }
        boolean dispatched = huskTownsApiHook.acceptWarDeclaration(actor);
        return dispatched ? new ActionResult(true, "accepted") : new ActionResult(false, "accept-dispatch-failed");
    }

    public ActionResult surrender(Player actor, int townId) {
        if (!warsConfiguredAndEnabled()) {
            return new ActionResult(false, "native-wars-disabled");
        }
        Optional<Town> ownTown = huskTownsApiHook.getTownById(townId);
        if (ownTown.isEmpty()) {
            return new ActionResult(false, "town-not-found");
        }
        if (!huskTownsApiHook.hasPrivilege(actor, Privilege.DECLARE_WAR)) {
            return new ActionResult(false, "no-declare-privilege");
        }
        if (ownTown.get().getCurrentWar().isEmpty()) {
            return new ActionResult(false, "not-at-war");
        }
        return huskTownsApiHook.surrenderWar(actor)
                ? new ActionResult(true, "surrendered")
                : new ActionResult(false, "surrender-dispatch-failed");
    }

    public boolean shouldBlockNewWarCommands() {
        return settings.singleActiveWarGlobal() && hasSingleActiveWar();
    }

    public int guiPageSize() {
        return settings.gui().pageSize();
    }

    public String relationLabel(Town.Relation relation) {
        if (relation == null) {
            return "Sconosciuta";
        }
        return switch (relation) {
            case ALLY -> "Alleati";
            case ENEMY -> "Nemici";
            case NEUTRAL -> "Neutrali";
        };
    }

    public String formatMoney(BigDecimal value) {
        return safeMoney(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    public String formatTimestamp(OffsetDateTime timestamp) {
        if (timestamp == null) {
            return "-";
        }
        return TROPHY_TIME_FORMAT.format(timestamp);
    }

    public boolean xpRewardsEnabled() {
        return settings.xpRewards().enabled();
    }

    public boolean payoutEnabled() {
        return settings.payout().enabled();
    }

    public WarMoneyRewardPreview attackerVictoryMoneyPreview(BigDecimal wager) {
        return moneyPreview(War.EndState.ATTACKER_WIN, wager);
    }

    public WarMoneyRewardPreview defenderVictoryMoneyPreview(BigDecimal wager) {
        return moneyPreview(War.EndState.DEFENDER_WIN, wager);
    }

    public WarXpRewardPreview attackerVictoryPreview(BigDecimal wager) {
        return xpPreview(War.EndState.ATTACKER_WIN, wager);
    }

    public WarXpRewardPreview defenderVictoryPreview(BigDecimal wager) {
        return xpPreview(War.EndState.DEFENDER_WIN, wager);
    }

    @EventHandler
    public void onTownWarCreate(TownWarCreateEvent event) {
        runtimeFromWar(event.getWar()).ifPresent(runtime -> {
            activeRuntime = runtime;
            rollbackService.beginTracking(runtime);
            announceWarStart(runtime);
        });
    }

    @EventHandler
    public void onTownWarEnd(TownWarEndEvent event) {
        CityWarRuntime runtime = activeRuntime;
        if (runtime == null) {
            runtime = runtimeFromWar(event.getWar()).orElse(null);
        }
        rollbackService.restoreTrackedBlocks();
        if (runtime != null) {
            War.EndState endReason = event.getWarEndReason();
            WarMoneyRewardPreview moneyReward = moneyPreview(endReason, runtime.wager());
            applyWarSettlement(runtime, endReason);
            WarXpRewardPreview xpReward = xpPreview(endReason, runtime.wager());
            if (endReason == War.EndState.ATTACKER_WIN || endReason == War.EndState.DEFENDER_WIN) {
                int winnerTownId = endReason == War.EndState.ATTACKER_WIN ? runtime.attackerTownId() : runtime.defenderTownId();
                String winnerTownName = endReason == War.EndState.ATTACKER_WIN ? runtime.attackerTownName() : runtime.defenderTownName();
                awardWinnerXp(winnerTownId, runtime, endReason, xpReward);
                depositTrophy(runtime, winnerTownId, winnerTownName, endReason, moneyReward, xpReward);
            }
            announceWarEnd(runtime, event.getWarEndReason(), moneyReward, xpReward);
        }
        activeRuntime = null;
        rollbackService.clearRuntime();
    }

    private Optional<CityWarRuntime> runtimeFromWar(War war) {
        if (war == null) {
            return Optional.empty();
        }
        Town attackingTown = war.getAttacking(huskTownsApiHook.plugin());
        Town defendingTown = war.getDefending(huskTownsApiHook.plugin());
        Optional<Location> defenderSpawn = huskTownsApiHook.toLocation(war.getDefenderSpawn());
        if (attackingTown == null || defendingTown == null || defenderSpawn.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new CityWarRuntime(
                war,
                attackingTown.getId(),
                attackingTown.getName(),
                defendingTown.getId(),
                defendingTown.getName(),
                safeMoney(war.getWager()),
                war.getWarZoneRadius(),
                war.getStartTime(),
                defenderSpawn.get()
        ));
    }

    private Optional<IncomingDeclarationView> incomingFromDeclaration(Declaration declaration) {
        if (declaration == null) {
            return Optional.empty();
        }
        Optional<Town> attacking = declaration.getAttackingTown(huskTownsApiHook.plugin());
        Optional<Town> defending = declaration.getDefendingTown(huskTownsApiHook.plugin());
        if (attacking.isEmpty() || defending.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new IncomingDeclarationView(
                attacking.get().getId(),
                attacking.get().getName(),
                defending.get().getId(),
                defending.get().getName(),
                safeMoney(declaration.wager()),
                declaration.expiryTime(),
                huskTownsApiHook.warSettings().declarationExpiryMinutes(),
                attacking.get().areRelationsBilateral(defending.get(), Town.Relation.ENEMY)
        ));
    }

    private WarOverview overview(WarOverview overview, int viewerTownId) {
        return new WarOverview(
                overview.attackerTownId(),
                overview.attackerTownName(),
                overview.defenderTownId(),
                overview.defenderTownName(),
                overview.wager(),
                overview.warZoneRadius(),
                overview.startTime(),
                overview.aliveAttackers(),
                overview.aliveDefenders(),
                overview.attackerVictoryMoney(),
                overview.defenderVictoryMoney(),
                overview.attackerVictoryXp(),
                overview.defenderVictoryXp(),
                overview.attackerTownId() == viewerTownId,
                overview.defenderTownId() == viewerTownId
        );
    }

    private WarOverview overview(War war, int viewerTownId) {
        Town attackingTown = war.getAttacking(huskTownsApiHook.plugin());
        Town defendingTown = war.getDefending(huskTownsApiHook.plugin());
        return new WarOverview(
                attackingTown == null ? war.getAttacking() : attackingTown.getId(),
                attackingTown == null ? "#" + war.getAttacking() : attackingTown.getName(),
                defendingTown == null ? war.getDefending() : defendingTown.getId(),
                defendingTown == null ? "#" + war.getDefending() : defendingTown.getName(),
                safeMoney(war.getWager()),
                war.getWarZoneRadius(),
                war.getStartTime(),
                war.getAliveAttackers().size(),
                war.getAliveDefenders().size(),
                attackerVictoryMoneyPreview(war.getWager()),
                defenderVictoryMoneyPreview(war.getWager()),
                attackerVictoryPreview(war.getWager()),
                defenderVictoryPreview(war.getWager()),
                war.getAttacking() == viewerTownId,
                war.getDefending() == viewerTownId
        );
    }

    private WarTargetView buildTargetView(
            Town sourceTown,
            int sourceLevel,
            boolean sourceHasDeclarePrivilege,
            Town targetTown,
            BigDecimal minimumWager
    ) {
        int targetLevel = cityLevelService.statusForTown(targetTown.getId(), false).level();
        Town.Relation relation = sourceTown.getRelationWith(targetTown);
        Town.Relation reciprocal = targetTown.getRelationWith(sourceTown);
        boolean warningAllyBreak = relation == Town.Relation.ALLY || reciprocal == Town.Relation.ALLY;
        boolean needsEnemyNormalization = !sourceTown.areRelationsBilateral(targetTown, Town.Relation.ENEMY);
        boolean sourceHasSpawn = sourceTown.getSpawn().isPresent();
        boolean targetHasSpawn = targetTown.getSpawn().isPresent();
        boolean attackerCanAffordMinimum = sourceTown.getMoney().compareTo(minimumWager) >= 0;
        boolean defenderCanAffordMinimum = targetTown.getMoney().compareTo(minimumWager) >= 0;
        boolean sourceOnCooldown = remainingCooldownHours(sourceTown) > 0L;
        boolean targetOnCooldown = remainingCooldownHours(targetTown) > 0L;
        ActionResult validation = validateTarget(
                sourceTown,
                targetTown,
                sourceLevel,
                targetLevel,
                sourceHasDeclarePrivilege,
                sourceHasSpawn,
                targetHasSpawn,
                attackerCanAffordMinimum,
                defenderCanAffordMinimum,
                sourceOnCooldown,
                targetOnCooldown
        );
        return new WarTargetView(
                targetTown.getId(),
                targetTown.getName(),
                targetTown.getMetadataTag(CITY_TAG_KEY).orElse("---"),
                relation,
                reciprocal,
                targetLevel,
                validation.success(),
                validation.reason(),
                warningAllyBreak,
                needsEnemyNormalization,
                sourceHasDeclarePrivilege,
                sourceHasSpawn,
                targetHasSpawn,
                attackerCanAffordMinimum,
                defenderCanAffordMinimum,
                sourceOnCooldown,
                targetOnCooldown,
                attackerVictoryMoneyPreview(minimumWager),
                attackerVictoryPreview(minimumWager)
        );
    }

    private ActionResult validateDeclaration(Player actor, Town sourceTown, Town targetTown, BigDecimal wager) {
        if (!warsConfiguredAndEnabled()) {
            return new ActionResult(false, "native-wars-disabled");
        }
        if (actor == null || !huskTownsApiHook.hasPrivilege(actor, Privilege.DECLARE_WAR)) {
            return new ActionResult(false, "no-declare-privilege");
        }
        int sourceLevel = cityLevelService.statusForTown(sourceTown.getId(), false).level();
        int targetLevel = cityLevelService.statusForTown(targetTown.getId(), false).level();
        boolean sourceHasSpawn = sourceTown.getSpawn().isPresent();
        boolean targetHasSpawn = targetTown.getSpawn().isPresent();
        boolean attackerCanAfford = sourceTown.getMoney().compareTo(safeMoney(wager)) >= 0;
        boolean defenderCanAfford = targetTown.getMoney().compareTo(safeMoney(wager)) >= 0;
        boolean sourceOnCooldown = remainingCooldownHours(sourceTown) > 0L;
        boolean targetOnCooldown = remainingCooldownHours(targetTown) > 0L;
        ActionResult targetValidation = validateTarget(
                sourceTown,
                targetTown,
                sourceLevel,
                targetLevel,
                true,
                sourceHasSpawn,
                targetHasSpawn,
                attackerCanAfford,
                defenderCanAfford,
                sourceOnCooldown,
                targetOnCooldown
        );
        if (!targetValidation.success()) {
            return targetValidation;
        }
        BigDecimal minimum = minimumWager();
        if (safeMoney(wager).compareTo(BigDecimal.ZERO) <= 0) {
            return new ActionResult(false, "wager-invalid");
        }
        if (safeMoney(wager).compareTo(minimum) < 0) {
            return new ActionResult(false, "wager-below-minimum");
        }
        return new ActionResult(true, "ready");
    }

    private ActionResult validateTarget(
            Town sourceTown,
            Town targetTown,
            int sourceLevel,
            int targetLevel,
            boolean sourceHasDeclarePrivilege,
            boolean sourceHasSpawn,
            boolean targetHasSpawn,
            boolean attackerCanAfford,
            boolean defenderCanAfford,
            boolean sourceOnCooldown,
            boolean targetOnCooldown
    ) {
        if (!settings.enabled()) {
            return new ActionResult(false, "war-disabled");
        }
        if (!huskTownsApiHook.isRelationsEnabled()) {
            return new ActionResult(false, "relations-disabled");
        }
        if (!huskTownsApiHook.warsEnabled()) {
            return new ActionResult(false, "native-wars-disabled");
        }
        if (!sourceHasDeclarePrivilege) {
            return new ActionResult(false, "no-declare-privilege");
        }
        if (settings.singleActiveWarGlobal() && hasSingleActiveWar()) {
            return new ActionResult(false, "active-war-global");
        }
        if (sourceTown.getId() == targetTown.getId()) {
            return new ActionResult(false, "same-town");
        }
        if (sourceLevel < settings.minCityLevel()) {
            return new ActionResult(false, "source-level-low");
        }
        if (targetLevel < settings.minCityLevel()) {
            return new ActionResult(false, "target-level-low");
        }
        if (Math.abs(sourceLevel - targetLevel) > settings.maxCityLevelDifference()) {
            return new ActionResult(false, "level-gap-too-high");
        }
        if (!sourceHasSpawn) {
            return new ActionResult(false, "source-spawn-missing");
        }
        if (!targetHasSpawn) {
            return new ActionResult(false, "target-spawn-missing");
        }
        if (sourceTown.getCurrentWar().isPresent() || targetTown.getCurrentWar().isPresent()) {
            return new ActionResult(false, "already-at-war");
        }
        if (huskTownsApiHook.pendingDeclarationForTown(targetTown.getId()).isPresent()) {
            return new ActionResult(false, "pending-target");
        }
        if (sourceOnCooldown) {
            return new ActionResult(false, "source-cooldown");
        }
        if (targetOnCooldown) {
            return new ActionResult(false, "target-cooldown");
        }
        if (!attackerCanAfford) {
            return new ActionResult(false, "attacker-minimum-funds");
        }
        if (!defenderCanAfford) {
            return new ActionResult(false, "defender-minimum-funds");
        }
        return new ActionResult(true, "ready");
    }

    private long remainingCooldownHours(Town town) {
        if (town == null) {
            return 0L;
        }
        long cooldownHours = huskTownsApiHook.warSettings().cooldownHours();
        if (cooldownHours <= 0L) {
            return 0L;
        }
        Optional<OffsetDateTime> lastWar = town.getLog().getLastWarTime();
        if (lastWar.isEmpty()) {
            return 0L;
        }
        OffsetDateTime readyAt = lastWar.get().plusHours(cooldownHours);
        if (!OffsetDateTime.now().isBefore(readyAt)) {
            return 0L;
        }
        long seconds = java.time.Duration.between(OffsetDateTime.now(), readyAt).getSeconds();
        return Math.max(1L, (seconds + 3599L) / 3600L);
    }

    private BigDecimal minimumWager() {
        return BigDecimal.valueOf(huskTownsApiHook.warSettings().minimumWager()).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal safeMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private int relationOrder(Town.Relation relation) {
        if (relation == null) {
            return 3;
        }
        return switch (relation) {
            case ENEMY -> 0;
            case NEUTRAL -> 1;
            case ALLY -> 2;
        };
    }

    private void announceWarStart(CityWarRuntime runtime) {
        broadcast(cfg.msg(
                "city.war.broadcast.start",
                "<red><bold>⚔ Guerra dichiarata</bold></red> <gray>|</gray> <white>{attacker}</white> <gray>contro</gray> <white>{defender}</white> <gray>|</gray> <gold>Posta:</gold> <white>{wager}</white>",
                Placeholder.unparsed("attacker", runtime.attackerTownName()),
                Placeholder.unparsed("defender", runtime.defenderTownName()),
                Placeholder.unparsed("wager", formatMoney(runtime.wager()))
        ));
        sendParticipantTitles(runtime,
                cfg.msg("city.war.title.start.title", "<red>Guerra aperta</red>"),
                cfg.msg(
                        "city.war.title.start.subtitle",
                        "<white>{attacker}</white> <gray>contro</gray> <white>{defender}</white>",
                        Placeholder.unparsed("attacker", runtime.attackerTownName()),
                        Placeholder.unparsed("defender", runtime.defenderTownName())
                ));
    }

    private void announceWarEnd(CityWarRuntime runtime, War.EndState endState, WarMoneyRewardPreview moneyReward, WarXpRewardPreview xpReward) {
        String winner = switch (endState) {
            case ATTACKER_WIN -> runtime.attackerTownName();
            case DEFENDER_WIN -> runtime.defenderTownName();
            case TIME_OUT -> "Nessun vessillo";
        };
        String result = warEndResultLabel(endState);
        String payoutLabel = moneyReward != null && moneyReward.enabled()
                ? formatMoney(moneyReward.netPayout())
                : "nessuna riscossione";
        String feeLabel = moneyReward != null && moneyReward.enabled()
                ? formatMoney(moneyReward.feeAmount())
                : "nessun tributo";
        String broadcastKey = xpReward != null && xpReward.enabled() && xpReward.scaledXp() > 0L
                ? "city.war.broadcast.end_with_xp"
                : "city.war.broadcast.end";
        broadcast(cfg.msg(
                broadcastKey,
                xpReward != null && xpReward.enabled() && xpReward.scaledXp() > 0L
                        ? "<gold><bold>⚔ Cronaca di guerra chiusa</bold></gold> <gray>|</gray> <white>{attacker}</white> <gray>vs</gray> <white>{defender}</white> <gray>|</gray> <yellow>{result}</yellow> <gray>|</gray> <white>Vincitore: {winner}</white> <gray>|</gray> <aqua>+{xp} XP citta</aqua>"
                        : "<gold><bold>⚔ Cronaca di guerra chiusa</bold></gold> <gray>|</gray> <white>{attacker}</white> <gray>vs</gray> <white>{defender}</white> <gray>|</gray> <yellow>{result}</yellow> <gray>|</gray> <white>Vincitore: {winner}</white>",
                Placeholder.unparsed("attacker", runtime.attackerTownName()),
                Placeholder.unparsed("defender", runtime.defenderTownName()),
                Placeholder.unparsed("result", result),
                Placeholder.unparsed("winner", winner),
                Placeholder.unparsed("payout", payoutLabel),
                Placeholder.unparsed("fee", feeLabel),
                Placeholder.unparsed("xp", xpReward == null ? "0" : formatXp(xpReward.totalXp()))
        ));
        sendParticipantTitles(runtime,
                cfg.msg("city.war.title.end.title", "<gold>Guerra conclusa</gold>"),
                cfg.msg(
                        "city.war.title.end.subtitle",
                        "<white>{result}</white> <gray>| Vincitore:</gray> <white>{winner}</white>",
                        Placeholder.unparsed("result", result),
                        Placeholder.unparsed("winner", winner)
                ));
    }

    private void depositTrophy(CityWarRuntime runtime, int winnerTownId, String winnerTownName, War.EndState endState, WarMoneyRewardPreview moneyReward, WarXpRewardPreview xpReward) {
        if (!settings.trophy().enabled()) {
            return;
        }
        String result = warEndResultLabel(endState);
        ItemStack trophy = createWarTrophyBase();
        ItemMeta meta = trophy.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.displayName(cfg.msg(
                "city.war.trophy.title",
                "<gradient:#ffd76a:#ff8c42><bold>✦ Sigillo della Guerra Vinta ✦</bold></gradient> <gray>•</gray> <white>{winner}</white> <gray>contro</gray> <white>{loser}</white>",
                Placeholder.unparsed("winner", winnerTownName),
                Placeholder.unparsed("loser", losingTownName(runtime, winnerTownId))
        ));
        List<Component> lore = new ArrayList<>();
        lore.add(cfg.msg("city.war.trophy.lore.frame_top", "<dark_gray>╭────────────╮</dark_gray>"));
        lore.add(cfg.msg("city.war.trophy.lore.when", "<gold>✦</gold> <white>Sigillata il:</white> <gray>{time}</gray>", Placeholder.unparsed("time", formatTimestamp(OffsetDateTime.now()))));
        lore.add(cfg.msg("city.war.trophy.lore.attacker", "<red>⚔</red> <white>Vessillo d'assalto:</white> <gray>{town}</gray>", Placeholder.unparsed("town", runtime.attackerTownName())));
        lore.add(cfg.msg("city.war.trophy.lore.defender", "<aqua>🛡</aqua> <white>Baluardo difensore:</white> <gray>{town}</gray>", Placeholder.unparsed("town", runtime.defenderTownName())));
        lore.add(cfg.msg("city.war.trophy.lore.winner", "<green>✦</green> <white>Casato incoronato:</white> <gold>{town}</gold>", Placeholder.unparsed("town", winnerTownName)));
        lore.add(cfg.msg("city.war.trophy.lore.result", "<gray>✧ Esito inciso:</gray> <white>{result}</white>", Placeholder.unparsed("result", result)));
        lore.add(cfg.msg("city.war.trophy.lore.wager", "<gray>✧ Posta consacrata:</gray> <white>{wager}</white>", Placeholder.unparsed("wager", formatMoney(runtime.wager()))));
        if (moneyReward != null && moneyReward.enabled() && moneyReward.netPayout().compareTo(BigDecimal.ZERO) > 0) {
            lore.add(cfg.msg("city.war.trophy.lore.payout", "<gold>🪙</gold> <white>Ricompensa del vessillo:</white> <gold>{money}</gold>", Placeholder.unparsed("money", formatMoney(moneyReward.netPayout()))));
            lore.add(cfg.msg("city.war.trophy.lore.fee", "<gray>✧ Tributo trattenuto:</gray> <white>{money}</white>", Placeholder.unparsed("money", formatMoney(moneyReward.feeAmount()))));
        }
        if (xpReward != null && xpReward.enabled() && xpReward.scaledXp() > 0L) {
            lore.add(cfg.msg("city.war.trophy.lore.xp", "<aqua>✦</aqua> <white>Onore riscattato:</white> <aqua>{xp} XP citta</aqua>", Placeholder.unparsed("xp", formatXp(xpReward.totalXp()))));
        }
        lore.add(cfg.msg("city.war.trophy.lore.outro", "<gray>La reliquia e stata deposta nella</gray> <gold>Camera delle Reliquie</gold> <gray>del casato vincitore.</gray>"));
        lore.add(cfg.msg("city.war.trophy.lore.frame_bottom", "<dark_gray>╰────────────╯</dark_gray>"));
        meta.lore(lore);
        trophy.setItemMeta(meta);
        cityItemVaultService.depositRewardStacks(winnerTownId, List.of(trophy), "war-trophy:" + runtime.attackerTownId() + ":" + runtime.defenderTownId());
    }

    private ItemStack createWarTrophyBase() {
        String itemId = settings.trophy().itemsAdderItemId();
        if (itemId != null && !itemId.isBlank()) {
            try {
                CustomStack custom = CustomStack.getInstance(itemId);
                if (custom != null && custom.getItemStack() != null) {
                    ItemStack stack = custom.getItemStack().clone();
                    stack.setAmount(1);
                    return stack;
                }
            } catch (Throwable ignored) {
            }
        }
        Material material = settings.trophy().material();
        if (material == null || material.isAir()) {
            material = Material.TOTEM_OF_UNDYING;
        }
        return new ItemStack(material);
    }

    private String losingTownName(CityWarRuntime runtime, int winnerTownId) {
        return runtime.attackerTownId() == winnerTownId ? runtime.defenderTownName() : runtime.attackerTownName();
    }

    private void awardWinnerXp(int winnerTownId, CityWarRuntime runtime, War.EndState endState, WarXpRewardPreview xpReward) {
        if (xpReward == null || !xpReward.enabled() || xpReward.scaledXp() <= 0L) {
            return;
        }
        cityLevelService.grantXpBonus(
                winnerTownId,
                xpReward.scaledXp(),
                null,
                "war:" + endState.name().toLowerCase(java.util.Locale.ROOT) + ":" + runtime.attackerTownId() + ":" + runtime.defenderTownId()
        );
    }

    private WarXpRewardPreview xpPreview(War.EndState outcome, BigDecimal wager) {
        CityWarXpRewardPolicy.RewardPreview preview = CityWarXpRewardPolicy.resolve(settings.xpRewards(), toRewardOutcome(outcome), safeMoney(wager));
        long scaledXp = preview.enabled() ? Math.max(0L, cityLevelService.toScaledXp(preview.totalXp())) : 0L;
        return new WarXpRewardPreview(
                preview.enabled(),
                preview.baseXp(),
                preview.wagerBonusXp(),
                preview.totalXp(),
                scaledXp
        );
    }

    private WarMoneyRewardPreview moneyPreview(War.EndState outcome, BigDecimal wager) {
        CityWarPayoutPolicy.SettlementPreview preview = CityWarPayoutPolicy.resolve(settings.payout(), toPayoutOutcome(outcome), safeMoney(wager));
        return new WarMoneyRewardPreview(preview.enabled(), preview.grossPayout(), preview.feeAmount(), preview.netPayout());
    }

    private void applyWarSettlement(CityWarRuntime runtime, War.EndState endState) {
        CityWarPayoutPolicy.SettlementPreview preview = CityWarPayoutPolicy.resolve(settings.payout(), toPayoutOutcome(endState), runtime.wager());
        if (!preview.enabled()) {
            return;
        }
        if (preview.attackerAdjustment().compareTo(BigDecimal.ZERO) != 0) {
            if (!huskTownsApiHook.adjustTownBalance(runtime.attackerTownId(), preview.attackerAdjustment())) {
                plugin.getLogger().warning("[war] Failed to apply attacker settlement adjustment for town " + runtime.attackerTownId());
            }
        }
        if (preview.defenderAdjustment().compareTo(BigDecimal.ZERO) != 0) {
            if (!huskTownsApiHook.adjustTownBalance(runtime.defenderTownId(), preview.defenderAdjustment())) {
                plugin.getLogger().warning("[war] Failed to apply defender settlement adjustment for town " + runtime.defenderTownId());
            }
        }
    }

    private CityWarXpRewardPolicy.Outcome toRewardOutcome(War.EndState outcome) {
        if (outcome == null) {
            return CityWarXpRewardPolicy.Outcome.TIME_OUT;
        }
        return switch (outcome) {
            case ATTACKER_WIN -> CityWarXpRewardPolicy.Outcome.ATTACKER_WIN;
            case DEFENDER_WIN -> CityWarXpRewardPolicy.Outcome.DEFENDER_WIN;
            case TIME_OUT -> CityWarXpRewardPolicy.Outcome.TIME_OUT;
        };
    }

    private CityWarPayoutPolicy.Outcome toPayoutOutcome(War.EndState outcome) {
        if (outcome == null) {
            return CityWarPayoutPolicy.Outcome.TIME_OUT;
        }
        return switch (outcome) {
            case ATTACKER_WIN -> CityWarPayoutPolicy.Outcome.ATTACKER_WIN;
            case DEFENDER_WIN -> CityWarPayoutPolicy.Outcome.DEFENDER_WIN;
            case TIME_OUT -> CityWarPayoutPolicy.Outcome.TIME_OUT;
        };
    }

    private String formatXp(double xp) {
        return BigDecimal.valueOf(Math.max(0.0D, xp)).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String warEndResultLabel(War.EndState endState) {
        if (endState == null) {
            return "Cronaca interrotta";
        }
        return switch (endState) {
            case ATTACKER_WIN -> "Il vessillo d'assalto ha spezzato il fronte";
            case DEFENDER_WIN -> "Il baluardo ha retto il fronte";
            case TIME_OUT -> "Il conflitto si e spento in stallo";
        };
    }

    private void sendParticipantTitles(CityWarRuntime runtime, Component title, Component subtitle) {
        Title view = Title.title(title, subtitle);
        for (UUID uuid : runtime.war().getAliveAttackers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.showTitle(view);
            }
        }
        for (UUID uuid : runtime.war().getAliveDefenders()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.showTitle(view);
            }
        }
    }

    private void broadcast(Component component) {
        Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(component));
        plugin.getServer().getConsoleSender().sendMessage(component);
    }
}
