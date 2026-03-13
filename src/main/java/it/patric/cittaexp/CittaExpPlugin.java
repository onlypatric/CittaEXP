package it.patric.cittaexp;

import it.patric.cittaexp.command.CittaExpCommandTree;
import it.patric.cittaexp.command.CityCommandTree;
import it.patric.cittaexp.citylistgui.CityTownListGuiService;
import it.patric.cittaexp.createtown.CityCreateDialogFlow;
import it.patric.cittaexp.disbandtown.TownDisbandDialogFlow;
import it.patric.cittaexp.edittown.TownEditDialogFlow;
import it.patric.cittaexp.integration.huskclaims.HuskClaimsChunkGuard;
import it.patric.cittaexp.integration.huskclaims.HuskClaimsHookStateService;
import it.patric.cittaexp.integration.huskclaims.HuskClaimsTownFallbackGuardListener;
import it.patric.cittaexp.integration.huskclaims.TownClaimOverlapChecker;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.levels.CityLevelRepository;
import it.patric.cittaexp.levels.CityLevelRequestService;
import it.patric.cittaexp.levels.CityLevelRuntimeService;
import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.levels.CityLevelSettings;
import it.patric.cittaexp.levels.CityLevelStore;
import it.patric.cittaexp.levels.CityTaxService;
import it.patric.cittaexp.levels.CityXpScanService;
import it.patric.cittaexp.levels.MySqlLevelBackend;
import it.patric.cittaexp.levels.StaffApprovalService;
import it.patric.cittaexp.levels.TownLevelCommandBlockListener;
import it.patric.cittaexp.levels.TownSpawnerCapListener;
import it.patric.cittaexp.manageclaim.CitySectionMapGuiService;
import it.patric.cittaexp.playersgui.CityPlayersActionService;
import it.patric.cittaexp.playersgui.CityPlayersGuiService;
import it.patric.cittaexp.playersgui.HuskTownsRoleLadderService;
import it.patric.cittaexp.renametown.TownRenameDialogFlow;
import net.william278.husktowns.api.HuskTownsAPI;
import java.io.File;
import java.util.List;
import java.util.logging.Level;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class CittaExpPlugin extends JavaPlugin {

    private static final String HUSK_TOWNS_PLUGIN = "HuskTowns";
    private HuskTownsApiHook huskTownsApiHook;
    private CityPlayersGuiService cityPlayersGuiService;
    private CityTownListGuiService cityTownListGuiService;
    private CitySectionMapGuiService citySectionMapGuiService;
    private CityLevelRuntimeService cityLevelRuntimeService;
    private CityTaxService cityTaxService;
    private CityLevelStore cityLevelStore;
    private HuskClaimsHookStateService huskClaimsHookStateService;
    private HuskClaimsTownFallbackGuardListener huskClaimsTownFallbackGuardListener;

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
        saveResource("levels.yml", false);
        saveResource("materials-xp.yml", false);

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
        CityLevelService cityLevelService = new CityLevelService(this, this.huskTownsApiHook, this.cityLevelStore, levelSettings, cityXpScanService);
        StaffApprovalService staffApprovalService = new StaffApprovalService(this.cityLevelStore);
        CityLevelRequestService cityLevelRequestService = new CityLevelRequestService(
                this.huskTownsApiHook,
                staffApprovalService,
                cityLevelService,
                levelSettings
        );
        this.cityTaxService = new CityTaxService(this, this.huskTownsApiHook, this.cityLevelStore, levelSettings);
        this.cityLevelRuntimeService = new CityLevelRuntimeService(this, this.huskTownsApiHook, cityLevelService, levelSettings);

        CityCreateDialogFlow cityCreateDialogFlow = new CityCreateDialogFlow(this, this.huskTownsApiHook);
        TownDisbandDialogFlow townDisbandDialogFlow = new TownDisbandDialogFlow(this, this.huskTownsApiHook);
        TownRenameDialogFlow townRenameDialogFlow = new TownRenameDialogFlow(this, this.huskTownsApiHook);
        TownEditDialogFlow townEditDialogFlow = new TownEditDialogFlow(this, this.huskTownsApiHook, townRenameDialogFlow);
        HuskClaimsChunkGuard huskClaimsChunkGuard = new HuskClaimsChunkGuard(this);
        this.huskClaimsHookStateService = new HuskClaimsHookStateService(this);

        if (this.huskClaimsHookStateService.isFallbackActive()) {
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

        this.citySectionMapGuiService = new CitySectionMapGuiService(
                this,
                this.huskTownsApiHook,
                huskClaimsChunkGuard,
                cityLevelService
        );
        this.cityTownListGuiService = new CityTownListGuiService(
                this,
                this.huskTownsApiHook,
                cityLevelService
        );
        File rolesFile = new File(huskTowns.getDataFolder(), "roles.yml");
        HuskTownsRoleLadderService roleLadderService = new HuskTownsRoleLadderService(rolesFile, getLogger());
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
                cityLevelService
        );
        getLogger().info("HuskClaims guard chunk-overlay: " + (huskClaimsChunkGuard.isAvailable() ? "enabled" : "disabled"));
        HuskClaimsHookStateService.Diagnostics hookDiagnostics = this.huskClaimsHookStateService.diagnostics();
        getLogger().info("HuskClaims guard mode=" + hookDiagnostics.mode().probeValue()
                + ", reason=" + hookDiagnostics.reasonCode()
                + ", hookEnabled=" + hookDiagnostics.huskTownsHookEnabled()
                + ", config=" + hookDiagnostics.configPath());
        getServer().getPluginManager().registerEvents(this.cityPlayersGuiService, this);
        getServer().getPluginManager().registerEvents(this.cityTownListGuiService, this);
        getServer().getPluginManager().registerEvents(this.citySectionMapGuiService, this);
        getServer().getPluginManager().registerEvents(new TownLevelCommandBlockListener(this), this);
        getServer().getPluginManager().registerEvents(new TownSpawnerCapListener(this, this.huskTownsApiHook, cityLevelService), this);
        this.cityLevelRuntimeService.start();
        this.cityTaxService.start();

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            commands.registrar().register(
                    CityCommandTree.create(
                            this,
                            cityCreateDialogFlow,
                            this.cityPlayersGuiService,
                            cityLevelService,
                            cityLevelRequestService,
                            this.huskTownsApiHook,
                            this.citySectionMapGuiService,
                            this.cityTownListGuiService
                    ).build(),
                    "Wrapper minimale comandi citta",
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
                                    this.huskClaimsTownFallbackGuardListener
                            ),
                            this.cityPlayersGuiService,
                            this.huskTownsApiHook
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
        if (cityTaxService != null) {
            cityTaxService.stop();
        }
        if (cityLevelStore != null) {
            cityLevelStore.stop();
        }
        if (cityPlayersGuiService != null) {
            cityPlayersGuiService.clearAllSessions();
        }
        if (cityTownListGuiService != null) {
            cityTownListGuiService.clearAllSessions();
        }
        if (citySectionMapGuiService != null) {
            citySectionMapGuiService.clearAllSessions();
        }
        getLogger().info("CittaEXP bootstrap disabilitato.");
    }
}
