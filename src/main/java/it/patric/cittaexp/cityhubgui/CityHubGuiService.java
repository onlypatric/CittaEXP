package it.patric.cittaexp.cityhubgui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import dev.lone.itemsadder.api.CustomStack;
import it.patric.cittaexp.challenges.CityChallengeGuiService;
import it.patric.cittaexp.defense.CityDefenseService;
import it.patric.cittaexp.defense.CityDefenseTier;
import it.patric.cittaexp.disbandtown.TownDisbandDialogFlow;
import it.patric.cittaexp.edittown.TownEditDialogFlow;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.levels.TownStage;
import it.patric.cittaexp.permissions.TownMemberPermission;
import it.patric.cittaexp.permissions.TownMemberPermissionService;
import it.patric.cittaexp.levelsgui.CityLevelGuiService;
import it.patric.cittaexp.manageclaim.CitySectionMapGuiService;
import it.patric.cittaexp.playersgui.CityPlayersBackTarget;
import it.patric.cittaexp.playersgui.CityPlayersGuiService;
import it.patric.cittaexp.playersgui.CityPlayersSession;
import it.patric.cittaexp.relationsgui.CityRelationsGuiService;
import it.patric.cittaexp.text.UiLoreStyle;
import it.patric.cittaexp.war.CityWarService;
import it.patric.cittaexp.utils.DialogConfirmHelper;
import it.patric.cittaexp.utils.DialogInputUtils;
import it.patric.cittaexp.utils.DialogViewUtils;
import it.patric.cittaexp.utils.GuiHeadTextures;
import it.patric.cittaexp.utils.GuiItemFactory;
import it.patric.cittaexp.utils.GuiLayoutUtils;
import it.patric.cittaexp.utils.PluginConfigUtils;
import it.patric.cittaexp.utils.TexturedGuiSupport;
import it.patric.cittaexp.vault.CityItemVaultGuiService;
import it.patric.cittaexp.wiki.CityWikiBackTarget;
import it.patric.cittaexp.wiki.CityWikiDialogService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.william278.husktowns.town.Member;
import net.william278.husktowns.town.Privilege;
import net.william278.husktowns.town.Town;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

public final class CityHubGuiService implements Listener {

    public enum WarSampleState {
        LIST,
        LIVE,
        INCOMING,
        BUSY,
        UNAVAILABLE,
        ALLY_WARNING,
        DECLARE
    }

    private static final int GUI_SIZE = 54;
    private static final int SUB_GUI_SIZE = 27;
    private static final int DEFENSE_START_GUI_SIZE = 54;
    private static final int DEFENSE_LIVE_GUI_SIZE = 36;
    private static final int WAR_LIST_GUI_SIZE = 36;
    private static final int WAR_LIVE_GUI_SIZE = 45;
    private static final int COMMAND_GUI_SIZE = 54;
    private static final int DANGER_GUI_SIZE = 54;
    private static final String HUB_FONT_IMAGE_ID = "cittaexp_gui:hub_gui_bg";
    private static final String DEFENSE_FONT_IMAGE_ID = "cittaexp_gui:defense_wars_bg";
    private static final String MOB_INVASION_FONT_IMAGE_ID = "cittaexp_gui:mob_invasion_bg";
    private static final String MOB_INVASION_ACTIVE_FONT_IMAGE_ID = "cittaexp_gui:mob_invasion_active_bg";
    private static final String WAR_LIST_FONT_IMAGE_ID = "cittaexp_gui:war_list_9x4";
    private static final String WAR_LIVE_FONT_IMAGE_ID = "cittaexp_gui:war_active_overview_9x5";
    private static final String TRANSPARENT_BUTTON_ID = "cittaexp_gui:transparent_button";
    private static final int MEMBER_PAGE_SIZE = 13;

    private static final int[] SLOTS_CITY = {0, 1, 2, 9, 10, 11};
    private static final int[] SLOTS_QUEST = {3, 4, 5, 12, 13, 14};
    private static final int[] SLOTS_VAULT = {6, 7, 8, 15, 16, 17};
    private static final int[] SLOTS_LEVELS = {24, 25, 26, 33, 34, 35};
    private static final int[] SLOTS_TERRITORIES = {42, 43, 44};
    private static final int[] SLOTS_CLOSE = {46, 47, 48};
    private static final int[] SLOTS_HELP = {50, 51, 52};

    private static final int SLOT_ACTION_COMMAND_CENTER = 23;
    private static final int SLOT_ACTION_DEFENSE = 32;
    private static final int SLOT_ACTION_LEAVE = 41;

    private static final int SLOT_MEMBER_PREV = 36;
    private static final int SLOT_MEMBER_NEXT = 40;
    private static final int[] MEMBER_ITEM_SLOTS = {
            18, 19, 20, 21, 22,
            27, 28, 29, 30, 31,
            37, 38, 39
    };

    private static final int SLOT_BACK = 18;
    private static final int SLOT_CLOSE = 26;

    private static final int SLOT_TERRITORI_MAPPA = 11;
    private static final int SLOT_TERRITORI_REGOLE = 13;
    private static final int SLOT_TERRITORI_INFO = 15;

    private static final int SLOT_TESORO_SALDO = 11;
    private static final int SLOT_TESORO_VAULT = 13;
    private static final int SLOT_TESORO_MISSIONI = 15;
    private static final int SLOT_TESORO_BANCA = 22;

    private static final int SLOT_BANCA_DEP_500 = 10;
    private static final int SLOT_BANCA_DEP_2000 = 11;
    private static final int SLOT_BANCA_DEP_CUSTOM = 12;
    private static final int SLOT_BANCA_BALANCE = 13;
    private static final int SLOT_BANCA_WD_CUSTOM = 14;
    private static final int SLOT_BANCA_WD_2000 = 15;
    private static final int SLOT_BANCA_WD_500 = 16;

    private static final int SLOT_COMANDO_HEADER = 4;
    private static final int SLOT_COMANDO_DIPLOMAZIA = 19;
    private static final int SLOT_COMANDO_REGOLE = 22;
    private static final int SLOT_COMANDO_EDIT = 25;
    private static final int SLOT_COMANDO_DANGER = 40;
    private static final int SLOT_COMANDO_BACK = 45;

    private static final int SLOT_DANGER_HEADER = 4;
    private static final int SLOT_DANGER_LEAVE = 29;
    private static final int SLOT_DANGER_DISBAND = 33;
    private static final int SLOT_DANGER_BACK = 45;

    private static final int SLOT_DEFENSE_TIER_L1 = 10;
    private static final int SLOT_DEFENSE_TIER_L2 = 13;
    private static final int SLOT_DEFENSE_TIER_L3 = 16;
    private static final int[] SLOTS_DEFENSE_START_TIERS = {
            22
    };
    private static final int SLOT_DEFENSE_PREV_PAGE = 18;
    private static final int SLOT_DEFENSE_STATUS = 22;
    private static final int SLOT_DEFENSE_REFRESH = 23;
    private static final int SLOT_DEFENSE_NEXT_PAGE = 26;
    private static final int[] SLOTS_DEFENSE_START_BACK = {46, 47, 48};
    private static final int[] SLOTS_DEFENSE_LIVE_GUARDIAN = {0, 1, 2, 9, 10, 11, 18, 19, 20};
    private static final int[] SLOTS_DEFENSE_LIVE_WAVE = {3, 4, 5, 12, 13, 14, 21, 22, 23};
    private static final int[] SLOTS_DEFENSE_LIVE_TIMER = {6, 7, 8, 15, 16, 17, 24, 25, 26};
    private static final int[] SLOTS_DEFENSE_LIVE_BACK = {28, 29, 30};
    private static final int[] SLOTS_DEFENSE_LIVE_STOP = {32, 33, 34};
    private static final int[] SLOTS_DEFENSE_MENU_INVASION = {
            0, 1, 2, 3,
            9, 10, 11, 12,
            18, 19, 20, 21,
            27, 28, 29, 30,
            36, 37, 38, 39
    };
    private static final int[] SLOTS_DEFENSE_MENU_WAR = {
            4, 5, 6, 7, 8,
            13, 14, 15, 16, 17,
            22, 23, 24, 25, 26
    };
    private static final int[] SLOTS_DEFENSE_MENU_SHIELD = {
            31, 32, 33, 34, 35,
            40, 41, 42, 43, 44
    };
    private static final int[] SLOTS_DEFENSE_MENU_BACK = {46, 47, 48};
    private static final int[] SLOTS_DEFENSE_MENU_HELP = {50, 51, 52};
    private static final int[] SLOTS_WAR_LIST_PREV = {0, 9, 18};
    private static final int[] SLOTS_WAR_LIST_TARGETS = {
            1, 2, 3, 4, 5, 6, 7,
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25
    };
    private static final int[] SLOTS_WAR_LIST_NEXT = {8, 17, 26};
    private static final int[] SLOTS_WAR_LIST_BACK = {28, 29, 30};
    private static final int[] SLOTS_WAR_LIVE_ATTACKER = {0, 1, 2, 9, 10, 11, 18, 19, 20, 27, 28, 29};
    private static final int[] SLOTS_WAR_LIVE_DEFENDER = {3, 4, 5, 12, 13, 14, 21, 22, 23, 30, 31, 32};
    private static final int[] SLOTS_WAR_LIVE_OVERVIEW = {6, 7, 8, 15, 16, 17, 24, 25, 26, 33, 34, 35};
    private static final int[] SLOTS_WAR_LIVE_BACK = {37, 38, 39};
    private static final int[] SLOTS_WAR_LIVE_SURRENDER = {41, 42, 43};

    private static final ClickCallback.Options CLICK_OPTIONS = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(2))
            .build();

    private final Plugin plugin;
    private final PluginConfigUtils cfg;
    private final HuskTownsApiHook huskTownsApiHook;
    private final CityLevelService cityLevelService;
    private final CityLevelGuiService cityLevelGuiService;
    private final CityPlayersGuiService cityPlayersGuiService;
    private final CityChallengeGuiService cityChallengeGuiService;
    private final CitySectionMapGuiService citySectionMapGuiService;
    private final CityRelationsGuiService cityRelationsGuiService;
    private final CityItemVaultGuiService cityItemVaultGuiService;
    private final CityDefenseService cityDefenseService;
    private final CityWarService cityWarService;
    private final TownEditDialogFlow townEditDialogFlow;
    private final TownDisbandDialogFlow townDisbandDialogFlow;
    private final TownMemberPermissionService permissionService;
    private final CityWikiDialogService cityWikiDialogService;
    private final ConcurrentMap<UUID, HubSession> sessionsByViewer = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Integer> memberPageByViewer = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Integer> defenseTierPageByViewer = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Integer> warPageByViewer = new ConcurrentHashMap<>();

    public CityHubGuiService(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            CityLevelService cityLevelService,
            CityLevelGuiService cityLevelGuiService,
            CityPlayersGuiService cityPlayersGuiService,
            CityChallengeGuiService cityChallengeGuiService,
            CitySectionMapGuiService citySectionMapGuiService,
            CityRelationsGuiService cityRelationsGuiService,
            CityItemVaultGuiService cityItemVaultGuiService,
            CityDefenseService cityDefenseService,
            CityWarService cityWarService,
            TownEditDialogFlow townEditDialogFlow,
            TownDisbandDialogFlow townDisbandDialogFlow,
            TownMemberPermissionService permissionService,
            CityWikiDialogService cityWikiDialogService
    ) {
        this.plugin = plugin;
        this.cfg = new PluginConfigUtils(plugin);
        this.huskTownsApiHook = huskTownsApiHook;
        this.cityLevelService = cityLevelService;
        this.cityLevelGuiService = cityLevelGuiService;
        this.cityPlayersGuiService = cityPlayersGuiService;
        this.cityChallengeGuiService = cityChallengeGuiService;
        this.citySectionMapGuiService = citySectionMapGuiService;
        this.cityRelationsGuiService = cityRelationsGuiService;
        this.cityItemVaultGuiService = cityItemVaultGuiService;
        this.cityDefenseService = cityDefenseService;
        this.cityWarService = cityWarService;
        this.townEditDialogFlow = townEditDialogFlow;
        this.townDisbandDialogFlow = townDisbandDialogFlow;
        this.permissionService = permissionService;
        this.cityWikiDialogService = cityWikiDialogService;
    }

    public void clearAllSessions() {
        sessionsByViewer.clear();
        memberPageByViewer.clear();
        defenseTierPageByViewer.clear();
        warPageByViewer.clear();
    }

    public void openForOwnTown(Player player) {
        Optional<HubSession> session = createOwnTownSession(player);
        if (session.isEmpty()) {
            return;
        }
        sessionsByViewer.put(player.getUniqueId(), session.get());
        memberPageByViewer.put(player.getUniqueId(), 0);
        defenseTierPageByViewer.put(player.getUniqueId(), 0);
        openHome(player, session.get());
    }

    public void openDefenseForOwnTown(Player player) {
        Optional<HubSession> session = createOwnTownSession(player);
        if (session.isEmpty()) {
            return;
        }
        sessionsByViewer.put(player.getUniqueId(), session.get());
        memberPageByViewer.put(player.getUniqueId(), 0);
        defenseTierPageByViewer.put(player.getUniqueId(), 0);
        openDefenseMenu(player, session.get());
    }

    public void openDefenseLiveSampleForOwnTown(Player player) {
        Optional<HubSession> session = createOwnTownSession(player);
        if (session.isEmpty()) {
            return;
        }
        sessionsByViewer.put(player.getUniqueId(), session.get());
        memberPageByViewer.put(player.getUniqueId(), 0);
        defenseTierPageByViewer.put(player.getUniqueId(), 0);
        openDefenseLive(player, session.get(), Optional.of(sampleDefenseSnapshot(session.get(), player)));
    }

    public void openWarSampleForOwnTown(Player player, WarSampleState state) {
        Optional<HubSession> sessionOptional = createOwnTownSession(player);
        if (sessionOptional.isEmpty()) {
            return;
        }
        HubSession session = new HubSession(
                sessionOptional.get().sessionId(),
                sessionOptional.get().viewerId(),
                sessionOptional.get().townId(),
                sessionOptional.get().townName(),
                sessionOptional.get().mayorId(),
                true
        );
        sessionsByViewer.put(player.getUniqueId(), session);
        memberPageByViewer.put(player.getUniqueId(), 0);
        defenseTierPageByViewer.put(player.getUniqueId(), 0);
        warPageByViewer.put(player.getUniqueId(), 0);
        switch (state) {
            case LIST -> openWarList(player, session);
            case LIVE -> openWarLive(player, session, sampleWarOverview(session));
            case INCOMING -> openWarIncomingDialogSample(player, session);
            case BUSY -> openWarBusyDialog(player, sampleWarOverview(session));
            case UNAVAILABLE -> openWarUnavailableDialog(player);
            case ALLY_WARNING -> openWarAllyWarningDialogSample(player, session);
            case DECLARE -> openWarDeclareDialogSample(player, session);
        }
    }

    private Optional<HubSession> createOwnTownSession(Player player) {
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty()) {
            player.sendMessage(cfg.msg("city.hub.errors.no_town", "<red>Devi essere membro attivo di una citta.</red>"));
            return Optional.empty();
        }
        Town town = member.get().town();
        return Optional.of(new HubSession(
                UUID.randomUUID(),
                player.getUniqueId(),
                town.getId(),
                town.getName(),
                town.getMayor(),
                false
        ));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getRawSlot() < 0) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof HubHolder holder)) {
            return;
        }
        if (event.getClickedInventory() != top) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);

        HubSession session = sessionsByViewer.get(player.getUniqueId());
        if (session == null || !session.sessionId().equals(holder.sessionId())) {
            player.closeInventory();
            memberPageByViewer.remove(player.getUniqueId());
            defenseTierPageByViewer.remove(player.getUniqueId());
            warPageByViewer.remove(player.getUniqueId());
            return;
        }

        int slot = event.getRawSlot();
        switch (holder.view()) {
            case HOME -> handleHomeClick(player, session, slot);
            case DEFENSE_MENU -> handleDefenseMenuClick(player, session, slot);
            case DEFENSE_START -> handleDefenseStartClick(player, session, slot);
            case DEFENSE_LIVE -> handleDefenseLiveClick(player, session, slot);
            case WAR_LIST -> handleWarListClick(player, session, slot);
            case WAR_LIVE -> handleWarLiveClick(player, session, slot);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessionsByViewer.remove(event.getPlayer().getUniqueId());
        memberPageByViewer.remove(event.getPlayer().getUniqueId());
        defenseTierPageByViewer.remove(event.getPlayer().getUniqueId());
        warPageByViewer.remove(event.getPlayer().getUniqueId());
    }

    private void handleHomeClick(Player player, HubSession session, int slot) {
        if (contains(SLOTS_CITY, slot)) {
            cityRelationsGuiService.open(player, toPlayersSession(session));
            return;
        }
        if (contains(SLOTS_QUEST, slot)) {
            cityChallengeGuiService.open(player, toPlayersSession(session));
            return;
        }
        if (contains(SLOTS_VAULT, slot)) {
            cityPlayersGuiService.openBankForOwnTown(player, CityPlayersBackTarget.HUB);
            return;
        }
        if (contains(SLOTS_LEVELS, slot)) {
            cityLevelGuiService.open(player, toPlayersSession(session));
            return;
        }
        if (contains(SLOTS_TERRITORIES, slot)) {
            citySectionMapGuiService.openForOwnTown(player);
            return;
        }
        if (slot == SLOT_ACTION_COMMAND_CENTER) {
            openCommandCenterDialog(player, session);
            return;
        }
        if (slot == SLOT_ACTION_DEFENSE) {
            openDefenseMenu(player, session);
            return;
        }
        if (slot == SLOT_ACTION_LEAVE) {
            openLeaveDialog(player, session);
            return;
        }
        if (slot == SLOT_MEMBER_PREV) {
            int currentPage = memberPageByViewer.getOrDefault(player.getUniqueId(), 0);
            if (currentPage > 0) {
                memberPageByViewer.put(player.getUniqueId(), currentPage - 1);
                openHome(player, session);
            }
            return;
        }
        if (slot == SLOT_MEMBER_NEXT) {
            List<UUID> members = resolveMemberIds(session);
            int currentPage = memberPageByViewer.getOrDefault(player.getUniqueId(), 0);
            int maxPage = Math.max(0, (members.size() - 1) / MEMBER_PAGE_SIZE);
            if (currentPage < maxPage) {
                memberPageByViewer.put(player.getUniqueId(), currentPage + 1);
                openHome(player, session);
            }
            return;
        }
        if (contains(MEMBER_ITEM_SLOTS, slot)) {
            List<UUID> members = resolveMemberIds(session);
            int currentPage = memberPageByViewer.getOrDefault(player.getUniqueId(), 0);
            int localIndex = localMemberIndexForSlot(slot);
            int absoluteIndex = currentPage * MEMBER_PAGE_SIZE + localIndex;
            if (localIndex >= 0 && absoluteIndex < members.size()) {
                cityPlayersGuiService.openMemberDetailFromHub(player, session.townId(), members.get(absoluteIndex));
            }
            return;
        }
        if (contains(SLOTS_CLOSE, slot)) {
            player.closeInventory();
            return;
        }
        if (contains(SLOTS_HELP, slot)) {
            cityWikiDialogService.openRoot(player, CityWikiBackTarget.HUB);
        }
    }

    private void handleDefenseMenuClick(Player player, HubSession session, int slot) {
        if (contains(SLOTS_DEFENSE_MENU_BACK, slot)) {
            openHome(player, session);
            return;
        }
        if (contains(SLOTS_DEFENSE_MENU_HELP, slot)) {
            openDefenseHelpDialog(player);
            return;
        }
        if (contains(SLOTS_DEFENSE_MENU_INVASION, slot)) {
            if (cityDefenseService.snapshotForTown(session.townId()).isPresent()) {
                openDefenseLive(player, session);
            } else {
                openDefenseStart(player, session);
            }
            return;
        }
        if (contains(SLOTS_DEFENSE_MENU_WAR, slot)) {
            openWarEntry(player, session);
            return;
        }
        if (contains(SLOTS_DEFENSE_MENU_SHIELD, slot)) {
            player.sendMessage(cfg.msg("city.defense.gui.menu.shield_coming_soon", "<gray>Scudo in arrivo.</gray>"));
        }
    }

    private void handleDefenseStartClick(Player player, HubSession session, int slot) {
        if (contains(SLOTS_DEFENSE_START_BACK, slot)) {
            openDefenseMenu(player, session);
            return;
        }
        if (slot == SLOT_DEFENSE_PREV_PAGE) {
            int page = Math.max(0, defenseTierPageByViewer.getOrDefault(player.getUniqueId(), 0));
            if (page > 0) {
                defenseTierPageByViewer.put(player.getUniqueId(), page - 1);
            }
            openDefenseStart(player, session);
            return;
        }
        if (slot == SLOT_DEFENSE_NEXT_PAGE) {
            int page = Math.max(0, defenseTierPageByViewer.getOrDefault(player.getUniqueId(), 0));
            int maxPage = defenseStartMaxPage();
            if (page < maxPage) {
                defenseTierPageByViewer.put(player.getUniqueId(), page + 1);
            }
            openDefenseStart(player, session);
            return;
        }
        CityDefenseTier tier = defenseStartTierForSlot(slot, defenseTierPageByViewer.getOrDefault(player.getUniqueId(), 0));
        if (tier != null) {
            CityDefenseService.StartResult result = cityDefenseService.start(player, tier);
            if (!result.success()) {
                player.sendMessage(cfg.msg(
                        "city.defense.errors." + result.reason(),
                        "<red>Avvio difesa fallito:</red> <gray>{reason}</gray>",
                        Placeholder.unparsed("reason", result.reason())
                ));
                openDefenseStart(player, session);
                return;
            }
            openDefenseLive(player, session);
        }
    }

    private void handleDefenseLiveClick(Player player, HubSession session, int slot) {
        if (slotInGroup(slot, SLOTS_DEFENSE_LIVE_BACK)) {
            openDefenseMenu(player, session);
            return;
        }
        if (slotInGroup(slot, SLOTS_DEFENSE_LIVE_STOP)) {
            CityDefenseService.StartResult result = cityDefenseService.abort(player);
            if (!result.success()) {
                player.sendMessage(cfg.msg(
                        "city.defense.errors." + result.reason(),
                        "<red>Arresto assedio fallito:</red> <gray>{reason}</gray>",
                        Placeholder.unparsed("reason", result.reason())
                ));
                openDefenseLive(player, session);
                return;
            }
            player.sendMessage(cfg.msg(
                    "city.defense.gui.live.stop_success",
                    "<yellow>✦ Il bando d'assedio viene ritirato.</yellow> <gray>La cronaca del fronte si richiude senza ricompense.</gray>"
            ));
            openDefenseMenu(player, session);
            return;
        }
        if (slotInGroup(slot, SLOTS_DEFENSE_LIVE_GUARDIAN)
                || slotInGroup(slot, SLOTS_DEFENSE_LIVE_WAVE)
                || slotInGroup(slot, SLOTS_DEFENSE_LIVE_TIMER)) {
            if (cityDefenseService.snapshotForTown(session.townId()).isEmpty()) {
                openDefenseStart(player, session);
                return;
            }
            openDefenseLive(player, session);
            return;
        }
    }

    private void openWarEntry(Player player, HubSession session) {
        CityWarService.ViewerDecision decision = cityWarService.evaluateViewer(session.townId());
        switch (decision.access()) {
            case WARS_DISABLED -> openWarUnavailableDialog(player);
            case SERVER_BUSY -> openWarBusyDialog(player, decision.activeWar());
            case LIVE -> openWarLive(player, session, decision.activeWar());
            case INCOMING_DECLARATION -> openWarIncomingDialog(player, session, decision.incomingDeclaration());
            case LIST -> openWarList(player, session);
        }
    }

    private void handleWarListClick(Player player, HubSession session, int slot) {
        if (slotInGroup(slot, SLOTS_WAR_LIST_BACK)) {
            openDefenseMenu(player, session);
            return;
        }
        if (slotInGroup(slot, SLOTS_WAR_LIST_PREV)) {
            int page = Math.max(0, warPageByViewer.getOrDefault(player.getUniqueId(), 0));
            if (page > 0) {
                warPageByViewer.put(player.getUniqueId(), page - 1);
            }
            openWarList(player, session);
            return;
        }
        if (slotInGroup(slot, SLOTS_WAR_LIST_NEXT)) {
            List<CityWarService.WarTargetView> targets = cityWarService.targetViewsForTown(player, session.townId());
            int maxPage = Math.max(0, (targets.size() - 1) / Math.max(1, cityWarServiceSettingsPageSize()));
            int page = Math.max(0, warPageByViewer.getOrDefault(player.getUniqueId(), 0));
            if (page < maxPage) {
                warPageByViewer.put(player.getUniqueId(), page + 1);
            }
            openWarList(player, session);
            return;
        }
        int contentIndex = warListContentIndex(slot);
        if (contentIndex < 0 || contentIndex >= cityWarServiceSettingsPageSize()) {
            return;
        }
        List<CityWarService.WarTargetView> targets = cityWarService.targetViewsForTown(player, session.townId());
        int page = Math.max(0, warPageByViewer.getOrDefault(player.getUniqueId(), 0));
        int index = page * cityWarServiceSettingsPageSize() + contentIndex;
        if (index < 0 || index >= targets.size()) {
            return;
        }
        if (session.warSampleMode()) {
            player.sendMessage(cfg.msg("city.war.sample.list_click_locked", "<yellow>Preview war: usa i comandi sample dedicati per vedere declare o ally-warning.</yellow>"));
            return;
        }
        CityWarService.WarTargetView target = targets.get(index);
        if (!target.eligible()) {
            player.sendMessage(cfg.msg(
                    "city.war.gui.list.state." + target.stateReason(),
                    "<red>Questa citta non e pronta alla guerra:</red> <gray>{state}</gray>",
                    Placeholder.unparsed("state", warTargetState(target.stateReason()))
            ));
            return;
        }
        if (target.warningAllyBreak()) {
            openWarAllyWarningDialog(player, session, target);
            return;
        }
        openWarDeclareDialog(player, session, target);
    }

    private void handleWarLiveClick(Player player, HubSession session, int slot) {
        if (slotInGroup(slot, SLOTS_WAR_LIVE_BACK)) {
            openDefenseMenu(player, session);
            return;
        }
        if (session.warSampleMode()) {
            if (slotInGroup(slot, SLOTS_WAR_LIVE_ATTACKER)
                    || slotInGroup(slot, SLOTS_WAR_LIVE_DEFENDER)
                    || slotInGroup(slot, SLOTS_WAR_LIVE_OVERVIEW)) {
                openWarSampleForOwnTown(player, WarSampleState.LIVE);
                return;
            }
            if (slotInGroup(slot, SLOTS_WAR_LIVE_SURRENDER)) {
                player.sendMessage(cfg.msg("city.war.sample.noop", "<yellow>Preview war: nessuna azione reale e stata eseguita.</yellow>"));
                return;
            }
        }
        if (slotInGroup(slot, SLOTS_WAR_LIVE_ATTACKER)
                || slotInGroup(slot, SLOTS_WAR_LIVE_DEFENDER)
                || slotInGroup(slot, SLOTS_WAR_LIVE_OVERVIEW)) {
            openWarEntry(player, session);
            return;
        }
        if (slotInGroup(slot, SLOTS_WAR_LIVE_SURRENDER)) {
            CityWarService.ActionResult result = cityWarService.surrender(player, session.townId());
            if (!result.success()) {
                player.sendMessage(cfg.msg(
                        "city.war.errors." + result.reason(),
                        "<red>Operazione di resa fallita:</red> <gray>{reason}</gray>",
                        Placeholder.unparsed("reason", result.reason())
                ));
                openWarEntry(player, session);
                return;
            }
            player.sendMessage(cfg.msg(
                    "city.war.gui.live.surrender_success",
                    "<yellow>La resa e stata inviata. La guerra si chiudera appena il sistema conferma l'esito.</yellow>"
            ));
        }
    }

    private void openWarList(Player player, HubSession session) {
        HubHolder holder = new HubHolder(session.sessionId(), View.WAR_LIST);
        HomeOpenResult openResult = createTexturedInventory(
                holder,
                WAR_LIST_GUI_SIZE,
                cfg.msg(
                        "city.war.gui.list.title",
                        "<red>Guerre</red> <gray>{town}</gray>",
                        Placeholder.unparsed("town", session.townName())
                ),
                cfg.missingKeyFallback("city.war.gui.list.itemsadder.title_plain", "Guerre"),
                cfg.cfgString("city.war.gui.list.itemsadder.font_image_id", WAR_LIST_FONT_IMAGE_ID),
                "war-list"
        );
        Inventory inventory = openResult.inventory();
        if (openResult.texturedWrapper() == null) {
            GuiLayoutUtils.fillAllExcept(inventory, GuiItemFactory.fillerPane(),
                    mergeSlots(SLOTS_WAR_LIST_PREV, SLOTS_WAR_LIST_TARGETS, SLOTS_WAR_LIST_NEXT, SLOTS_WAR_LIST_BACK));
        }
        ItemStack transparentBase = transparentButtonBase();
        List<CityWarService.WarTargetView> targets = cityWarService.targetViewsForTown(player, session.townId());
        int pageSize = cityWarServiceSettingsPageSize();
        int pageCount = Math.max(1, (targets.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(warPageByViewer.getOrDefault(player.getUniqueId(), 0), pageCount - 1));
        warPageByViewer.put(player.getUniqueId(), page);

        fillButtonGroup(
                inventory,
                transparentBase,
                SLOTS_WAR_LIST_PREV,
                page > 0 ? "city.war.gui.list.prev" : "city.war.gui.list.prev_disabled",
                page > 0 ? "<yellow>Pagina precedente</yellow>" : "<gray>Pagina precedente</gray>",
                hubLore(
                        page > 0 ? "city.war.gui.list.prev_lore" : "city.war.gui.list.prev_disabled_lore",
                        List.of(
                                "<dark_gray>╭────────────╮</dark_gray>",
                                page > 0 ? "<yellow>✦</yellow> <white>Scorri il registro alla pagina precedente.</white>" : "<gray>La prima pagina del registro e gia aperta.</gray>",
                                "<dark_gray>╰────────────╯</dark_gray>"
                        )
                )
        );
        fillButtonGroup(
                inventory,
                transparentBase,
                SLOTS_WAR_LIST_NEXT,
                page + 1 < pageCount ? "city.war.gui.list.next" : "city.war.gui.list.next_disabled",
                page + 1 < pageCount ? "<yellow>Pagina successiva</yellow>" : "<gray>Pagina successiva</gray>",
                hubLore(
                        page + 1 < pageCount ? "city.war.gui.list.next_lore" : "city.war.gui.list.next_disabled_lore",
                        List.of(
                                "<dark_gray>╭────────────╮</dark_gray>",
                                page + 1 < pageCount ? "<yellow>✦</yellow> <white>Scorri il registro alla pagina successiva.</white>" : "<gray>Sei gia all'ultima pagina del registro.</gray>",
                                "<dark_gray>╰────────────╯</dark_gray>"
                        )
                )
        );
        fillButtonGroup(
                inventory,
                transparentBase,
                SLOTS_WAR_LIST_BACK,
                "city.war.gui.list.back",
                "<yellow>Indietro</yellow>",
                hubLore(
                        "city.war.gui.list.back_lore",
                        List.of(
                                "<dark_gray>╭────────────╮</dark_gray>",
                                "<yellow>✦</yellow> <white>Lascia il registro di guerra e torna al presidio.</white>",
                                "<dark_gray>╰────────────╯</dark_gray>"
                        )
                )
        );

        int from = page * pageSize;
        int to = Math.min(from + pageSize, targets.size());
        int renderIndex = 0;
        for (int i = from; i < to; i++) {
            inventory.setItem(SLOTS_WAR_LIST_TARGETS[renderIndex++], warTargetItem(targets.get(i)));
        }
        while (renderIndex < SLOTS_WAR_LIST_TARGETS.length) {
            inventory.setItem(SLOTS_WAR_LIST_TARGETS[renderIndex++], null);
        }
        if (targets.isEmpty()) {
            inventory.setItem(SLOTS_WAR_LIST_TARGETS[10], GuiItemFactory.item(
                    Material.BARRIER,
                    cfg.msg("city.war.gui.list.empty", "<gray>Nessuna citta disponibile per la guerra.</gray>")
            ));
        }
        if (openResult.texturedWrapper() != null) {
            openResult.texturedWrapper().showInventory(player);
            return;
        }
        player.openInventory(inventory);
    }

    private void openWarLive(Player player, HubSession session, CityWarService.WarOverview overview) {
        if (overview == null) {
            openWarList(player, session);
            return;
        }
        HubHolder holder = new HubHolder(session.sessionId(), View.WAR_LIVE);
        HomeOpenResult openResult = createTexturedInventory(
                holder,
                WAR_LIVE_GUI_SIZE,
                cfg.msg(
                        "city.war.gui.live.title",
                        "<red>Guerra Attiva</red> <gray>{town}</gray>",
                        Placeholder.unparsed("town", session.townName())
                ),
                cfg.missingKeyFallback("city.war.gui.live.itemsadder.title_plain", "Guerra Attiva"),
                cfg.cfgString("city.war.gui.live.itemsadder.font_image_id", WAR_LIVE_FONT_IMAGE_ID),
                "war-live"
        );
        Inventory inventory = openResult.inventory();
        if (openResult.texturedWrapper() == null) {
            GuiLayoutUtils.fillAllExcept(
                    inventory,
                    GuiItemFactory.fillerPane(),
                    mergeSlots(
                            SLOTS_WAR_LIVE_ATTACKER,
                            SLOTS_WAR_LIVE_DEFENDER,
                            SLOTS_WAR_LIVE_OVERVIEW,
                            SLOTS_WAR_LIVE_BACK,
                            SLOTS_WAR_LIVE_SURRENDER
                    )
            );
        }
        ItemStack transparentBase = transparentButtonBase();
        fillButtonGroup(
                inventory,
                transparentBase,
                SLOTS_WAR_LIVE_ATTACKER,
                "city.war.gui.live.attacker",
                "<red>Fronte Attaccante</red>",
                List.of(
                        noItalic(cfg.msg("city.war.gui.live.attacker_name", "<gray>Citta:</gray> <white>{town}</white>", Placeholder.unparsed("town", overview.attackerTownName()))),
                        noItalic(cfg.msg("city.war.gui.live.attacker_alive", "<gray>Vivi:</gray> <white>{count}</white>", Placeholder.unparsed("count", Integer.toString(overview.aliveAttackers())))),
                        noItalic(cfg.msg("city.war.gui.live.you_are", overview.viewerIsAttacker() ? "<green>La tua citta combatte da attaccante.</green>" : "<gray>Non e il tuo fronte.</gray>")),
                        noItalic(cfg.msg("city.war.gui.live.refresh_hint", "<aqua>Clicca per aggiornare la cronaca.</aqua>"))
                )
        );
        fillButtonGroup(
                inventory,
                transparentBase,
                SLOTS_WAR_LIVE_DEFENDER,
                "city.war.gui.live.defender",
                "<aqua>Fronte Difensore</aqua>",
                List.of(
                        noItalic(cfg.msg("city.war.gui.live.defender_name", "<gray>Citta:</gray> <white>{town}</white>", Placeholder.unparsed("town", overview.defenderTownName()))),
                        noItalic(cfg.msg("city.war.gui.live.defender_alive", "<gray>Vivi:</gray> <white>{count}</white>", Placeholder.unparsed("count", Integer.toString(overview.aliveDefenders())))),
                        noItalic(cfg.msg("city.war.gui.live.you_are", overview.viewerIsDefender() ? "<green>La tua citta combatte da difensore.</green>" : "<gray>Non e il tuo fronte.</gray>")),
                        noItalic(cfg.msg("city.war.gui.live.refresh_hint", "<aqua>Clicca per aggiornare la cronaca.</aqua>"))
                )
        );
        fillButtonGroup(
                inventory,
                transparentBase,
                SLOTS_WAR_LIVE_OVERVIEW,
                "city.war.gui.live.overview",
                "<gold>Quadro del Conflitto</gold>",
                appendLore(
                        warLiveOverviewLore(overview),
                        noItalic(cfg.msg("city.war.gui.live.refresh_hint", "<aqua>Clicca per aggiornare la cronaca.</aqua>"))
                )
        );
        fillButtonGroup(
                inventory,
                transparentBase,
                SLOTS_WAR_LIVE_BACK,
                "city.war.gui.live.back",
                "<yellow>Indietro</yellow>",
                hubLore(
                        "city.war.gui.live.back_lore",
                        List.of(
                                "<dark_gray>╭────────────╮</dark_gray>",
                                "<yellow>✦</yellow> <white>Lascia la cronaca attiva e torna al presidio.</white>",
                                "<dark_gray>╰────────────╯</dark_gray>"
                        )
                )
        );
        if (hasPrivilege(player, Privilege.DECLARE_WAR)) {
            fillButtonGroup(
                    inventory,
                    transparentBase,
                    SLOTS_WAR_LIVE_SURRENDER,
                    "city.war.gui.live.surrender",
                    "<red>Arrenditi</red>",
                    hubLore(
                            "city.war.gui.live.surrender_lore_lines",
                            List.of(
                                    "<dark_gray>╭────────────╮</dark_gray>",
                                    "<red>✦</red> <white>Chiude la guerra arrendendo la tua citta.</white>",
                                    "<dark_gray>╰────────────╯</dark_gray>"
                            )
                    )
            );
        } else {
            fillButtonGroup(
                    inventory,
                    transparentBase,
                    SLOTS_WAR_LIVE_SURRENDER,
                    "city.war.gui.live.surrender_locked",
                    "<gray>Arrenditi (sigillato)</gray>",
                    hubLore(
                            "city.war.gui.live.surrender_locked_lore_lines",
                            List.of(
                                    "<dark_gray>╭────────────╮</dark_gray>",
                                    "<gray>Serve il privilegio DECLARE_WAR per levare la resa.</gray>",
                                    "<dark_gray>╰────────────╯</dark_gray>"
                            )
                    )
            );
        }
        if (openResult.texturedWrapper() != null) {
            openResult.texturedWrapper().showInventory(player);
            return;
        }
        player.openInventory(inventory);
    }

    private void openWarUnavailableDialog(Player player) {
        openNoticeDialog(
                player,
                cfg.msg("city.war.dialog.unavailable.title", "<red>Guerre disabilitate</red>"),
                List.of(
                        cfg.msg("city.war.dialog.unavailable.body_1", "<gray>Le guerre non sono disponibili oppure le relazioni tra citta non sono attive.</gray>"),
                        cfg.msg("city.war.dialog.unavailable.body_2", "<gray>Finche il sistema non le riattiva, non puoi aprire una nuova guerra.</gray>")
                ),
                "war-unavailable"
        );
    }

    private void openWarBusyDialog(Player player, CityWarService.WarOverview overview) {
        openNoticeDialog(
                player,
                cfg.msg("city.war.dialog.busy.title", "<red>Guerra gia in corso</red>"),
                List.of(
                        cfg.msg("city.war.dialog.busy.body_1", "<gray>Il server ha gia una guerra attiva.</gray>"),
                        cfg.msg("city.war.dialog.busy.body_2", "<white>{attacker}</white> <gray>contro</gray> <white>{defender}</white>", Placeholder.unparsed("attacker", overview == null ? "-" : overview.attackerTownName()), Placeholder.unparsed("defender", overview == null ? "-" : overview.defenderTownName())),
                        cfg.msg("city.war.dialog.busy.body_3", "<gray>Fino a chiusura del conflitto non si possono aprire nuove guerre.</gray>")
                ),
                "war-busy"
        );
    }

    private void openWarIncomingDialog(Player player, HubSession session, CityWarService.IncomingDeclarationView incoming) {
        if (incoming == null) {
            openWarList(player, session);
            return;
        }
        DialogBase base = DialogBase.builder(cfg.msg("city.war.dialog.incoming.title", "<red>Dichiarazione di Guerra</red>"))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(
                        DialogBody.plainMessage(cfg.msg("city.war.dialog.incoming.body_1", "<gray>Attaccante:</gray> <white>{attacker}</white>", Placeholder.unparsed("attacker", incoming.attackerTownName()))),
                        DialogBody.plainMessage(cfg.msg("city.war.dialog.incoming.body_2", "<gray>Difensore:</gray> <white>{defender}</white>", Placeholder.unparsed("defender", incoming.defenderTownName()))),
                        DialogBody.plainMessage(cfg.msg("city.war.dialog.incoming.body_3", "<gray>Posta:</gray> <white>{wager}</white>", Placeholder.unparsed("wager", cityWarService.formatMoney(incoming.wager())))),
                        DialogBody.plainMessage(cfg.msg("city.war.dialog.incoming.body_4", "<gray>Scadenza:</gray> <white>{time}</white>", Placeholder.unparsed("time", cityWarService.formatTimestamp(incoming.expiryTime())))),
                        DialogBody.plainMessage(cfg.msg("city.war.dialog.incoming.body_5", "<gray>La relazione e gia stata forzata su</gray> <red>Nemici</red><gray> per aprire il bando.</gray>"))
                ))
                .build();
        Dialog dialog = Dialog.create(factory -> {
            ActionButton acceptButton = ActionButton.create(
                    cfg.msg("city.war.dialog.incoming.accept", "<green>ACCETTA</green>"),
                    null,
                    140,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player actor) {
                            CityWarService.ActionResult result = cityWarService.acceptIncoming(actor, session.townId());
                            if (!result.success()) {
                                actor.sendMessage(cfg.msg("city.war.errors." + result.reason(), "<red>Impossibile accettare la dichiarazione:</red> <gray>{reason}</gray>", Placeholder.unparsed("reason", result.reason())));
                                return;
                            }
                            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                                HubSession current = sessionsByViewer.get(actor.getUniqueId());
                                if (current != null && current.sessionId().equals(session.sessionId())) {
                                    openWarEntry(actor, current);
                                }
                            }, 10L);
                        }
                    }, CLICK_OPTIONS)
            );
            ActionButton closeButton = ActionButton.create(
                    cfg.msg("city.war.dialog.incoming.close", "<gray>CHIUDI</gray>"),
                    null,
                    140,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player actor) {
                            openDefenseMenu(actor, session);
                        }
                    }, CLICK_OPTIONS)
            );
            factory.empty().base(base).type(DialogType.multiAction(List.of(acceptButton), closeButton, 2));
        });
        DialogViewUtils.showDialog(plugin, player, dialog, cfg.msg("city.war.errors.dialog_unavailable", "<red>Dialog non disponibile su questo server.</red>"), "war-incoming");
    }

    private void openWarAllyWarningDialog(Player player, HubSession session, CityWarService.WarTargetView target) {
        DialogConfirmHelper.open(
                plugin,
                player,
                cfg.msg("city.war.dialog.ally_warning.title", "<red>Rompere l'alleanza</red>"),
                cfg.msg("city.war.dialog.ally_warning.body", "<gray>Dichiarando guerra a</gray> <white>{town}</white> <gray>la relazione verra convertita in</gray> <red>Nemici</red><gray>.</gray>", Placeholder.unparsed("town", target.townName())),
                cfg.msg("city.war.dialog.ally_warning.confirm", "<red>PROSEGUI</red>"),
                cfg.msg("city.war.dialog.ally_warning.cancel", "<gray>ANNULLA</gray>"),
                actor -> openWarDeclareDialog(actor, session, target, true),
                cfg.msg("city.war.errors.dialog_unavailable", "<red>Dialog non disponibile su questo server.</red>")
        );
    }

    private void openWarDeclareDialog(Player player, HubSession session, CityWarService.WarTargetView target) {
        openWarDeclareDialog(player, session, target, false);
    }

    private void openWarDeclareDialog(Player player, HubSession session, CityWarService.WarTargetView target, boolean allowAllyBreak) {
        Optional<CityWarService.DeclarationPreview> previewOptional = cityWarService.declarationPreview(player, session.townId(), target.townId());
        if (previewOptional.isEmpty()) {
            player.sendMessage(cfg.msg("city.war.errors.preview_failed", "<red>Impossibile aprire il bando di guerra adesso.</red>"));
            return;
        }
        CityWarService.DeclarationPreview preview = previewOptional.get();
        DialogBase base = DialogBase.builder(cfg.msg("city.war.dialog.declare.title", "<red>Apri Guerra</red>"))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(warDeclareDialogBody(target, preview))
                .inputs(List.of(DialogInputUtils.text(
                        "wager",
                        220,
                        cfg.msg("city.war.dialog.declare.input", "<yellow>Posta</yellow>"),
                        true,
                        cityWarService.formatMoney(preview.minimumWager()),
                        16
                )))
                .build();
        Dialog dialog = Dialog.create(factory -> {
            ActionButton confirmButton = ActionButton.create(
                    cfg.msg("city.war.dialog.declare.confirm", "<red>DICHIARA</red>"),
                    null,
                    140,
                    DialogAction.customClick((response, audience) -> {
                        if (!(audience instanceof Player actor)) {
                            return;
                        }
                        Optional<BigDecimal> wager = DialogInputUtils.parsePositiveAmount(response, "wager", cfg);
                        if (wager.isEmpty()) {
                            actor.sendMessage(cfg.msg("city.war.errors.wager-invalid", "<red>La posta deve essere un numero positivo.</red>"));
                            openWarDeclareDialog(actor, session, target, allowAllyBreak);
                            return;
                        }
                        cityWarService.declareWar(actor, session.townId(), target.townId(), wager.get(), allowAllyBreak)
                                .thenAccept(result -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    if (!result.success()) {
                                        actor.sendMessage(cfg.msg("city.war.errors." + result.reason(), "<red>Dichiarazione fallita:</red> <gray>{reason}</gray>", Placeholder.unparsed("reason", result.reason())));
                                        openWarEntry(actor, session);
                                        return;
                                    }
                                    actor.sendMessage(cfg.msg("city.war.gui.list.declare_success", "<yellow>Il bando di guerra e stato inviato.</yellow>"));
                                    openDefenseMenu(actor, session);
                                }));
                    }, CLICK_OPTIONS)
            );
            ActionButton closeButton = ActionButton.create(
                    cfg.msg("city.war.dialog.declare.cancel", "<gray>ANNULLA</gray>"),
                    null,
                    140,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player actor) {
                            openWarList(actor, session);
                        }
                    }, CLICK_OPTIONS)
            );
            factory.empty().base(base).type(DialogType.multiAction(List.of(confirmButton), closeButton, 2));
        });
        DialogViewUtils.showDialog(plugin, player, dialog, cfg.msg("city.war.errors.dialog_unavailable", "<red>Dialog non disponibile su questo server.</red>"), "war-declare");
    }

    private void openWarIncomingDialogSample(Player player, HubSession session) {
        CityWarService.IncomingDeclarationView incoming = sampleIncomingDeclaration(session);
        DialogBase base = DialogBase.builder(cfg.msg("city.war.dialog.incoming.title", "<red>Dichiarazione di Guerra</red>"))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(
                        DialogBody.plainMessage(cfg.msg("city.war.dialog.incoming.body_1", "<gray>Attaccante:</gray> <white>{attacker}</white>", Placeholder.unparsed("attacker", incoming.attackerTownName()))),
                        DialogBody.plainMessage(cfg.msg("city.war.dialog.incoming.body_2", "<gray>Difensore:</gray> <white>{defender}</white>", Placeholder.unparsed("defender", incoming.defenderTownName()))),
                        DialogBody.plainMessage(cfg.msg("city.war.dialog.incoming.body_3", "<gray>Posta:</gray> <white>{wager}</white>", Placeholder.unparsed("wager", cityWarService.formatMoney(incoming.wager())))),
                        DialogBody.plainMessage(cfg.msg("city.war.dialog.incoming.body_4", "<gray>Scadenza:</gray> <white>{time}</white>", Placeholder.unparsed("time", cityWarService.formatTimestamp(incoming.expiryTime())))),
                        DialogBody.plainMessage(cfg.msg("city.war.dialog.incoming.body_5", "<gray>La relazione e gia stata forzata su</gray> <red>Nemici</red><gray> per aprire il bando.</gray>"))
                ))
                .build();
        Dialog dialog = Dialog.create(factory -> {
            ActionButton acceptButton = ActionButton.create(
                    cfg.msg("city.war.dialog.incoming.accept", "<green>ACCETTA</green>"),
                    null,
                    140,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player actor) {
                            actor.sendMessage(cfg.msg("city.war.sample.noop", "<yellow>Preview war: nessuna azione reale e stata eseguita.</yellow>"));
                        }
                    }, CLICK_OPTIONS)
            );
            ActionButton closeButton = ActionButton.create(
                    cfg.msg("city.war.dialog.incoming.close", "<gray>CHIUDI</gray>"),
                    null,
                    140,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player actor) {
                            openDefenseMenu(actor, session);
                        }
                    }, CLICK_OPTIONS)
            );
            factory.empty().base(base).type(DialogType.multiAction(List.of(acceptButton), closeButton, 2));
        });
        DialogViewUtils.showDialog(plugin, player, dialog, cfg.msg("city.war.errors.dialog_unavailable", "<red>Dialog non disponibile su questo server.</red>"), "war-incoming-sample");
    }

    private void openWarAllyWarningDialogSample(Player player, HubSession session) {
        CityWarService.WarTargetView target = sampleWarTarget();
        DialogConfirmHelper.open(
                plugin,
                player,
                cfg.msg("city.war.dialog.ally_warning.title", "<red>Rompere l'alleanza</red>"),
                cfg.msg("city.war.dialog.ally_warning.body", "<gray>Dichiarando guerra a</gray> <white>{town}</white> <gray>la relazione verra convertita in</gray> <red>Nemici</red><gray>.</gray>", Placeholder.unparsed("town", target.townName())),
                cfg.msg("city.war.dialog.ally_warning.confirm", "<red>PROSEGUI</red>"),
                cfg.msg("city.war.dialog.ally_warning.cancel", "<gray>ANNULLA</gray>"),
                actor -> openWarDeclareDialogSample(actor, session),
                cfg.msg("city.war.errors.dialog_unavailable", "<red>Dialog non disponibile su questo server.</red>")
        );
    }

    private void openWarDeclareDialogSample(Player player, HubSession session) {
        CityWarService.WarTargetView target = sampleWarTarget();
        CityWarService.DeclarationPreview preview = sampleDeclarationPreview(target);
        DialogBase base = DialogBase.builder(cfg.msg("city.war.dialog.declare.title", "<red>Apri Guerra</red>"))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(warDeclareDialogBody(target, preview))
                .inputs(List.of(DialogInputUtils.text(
                        "wager",
                        220,
                        cfg.msg("city.war.dialog.declare.input", "<yellow>Posta</yellow>"),
                        true,
                        cityWarService.formatMoney(preview.minimumWager()),
                        16
                )))
                .build();
        Dialog dialog = Dialog.create(factory -> {
            ActionButton confirmButton = ActionButton.create(
                    cfg.msg("city.war.dialog.declare.confirm", "<red>DICHIARA</red>"),
                    null,
                    140,
                    DialogAction.customClick((response, audience) -> {
                        if (!(audience instanceof Player actor)) {
                            return;
                        }
                        Optional<BigDecimal> wager = DialogInputUtils.parsePositiveAmount(response, "wager", cfg);
                        if (wager.isEmpty()) {
                            actor.sendMessage(cfg.msg("city.war.errors.wager-invalid", "<red>La posta deve essere un numero positivo.</red>"));
                            openWarDeclareDialogSample(actor, session);
                            return;
                        }
                        actor.sendMessage(cfg.msg("city.war.sample.noop", "<yellow>Preview war: nessuna azione reale e stata eseguita.</yellow>"));
                    }, CLICK_OPTIONS)
            );
            ActionButton closeButton = ActionButton.create(
                    cfg.msg("city.war.dialog.declare.cancel", "<gray>ANNULLA</gray>"),
                    null,
                    140,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player actor) {
                            openWarSampleForOwnTown(actor, WarSampleState.LIST);
                        }
                    }, CLICK_OPTIONS)
            );
            factory.empty().base(base).type(DialogType.multiAction(List.of(confirmButton), closeButton, 2));
        });
        DialogViewUtils.showDialog(plugin, player, dialog, cfg.msg("city.war.errors.dialog_unavailable", "<red>Dialog non disponibile su questo server.</red>"), "war-declare-sample");
    }

    private CityWarService.WarOverview sampleWarOverview(HubSession session) {
        return new CityWarService.WarOverview(
                session.townId(),
                session.townName(),
                9025,
                "Forte d'Acciaio",
                BigDecimal.valueOf(18_000L),
                96L,
                OffsetDateTime.now().minusMinutes(17L),
                9,
                7,
                cityWarService.attackerVictoryMoneyPreview(BigDecimal.valueOf(18_000L)),
                cityWarService.defenderVictoryMoneyPreview(BigDecimal.valueOf(18_000L)),
                cityWarService.attackerVictoryPreview(BigDecimal.valueOf(18_000L)),
                cityWarService.defenderVictoryPreview(BigDecimal.valueOf(18_000L)),
                true,
                false
        );
    }

    private CityWarService.IncomingDeclarationView sampleIncomingDeclaration(HubSession session) {
        return new CityWarService.IncomingDeclarationView(
                9042,
                "Casata Cremisi",
                session.townId(),
                session.townName(),
                BigDecimal.valueOf(12_500L),
                OffsetDateTime.now().plusMinutes(24L),
                45L,
                true
        );
    }

    private CityWarService.WarTargetView sampleWarTarget() {
        return new CityWarService.WarTargetView(
                9033,
                "Bastione di Giada",
                "BDG",
                Town.Relation.ALLY,
                Town.Relation.ALLY,
                37,
                true,
                "ready",
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                false,
                cityWarService.attackerVictoryMoneyPreview(BigDecimal.valueOf(huskTownsApiHook.warSettings().minimumWager())),
                cityWarService.attackerVictoryPreview(BigDecimal.valueOf(huskTownsApiHook.warSettings().minimumWager()))
        );
    }

    private CityWarService.DeclarationPreview sampleDeclarationPreview(CityWarService.WarTargetView target) {
        return new CityWarService.DeclarationPreview(
                target,
                BigDecimal.valueOf(8_000L),
                24L,
                45L,
                96L,
                0.25D,
                "Nemici",
                new CityWarService.ActionResult(true, "sample-preview"),
                cityWarService.attackerVictoryMoneyPreview(BigDecimal.valueOf(8_000L)),
                cityWarService.attackerVictoryPreview(BigDecimal.valueOf(8_000L))
        );
    }

    private void openHome(Player player, HubSession session) {
        HubHolder holder = new HubHolder(session.sessionId(), View.HOME);
        HomeOpenResult home = createHomeInventory(holder, session);
        Inventory inventory = home.inventory();
        ItemStack transparentBase = transparentButtonBase();

        // Top section
        fillButtonGroup(inventory, transparentBase, SLOTS_CITY, "city.hub.custom.city", "<gradient:#ffd76a:#ff9f43><bold>✦ CITTA ✦</bold></gradient>", hubLore(
                "city.hub.custom.city.lore",
                List.of(
                        "<dark_gray>╭────────────╮</dark_gray>",
                        "<gray>Qui si aprono i registri del tuo casato.</gray>",
                        "<yellow>✦</yellow> <white>Consulta i cittadini del vessillo</white>",
                        "<yellow>✦</yellow> <white>Accedi al registro diplomatico della citta</white>",
                        "<green>➜ Clicca per aprire il registro del casato.</green>",
                        "<dark_gray>╰────────────╯</dark_gray>"
                )
        ));
        fillButtonGroup(inventory, transparentBase, SLOTS_QUEST, "city.hub.custom.quest", "<gradient:#fff07a:#ffb347><bold>✦ QUEST ✦</bold></gradient>", hubLore(
                "city.hub.custom.quest.lore",
                List.of(
                        "<dark_gray>╭────────────╮</dark_gray>",
                        "<gray>Da qui partono tutte le missioni della tua citta.</gray>",
                        "<yellow>✦</yellow> <white>Guarda le sfide attive del momento</white>",
                        "<yellow>✦</yellow> <white>Controlla progressi, premi e tab speciali</white>",
                        "<green>➜ Clicca per aprire il pannello missioni.</green>",
                        "<dark_gray>╰────────────╯</dark_gray>"
                )
        ));
        fillButtonGroup(inventory, transparentBase, SLOTS_VAULT, "city.hub.custom.vault", "<gradient:#ffe680:#c98f2b><bold>✦ VAULT ✦</bold></gradient>", hubLore(
                "city.hub.custom.vault.lore",
                List.of(
                        "<dark_gray>╭────────────╮</dark_gray>",
                        "<gray>Il deposito condiviso dove la citta conserva oggetti e ricompense.</gray>",
                        "<yellow>✦</yellow> <white>Apri il magazzino comune della citta</white>",
                        "<yellow>✦</yellow> <white>Controlla cosa e gia stato accumulato</white>",
                        "<green>➜ Clicca per entrare nel vault.</green>",
                        "<dark_gray>╰────────────╯</dark_gray>"
                )
        ));

        // Center section
        inventory.setItem(SLOT_ACTION_COMMAND_CENTER, button(
                Material.COMPASS,
                "city.hub.custom.command_center",
                "<gradient:#8be9fd:#4fc3f7><bold>✦ CENTRO COMANDO ✦</bold></gradient>",
                hubLore(
                        "city.hub.custom.command_center.lore",
                        List.of(
                                "<dark_gray>╭────────────╮</dark_gray>",
                                "<gray>La cancelleria dove riforgi la voce pubblica del vessillo.</gray>",
                                "<yellow>✦</yellow> <white>Cronaca, bandi e araldica cromatica</white>",
                                "<yellow>✦</yellow> <white>Riforgia il nome quando il sigillo lo consente</white>",
                                "<green>➜ Clicca per aprire la cancelleria.</green>",
                                "<dark_gray>╰────────────╯</dark_gray>"
                        )
                )
        ));
        inventory.setItem(SLOT_ACTION_DEFENSE, button(
                Material.SHIELD,
                "city.hub.custom.defense",
                "<gradient:#ff8a8a:#ff5252><bold>✦ DIFESA CITTA ✦</bold></gradient>",
                hubLore(
                        "city.hub.custom.defense.lore",
                        List.of(
                                "<dark_gray>╭────────────╮</dark_gray>",
                                "<gray>Qui inizi o segui le invasioni che mettono alla prova la tua citta.</gray>",
                                "<yellow>✦</yellow> <white>Avvia una difesa PvE</white>",
                                "<yellow>✦</yellow> <white>Monitora wave, guardian e ricompense</white>",
                                "<green>➜ Clicca per entrare nella difesa citta.</green>",
                                "<dark_gray>╰────────────╯</dark_gray>"
                        )
                )
        ));
        inventory.setItem(SLOT_ACTION_LEAVE, button(
                Material.RED_BED,
                "city.hub.custom.leave",
                "<gradient:#ffb3b3:#ff5e5e><bold>✦ LASCIA CITTA ✦</bold></gradient>",
                hubLore(
                        "city.hub.custom.leave.lore",
                        List.of(
                                "<dark_gray>╭────────────╮</dark_gray>",
                                "<gray>Da qui puoi lasciare la citta.</gray>",
                                "<yellow>✦</yellow> <white>Prima di uscire vedrai sempre una conferma</white>",
                                "<yellow>✦</yellow> <white>Se sei il Capo, vedrai anche l'opzione per sciogliere la citta</white>",
                                "<red>➜ Clicca per aprire il dialog di conferma.</red>",
                                "<dark_gray>╰────────────╯</dark_gray>"
                        )
                )
        ));

        fillButtonGroup(inventory, transparentBase, SLOTS_LEVELS, "city.hub.custom.levels", "<gradient:#f6d365:#fda085><bold>✦ LIVELLI ✦</bold></gradient>", levelsHubLore(session));
        fillButtonGroup(inventory, transparentBase, SLOTS_TERRITORIES, "city.hub.custom.territories", "<gradient:#7afcff:#4facfe><bold>✦ TERRITORI ✦</bold></gradient>", hubLore(
                "city.hub.custom.territories.lore",
                List.of(
                        "<dark_gray>╭────────────╮</dark_gray>",
                        "<gray>La zona dove controlli claim, mappa e info del terreno.</gray>",
                        "<yellow>✦</yellow> <white>Apri la mappa dei chunk della citta</white>",
                        "<yellow>✦</yellow> <white>Guarda e gestisci il territorio controllato</white>",
                        "<green>➜ Clicca per entrare nella sezione territori.</green>",
                        "<dark_gray>╰────────────╯</dark_gray>"
                )
        ));
        renderMembersPanel(inventory, session, transparentBase);

        // Bottom section
        fillButtonGroup(inventory, transparentBase, SLOTS_CLOSE, "city.hub.custom.close", "<gradient:#ff9a9e:#f6416c><bold>✦ CHIUDI ✦</bold></gradient>", hubLore(
                "city.hub.custom.close.lore",
                List.of(
                        "<dark_gray>╭────────────╮</dark_gray>",
                        "<gray>Chiude la schermata e ti riporta al gioco.</gray>",
                        "<white>Se hai finito qui, questo e il pulsante giusto.</white>",
                        "<dark_gray>╰────────────╯</dark_gray>"
                )
        ));
        fillButtonGroup(inventory, transparentBase, SLOTS_HELP, "city.hub.custom.help", "<gradient:#7afcff:#5ee7df><bold>✦ AIUTO ✦</bold></gradient>", hubLore(
                "city.hub.custom.help.lore",
                List.of(
                        "<dark_gray>╭────────────╮</dark_gray>",
                        "<gray>Una guida rapida per ricordarti cosa puoi fare con /city.</gray>",
                        "<yellow>✦</yellow> <white>Comandi utili e scorciatoie principali</white>",
                        "<yellow>✦</yellow> <white>Perfetto se non sai dove cliccare dopo</white>",
                        "<green>➜ Clicca per aprire l'aiuto.</green>",
                        "<dark_gray>╰────────────╯</dark_gray>"
                )
        ));

        if (home.texturedWrapper() != null) {
            home.texturedWrapper().showInventory(player);
            return;
        }
        player.openInventory(inventory);
    }

    private HomeOpenResult createHomeInventory(HubHolder holder, HubSession session) {
        return createTexturedInventory(
                holder,
                GUI_SIZE,
                cfg.msg(
                        "city.hub.title",
                        "<gold>Menu</gold> <gray>{town}</gray>",
                        Placeholder.unparsed("town", session.townName())
                ),
                cfg.missingKeyFallback("city.hub.itemsadder.title_plain", "Menù"),
                HUB_FONT_IMAGE_ID,
                "city-hub"
        );
    }

    private HomeOpenResult createTexturedInventory(
            HubHolder holder,
            int size,
            Component fallbackTitle,
            String plainTitle,
            String fontImageId,
            String logPrefix
    ) {
        TexturedGuiSupport.OpenResult result = TexturedGuiSupport.createInventory(
                plugin,
                holder,
                holder::bind,
                size,
                fallbackTitle,
                plainTitle,
                cfg.cfgInt("city.hub.itemsadder.title_offset", 0),
                cfg.cfgInt("city.hub.itemsadder.texture_offset", -8),
                fontImageId,
                logPrefix
        );
        return new HomeOpenResult(result.inventory(), result.texturedWrapper());
    }

    private HomeOpenResult createRequiredTexturedInventory(
            HubHolder holder,
            int size,
            Component fallbackTitle,
            String plainTitle,
            String fontImageId,
            String logPrefix
    ) {
        HomeOpenResult result = createTexturedInventory(holder, size, fallbackTitle, plainTitle, fontImageId, logPrefix);
        return result.texturedWrapper() == null ? null : result;
    }

    private void fillButtonGroup(
            Inventory inventory,
            ItemStack transparentBase,
            int[] slots,
            String path,
            String fallback,
            List<Component> lore
    ) {
        TexturedGuiSupport.fillGroup(inventory, slots, overlayButton(transparentBase, path, fallback, lore));
    }

    private List<Component> hubLore(String path, List<String> fallbackLines) {
        return cfg.lore(path, fallbackLines).stream().map(this::noItalic).toList();
    }

    private List<Component> hubLore(String path, List<String> fallbackLines, TagResolver... resolvers) {
        return cfg.lore(path, fallbackLines, resolvers).stream().map(this::noItalic).toList();
    }

    private List<Component> levelsHubLore(HubSession session) {
        CityLevelService.LevelStatus status = cityLevelService.statusForTown(session.townId(), false);
        int nextLevel = Math.min(cityLevelService.settings().levelCap(), status.level() + 1);
        double nextLevelXp = requiredXpForLevel(nextLevel);
        double missingXp = Math.max(0.0D, nextLevelXp - status.xp());
        List<UiLoreStyle.StyledLine> lines = new ArrayList<>();
        lines.add(UiLoreStyle.line(UiLoreStyle.LineType.INFO, "Qui vedi come cresce la tua citta nel tempo."));
        lines.add(UiLoreStyle.line(UiLoreStyle.LineType.STATUS,
                "Livello attuale: " + status.level() + " • XP citta: " + formatDecimal(status.xp())));
        if (nextLevel > status.level()) {
            lines.add(UiLoreStyle.line(UiLoreStyle.LineType.FOCUS,
                    "Prossimo livello: " + nextLevel + " • XP richiesta: " + formatDecimal(nextLevelXp)));
            lines.add(UiLoreStyle.line(UiLoreStyle.LineType.WARNING,
                    "XP mancanti al prossimo livello: " + formatDecimal(missingXp)));
        } else {
            lines.add(UiLoreStyle.line(UiLoreStyle.LineType.REWARD, "Hai gia raggiunto il livello massimo."));
        }
        lines.add(UiLoreStyle.line(UiLoreStyle.LineType.ACTION, "Clicca per aprire il dettaglio livelli."));
        return UiLoreStyle.renderComponents(lines.getFirst(), lines.get(1), lines.subList(2, lines.size()))
                .stream()
                .map(this::noItalic)
                .toList();
    }

    private double requiredXpForLevel(int level) {
        long requiredXpScaled = it.patric.cittaexp.levels.CityLevelCurve.requiredXpScaledForLevel(
                level,
                cityLevelService.settings().xpPerLevel(),
                cityLevelService.settings().xpScale(),
                cityLevelService.settings().curveExponent()
        );
        return cityLevelService.toXp(requiredXpScaled);
    }

    private ItemStack overlayButton(
            ItemStack transparentBase,
            String path,
            String fallback,
            List<Component> lore
    ) {
        return TexturedGuiSupport.overlayButton(
                transparentBase,
                noItalic(cfg.msg(path, fallback)),
                lore == null ? List.of() : lore.stream().map(this::noItalic).toList(),
                GuiItemFactory.item(Material.PAPER, Component.empty())
        );
    }

    private ItemStack transparentButtonBase() {
        return TexturedGuiSupport.transparentButtonBase(plugin, TRANSPARENT_BUTTON_ID);
    }

    private void renderMembersPanel(Inventory inventory, HubSession session, ItemStack transparentBase) {
        List<UUID> members = resolveMemberIds(session);
        int page = Math.max(0, memberPageByViewer.getOrDefault(session.viewerId(), 0));
        int maxPage = Math.max(0, (members.size() - 1) / MEMBER_PAGE_SIZE);
        if (page > maxPage) {
            page = maxPage;
            memberPageByViewer.put(session.viewerId(), page);
        }

        int start = page * MEMBER_PAGE_SIZE;
        for (int i = 0; i < MEMBER_ITEM_SLOTS.length; i++) {
            int memberIndex = start + i;
            int slot = MEMBER_ITEM_SLOTS[i];
            if (memberIndex >= members.size()) {
                inventory.setItem(slot, null);
                continue;
            }
            UUID memberId = members.get(memberIndex);
            inventory.setItem(slot, memberItem(memberId, memberId.equals(session.mayorId())));
        }

        inventory.setItem(SLOT_MEMBER_PREV, memberPageButton(
                transparentBase,
                page > 0,
                true,
                page,
                maxPage
        ));
        inventory.setItem(SLOT_MEMBER_NEXT, memberPageButton(
                transparentBase,
                page < maxPage,
                false,
                page,
                maxPage
        ));
    }

    private ItemStack memberPageButton(ItemStack transparentBase, boolean enabled, boolean previous, int page, int maxPage) {
        String titlePath = previous
                ? (enabled ? "city.hub.custom.members.prev" : "city.hub.custom.members.prev_disabled")
                : (enabled ? "city.hub.custom.members.next" : "city.hub.custom.members.next_disabled");
        String fallbackTitle = previous
                ? (enabled ? "<yellow>Pagina membri precedente</yellow>" : "<gray>Pagina precedente</gray>")
                : (enabled ? "<yellow>Pagina membri successiva</yellow>" : "<gray>Pagina successiva</gray>");
        String extra = "Pagina " + (page + 1) + "/" + (maxPage + 1);
        return TexturedGuiSupport.overlayButton(
                transparentBase,
                noItalic(cfg.msg(titlePath, fallbackTitle)),
                UiLoreStyle.renderComponents(
                        UiLoreStyle.line(
                                enabled ? UiLoreStyle.LineType.ACTION : UiLoreStyle.LineType.WARNING,
                                previous
                                        ? (enabled ? "Vai alla pagina membri prima" : "Sei gia alla prima pagina")
                                        : (enabled ? "Vai alla pagina membri dopo" : "Sei gia all'ultima pagina")
                        ),
                        UiLoreStyle.line(UiLoreStyle.LineType.INFO, "Scorri l'elenco dei membri della citta"),
                        List.of(UiLoreStyle.line(UiLoreStyle.LineType.STATUS, extra))
                ).stream().map(this::noItalic).toList(),
                GuiItemFactory.customHead(
                        previous
                                ? (enabled ? GuiHeadTextures.NAV_LEFT : GuiHeadTextures.NAV_DISABLED)
                                : (enabled ? GuiHeadTextures.NAV_RIGHT : GuiHeadTextures.NAV_DISABLED_RIGHT),
                        Component.empty()
                )
        );
    }

    private List<UUID> resolveMemberIds(HubSession session) {
        Map<UUID, Integer> members = huskTownsApiHook.getTownMembers(session.townId());
        List<UUID> ids = new ArrayList<>(members.keySet());
        ids.sort((a, b) -> {
            if (a.equals(session.mayorId()) && !b.equals(session.mayorId())) {
                return -1;
            }
            if (!a.equals(session.mayorId()) && b.equals(session.mayorId())) {
                return 1;
            }
            boolean aOnline = Bukkit.getPlayer(a) != null;
            boolean bOnline = Bukkit.getPlayer(b) != null;
            if (aOnline && !bOnline) {
                return -1;
            }
            if (!aOnline && bOnline) {
                return 1;
            }
            return resolveMemberName(a).compareToIgnoreCase(resolveMemberName(b));
        });
        return ids;
    }

    private ItemStack memberItem(UUID memberId, boolean mayor) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(memberId);
        String name = resolveMemberName(memberId);
        String stateLabel = offline.isOnline() ? "<green>Online</green>" : "<red>Offline</red>";
        return GuiItemFactory.playerHead(memberId, skull -> {
            skull.displayName(noItalic(cfg.msg(
                    mayor ? "city.hub.custom.members.mayor" : "city.hub.custom.members.member",
                    mayor ? "<gradient:#ffd76a:#ffb347><bold>✦ {name} ✦</bold></gradient> <gray>(Capo)</gray>" : "<gradient:#fdfbfb:#ebedee><bold>• {name} •</bold></gradient>",
                    Placeholder.unparsed("name", name)
            )));
            skull.lore(hubLore(
                    mayor ? "city.hub.custom.members.mayor_lore" : "city.hub.custom.members.member_lore",
                    mayor
                            ? List.of(
                                    "<gray>Questo giocatore e il capo della citta.</gray>",
                                    "<yellow>•</yellow> <white>Stato:</white> " + stateLabel,
                                    "<yellow>•</yellow> <white>Clicca per ispezionare il profilo civico.</white>"
                            )
                            : List.of(
                                    "<gray>Uno dei membri che fanno parte della tua citta.</gray>",
                                    "<yellow>•</yellow> <white>Stato:</white> " + stateLabel,
                                    "<yellow>•</yellow> <white>Clicca per aprire la scheda del membro.</white>"
                            )
            ));
        });
    }

    private String resolveMemberName(UUID memberId) {
        String onlineName = Optional.ofNullable(Bukkit.getPlayer(memberId)).map(Player::getName).orElse(null);
        if (onlineName != null && !onlineName.isBlank()) {
            return onlineName;
        }
        String offlineName = Optional.ofNullable(Bukkit.getOfflinePlayer(memberId).getName()).orElse(null);
        if (offlineName != null && !offlineName.isBlank()) {
            return offlineName;
        }
        return memberId.toString().substring(0, 8);
    }

    private boolean contains(int[] slots, int slot) {
        for (int value : slots) {
            if (value == slot) {
                return true;
            }
        }
        return false;
    }

    private int localMemberIndexForSlot(int slot) {
        for (int index = 0; index < MEMBER_ITEM_SLOTS.length; index++) {
            if (MEMBER_ITEM_SLOTS[index] == slot) {
                return index;
            }
        }
        return -1;
    }

    private boolean canManageDefense(Player player, HubSession session) {
        return permissionService.isAllowed(
                session.townId(),
                session.mayorId(),
                player.getUniqueId(),
                TownMemberPermission.CITY_DEFENSE_MANAGE
        ) && permissionService.isAllowed(
                session.townId(),
                session.mayorId(),
                player.getUniqueId(),
                TownMemberPermission.MOB_INVASION_MANAGE
        );
    }

    private Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private void openDefenseStart(Player player, HubSession session) {
        HubHolder holder = new HubHolder(session.sessionId(), View.DEFENSE_START);
        HomeOpenResult openResult = createTexturedInventory(
                holder,
                DEFENSE_START_GUI_SIZE,
                cfg.msg(
                        "city.defense.gui.start.title",
                        "<gold>Baluardo Cittadino</gold> <gray>{town}</gray>",
                        Placeholder.unparsed("town", session.townName())
                ),
                cfg.missingKeyFallback("city.defense.gui.start.itemsadder.title_plain", "Invasione Ostile"),
                MOB_INVASION_FONT_IMAGE_ID,
                "mob-invasion-start"
        );
        Inventory inventory = openResult.inventory();
        if (openResult.texturedWrapper() == null) {
            GuiLayoutUtils.fillAllExcept(
                    inventory,
                    GuiItemFactory.fillerPane(),
                    concatSlots(
                            SLOTS_DEFENSE_START_TIERS,
                            SLOT_DEFENSE_PREV_PAGE,
                            SLOT_DEFENSE_NEXT_PAGE,
                            SLOTS_DEFENSE_START_BACK[0],
                            SLOTS_DEFENSE_START_BACK[1],
                            SLOTS_DEFENSE_START_BACK[2]
                    )
            );
        }
        ItemStack transparentBase = transparentButtonBase();

        Map<CityDefenseTier, CityDefenseService.CityDefenseTierView> views = cityDefenseService.tierViewsForTown(session.townId())
                .stream()
                .collect(java.util.stream.Collectors.toMap(CityDefenseService.CityDefenseTierView::tier, view -> view));
        CityLevelService.LevelStatus status = cityLevelService.statusForTown(session.townId(), false);
        CityDefenseTier[] tiers = CityDefenseTier.values();
        int maxPage = defenseStartMaxPage();
        int currentPage = Math.max(0, Math.min(maxPage, defenseTierPageByViewer.getOrDefault(player.getUniqueId(), 0)));
        defenseTierPageByViewer.put(player.getUniqueId(), currentPage);
        int offset = currentPage * SLOTS_DEFENSE_START_TIERS.length;
        for (int i = 0; i < SLOTS_DEFENSE_START_TIERS.length; i++) {
            int index = offset + i;
            if (index >= tiers.length) {
                continue;
            }
            inventory.setItem(SLOTS_DEFENSE_START_TIERS[i], defenseTierItem(views.get(tiers[index]), status.level()));
        }
        fillButtonGroup(
                inventory,
                transparentBase,
                new int[]{SLOT_DEFENSE_PREV_PAGE},
                currentPage <= 0 ? "city.defense.gui.prev_locked" : "city.defense.gui.prev",
                currentPage <= 0 ? "<gray>Pagina precedente</gray>" : "<yellow>Pagina precedente</yellow>",
                hubLore(
                        currentPage <= 0 ? "city.defense.gui.prev_locked_lore" : "city.defense.gui.prev_lore",
                        List.of(
                                "<dark_gray>╭────────────╮</dark_gray>",
                                currentPage <= 0
                                        ? "<gray>Il primo sigillo e gia aperto.</gray>"
                                        : "<yellow>✦</yellow> <white>Torna al grado precedente</white>",
                                currentPage <= 0
                                        ? "<gray>Non esistono pagine anteriori.</gray>"
                                        : "<gray>Scorri verso il grado d'assedio precedente.</gray>",
                                "<dark_gray>╰────────────╯</dark_gray>"
                        )
                )
        );
        fillButtonGroup(
                inventory,
                transparentBase,
                new int[]{SLOT_DEFENSE_NEXT_PAGE},
                currentPage >= maxPage ? "city.defense.gui.next_locked" : "city.defense.gui.next",
                currentPage >= maxPage ? "<gray>Pagina successiva</gray>" : "<yellow>Pagina successiva</yellow>",
                hubLore(
                        currentPage >= maxPage ? "city.defense.gui.next_locked_lore" : "city.defense.gui.next_lore",
                        List.of(
                                "<dark_gray>╭────────────╮</dark_gray>",
                                currentPage >= maxPage
                                        ? "<gray>L'ultimo sigillo e gia esposto.</gray>"
                                        : "<yellow>✦</yellow> <white>Apri il grado successivo</white>",
                                currentPage >= maxPage
                                        ? "<gray>Non esistono pagine ulteriori.</gray>"
                                        : "<gray>Scorri verso il grado d'assedio successivo.</gray>",
                                "<dark_gray>╰────────────╯</dark_gray>"
                        )
                )
        );
        fillButtonGroup(
                inventory,
                transparentBase,
                SLOTS_DEFENSE_START_BACK,
                "city.hub.back",
                "<yellow>Indietro</yellow>",
                hubLore(
                        "city.hub.back_lore",
                        List.of(
                                "<dark_gray>╭────────────╮</dark_gray>",
                                "<yellow>✦</yellow> <white>Ritorna al nodo difesa</white>",
                                "<gray>Lascia il registro dell'invasione e torna al submenu difesa.</gray>",
                                "<green>➜ Clicca per tornare indietro.</green>",
                                "<dark_gray>╰────────────╯</dark_gray>"
                        )
                )
        );
        if (openResult.texturedWrapper() != null) {
            openResult.texturedWrapper().showInventory(player);
            return;
        }
        player.openInventory(inventory);
    }

    private void openDefenseMenu(Player player, HubSession session) {
        HubHolder holder = new HubHolder(session.sessionId(), View.DEFENSE_MENU);
        HomeOpenResult openResult = createTexturedInventory(
                holder,
                GUI_SIZE,
                cfg.msg(
                        "city.defense.gui.menu.title",
                        "<gold>Difesa e Guerre</gold> <gray>{town}</gray>",
                        Placeholder.unparsed("town", session.townName())
                ),
                cfg.missingKeyFallback("city.defense.gui.menu.itemsadder.title_plain", "Difesa e Guerre"),
                DEFENSE_FONT_IMAGE_ID,
                "defense-menu"
        );
        Inventory inventory = openResult.inventory();
        ItemStack transparentBase = transparentButtonBase();

        fillButtonGroup(
                inventory,
                transparentBase,
                SLOTS_DEFENSE_MENU_INVASION,
                "city.defense.gui.menu.mob_invasion",
                "<gold>Invasione Ostile</gold>",
                hubLore(
                        "city.defense.gui.menu.mob_invasion_lore",
                        List.of(
                                "<dark_gray>╭────────────╮</dark_gray>",
                                "<gold>✦</gold> <white>Invasione Ostile</white>",
                                "<gray>Apri il fronte d'assedio gia operativo della citta.</gray>",
                                "<gray>Qui entri nei gradi, nello stato attivo e nelle ondate.</gray>",
                                "<green>➜ Clicca per aprire la cronaca del baluardo.</green>",
                                "<dark_gray>╰────────────╯</dark_gray>"
                        )
                )
        );
        fillButtonGroup(
                inventory,
                transparentBase,
                SLOTS_DEFENSE_MENU_WAR,
                "city.defense.gui.menu.war",
                "<red>Guerre</red>",
                hubLore(
                        "city.defense.gui.menu.war_lore",
                        List.of(
                                "<dark_gray>╭────────────╮</dark_gray>",
                                "<red>✦</red> <white>Guerre</white>",
                                "<gray>Apri il registro delle guerre tra citta.</gray>",
                                "<gray>Da qui puoi dichiarare, accettare e monitorare i conflitti attivi.</gray>",
                                "<gray>Requisiti:</gray> <white>livello 25+</white>",
                                "<dark_gray>╰────────────╯</dark_gray>"
                        )
                )
        );
        fillButtonGroup(
                inventory,
                transparentBase,
                SLOTS_DEFENSE_MENU_SHIELD,
                "city.defense.gui.menu.shield",
                "<aqua>Scudo</aqua>",
                hubLore(
                        "city.defense.gui.menu.shield_lore",
                        List.of(
                                "<dark_gray>╭────────────╮</dark_gray>",
                                "<aqua>✦</aqua> <white>Scudo</white>",
                                "<gray>Varco futuro per protezioni, coperture e sistemi difensivi permanenti.</gray>",
                                "<gray>Questa sezione e ancora in costruzione.</gray>",
                                "<gray>Stato:</gray> <yellow>In arrivo</yellow>",
                                "<dark_gray>╰────────────╯</dark_gray>"
                        )
                )
        );
        fillButtonGroup(
                inventory,
                transparentBase,
                SLOTS_DEFENSE_MENU_BACK,
                "city.defense.gui.menu.back",
                "<yellow>Indietro</yellow>",
                hubLore(
                        "city.defense.gui.menu.back_lore",
                        List.of(
                                "<dark_gray>╭────────────╮</dark_gray>",
                                "<yellow>✦</yellow> <white>Ritorna al nodo principale</white>",
                                "<gray>Lascia il presidio difensivo e torna all'hub cittadino.</gray>",
                                "<green>➜ Clicca per tornare indietro.</green>",
                                "<dark_gray>╰────────────╯</dark_gray>"
                        )
                )
        );
        fillButtonGroup(
                inventory,
                transparentBase,
                SLOTS_DEFENSE_MENU_HELP,
                "city.defense.gui.menu.help",
                "<gold>Aiuto</gold>",
                hubLore(
                        "city.defense.gui.menu.help_lore",
                        List.of(
                                "<dark_gray>╭────────────╮</dark_gray>",
                                "<gold>✦</gold> <white>Guida al presidio</white>",
                                "<gray>Apri un dialog che spiega il ruolo di Invasione Ostile, Guerre e Scudo.</gray>",
                                "<green>➜ Clicca per aprire l'aiuto.</green>",
                                "<dark_gray>╰────────────╯</dark_gray>"
                        )
                )
        );

        if (openResult.texturedWrapper() != null) {
            openResult.texturedWrapper().showInventory(player);
            return;
        }
        player.openInventory(inventory);
    }

    private void openDefenseLive(Player player, HubSession session) {
        openDefenseLive(player, session, cityDefenseService.snapshotForTown(session.townId()));
    }

    private void openDefenseLive(Player player, HubSession session, Optional<CityDefenseService.CityDefenseSessionSnapshot> snapshotOptional) {
        HubHolder holder = new HubHolder(session.sessionId(), View.DEFENSE_LIVE);
        HomeOpenResult openResult = createRequiredTexturedInventory(
                holder,
                DEFENSE_LIVE_GUI_SIZE,
                cfg.msg(
                        "city.defense.gui.live.title",
                        "<gold>Cronaca d'Assedio</gold> <gray>{town}</gray>",
                        Placeholder.unparsed("town", session.townName())
                ),
                cfg.missingKeyFallback("city.defense.gui.live.itemsadder.title_plain", "Assedio Attivo"),
                MOB_INVASION_ACTIVE_FONT_IMAGE_ID,
                "mob-invasion-live"
        );
        if (openResult == null) {
            player.sendMessage(cfg.msg(
                    "city.defense.gui.live.unavailable",
                    "<red>La cronaca d'assedio attiva richiede la GUI custom caricata correttamente.</red>"
            ));
            return;
        }
        Inventory inventory = openResult.inventory();
        ItemStack transparentBase = transparentButtonBase();
        boolean canManageDefense = canManageDefense(player, session);

        if (snapshotOptional.isEmpty()) {
            fillButtonGroup(
                    inventory,
                    transparentBase,
                    SLOTS_DEFENSE_LIVE_GUARDIAN,
                    "city.defense.gui.live.guardian_idle",
                    "<gray>Guardiano in quiete</gray>",
                    hubLore(
                            "city.defense.gui.live.guardian_idle_lore",
                            List.of(
                                    "<dark_gray>╭────────────╮</dark_gray>",
                                    "<gray>Il Guardiano del Baluardo non e sotto assedio.</gray>",
                                    "<gray>La sua integrita resta intatta finche il bando non verra levato.</gray>",
                                    "<dark_gray>╰────────────╯</dark_gray>"
                            )
                    )
            );
            fillButtonGroup(
                    inventory,
                    transparentBase,
                    SLOTS_DEFENSE_LIVE_WAVE,
                    "city.defense.gui.live.wave_idle",
                    "<yellow>Apri il bando d'assedio</yellow>",
                    hubLore(
                            "city.defense.gui.live.wave_idle_lore",
                            List.of(
                                    "<dark_gray>╭────────────╮</dark_gray>",
                                    "<yellow>✦</yellow> <white>Nessuna invasione e attiva</white>",
                                    "<gray>Apri il registro dei gradi e scegli quale cronaca d'assedio levare.</gray>",
                                    "<green>➜ Clicca per aprire il bando.</green>",
                                    "<dark_gray>╰────────────╯</dark_gray>"
                            )
                    )
            );
            fillButtonGroup(
                    inventory,
                    transparentBase,
                    SLOTS_DEFENSE_LIVE_TIMER,
                    "city.defense.gui.live.timer_idle",
                    "<gray>Clessidra immobile</gray>",
                    hubLore(
                            "city.defense.gui.live.timer_idle_lore",
                            List.of(
                                    "<dark_gray>╭────────────╮</dark_gray>",
                                    "<gray>Nessun conto alla rovescia grava sul baluardo.</gray>",
                                    "<gray>Il fronte restera quieto finche non verra aperto un nuovo bando.</gray>",
                                    "<dark_gray>╰────────────╯</dark_gray>"
                            )
                    )
            );
        } else {
            CityDefenseService.CityDefenseSessionSnapshot snapshot = snapshotOptional.get();
            double hpPercent = snapshot.guardianHpMax() <= 0.0D
                    ? 0.0D
                    : Math.max(0.0D, Math.min(1.0D, snapshot.guardianHp() / snapshot.guardianHpMax()));
            fillButtonGroup(
                    inventory,
                    transparentBase,
                    SLOTS_DEFENSE_LIVE_GUARDIAN,
                    "city.defense.gui.live.guardian",
                    "<gold>Guardiano del Baluardo</gold>",
                    hubLore(
                            "city.defense.gui.live.guardian_lore_lines",
                            List.of(
                                    "<dark_gray>╭────────────╮</dark_gray>",
                                    "<gray>Integrita residua:</gray> <white>{hp}/{max}</white>",
                                    "<gray>Tenuta del baluardo:</gray> <white>{percent}%</white>",
                                    "<gray>Il cuore della difesa regge finche la linea non cede.</gray>",
                                    "<green>➜ Clicca per aggiornare la cronaca.</green>",
                                    "<dark_gray>╰────────────╯</dark_gray>"
                            ),
                            Placeholder.unparsed("hp", Integer.toString((int) Math.round(snapshot.guardianHp()))),
                            Placeholder.unparsed("max", Integer.toString((int) Math.round(snapshot.guardianHpMax()))),
                            Placeholder.unparsed("percent", Integer.toString((int) Math.round(hpPercent * 100.0D)))
                    )
            );
            fillButtonGroup(
                    inventory,
                    transparentBase,
                    SLOTS_DEFENSE_LIVE_WAVE,
                    "city.defense.gui.live.wave",
                    "<gold>Cronaca del Fronte</gold>",
                    hubLore(
                            "city.defense.gui.live.wave_lore_lines",
                            List.of(
                                    "<dark_gray>╭────────────╮</dark_gray>",
                                    "<gray>Ondata attuale:</gray> <white>{wave}/{total}</white>",
                                    "<gray>Stato del fronte:</gray> <white>{phase}</white>",
                                    "<gray>Ostili ancora in campo:</gray> <white>{mobs}</white>",
                                    "<gray>Campione in vista:</gray> <white>{boss}</white>",
                                    "<gray>Segno del campione:</gray> <white>{boss_phase}</white>",
                                    "<green>➜ Clicca per aggiornare la cronaca.</green>",
                                    "<dark_gray>╰────────────╯</dark_gray>"
                            ),
                            Placeholder.unparsed("wave", Integer.toString(snapshot.currentWave())),
                            Placeholder.unparsed("total", Integer.toString(snapshot.totalWaves())),
                            Placeholder.unparsed("phase", snapshot.phase()),
                            Placeholder.unparsed("mobs", Integer.toString(snapshot.aliveMobs())),
                            Placeholder.unparsed("boss", snapshot.bossName()),
                            Placeholder.unparsed("boss_phase", snapshot.bossPhase())
                    )
            );
            fillButtonGroup(
                    inventory,
                    transparentBase,
                    SLOTS_DEFENSE_LIVE_TIMER,
                    "city.defense.gui.live.timer",
                    "<gold>Clessidra d'Assedio</gold>",
                    hubLore(
                            "city.defense.gui.live.timer_lore_lines",
                            List.of(
                                    "<dark_gray>╭────────────╮</dark_gray>",
                                    "<gray>Tempo residuo della cronaca:</gray> <white>{seconds}s</white>",
                                    "<gray>Durata massima del grado:</gray> <white>{max_seconds}s</white>",
                                    "<gray>Integrita del campione:</gray> <white>{boss_hp}/{boss_max}</white>",
                                    "<gray>Ogni istante perso lascia il fronte piu esposto.</gray>",
                                    "<green>➜ Clicca per aggiornare la cronaca.</green>",
                                    "<dark_gray>╰────────────╯</dark_gray>"
                            ),
                            Placeholder.unparsed("seconds", Long.toString(snapshot.remainingSeconds())),
                            Placeholder.unparsed("max_seconds", Long.toString(snapshot.maxDurationSeconds())),
                            Placeholder.unparsed("boss_hp", Integer.toString((int) Math.round(snapshot.bossHp()))),
                            Placeholder.unparsed("boss_max", Integer.toString((int) Math.round(snapshot.bossHpMax())))
                    )
            );
        }
        fillButtonGroup(
                inventory,
                transparentBase,
                SLOTS_DEFENSE_LIVE_BACK,
                "city.hub.back",
                "<yellow>Indietro</yellow>",
                hubLore(
                        "city.hub.back_lore",
                        List.of(
                                "<dark_gray>╭────────────╮</dark_gray>",
                                "<yellow>✦</yellow> <white>Ritorna al nodo difesa</white>",
                                "<gray>Lascia la cronaca attiva e torna al submenu del baluardo.</gray>",
                                "<green>➜ Clicca per tornare indietro.</green>",
                                "<dark_gray>╰────────────╯</dark_gray>"
                        )
                )
        );
        fillButtonGroup(
                inventory,
                transparentBase,
                SLOTS_DEFENSE_LIVE_STOP,
                !canManageDefense
                        ? "city.defense.gui.live.stop_locked"
                        : snapshotOptional.isPresent()
                                ? "city.defense.gui.live.stop"
                                : "city.defense.gui.live.stop_idle",
                !canManageDefense
                        ? "<gray>Ritiro sigillato</gray>"
                        : snapshotOptional.isPresent()
                                ? "<red>Ritira il bando</red>"
                                : "<gray>Nessun bando da ritirare</gray>",
                hubLore(
                        !canManageDefense
                                ? "city.defense.gui.live.stop_locked_lore"
                                : snapshotOptional.isPresent()
                                        ? "city.defense.gui.live.stop_lore"
                                        : "city.defense.gui.live.stop_idle_lore",
                        !canManageDefense
                                ? List.of(
                                        "<dark_gray>╭────────────╮</dark_gray>",
                                        "<gray>Solo chi gestisce il tesoro e il bando puo ritirare l'assedio.</gray>",
                                        "<gray>Occorre il privilegio di prelievo cittadino.</gray>",
                                        "<dark_gray>╰────────────╯</dark_gray>"
                                )
                                : snapshotOptional.isPresent()
                                        ? List.of(
                                                "<dark_gray>╭────────────╮</dark_gray>",
                                                "<red>✦</red> <white>Ritira il bando d'assedio</white>",
                                                "<gray>Interrompe subito la cronaca attiva.</gray>",
                                                "<gray>La citta non ricevera ricompense e non maturera cooldown.</gray>",
                                                "<red>➜ Clicca solo se vuoi fermare il test o l'assedio.</red>",
                                                "<dark_gray>╰────────────╯</dark_gray>"
                                        )
                                        : List.of(
                                                "<dark_gray>╭────────────╮</dark_gray>",
                                                "<gray>Nessuna invasione attiva da interrompere.</gray>",
                                                "<dark_gray>╰────────────╯</dark_gray>"
                                        )
                )
        );
        openResult.texturedWrapper().showInventory(player);
    }

    private CityDefenseService.CityDefenseSessionSnapshot sampleDefenseSnapshot(HubSession session, Player player) {
        return new CityDefenseService.CityDefenseSessionSnapshot(
                "sample-live",
                session.townId(),
                session.townName(),
                CityDefenseTier.L18,
                18,
                25,
                "Assedio in Corso",
                37,
                742.0D,
                900.0D,
                "Re dell'Ultimo Assedio",
                "Corona di Guerra",
                420.0D,
                900.0D,
                84L,
                4050L,
                player.getLocation().clone()
        );
    }

    private void openDefenseHelpDialog(Player player) {
        cityWikiDialogService.openPage(player, "start_invasion", CityWikiBackTarget.DEFENSE);
    }

    private void openCommandCenterDialog(Player player, HubSession session) {
        List<ActionButton> actions = new ArrayList<>();
        if (hasPrivilege(player, Privilege.SET_BIO)) {
            actions.add(ActionButton.create(
                    cfg.msg("city.hub.command_dialog.bio", "<yellow>Cronaca Ufficiale</yellow>"),
                    null,
                    170,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player actor) {
                            townEditDialogFlow.openBio(actor);
                        }
                    }, CLICK_OPTIONS)
            ));
        }
        if (hasPrivilege(player, Privilege.SET_GREETING)) {
            actions.add(ActionButton.create(
                    cfg.msg("city.hub.command_dialog.greeting", "<yellow>Bando di Benvenuto</yellow>"),
                    null,
                    170,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player actor) {
                            townEditDialogFlow.openGreeting(actor);
                        }
                    }, CLICK_OPTIONS)
            ));
        }
        if (hasPrivilege(player, Privilege.SET_FAREWELL)) {
            actions.add(ActionButton.create(
                    cfg.msg("city.hub.command_dialog.farewell", "<yellow>Bando di Congedo</yellow>"),
                    null,
                    170,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player actor) {
                            townEditDialogFlow.openFarewell(actor);
                        }
                    }, CLICK_OPTIONS)
            ));
        }
        if (hasPrivilege(player, Privilege.SET_COLOR)) {
            actions.add(ActionButton.create(
                    cfg.msg("city.hub.command_dialog.color", "<yellow>Araldica Cromatica</yellow>"),
                    null,
                    170,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player actor) {
                            townEditDialogFlow.openColor(actor);
                        }
                    }, CLICK_OPTIONS)
            ));
        }
        if (hasPrivilege(player, Privilege.RENAME)) {
            actions.add(ActionButton.create(
                    cfg.msg("city.hub.command_dialog.rename", "<yellow>Riforgia il Nome</yellow>"),
                    null,
                    170,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player actor) {
                            townEditDialogFlow.openRename(actor);
                        }
                    }, CLICK_OPTIONS)
            ));
        }

        DialogBase base = DialogBase.builder(cfg.msg(
                        "city.hub.command_dialog.title",
                        "<gold>Centro di Cancelleria</gold> <gray>{town}</gray>",
                        Placeholder.unparsed("town", session.townName())
                ))
                .body(List.of(
                        DialogBody.plainMessage(cfg.msg(
                                "city.hub.command_dialog.body_1",
                                "<gray>Qui si riforgia la voce pubblica del vessillo: cronache, colori, bandi e nome della citta.</gray>"
                        )),
                        DialogBody.plainMessage(cfg.msg(
                                actions.isEmpty()
                                        ? "city.hub.command_dialog.body_2_locked"
                                        : "city.hub.command_dialog.body_2",
                                actions.isEmpty()
                                        ? "<gray>Nessun sigillo modificabile ti e stato concesso in questa cancelleria.</gray>"
                                        : "<gray>Sono esposti solo i sigilli che il tuo rango puo davvero muovere.</gray>"
                        ))
                ))
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .build();
        Dialog dialog = Dialog.create(factory -> {
            ActionButton closeButton = ActionButton.create(
                    cfg.msg("city.hub.command_dialog.close", "<gray>Lascia la Cancelleria</gray>"),
                    null,
                    170,
                    null
            );
            factory.empty().base(base).type(DialogType.multiAction(actions, closeButton, 2));
        });
        DialogViewUtils.showDialog(
                plugin,
                player,
                dialog,
                cfg.msg("city.hub.errors.dialog_unavailable", "<red>Dialog non disponibile su questo server.</red>"),
                "city-hub-command"
        );
    }

    private void executeLeave(Player player, HubSession session) {
        Integer originTownId = session.townId();
        huskTownsApiHook.leaveTown(player);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            boolean leftTown = huskTownsApiHook.getUserTown(player)
                    .map(member -> member.town().getId() != originTownId)
                    .orElse(true);
            if (!leftTown) {
                return;
            }
            player.closeInventory();
            sessionsByViewer.remove(player.getUniqueId());
            memberPageByViewer.remove(player.getUniqueId());
            defenseTierPageByViewer.remove(player.getUniqueId());
        }, 10L);
    }

    private void openLeaveDialog(Player player, HubSession session) {
        boolean isMayor = session.viewerId().equals(session.mayorId());
        DialogBase base = DialogBase.builder(cfg.msg(
                        "city.hub.leave_dialog.title",
                        "<red>Lascia la citta</red>"
                ))
                .body(List.of(
                        DialogBody.plainMessage(cfg.msg(
                                "city.hub.leave_dialog.body_1",
                                "<gray>Se confermi, uscirai dalla tua citta.</gray>"
                        )),
                        DialogBody.plainMessage(cfg.msg(
                                isMayor
                                        ? "city.hub.leave_dialog.body_2_mayor"
                                        : "city.hub.leave_dialog.body_2",
                                isMayor
                                        ? "<gray>Se sei il Capo, da qui puoi anche aprire la conferma per sciogliere la citta.</gray>"
                                        : "<gray>Potrai rientrare solo se riceverai un nuovo invito.</gray>"
                        ))
                ))
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .build();
        Dialog dialog = Dialog.create(factory -> {
            List<ActionButton> actions = new ArrayList<>();
            actions.add(ActionButton.create(
                    cfg.msg("city.hub.leave_dialog.leave", "<red>Lascia la citta</red>"),
                    null,
                    170,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player actor) {
                            executeLeave(actor, session);
                        }
                    }, CLICK_OPTIONS)
            ));
            if (isMayor) {
                actions.add(ActionButton.create(
                        cfg.msg("city.hub.leave_dialog.disband", "<dark_red>Sciogli la citta</dark_red>"),
                        null,
                        170,
                        DialogAction.customClick((response, audience) -> {
                            if (audience instanceof Player actor) {
                                townDisbandDialogFlow.openConfirm(actor);
                            }
                        }, CLICK_OPTIONS)
                ));
            }
            ActionButton cancelButton = ActionButton.create(
                    cfg.msg("city.hub.leave_dialog.cancel", "<gray>Annulla</gray>"),
                    null,
                    170,
                    null
            );
            factory.empty().base(base).type(DialogType.multiAction(actions, cancelButton, 2));
        });
        DialogViewUtils.showDialog(
                plugin,
                player,
                dialog,
                cfg.msg("city.hub.errors.dialog_unavailable", "<red>Dialog non disponibile su questo server.</red>"),
                "city-hub-leave"
        );
    }

    private ItemStack headerItem(HubSession session) {
        CityLevelService.LevelStatus status = cityLevelService.statusForTown(session.townId(), false);
        return GuiItemFactory.customHead(
                GuiHeadTextures.INFO,
                cfg.msg(
                        "city.hub.header.title",
                        "<gold>{town}</gold> <gray>|</gray> <yellow>{stage}</yellow>",
                        Placeholder.unparsed("town", status.townName()),
                        Placeholder.unparsed("stage", status.stage().displayName())
                ),
                List.of(
                        cfg.msg(
                                "city.hub.header.level",
                                "<gray>Livello:</gray> <white>{level}</white> <gray>| XP:</gray> <white>{xp}</white>",
                                Placeholder.unparsed("level", Integer.toString(status.level())),
                                Placeholder.unparsed("xp", formatDecimal(status.xp()))
                        ),
                        cfg.msg(
                                "city.hub.header.caps",
                                "<gray>Cap:</gray> <white>{claims}</white> claim <gray>|</gray> <white>{members}</white> membri <gray>|</gray> <white>{spawners}</white> spawner",
                                Placeholder.unparsed("claims", Integer.toString(status.claimCap())),
                                Placeholder.unparsed("members", Integer.toString(status.memberCap())),
                                Placeholder.unparsed("spawners", Integer.toString(status.spawnerCap()))
                        )
                )
        );
    }

    private ItemStack statusItem(HubSession session) {
        boolean mayor = session.viewerId().equals(session.mayorId());
        TownStage stage = cityLevelService.statusForTown(session.townId(), false).stage();
        return GuiItemFactory.customHead(
                mayor ? GuiHeadTextures.STATUS_ONLINE : GuiHeadTextures.STATUS_OFFLINE,
                cfg.msg("city.hub.footer.status.title", "<gold>Stato</gold>"),
                List.of(
                        cfg.msg(
                                "city.hub.footer.status.role",
                                "<gray>Ruolo:</gray> <white>{role}</white>",
                                Placeholder.unparsed("role", mayor ? "Capo" : "Membro")
                        ),
                        cfg.msg(
                                "city.hub.footer.status.stage",
                                "<gray>Stage:</gray> <yellow>{stage}</yellow>",
                                Placeholder.unparsed("stage", stage.displayName())
                        )
                )
        );
    }

    private ItemStack defenseStatusItem(HubSession session) {
        Optional<CityDefenseService.CityDefenseSessionSnapshot> snapshot = cityDefenseService.snapshotForTown(session.townId());
        if (snapshot.isEmpty()) {
            return button(Material.GRAY_DYE, "city.defense.gui.status.none", "<gray>Stato: baluardo silente</gray>");
        }
        CityDefenseService.CityDefenseSessionSnapshot value = snapshot.get();
        return GuiItemFactory.item(
                Material.LIME_DYE,
                cfg.msg("city.defense.gui.status.active", "<green>Assedio attivo</green>"),
                List.of(
                        cfg.msg(
                                "city.defense.gui.status.active_lore",
                                "<gray>Ondata:</gray> <white>{wave}/{total}</white> <gray>| Ostili:</gray> <white>{mobs}</white>",
                                Placeholder.unparsed("wave", Integer.toString(value.currentWave())),
                                Placeholder.unparsed("total", Integer.toString(value.totalWaves())),
                                Placeholder.unparsed("mobs", Integer.toString(value.aliveMobs()))
                        )
                )
        );
    }

    private ItemStack centroComandoItem(HubSession session) {
        boolean canDisband = session.viewerId().equals(session.mayorId());
        return GuiItemFactory.customHead(
                GuiHeadTextures.ACTIONS,
                cfg.msg("city.hub.cards.command_center", "<yellow>Centro comando</yellow>"),
                List.of(
                        cfg.msg("city.hub.cards.command_center.lore.diplomazia", "<gray>| Diplomazia tra citta</gray>"),
                        cfg.msg("city.hub.cards.command_center.lore.territori", "<gray>| Impostazioni territori</gray>"),
                        cfg.msg("city.hub.cards.command_center.lore.edit", "<gray>| Modifica dettagli villaggio</gray>"),
                        canDisband
                                ? cfg.msg("city.hub.cards.command_center.lore.disband", "<gray>| Elimina citta</gray>")
                                : cfg.msg("city.hub.cards.command_center.lore.leave", "<gray>| Abbandona citta</gray>")
                )
        );
    }

    private ItemStack territoriInfoItem(HubSession session) {
        CityLevelService.LevelStatus status = cityLevelService.statusForTown(session.townId(), false);
        Optional<Town> town = huskTownsApiHook.getTownById(session.townId());
        int claims = town.map(Town::getClaimCount).orElse(0);
        return GuiItemFactory.customHead(
                GuiHeadTextures.INFO,
                cfg.msg("city.hub.territori.info.title", "<gold>Info territori</gold>"),
                List.of(
                        cfg.msg(
                                "city.hub.territori.info.claims",
                                "<gray>Claim:</gray> <white>{current}/{cap}</white>",
                                Placeholder.unparsed("current", Integer.toString(claims)),
                                Placeholder.unparsed("cap", Integer.toString(status.claimCap()))
                        ),
                        cfg.msg(
                                "city.hub.territori.info.tip",
                                "<gray>Apri la mappa per claim, tipo terreno o info chunk.</gray>"
                        )
                )
        );
    }

    private ItemStack balanceItem(HubSession session) {
        String amount = huskTownsApiHook.getTownById(session.townId())
                .map(Town::getMoney)
                .map(this::formatMoney)
                .orElse("0");
        return GuiItemFactory.customHead(
                GuiHeadTextures.BANK,
                cfg.msg("city.hub.tesoro.balance.title", "<gold>Saldo citta</gold>"),
                List.of(cfg.msg(
                        "city.hub.tesoro.balance.value",
                        "<gray>Bilancio:</gray> <white>{amount}</white>",
                        Placeholder.unparsed("amount", amount)
                ))
        );
    }

    private ItemStack defenseTierItem(
            CityDefenseService.CityDefenseTierView view,
            int currentCityLevel
    ) {
        if (view == null) {
            return button(Material.GRAY_DYE, "city.defense.gui.tier.unavailable", "<gray>Grado d'assedio non disponibile</gray>");
        }
        boolean levelReady = currentCityLevel >= view.minCityLevel();
        boolean cooldownReady = view.cooldownRemainingSeconds() <= 0L;
        Material material = defenseTierMaterial(view.tier());
        String titlePath = "city.defense.gui.tier." + view.tier().id() + ".title";
        String fallbackTitle = "<gold>Difesa " + view.tier().name() + "</gold>";
        String levelState = levelReady
                ? cfg.missingKeyFallback("city.defense.gui.tier.level_ok", "Pronto")
                : cfg.missingKeyFallback("city.defense.gui.tier.level_missing", "Richiede livello {level}")
                        .replace("{level}", Integer.toString(view.minCityLevel()));
        String cooldownState = cooldownReady
                ? cfg.missingKeyFallback("city.defense.gui.tier.cooldown_ready", "Pronto al bando")
                : cfg.missingKeyFallback("city.defense.gui.tier.cooldown_wait", "Ripresa: {time}")
                        .replace("{time}", formatDurationSeconds(view.cooldownRemainingSeconds()));
        String actionPath = levelReady && cooldownReady
                ? "city.defense.gui.tier.action_ready"
                : "city.defense.gui.tier.action_locked";
        String actionFallback = levelReady && cooldownReady
                ? "Puoi avviare subito questo assalto"
                : "Questo assalto non e ancora disponibile";
        return GuiItemFactory.item(
                material,
                cfg.msg(titlePath, fallbackTitle),
                UiLoreStyle.renderComponents(
                        UiLoreStyle.line(UiLoreStyle.LineType.FOCUS,
                                "Richiede livello " + view.minCityLevel() + " • " + view.waves() + " ondate • HP guardiano " + (int) Math.round(view.guardianHp())),
                        UiLoreStyle.line(UiLoreStyle.LineType.REWARD,
                                "Costo " + formatMoney(view.startCost()) + " • Ricompensa " + view.rewardXp() + " XP e " + formatMoney(view.rewardMoney())),
                        defenseTierRewardLore(view, levelState, cooldownState, cfg.missingKeyFallback(actionPath, actionFallback))
                )
        );
    }

    private CityDefenseTier defenseStartTierForSlot(int slot, int page) {
        CityDefenseTier[] tiers = CityDefenseTier.values();
        int offset = Math.max(0, page) * SLOTS_DEFENSE_START_TIERS.length;
        for (int i = 0; i < SLOTS_DEFENSE_START_TIERS.length; i++) {
            if (slot == SLOTS_DEFENSE_START_TIERS[i]) {
                int index = offset + i;
                if (index >= 0 && index < tiers.length) {
                    return tiers[index];
                }
                return null;
            }
        }
        return null;
    }

    private int defenseStartMaxPage() {
        return Math.max(0, (CityDefenseTier.values().length - 1) / SLOTS_DEFENSE_START_TIERS.length);
    }

    private Material defenseTierMaterial(CityDefenseTier tier) {
        return switch (tier.number()) {
            case 1 -> Material.IRON_SWORD;
            case 2 -> Material.DIAMOND_SWORD;
            case 3 -> Material.NETHERITE_SWORD;
            case 4 -> Material.CROSSBOW;
            case 5 -> Material.TOTEM_OF_UNDYING;
            case 6 -> Material.SHIELD;
            case 7 -> Material.NETHERITE_HELMET;
            case 8 -> Material.NETHERITE_CHESTPLATE;
            case 9 -> Material.BEACON;
            case 10 -> Material.NETHER_STAR;
            case 11 -> Material.BLAZE_ROD;
            case 12 -> Material.ECHO_SHARD;
            case 13 -> Material.TRIDENT;
            case 14 -> Material.RESPAWN_ANCHOR;
            case 15 -> Material.ENCHANTED_GOLDEN_APPLE;
            case 16 -> Material.HEART_OF_THE_SEA;
            case 17 -> Material.END_CRYSTAL;
            case 18 -> Material.NETHERITE_AXE;
            case 19 -> Material.DRAGON_BREATH;
            case 20 -> Material.WITHER_SKELETON_SKULL;
            case 21 -> Material.RECOVERY_COMPASS;
            case 22 -> Material.CONDUIT;
            case 23 -> Material.DRAGON_HEAD;
            case 24 -> Material.DRAGON_EGG;
            case 25 -> Material.NETHERITE_BLOCK;
            default -> Material.GRAY_DYE;
        };
    }

    private int[] concatSlots(int[] base, int... extras) {
        int[] merged = java.util.Arrays.copyOf(base, base.length + extras.length);
        System.arraycopy(extras, 0, merged, base.length, extras.length);
        return merged;
    }

    private int[] mergeSlots(int[]... groups) {
        int size = 0;
        for (int[] group : groups) {
            size += group.length;
        }
        int[] merged = new int[size];
        int offset = 0;
        for (int[] group : groups) {
            System.arraycopy(group, 0, merged, offset, group.length);
            offset += group.length;
        }
        return merged;
    }

    private boolean slotInGroup(int slot, int[] slots) {
        for (int candidate : slots) {
            if (candidate == slot) {
                return true;
            }
        }
        return false;
    }

    private ItemStack quickAmountItem(Material material, String path, String fallback, boolean enabled) {
        if (!enabled) {
            return button(Material.GRAY_DYE, path + "_locked", fallback.replace("</", " (lock)</"));
        }
        return button(material, path, fallback);
    }

    private ItemStack head(String texture, String path, String fallback) {
        return GuiItemFactory.customHead(texture, cfg.msg(path, fallback));
    }

    private ItemStack button(Material material, String path, String fallback) {
        return GuiItemFactory.item(material, cfg.msg(path, fallback));
    }

    private ItemStack button(Material material, String path, String fallback, List<Component> lore) {
        return GuiItemFactory.item(material, cfg.msg(path, fallback), lore.stream().map(this::noItalic).toList());
    }

    private boolean hasPrivilege(Player player, Privilege privilege) {
        return huskTownsApiHook.hasPrivilege(player, privilege);
    }

    private int cityWarServiceSettingsPageSize() {
        return Math.max(1, Math.min(SLOTS_WAR_LIST_TARGETS.length, cityWarService.guiPageSize()));
    }

    private int warListContentIndex(int slot) {
        for (int i = 0; i < SLOTS_WAR_LIST_TARGETS.length; i++) {
            if (SLOTS_WAR_LIST_TARGETS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private ItemStack warTargetItem(CityWarService.WarTargetView target) {
        Material icon = target.eligible() ? Material.IRON_SWORD : Material.GRAY_DYE;
        List<UiLoreStyle.StyledLine> loreLines = new ArrayList<>();
        loreLines.add(UiLoreStyle.line(UiLoreStyle.LineType.INFO, "Livello citta: " + target.cityLevel()));
        loreLines.add(UiLoreStyle.line(UiLoreStyle.LineType.STATUS, "Stato: " + warTargetState(target.stateReason())));
        if (target.eligible() && target.attackerVictoryXp() != null && target.attackerVictoryXp().enabled()) {
            loreLines.add(UiLoreStyle.line(UiLoreStyle.LineType.REWARD,
                    "XP vittoria prevista: " + formatDecimal(target.attackerVictoryXp().totalXp())));
        }
        if (target.eligible() && target.attackerVictoryMoney() != null && target.attackerVictoryMoney().enabled()) {
            loreLines.add(UiLoreStyle.line(UiLoreStyle.LineType.REWARD,
                    "Premio netto previsto: " + cityWarService.formatMoney(target.attackerVictoryMoney().netPayout())
                            + " • Tributo " + cityWarService.formatMoney(target.attackerVictoryMoney().feeAmount())));
        }
        loreLines.add(UiLoreStyle.line(
                target.eligible() ? UiLoreStyle.LineType.ACTION : UiLoreStyle.LineType.WARNING,
                target.eligible() ? "Clicca per preparare la dichiarazione." : "Il bando non e ancora lecito contro questa citta."
        ));
        List<Component> lore = UiLoreStyle.renderComponents(
                UiLoreStyle.line(UiLoreStyle.LineType.STATUS, "Relazione: " + cityWarService.relationLabel(target.relation())),
                UiLoreStyle.line(UiLoreStyle.LineType.STATUS, "Loro verso di voi: " + cityWarService.relationLabel(target.reciprocalRelation())),
                loreLines
        ).stream().map(this::noItalic).toList();
        return GuiItemFactory.item(
                icon,
                cfg.msg(
                        "city.war.gui.list.target",
                        target.eligible() ? "<red>{town}</red> <gray>[</gray><white>{tag}</white><gray>]</gray>" : "<gray>{town}</gray> <gray>[</gray><white>{tag}</white><gray>]</gray>",
                        Placeholder.unparsed("town", target.townName()),
                        Placeholder.unparsed("tag", target.townTag())
                ),
                lore
        );
    }

    private List<Component> warLiveOverviewLore(CityWarService.WarOverview overview) {
        List<UiLoreStyle.StyledLine> lines = new ArrayList<>();
        lines.add(UiLoreStyle.line(UiLoreStyle.LineType.INFO, "Inizio: " + cityWarService.formatTimestamp(overview.startTime())));
        if (overview.attackerVictoryMoney() != null && overview.attackerVictoryMoney().enabled()) {
            lines.add(UiLoreStyle.line(UiLoreStyle.LineType.REWARD,
                    "Premio netto se vince l'attaccante: " + cityWarService.formatMoney(overview.attackerVictoryMoney().netPayout())));
        }
        if (overview.defenderVictoryMoney() != null && overview.defenderVictoryMoney().enabled()) {
            lines.add(UiLoreStyle.line(UiLoreStyle.LineType.REWARD,
                    "Premio netto se vince il difensore: " + cityWarService.formatMoney(overview.defenderVictoryMoney().netPayout())));
        }
        if (overview.attackerVictoryXp() != null && overview.attackerVictoryXp().enabled()) {
            lines.add(UiLoreStyle.line(UiLoreStyle.LineType.REWARD,
                    "XP se vince l'attaccante: " + formatDecimal(overview.attackerVictoryXp().totalXp())));
        }
        if (overview.defenderVictoryXp() != null && overview.defenderVictoryXp().enabled()) {
            lines.add(UiLoreStyle.line(UiLoreStyle.LineType.REWARD,
                    "XP se vince il difensore: " + formatDecimal(overview.defenderVictoryXp().totalXp())));
        }
        CityWarService.WarMoneyRewardPreview viewerMoney = overview.viewerIsAttacker()
                ? overview.attackerVictoryMoney()
                : overview.viewerIsDefender() ? overview.defenderVictoryMoney() : null;
        if (viewerMoney != null && viewerMoney.enabled()) {
            lines.add(UiLoreStyle.line(UiLoreStyle.LineType.REWARD,
                    "Se vinci tu: " + cityWarService.formatMoney(viewerMoney.netPayout())
                            + " • Tributo " + cityWarService.formatMoney(viewerMoney.feeAmount())));
        }
        CityWarService.WarXpRewardPreview viewerReward = overview.viewerIsAttacker()
                ? overview.attackerVictoryXp()
                : overview.viewerIsDefender() ? overview.defenderVictoryXp() : null;
        if (viewerReward != null && viewerReward.enabled()) {
            lines.add(UiLoreStyle.line(UiLoreStyle.LineType.REWARD,
                    "La tua citta otterra: " + formatDecimal(viewerReward.totalXp()) + " XP"));
        }
        return UiLoreStyle.renderComponents(
                UiLoreStyle.line(UiLoreStyle.LineType.REWARD, "Posta: " + cityWarService.formatMoney(overview.wager())),
                UiLoreStyle.line(UiLoreStyle.LineType.STATUS, "Raggio war zone: " + overview.warZoneRadius()),
                lines
        ).stream().map(this::noItalic).toList();
    }

    private List<Component> appendLore(List<Component> lore, Component trailing) {
        List<Component> merged = new ArrayList<>(lore);
        merged.add(trailing);
        return merged;
    }

    private List<DialogBody> warDeclareDialogBody(CityWarService.WarTargetView target, CityWarService.DeclarationPreview preview) {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(cfg.msg("city.war.dialog.declare.body_1", "<gray>Target:</gray> <white>{town}</white>", Placeholder.unparsed("town", target.townName()))));
        body.add(DialogBody.plainMessage(cfg.msg("city.war.dialog.declare.body_2", "<gray>Relazione finale:</gray> <white>{relation}</white>", Placeholder.unparsed("relation", preview.relationOutcome()))));
        body.add(DialogBody.plainMessage(cfg.msg("city.war.dialog.declare.body_3", "<gray>Posta minima:</gray> <white>{money}</white>", Placeholder.unparsed("money", cityWarService.formatMoney(preview.minimumWager())))));
        body.add(DialogBody.plainMessage(cfg.msg("city.war.dialog.declare.body_4", "<gray>Attesa prima di una nuova guerra:</gray> <white>{hours}h</white>", Placeholder.unparsed("hours", Long.toString(preview.cooldownHours())))));
        body.add(DialogBody.plainMessage(cfg.msg("city.war.dialog.declare.body_5", "<gray>Tempo per accettare:</gray> <white>{minutes}m</white>", Placeholder.unparsed("minutes", Long.toString(preview.declarationExpiryMinutes())))));
        if (preview.attackerVictoryMoney() != null && preview.attackerVictoryMoney().enabled()) {
            body.add(DialogBody.plainMessage(cfg.msg("city.war.dialog.declare.body_6_money", "<gray>Premio lordo del vessillo vincitore:</gray> <gold>{money}</gold>", Placeholder.unparsed("money", cityWarService.formatMoney(preview.attackerVictoryMoney().grossPayout())))));
            body.add(DialogBody.plainMessage(cfg.msg("city.war.dialog.declare.body_7_money", "<gray>Tributo di guerra trattenuto:</gray> <gold>{money}</gold>", Placeholder.unparsed("money", cityWarService.formatMoney(preview.attackerVictoryMoney().feeAmount())))));
            body.add(DialogBody.plainMessage(cfg.msg("city.war.dialog.declare.body_8_money", "<gray>Premio netto previsto:</gray> <gold>{money}</gold>", Placeholder.unparsed("money", cityWarService.formatMoney(preview.attackerVictoryMoney().netPayout())))));
        }
        if (preview.attackerVictoryXp() != null && preview.attackerVictoryXp().enabled()) {
            body.add(DialogBody.plainMessage(cfg.msg("city.war.dialog.declare.body_6", "<gray>XP base vittoria:</gray> <aqua>{xp}</aqua>", Placeholder.unparsed("xp", formatDecimal(preview.attackerVictoryXp().baseXp())))));
            body.add(DialogBody.plainMessage(cfg.msg("city.war.dialog.declare.body_7", "<gray>Bonus da posta:</gray> <aqua>{xp}</aqua>", Placeholder.unparsed("xp", formatDecimal(preview.attackerVictoryXp().wagerBonusXp())))));
            body.add(DialogBody.plainMessage(cfg.msg("city.war.dialog.declare.body_8", "<gray>Totale previsto:</gray> <aqua>{xp}</aqua>", Placeholder.unparsed("xp", formatDecimal(preview.attackerVictoryXp().totalXp())))));
        }
        return body;
    }

    private String warTargetState(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Sconosciuto";
        }
        return switch (reason) {
            case "ready" -> "Pronta al bando";
            case "war-disabled" -> "Bando sigillato";
            case "relations-disabled" -> "Relazioni dormienti";
            case "native-wars-disabled" -> "Motore di guerra inattivo";
            case "no-declare-privilege" -> "Manca il sigillo DECLARE_WAR";
            case "active-war-global" -> "Reame gia in guerra";
            case "same-town" -> "Stesso vessillo";
            case "source-level-low" -> "Il tuo vessillo non ha rango sufficiente";
            case "target-level-low" -> "Bersaglio sotto il rango minimo";
            case "level-gap-too-high" -> "Divario di rango oltre il limite";
            case "source-spawn-missing" -> "Il tuo vessillo non ha warp";
            case "target-spawn-missing" -> "Il bersaglio non ha warp";
            case "already-at-war" -> "Gia legata a un fronte";
            case "pending-target" -> "Gia vincolata a un bando pendente";
            case "source-cooldown" -> "Il tuo fronte e in quiete";
            case "target-cooldown" -> "Il bersaglio e in quiete";
            case "attacker-minimum-funds" -> "Tesoro d'assalto insufficiente";
            case "defender-minimum-funds" -> "Tesoro difensore insufficiente";
            default -> reason;
        };
    }

    private void openNoticeDialog(Player player, Component title, List<Component> lines, String trace) {
        DialogBase base = DialogBase.builder(title)
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(lines.stream().map(DialogBody::plainMessage).toList())
                .build();
        Dialog dialog = Dialog.create(factory -> {
            ActionButton closeButton = ActionButton.create(
                    cfg.msg("city.war.dialog.close", "<gold>Chiudi</gold>"),
                    null,
                    180,
                    DialogAction.customClick((response, audience) -> {
                    }, CLICK_OPTIONS)
            );
            factory.empty().base(base).type(DialogType.notice(closeButton));
        });
        DialogViewUtils.showDialog(
                plugin,
                player,
                dialog,
                cfg.msg("city.war.errors.dialog_unavailable", "<red>Dialog non disponibile su questo server.</red>"),
                trace
        );
    }

    private String formatMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String defenseRewardProfileLabel(CityDefenseService.CityDefenseTierView view) {
        if (view.retryAdjusted()) {
            return "Ricompensa da nuovo tentativo";
        }
        if (view.completedBefore()) {
            return "Ricompensa standard";
        }
        return "Prima vittoria";
    }

    private List<UiLoreStyle.StyledLine> defenseTierRewardLore(
            CityDefenseService.CityDefenseTierView view,
            String levelState,
            String cooldownState,
            String actionFallback
    ) {
        List<UiLoreStyle.StyledLine> lines = new ArrayList<>();
        lines.add(UiLoreStyle.line(UiLoreStyle.LineType.TIME, "Durata: " + formatDurationSeconds(view.maxDurationSeconds())));
        lines.add(UiLoreStyle.line(UiLoreStyle.LineType.STATUS, "Stato: " + levelState + " • " + cooldownState));
        lines.add(UiLoreStyle.line(UiLoreStyle.LineType.SPECIAL, "Tipo bottino: " + defenseRewardProfileLabel(view)));

        if (view.rewardItems().isEmpty()) {
            lines.add(UiLoreStyle.line(UiLoreStyle.LineType.REWARD, "Bottino base: solo soldi e XP"));
        } else {
            lines.add(UiLoreStyle.line(UiLoreStyle.LineType.REWARD, "Bottino base:"));
            defenseRewardItemLines(view.rewardItems()).forEach(line ->
                    lines.add(UiLoreStyle.line(UiLoreStyle.LineType.REWARD, line))
            );
        }

        if (view.firstClearUniqueRewardAvailable()) {
            List<String> firstClearLines = defenseFirstClearRewardLines(view);
            if (firstClearLines.isEmpty()) {
                lines.add(UiLoreStyle.line(UiLoreStyle.LineType.SPECIAL, "Prima vittoria: ricompensa unica"));
            } else {
                lines.add(UiLoreStyle.line(UiLoreStyle.LineType.SPECIAL, "Prima vittoria:"));
                firstClearLines.forEach(line ->
                        lines.add(UiLoreStyle.line(UiLoreStyle.LineType.SPECIAL, line))
                );
            }
        } else if (!view.firstClearUniqueRewards().isEmpty()) {
            lines.add(UiLoreStyle.line(UiLoreStyle.LineType.SPECIAL, "Prima vittoria: gia riscossa"));
        } else {
            lines.add(UiLoreStyle.line(UiLoreStyle.LineType.INFO, "Prima vittoria: nessuna ricompensa unica"));
        }

        lines.add(UiLoreStyle.line(UiLoreStyle.LineType.ACTION, UiLoreStyle.normalizeLegacyContent(actionFallback)));
        return List.copyOf(lines);
    }

    private List<String> defenseRewardItemLines(Map<Material, Integer> rewardItems) {
        return rewardItems.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0)
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Enum::name)))
                .map(entry -> entry.getValue() + "x " + materialLabel(entry.getKey()))
                .toList();
    }

    private List<String> defenseFirstClearRewardLines(CityDefenseService.CityDefenseTierView view) {
        return view.firstClearUniqueRewards().stream()
                .filter(spec -> spec != null && spec.amount() > 0 && spec.itemId() != null && !spec.itemId().isBlank())
                .map(spec -> spec.amount() + "x " + rewardSpecLabel(spec))
                .toList();
    }

    private String rewardSpecLabel(it.patric.cittaexp.itemsadder.ItemsAdderRewardResolver.RewardSpec spec) {
        CustomStack custom = CustomStack.getInstance(spec.itemId());
        if (custom != null && custom.getItemStack() != null && custom.getItemStack().hasItemMeta()
                && custom.getItemStack().getItemMeta() != null
                && custom.getItemStack().getItemMeta().hasDisplayName()) {
            return UiLoreStyle.normalizeLegacyContent(custom.getItemStack().getItemMeta().getDisplayName());
        }
        String raw = spec.itemId();
        int separator = raw.indexOf(':');
        String local = separator >= 0 ? raw.substring(separator + 1) : raw;
        return local.replace('_', ' ').trim();
    }

    private String materialLabel(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String formatDurationSeconds(long seconds) {
        long safe = Math.max(0L, seconds);
        long hours = safe / 3600L;
        long minutes = (safe % 3600L) / 60L;
        long sec = safe % 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + sec + "s";
        }
        return sec + "s";
    }

    private String formatDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private CityPlayersSession toPlayersSession(HubSession session) {
        return new CityPlayersSession(
                session.sessionId(),
                session.viewerId(),
                session.townId(),
                session.townName(),
                session.mayorId(),
                false,
                CityPlayersBackTarget.LIST,
                List.of()
        );
    }

    private enum View {
        HOME,
        DEFENSE_MENU,
        DEFENSE_START,
        DEFENSE_LIVE,
        WAR_LIST,
        WAR_LIVE
    }

    private record HubSession(
            UUID sessionId,
            UUID viewerId,
            int townId,
            String townName,
            UUID mayorId,
            boolean warSampleMode
    ) {
    }

    private record HomeOpenResult(
            Inventory inventory,
            dev.lone.itemsadder.api.FontImages.TexturedInventoryWrapper texturedWrapper
    ) {
    }

    private static final class HubHolder implements InventoryHolder {
        private final UUID sessionId;
        private final View view;
        private Inventory inventory;

        private HubHolder(UUID sessionId, View view) {
            this.sessionId = sessionId;
            this.view = view;
        }

        private UUID sessionId() {
            return sessionId;
        }

        private View view() {
            return view;
        }

        private void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
