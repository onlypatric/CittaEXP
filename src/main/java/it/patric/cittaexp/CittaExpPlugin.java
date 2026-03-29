package it.patric.cittaexp;

import it.patric.cittaexp.activity.InactiveTownMemberPruneService;
import it.patric.cittaexp.activity.PlayerActivityRepository;
import it.patric.cittaexp.activity.PlayerActivityService;
import it.patric.cittaexp.command.CittaExpCommandTree;
import it.patric.cittaexp.command.CityCommandTree;
import it.patric.cittaexp.challenges.CityChallengeGuiService;
import it.patric.cittaexp.challenges.CityChallengeListener;
import it.patric.cittaexp.challenges.CityChallengeRepository;
import it.patric.cittaexp.challenges.CityChallengeService;
import it.patric.cittaexp.challenges.CityChallengeSettings;
import it.patric.cittaexp.challenges.ChallengeStore;
import it.patric.cittaexp.challenges.MySqlChallengeBackend;
import it.patric.cittaexp.challenges.StaffEventRepository;
import it.patric.cittaexp.challenges.StaffEventService;
import it.patric.cittaexp.custommobs.CustomMobSubsystem;
import it.patric.cittaexp.defense.CityDefenseService;
import it.patric.cittaexp.defense.CityDefenseSettings;
import it.patric.cittaexp.discord.DiscordBotService;
import it.patric.cittaexp.discord.DiscordBridgeSettings;
import it.patric.cittaexp.discord.DiscordCommandService;
import it.patric.cittaexp.discord.DiscordVerificationCommandBlockListener;
import it.patric.cittaexp.discord.DiscordIdentityLinkService;
import it.patric.cittaexp.discord.DiscordLinkRepository;
import it.patric.cittaexp.discord.DiscordLogHelper;
import it.patric.cittaexp.discord.DiscordTownSyncService;
import it.patric.cittaexp.citylistgui.CityTownListGuiService;
import it.patric.cittaexp.cityhubgui.CityHubGuiService;
import it.patric.cittaexp.createtown.CityCreateDialogFlow;
import it.patric.cittaexp.economy.EconomyBalanceSettings;
import it.patric.cittaexp.economy.EconomyInflationService;
import it.patric.cittaexp.economy.EconomyValueService;
import it.patric.cittaexp.economy.PlayerEconomyService;
import it.patric.cittaexp.disbandtown.TownDisbandDialogFlow;
import it.patric.cittaexp.edittown.TownEditDialogFlow;
import it.patric.cittaexp.integration.huskclaims.HuskClaimsChunkGuard;
import it.patric.cittaexp.integration.huskclaims.HuskClaimsHookStateService;
import it.patric.cittaexp.integration.huskclaims.HuskClaimsTownFallbackGuardListener;
import it.patric.cittaexp.integration.huskclaims.TownClaimOverlapChecker;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.integration.husktowns.HuskTownsClaimWorldSanityService;
import it.patric.cittaexp.integration.husktowns.HuskTownsRawCommandBlockListener;
import it.patric.cittaexp.levels.AdminClaimBonusService;
import it.patric.cittaexp.levels.CityLevelRepository;
import it.patric.cittaexp.levels.CityLevelRequestService;
import it.patric.cittaexp.levels.CityLevelRuntimeService;
import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.levels.CityLevelSettings;
import it.patric.cittaexp.levels.CityLevelStore;
import it.patric.cittaexp.levels.CityTaxService;
import it.patric.cittaexp.levels.CityXpScanService;
import it.patric.cittaexp.levels.GlobalHopperChunkCapListener;
import it.patric.cittaexp.levels.MySqlLevelBackend;
import it.patric.cittaexp.levels.StaffApprovalService;
import it.patric.cittaexp.levels.TownLevelCommandBlockListener;
import it.patric.cittaexp.levels.TownSpawnerCapListener;
import it.patric.cittaexp.levelsgui.CityLevelGuiService;
import it.patric.cittaexp.levelsgui.CityLevelUpgradeNoticeService;
import it.patric.cittaexp.manageclaim.ClaimBorderOverlayService;
import it.patric.cittaexp.manageclaim.CityClaimToolListener;
import it.patric.cittaexp.manageclaim.CitySectionMapGuiService;
import it.patric.cittaexp.permissions.TownMemberPermissionService;
import it.patric.cittaexp.playersgui.CityPlayersActionService;
import it.patric.cittaexp.playersgui.CityPlayersGuiService;
import it.patric.cittaexp.playersgui.CittaExpTownRoleGovernanceService;
import it.patric.cittaexp.playersgui.HuskTownsRoleLadderService;
import it.patric.cittaexp.relationsgui.CityRelationsGuiService;
import it.patric.cittaexp.renametown.TownRenameDialogFlow;
import it.patric.cittaexp.shop.CityShopService;
import it.patric.cittaexp.trust.CityChunkTrustCommandService;
import it.patric.cittaexp.utils.YamlResourceBackfill;
import it.patric.cittaexp.utils.PluginConfigUtils;
import it.patric.cittaexp.vault.CityItemVaultGuiService;
import it.patric.cittaexp.vault.CityItemVaultService;
import it.patric.cittaexp.vault.CityVaultSettings;
import it.patric.cittaexp.wiki.CityWikiDialogService;
import it.patric.cittaexp.war.CityWarService;
import it.patric.cittaexp.war.CityWarSettings;
import it.patric.cittaexp.war.TownWarCommandBlockListener;
import net.william278.husktowns.api.HuskTownsAPI;
import net.william278.husktowns.town.Town;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class CittaExpPlugin extends JavaPlugin {

    private static final String HUSK_TOWNS_PLUGIN = "HuskTowns";
    private HuskTownsApiHook huskTownsApiHook;
    private CityPlayersGuiService cityPlayersGuiService;
    private CityTownListGuiService cityTownListGuiService;
    private CitySectionMapGuiService citySectionMapGuiService;
    private ClaimBorderOverlayService claimBorderOverlayService;
    private CityRelationsGuiService cityRelationsGuiService;
    private CityLevelGuiService cityLevelGuiService;
    private CityLevelUpgradeNoticeService cityLevelUpgradeNoticeService;
    private CityLevelService cityLevelService;
    private CityChallengeGuiService cityChallengeGuiService;
    private CityLevelRuntimeService cityLevelRuntimeService;
    private CityTaxService cityTaxService;
    private CityLevelStore cityLevelStore;
    private CityChallengeService cityChallengeService;
    private StaffEventService staffEventService;
    private ChallengeStore challengeStore;
    private CityItemVaultService cityItemVaultService;
    private PlayerActivityService playerActivityService;
    private InactiveTownMemberPruneService inactiveTownMemberPruneService;
    private TownMemberPermissionService townMemberPermissionService;
    private CityItemVaultGuiService cityItemVaultGuiService;
    private AdminClaimBonusService adminClaimBonusService;
    private PlayerEconomyService playerEconomyService;
    private EconomyInflationService economyInflationService;
    private EconomyValueService economyValueService;
    private CityHubGuiService cityHubGuiService;
    private CityDefenseService cityDefenseService;
    private CityWarService cityWarService;
    private CityWikiDialogService cityWikiDialogService;
    private CityChunkTrustCommandService cityChunkTrustCommandService;
    private CustomMobSubsystem customMobSubsystem;
    private HuskClaimsHookStateService huskClaimsHookStateService;
    private HuskClaimsTownFallbackGuardListener huskClaimsTownFallbackGuardListener;
    private CityClaimToolListener cityClaimToolListener;
    private DiscordBotService discordBotService;
    private DiscordTownSyncService discordTownSyncService;
    private CityShopService cityShopService;
    private HuskTownsClaimWorldSanityService huskTownsClaimWorldSanityService;
    private final AtomicBoolean reloadInProgress = new AtomicBoolean(false);

    @Override
    public void onEnable() {
        Plugin huskTowns = getServer().getPluginManager().getPlugin(HUSK_TOWNS_PLUGIN);
        if (huskTowns == null || !huskTowns.isEnabled()) {
            getLogger().severe("HuskTowns non disponibile o non abilitato. CittaEXP viene disabilitato.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            this.huskTownsApiHook = new HuskTownsApiHook();
        } catch (HuskTownsAPI.NotRegisteredException exception) {
            getLogger().severe("HuskTowns API non registrata. CittaEXP viene disabilitato.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        YamlResourceBackfill.ensure(this, "config.yml");
        reloadConfig();
        YamlResourceBackfill.ensure(this, "levels.yml");
        YamlResourceBackfill.ensure(this, "materials-xp.yml");
        YamlResourceBackfill.ensure(this, "challenges.yml");
        YamlResourceBackfill.ensure(this, "challenge-rewards.yml");
        YamlResourceBackfill.ensure(this, "challenge-texts.yml");
        YamlResourceBackfill.ensure(this, "atlas.yml");
        YamlResourceBackfill.ensure(this, "vault.yml");
        YamlResourceBackfill.ensure(this, "defense.yml");
        YamlResourceBackfill.ensure(this, "war.yml");
        YamlResourceBackfill.ensure(this, "economy-balance.yml");
        YamlResourceBackfill.ensure(this, "discord.yml");
        this.playerEconomyService = new PlayerEconomyService(this);
        EconomyBalanceSettings economyBalanceSettings = EconomyBalanceSettings.load(this);
        this.economyInflationService = new EconomyInflationService(this, this.huskTownsApiHook, economyBalanceSettings);
        this.economyInflationService.start();
        this.economyValueService = new EconomyValueService(economyBalanceSettings, this.economyInflationService);
        this.customMobSubsystem = CustomMobSubsystem.bootstrap(this);

        CityLevelSettings levelSettings = CityLevelSettings.load(this);
        CityLevelRepository cityLevelRepository = new CityLevelRepository(this);
        MySqlLevelBackend mysqlBackend = new MySqlLevelBackend(new MySqlLevelBackend.Config(
                levelSettings.mySqlSettings().enabled(),
                levelSettings.mySqlSettings().host(),
                levelSettings.mySqlSettings().port(),
                levelSettings.mySqlSettings().database(),
                levelSettings.mySqlSettings().username(),
                levelSettings.mySqlSettings().password(),
                levelSettings.mySqlSettings().params()
        ));
        this.cityLevelStore = new CityLevelStore(this, cityLevelRepository, levelSettings, mysqlBackend);
        this.cityLevelStore.start();

        CityXpScanService cityXpScanService = new CityXpScanService(this, this.huskTownsApiHook, this.cityLevelStore, levelSettings);
        this.adminClaimBonusService = new AdminClaimBonusService(this);
        this.adminClaimBonusService.start();
        this.cityLevelService = new CityLevelService(
                this,
                this.huskTownsApiHook,
                this.cityLevelStore,
                levelSettings,
                cityXpScanService,
                this.economyValueService,
                this.adminClaimBonusService
        );
        StaffApprovalService staffApprovalService = new StaffApprovalService(this.cityLevelStore);
        this.cityTaxService = new CityTaxService(this, this.huskTownsApiHook, this.cityLevelStore, levelSettings, this.economyValueService);
        this.cityLevelRuntimeService = new CityLevelRuntimeService(this, this.huskTownsApiHook, this.cityLevelService, levelSettings);
        CityChallengeSettings challengeSettings = CityChallengeSettings.load(this);
        CityChallengeRepository challengeRepository = new CityChallengeRepository(this);
        MySqlChallengeBackend challengeMySqlBackend = new MySqlChallengeBackend(new MySqlChallengeBackend.Config(
                levelSettings.mySqlSettings().enabled(),
                levelSettings.mySqlSettings().host(),
                levelSettings.mySqlSettings().port(),
                levelSettings.mySqlSettings().database(),
                levelSettings.mySqlSettings().username(),
                levelSettings.mySqlSettings().password(),
                levelSettings.mySqlSettings().params()
        ));
        this.challengeStore = new ChallengeStore(
                this,
                challengeRepository,
                challengeMySqlBackend,
                levelSettings.storeFlushSeconds(),
                levelSettings.storeFlushBatchSize(),
                levelSettings.storeReplayBackoffSeconds(),
                levelSettings.storeShutdownDrainSeconds()
        );
        this.challengeStore.start();
        this.townMemberPermissionService = new TownMemberPermissionService(this, this.challengeStore);
        this.townMemberPermissionService.start();
        CityVaultSettings vaultSettings = CityVaultSettings.load(this);
        this.cityItemVaultService = new CityItemVaultService(
                this,
                this.challengeStore,
                this.huskTownsApiHook,
                this.townMemberPermissionService,
                vaultSettings
        );
        this.cityItemVaultService.start();
        DiscordBridgeSettings discordBridgeSettings = DiscordBridgeSettings.load(this);
        DiscordLogHelper discordLog = new DiscordLogHelper(this, discordBridgeSettings.verboseLogging());
        discordLog.info("discord bridge config loaded enabled=" + discordBridgeSettings.enabled()
                + " guildConfigured=" + discordBridgeSettings.guildConfigured()
                + " categoryConfigured=" + discordBridgeSettings.categoryConfigured()
                + " verificationCategoryConfigured=" + discordBridgeSettings.verificationCategoryConfigured()
                + " verificationChannelConfigured=" + discordBridgeSettings.verificationChannelConfigured()
                + " verifiedRoleConfigured=" + discordBridgeSettings.verifiedRoleConfigured()
                + " tokenConfigured=" + discordBridgeSettings.tokenConfigured()
                + " minStage=" + discordBridgeSettings.minStage().name()
                + " reconcileIntervalSeconds=" + discordBridgeSettings.reconcileIntervalSeconds()
                + " provisionAllTowns=" + discordBridgeSettings.provisionAllTowns()
                + " verboseLogging=" + discordBridgeSettings.verboseLogging());
        DiscordLinkRepository discordLinkRepository = new DiscordLinkRepository(this);
        discordLinkRepository.initialize();
        discordLog.info("discord repository initialized");
        DiscordIdentityLinkService discordIdentityLinkService = new DiscordIdentityLinkService(this, discordLinkRepository, discordBridgeSettings);
        this.discordBotService = new DiscordBotService(this, discordBridgeSettings, discordIdentityLinkService);
        CityDefenseSettings cityDefenseSettings = CityDefenseSettings.load(this, this.customMobSubsystem.mobRegistry());
        this.cityChallengeService = new CityChallengeService(
                this,
                this.huskTownsApiHook,
                this.cityLevelService,
                this.cityItemVaultService,
                this.challengeStore,
                challengeSettings,
                this.economyValueService
        );
        this.cityDefenseService = new CityDefenseService(
                this,
                this.huskTownsApiHook,
                this.cityLevelService,
                this.cityItemVaultService,
                this.townMemberPermissionService,
                this.challengeStore,
                cityDefenseSettings,
                this.economyValueService,
                this.customMobSubsystem.spawnService(),
                this.customMobSubsystem.effectsEngine()
        );
        this.cityLevelService.setDefenseService(this.cityDefenseService);
        CityLevelRequestService cityLevelRequestService = new CityLevelRequestService(
                this.huskTownsApiHook,
                staffApprovalService,
                this.cityLevelService,
                levelSettings,
                this.cityDefenseService
        );
        this.cityLevelGuiService = new CityLevelGuiService(
                this,
                this.cityLevelService,
                cityLevelRequestService,
                this.cityDefenseService
        );
        this.cityLevelUpgradeNoticeService = new CityLevelUpgradeNoticeService(
                this,
                this.huskTownsApiHook,
                this.cityLevelService,
                this.cityDefenseService
        );
        this.cityLevelService.setUpgradeAvailabilityListener(townId ->
                getServer().getScheduler().runTask(this, () -> this.cityLevelUpgradeNoticeService.refreshTown(townId, true)));
        CityWarSettings cityWarSettings = CityWarSettings.load(this);
        this.cityWarService = new CityWarService(
                this,
                this.huskTownsApiHook,
                this.cityLevelService,
                this.cityItemVaultService,
                cityWarSettings
        );
        this.cityShopService = new CityShopService(
                this,
                this.huskTownsApiHook,
                this.cityLevelService,
                this.playerEconomyService,
                this.townMemberPermissionService
        );
        this.discordTownSyncService = new DiscordTownSyncService(
                this,
                this.huskTownsApiHook,
                this.cityLevelService,
                discordBridgeSettings,
                discordLinkRepository,
                discordIdentityLinkService,
                this.discordBotService
        );
        discordIdentityLinkService.setPlayerLinkChangedListener(linkChange -> {
            if (this.discordTownSyncService != null) {
                if (linkChange != null && linkChange.discordUserId() != null) {
                    this.discordTownSyncService.cleanupLinkedDiscordUser(linkChange.discordUserId());
                }
                this.discordTownSyncService.reconcileAll();
            }
        });
        this.discordBotService.setReadyListener(() -> {
            if (this.discordTownSyncService != null) {
                this.discordTownSyncService.reconcileAll();
            }
        });
        this.cityItemVaultService.setContributionListener(event -> {
            int townId = event.townId();
            int amount = Math.max(1, event.amount());
            String townName = this.huskTownsApiHook.getTownById(townId).map(Town::getName).orElse("#" + townId);
            String materialKey = event.material() == null ? null : event.material().name();
            this.cityChallengeService.recordObjectiveForTown(
                    townId,
                    townName,
                    null,
                    it.patric.cittaexp.challenges.ChallengeObjectiveType.RESOURCE_CONTRIBUTION,
                    amount,
                    it.patric.cittaexp.challenges.ChallengeObjectiveContext.materialAction(materialKey, null, null)
            );
            this.cityChallengeService.recordObjectiveForTown(
                    townId,
                    townName,
                    null,
                    it.patric.cittaexp.challenges.ChallengeObjectiveType.VAULT_DELIVERY,
                    amount,
                    it.patric.cittaexp.challenges.ChallengeObjectiveContext.materialAction(materialKey, null, null)
            );
        });
        this.cityChallengeService.start();
        StaffEventRepository staffEventRepository = new StaffEventRepository(this);
        this.staffEventService = new StaffEventService(
                this,
                this.huskTownsApiHook,
                this.cityChallengeService,
                this.cityLevelService,
                this.cityItemVaultService,
                this.economyValueService,
                this.townMemberPermissionService,
                staffEventRepository
        );
        this.staffEventService.start();
        this.cityChallengeGuiService = new CityChallengeGuiService(
                this,
                this.huskTownsApiHook,
                this.townMemberPermissionService,
                this.cityChallengeService,
                this.staffEventService
        );
        this.cityItemVaultGuiService = new CityItemVaultGuiService(this, this.cityItemVaultService);
        CityCreateDialogFlow cityCreateDialogFlow = new CityCreateDialogFlow(this, this.huskTownsApiHook, this.playerEconomyService, this.economyValueService);
        TownDisbandDialogFlow townDisbandDialogFlow = new TownDisbandDialogFlow(this, this.huskTownsApiHook);
        TownRenameDialogFlow townRenameDialogFlow = new TownRenameDialogFlow(
                this,
                this.huskTownsApiHook,
                this.discordTownSyncService,
                this.economyValueService
        );
        TownEditDialogFlow townEditDialogFlow = new TownEditDialogFlow(
                this,
                this.huskTownsApiHook,
                this.discordTownSyncService,
                townRenameDialogFlow
        );
        HuskClaimsChunkGuard huskClaimsChunkGuard = new HuskClaimsChunkGuard(this);
        this.huskClaimsHookStateService = new HuskClaimsHookStateService(this);

        if (this.huskClaimsHookStateService.diagnostics().huskClaimsPresent()) {
            boolean denyOnGuardError = getConfig().getBoolean("city.huskclaims.guard.deny_on_guard_error", true);
            this.huskClaimsTownFallbackGuardListener = new HuskClaimsTownFallbackGuardListener(
                    this,
                    this.huskClaimsHookStateService,
                    new TownClaimOverlapChecker(this.huskTownsApiHook),
                    denyOnGuardError
            );
            getServer().getPluginManager().registerEvents(this.huskClaimsTownFallbackGuardListener, this);
        } else {
            this.huskClaimsTownFallbackGuardListener = null;
        }

        this.huskTownsClaimWorldSanityService = new HuskTownsClaimWorldSanityService(
                this,
                getLogger(),
                this.huskTownsApiHook,
                new File(huskTowns.getDataFolder(), "HuskTownsData.db")
        );
        this.claimBorderOverlayService = new ClaimBorderOverlayService(this, this.huskTownsApiHook);
        this.citySectionMapGuiService = new CitySectionMapGuiService(
                this,
                this.huskTownsApiHook,
                huskClaimsChunkGuard,
                this.cityLevelService,
                this.huskTownsClaimWorldSanityService,
                this.claimBorderOverlayService
        );
        this.cityClaimToolListener = new CityClaimToolListener(
                this.huskTownsApiHook,
                this.citySectionMapGuiService,
                this.claimBorderOverlayService,
                new PluginConfigUtils(this)
        );
        this.cityTownListGuiService = new CityTownListGuiService(
                this,
                this.huskTownsApiHook,
                this.cityLevelService
        );
        this.cityWikiDialogService = new CityWikiDialogService(this);
        this.cityChunkTrustCommandService = new CityChunkTrustCommandService(this, this.huskTownsApiHook);
        this.cityRelationsGuiService = new CityRelationsGuiService(
                this,
                this.huskTownsApiHook
        );
        File rolesFile = new File(huskTowns.getDataFolder(), "roles.yml");
        new CittaExpTownRoleGovernanceService(getLogger(), rolesFile).ensureGovernedRoles();
        int normalizedMayors = this.huskTownsApiHook.normalizeMayorRoleWeights();
        if (normalizedMayors > 0) {
            getLogger().warning("[players-gui] riallineati " + normalizedMayors
                    + " sindaci al peso massimo della ladder ruoli attuale.");
        }
        HuskTownsRoleLadderService roleLadderService = new HuskTownsRoleLadderService(rolesFile, getLogger());
        this.playerActivityService = new PlayerActivityService(
                this,
                new PlayerActivityRepository(getDataFolder().toPath().resolve("player-activity.db"))
        );
        this.playerActivityService.start();
        this.inactiveTownMemberPruneService = new InactiveTownMemberPruneService(
                this,
                this.huskTownsApiHook,
                this.playerActivityService,
                roleLadderService
        );
        CityPlayersActionService cityPlayersActionService = new CityPlayersActionService(this, this.huskTownsApiHook, roleLadderService);
        this.cityPlayersGuiService = new CityPlayersGuiService(
                this,
                this.huskTownsApiHook,
                cityPlayersActionService,
                roleLadderService,
                townEditDialogFlow,
                townDisbandDialogFlow,
                this.citySectionMapGuiService,
                this.cityTownListGuiService,
                this.cityRelationsGuiService,
                this.cityItemVaultGuiService,
                this.cityLevelService,
                this.cityItemVaultService,
                this.townMemberPermissionService,
                this.cityWikiDialogService,
                this.playerActivityService
        );
        this.cityItemVaultGuiService.setBankReturnOpener(this.cityPlayersGuiService::openBankFromVault);
        this.citySectionMapGuiService.setClaimSettingsOpener(this.cityPlayersGuiService::openClaimSettingsForOwnTown);
        this.cityHubGuiService = new CityHubGuiService(
                this,
                this.huskTownsApiHook,
                this.cityLevelService,
                this.cityLevelGuiService,
                this.cityPlayersGuiService,
                this.cityChallengeGuiService,
                this.citySectionMapGuiService,
                this.cityRelationsGuiService,
                this.cityItemVaultGuiService,
                this.cityDefenseService,
                this.cityWarService,
                townEditDialogFlow,
                townDisbandDialogFlow,
                this.townMemberPermissionService,
                this.cityWikiDialogService
        );
        this.cityWikiDialogService.setHubOpener(this.cityHubGuiService::openForOwnTown);
        this.cityWikiDialogService.setBankOpener(player -> this.cityPlayersGuiService.openBankForOwnTown(player, it.patric.cittaexp.playersgui.CityPlayersBackTarget.HUB));
        this.cityWikiDialogService.setClaimSettingsOpener(this.cityPlayersGuiService::openClaimSettingsForOwnTown);
        this.cityWikiDialogService.setTerritoriesOpener(this.citySectionMapGuiService::openForOwnTown);
        this.cityWikiDialogService.setDefenseOpener(this.cityHubGuiService::openDefenseForOwnTown);
        this.cityChallengeGuiService.setDefenseOpener(player -> this.cityHubGuiService.openDefenseForOwnTown(player));
        DiscordCommandService discordCommandService = new DiscordCommandService(
                this,
                discordBridgeSettings,
                discordIdentityLinkService,
                this.discordTownSyncService,
                this.huskTownsApiHook,
                this.discordBotService
        );
        getLogger().info("HuskClaims guard chunk-overlay: " + (huskClaimsChunkGuard.isAvailable() ? "enabled" : "disabled"));
        HuskClaimsHookStateService.Diagnostics hookDiagnostics = this.huskClaimsHookStateService.diagnostics();
        getLogger().info("HuskClaims guard mode=" + hookDiagnostics.mode().probeValue()
                + ", reason=" + hookDiagnostics.reasonCode()
                + ", hookEnabled=" + hookDiagnostics.huskTownsHookEnabled()
                + ", config=" + hookDiagnostics.configPath());
        getServer().getPluginManager().registerEvents(this.cityPlayersGuiService, this);
        getServer().getPluginManager().registerEvents(this.playerActivityService, this);
        getServer().getPluginManager().registerEvents(this.cityTownListGuiService, this);
        getServer().getPluginManager().registerEvents(this.citySectionMapGuiService, this);
        getServer().getPluginManager().registerEvents(this.claimBorderOverlayService, this);
        getServer().getPluginManager().registerEvents(this.cityClaimToolListener, this);
        getServer().getPluginManager().registerEvents(this.cityRelationsGuiService, this);
        getServer().getPluginManager().registerEvents(this.townMemberPermissionService, this);
        getServer().getPluginManager().registerEvents(this.adminClaimBonusService, this);
        getServer().getPluginManager().registerEvents(this.cityLevelGuiService, this);
        getServer().getPluginManager().registerEvents(this.cityLevelUpgradeNoticeService, this);
        getServer().getPluginManager().registerEvents(this.cityChallengeGuiService, this);
        getServer().getPluginManager().registerEvents(this.cityItemVaultGuiService, this);
        getServer().getPluginManager().registerEvents(this.cityHubGuiService, this);
        getServer().getPluginManager().registerEvents(this.cityDefenseService, this);
        getServer().getPluginManager().registerEvents(this.cityWarService, this);
        getServer().getPluginManager().registerEvents(this.cityWarService.rollbackService(), this);
        getServer().getPluginManager().registerEvents(this.cityShopService, this);
        getServer().getPluginManager().registerEvents(this.customMobSubsystem.combatListener(), this);
        getServer().getPluginManager().registerEvents(this.discordTownSyncService, this);
        getServer().getPluginManager().registerEvents(discordCommandService, this);
        getServer().getPluginManager().registerEvents(new DiscordVerificationCommandBlockListener(discordCommandService), this);
        getServer().getPluginManager().registerEvents(new CityChallengeListener(this.cityChallengeService, this.huskTownsApiHook), this);
        getServer().getPluginManager().registerEvents(new TownLevelCommandBlockListener(this), this);
        getServer().getPluginManager().registerEvents(new TownWarCommandBlockListener(this, this.cityWarService), this);
        getServer().getPluginManager().registerEvents(new HuskTownsRawCommandBlockListener(this, this.huskTownsApiHook), this);
        getServer().getPluginManager().registerEvents(new TownSpawnerCapListener(this, this.huskTownsApiHook, this.cityLevelService), this);
        getServer().getPluginManager().registerEvents(new GlobalHopperChunkCapListener(this), this);
        this.cityLevelRuntimeService.start();
        this.cityLevelUpgradeNoticeService.start();
        this.cityLevelService.syncAllTownCaps();
        this.cityTaxService.start();
        this.cityDefenseService.start();
        this.cityWarService.start();
        this.cityShopService.start();
        this.inactiveTownMemberPruneService.start();
        this.huskTownsClaimWorldSanityService.start();
        this.claimBorderOverlayService.start();
        this.discordTownSyncService.start();
        this.discordBotService.start();

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            commands.registrar().register(
                    CityCommandTree.create(
                            this,
                            cityCreateDialogFlow,
                            this.cityHubGuiService,
                            this.cityLevelService,
                            this.huskTownsApiHook,
                            this.citySectionMapGuiService,
                            this.cityTownListGuiService,
                            discordCommandService,
                            this.cityWikiDialogService,
                            this.cityChunkTrustCommandService,
                            this.cityItemVaultService
                    ).build(),
                    "Wrapper minimale comandi citta",
                    List.of()
            );
            commands.registrar().register(
                    CityCommandTree.createVerifyCommand(discordCommandService).build(),
                    "Avvia il collegamento Discord via codice DM",
                    List.of()
            );
            commands.registrar().register(
                    CittaExpCommandTree.create(
                            this,
                            new CittaExpCommandTree.StaffLevelCommandContext(
                                    cityLevelRequestService,
                                    this.cityLevelStore,
                                    this.cityTaxService,
                                    this.huskClaimsHookStateService,
                                    this.huskClaimsTownFallbackGuardListener,
                                    this.cityChallengeService,
                                    this.staffEventService,
                                    this.cityDefenseService,
                                    this.customMobSubsystem,
                                    this.economyInflationService
                            ),
                            this.cityHubGuiService,
                            this.cityPlayersGuiService,
                            this.cityRelationsGuiService,
                            this.cityLevelService,
                            this.huskTownsApiHook,
                            this.huskTownsClaimWorldSanityService,
                            discordCommandService
                    ).build(),
                    "Diagnostica bootstrap CittaEXP",
                    List.of()
            );
        });

        getLogger().log(
                Level.INFO,
                "CittaEXP bootstrap attivo. Backbone: HuskTowns {0}, API hook: ready",
                huskTowns.getDescription().getVersion()
        );
    }

    @Override
    public void onDisable() {
        if (cityLevelRuntimeService != null) {
            cityLevelRuntimeService.stop();
        }
        if (cityLevelUpgradeNoticeService != null) {
            cityLevelUpgradeNoticeService.stop();
        }
        if (cityTaxService != null) {
            cityTaxService.stop();
        }
        if (cityChallengeService != null) {
            cityChallengeService.stop();
        }
        if (staffEventService != null) {
            staffEventService.stop();
        }
        if (cityDefenseService != null) {
            cityDefenseService.stop();
        }
        if (cityWarService != null) {
            cityWarService.stop();
        }
        if (cityShopService != null) {
            cityShopService.stop();
        }
        if (huskTownsClaimWorldSanityService != null) {
            huskTownsClaimWorldSanityService.stop();
        }
        if (discordTownSyncService != null) {
            discordTownSyncService.stop();
        }
        if (discordBotService != null) {
            discordBotService.stop();
        }
        if (economyInflationService != null) {
            economyInflationService.stop();
        }
        if (customMobSubsystem != null) {
            customMobSubsystem.stop();
        }
        if (cityItemVaultService != null) {
            cityItemVaultService.stop();
        }
        if (inactiveTownMemberPruneService != null) {
            inactiveTownMemberPruneService.stop();
        }
        if (playerActivityService != null) {
            playerActivityService.stop();
        }
        if (townMemberPermissionService != null) {
            townMemberPermissionService.stop();
        }
        if (adminClaimBonusService != null) {
            adminClaimBonusService.stop();
        }
        if (cityLevelStore != null) {
            cityLevelStore.stop();
        }
        if (challengeStore != null) {
            challengeStore.stop();
        }
        if (cityPlayersGuiService != null) {
            cityPlayersGuiService.clearAllSessions();
        }
        if (cityHubGuiService != null) {
            cityHubGuiService.clearAllSessions();
        }
        if (cityTownListGuiService != null) {
            cityTownListGuiService.clearAllSessions();
        }
        if (citySectionMapGuiService != null) {
            citySectionMapGuiService.clearAllSessions();
        }
        if (claimBorderOverlayService != null) {
            claimBorderOverlayService.stop();
        }
        if (cityRelationsGuiService != null) {
            cityRelationsGuiService.clearAllSessions();
        }
        if (cityLevelGuiService != null) {
            cityLevelGuiService.clearAllSessions();
        }
        if (cityChallengeGuiService != null) {
            cityChallengeGuiService.clearAllSessions();
        }
        if (cityItemVaultGuiService != null) {
            cityItemVaultGuiService.clearAllSessions();
        }
        getLogger().info("CittaEXP bootstrap disabilitato.");
    }

    public boolean scheduleFullReload(CommandSender sender) {
        if (!reloadInProgress.compareAndSet(false, true)) {
            if (sender != null) {
                sender.sendMessage("[CittaEXP] Reload gia in corso.");
            }
            return false;
        }

        if (sender != null) {
            sender.sendMessage("[CittaEXP] Full reload in corso: ricarico config e YAML di CittaEXP...");
        }

        getServer().getScheduler().runTask(this, () -> {
            try {
                PluginManager pluginManager = getServer().getPluginManager();
                pluginManager.disablePlugin(this);
                pluginManager.enablePlugin(this);
                if (sender != null) {
                    sender.sendMessage(isEnabled()
                            ? "[CittaEXP] Full reload completato."
                            : "[CittaEXP] Full reload fallito: plugin rimasto disabilitato.");
                }
            } catch (RuntimeException ex) {
                getLogger().log(Level.SEVERE, "Full reload CittaEXP fallito", ex);
                if (sender != null) {
                    sender.sendMessage("[CittaEXP] Full reload fallito: " + (ex.getMessage() == null ? "errore sconosciuto" : ex.getMessage()));
                }
            } finally {
                reloadInProgress.set(false);
            }
        });
        return true;
    }
}
