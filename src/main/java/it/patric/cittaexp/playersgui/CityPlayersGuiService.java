package it.patric.cittaexp.playersgui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import it.patric.cittaexp.activity.PlayerActivityService;
import it.patric.cittaexp.claimsettings.CityClaimSettingsMenuService;
import it.patric.cittaexp.claimsettings.ClaimRulesDialogService;
import it.patric.cittaexp.citylistgui.CityTownListGuiService;
import it.patric.cittaexp.disbandtown.TownDisbandDialogFlow;
import it.patric.cittaexp.edittown.TownEditDialogFlow;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.relationsgui.CityRelationsGuiService;
import it.patric.cittaexp.manageclaim.CitySectionMapGuiService;
import it.patric.cittaexp.permissions.TownMemberPermission;
import it.patric.cittaexp.permissions.TownMemberPermissionService;
import it.patric.cittaexp.playersgui.CityPlayersActionService.ActionResult;
import it.patric.cittaexp.playersgui.CityPlayersActionService.ActionType;
import it.patric.cittaexp.text.MiniMessageHelper;
import it.patric.cittaexp.text.UiLoreStyle;
import it.patric.cittaexp.utils.DialogConfirmHelper;
import it.patric.cittaexp.utils.DialogInputUtils;
import it.patric.cittaexp.utils.DialogViewUtils;
import it.patric.cittaexp.utils.GuiHeadTextures;
import it.patric.cittaexp.utils.GuiItemFactory;
import it.patric.cittaexp.utils.GuiLayoutUtils;
import it.patric.cittaexp.utils.GuiNavigationUtils;
import it.patric.cittaexp.utils.PluginConfigUtils;
import it.patric.cittaexp.utils.ReasonMessageMapper;
import it.patric.cittaexp.utils.TexturedGuiSupport;
import it.patric.cittaexp.vault.CityItemVaultGuiService;
import it.patric.cittaexp.vault.CityItemVaultService;
import it.patric.cittaexp.wiki.CityWikiBackTarget;
import it.patric.cittaexp.wiki.CityWikiDialogService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.william278.husktowns.town.Member;
import net.william278.husktowns.town.Privilege;
import net.william278.husktowns.town.Town;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class CityPlayersGuiService implements Listener {

    private static final int GUI_SIZE = 54;
    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_ACTIONS = 47;
    private static final int SLOT_PAGE = 49;
    private static final int SLOT_NEXT = 53;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_CLOSE = 53;
    private static final int SLOT_PROMOTE = 30;
    private static final int SLOT_DEMOTE = 32;
    private static final int SLOT_EVICT = 39;
    private static final int SLOT_TRANSFER = 41;
    private static final int SLOT_DETAIL_TAB_OVERVIEW = 1;
    private static final int SLOT_DETAIL_TAB_NATIVE = 4;
    private static final int SLOT_DETAIL_TAB_PLUGIN = 7;
    private static final int[] SLOT_PLUGIN_PERMISSIONS = {
            19, 20, 21, 22, 23, 24,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int ACTIONS_GUI_SIZE = 54;
    private static final int BANK_GUI_SIZE = 45;
    private static final int ACTIONS_SLOT_RELATIONS = 9;
    private static final int ACTIONS_SLOT_ITEM_VAULT = 20;
    private static final String BANK_FONT_IMAGE_ID = "cittaexp_gui:deposito_banca_vault_9x5";
    private static final String TRANSPARENT_BUTTON_ID = "cittaexp_gui:transparent_button";
    private static final int[] BANK_SLOTS_BALANCE = {1, 2, 3, 10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int[] BANK_SLOTS_DEPOSIT_CUSTOM = {9};
    private static final int[] BANK_SLOTS_WITHDRAW_CUSTOM = {13};
    private static final int[] BANK_SLOTS_DEPOSIT_10K = {18};
    private static final int[] BANK_SLOTS_WITHDRAW_10K = {22};
    private static final int[] BANK_SLOTS_DEPOSIT_1K = {27};
    private static final int[] BANK_SLOTS_WITHDRAW_1K = {31};
    private static final int[] BANK_SLOTS_VAULT = {14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35};
    private static final int[] BANK_SLOTS_BACK = {37, 38, 39};
    private static final int[] BANK_SLOTS_HELP = {41, 42, 43};
    private static final int ACTIONS_SLOT_BANK = 10;
    private static final int ACTIONS_SLOT_LEAVE = 11;
    private static final int ACTIONS_SLOT_TOWN_LIST = 12;
    private static final int ACTIONS_SLOT_SECTION = 13;
    private static final int ACTIONS_SLOT_CLAIM_SETTINGS = 14;
    private static final int ACTIONS_SLOT_EDIT = 15;
    private static final int ACTIONS_SLOT_DISBAND = 16;
    private static final int ACTIONS_SLOT_BACK = 18;
    private static final int ACTIONS_SLOT_CLOSE = 26;
    private static final String STAFF_PERMISSION = "cittaexp.city.players.staff";
    private static final ClickCallback.Options CLICK_OPTIONS = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(2))
            .build();

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final PluginConfigUtils configUtils;
    private final CityPlayersActionService actionService;
    private final HuskTownsRoleLadderService roleLadderService;
    private final TownEditDialogFlow townEditDialogFlow;
    private final TownDisbandDialogFlow townDisbandDialogFlow;
    private final CitySectionMapGuiService citySectionMapGuiService;
    private final CityTownListGuiService cityTownListGuiService;
    private final CityRelationsGuiService cityRelationsGuiService;
    private final CityItemVaultGuiService cityItemVaultGuiService;
    private final CityLevelService cityLevelService;
    private final CityItemVaultService cityItemVaultService;
    private final TownMemberPermissionService permissionService;
    private final CityClaimSettingsMenuService cityClaimSettingsMenuService;
    private final CityWikiDialogService cityWikiDialogService;
    private final PlayerActivityService playerActivityService;
    private final Map<UUID, CityPlayersSession> sessionsByViewer = new ConcurrentHashMap<>();
    private final Map<UUID, CachedName> displayNameCache = new ConcurrentHashMap<>();

    public CityPlayersGuiService(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            CityPlayersActionService actionService,
            HuskTownsRoleLadderService roleLadderService,
            TownEditDialogFlow townEditDialogFlow,
            TownDisbandDialogFlow townDisbandDialogFlow,
            CitySectionMapGuiService citySectionMapGuiService,
            CityTownListGuiService cityTownListGuiService,
            CityRelationsGuiService cityRelationsGuiService,
            CityItemVaultGuiService cityItemVaultGuiService,
            CityLevelService cityLevelService,
            CityItemVaultService cityItemVaultService,
            TownMemberPermissionService permissionService,
            CityWikiDialogService cityWikiDialogService,
            PlayerActivityService playerActivityService
    ) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.configUtils = new PluginConfigUtils(plugin);
        this.actionService = actionService;
        this.roleLadderService = roleLadderService;
        this.townEditDialogFlow = townEditDialogFlow;
        this.townDisbandDialogFlow = townDisbandDialogFlow;
        this.citySectionMapGuiService = citySectionMapGuiService;
        this.cityTownListGuiService = cityTownListGuiService;
        this.cityRelationsGuiService = cityRelationsGuiService;
        this.cityItemVaultGuiService = cityItemVaultGuiService;
        this.cityLevelService = cityLevelService;
        this.cityItemVaultService = cityItemVaultService;
        this.permissionService = permissionService;
        this.cityWikiDialogService = cityWikiDialogService;
        this.playerActivityService = playerActivityService;
        this.cityClaimSettingsMenuService = new CityClaimSettingsMenuService(
                plugin,
                huskTownsApiHook,
                cityLevelService,
                new ClaimRulesDialogService(plugin, huskTownsApiHook),
                cityWikiDialogService
        );
    }

    public void openForOwnTown(Player player) {
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty()) {
            player.sendMessage(configUtils.msg("city.players.gui.errors.no_town", "<red>Devi essere membro attivo di una citta.</red>"));
            return;
        }
        openForTown(player, member.get().town(), false);
    }

    public void openClaimSettingsForOwnTown(Player player) {
        withOwnTownSession(player, session -> cityClaimSettingsMenuService.open(player, session));
    }

    public void openBankForOwnTown(Player player, CityPlayersBackTarget backTarget) {
        withOwnTownSession(player, session -> {
            session.backTarget(backTarget == null ? CityPlayersBackTarget.LIST : backTarget);
            openBankView(player, session);
        });
    }

    public void openForTargetTown(Player player, String townName) {
        if (!player.hasPermission(STAFF_PERMISSION)) {
            player.sendMessage("[CittaEXP] Permesso mancante: " + STAFF_PERMISSION);
            return;
        }
        Optional<Town> town = huskTownsApiHook.getTownByName(townName);
        if (town.isEmpty()) {
            player.sendMessage(configUtils.msg("city.players.gui.errors.town_not_found",
                    "<red>Citta non trovata:</red> <yellow>{town}</yellow>",
                    Placeholder.unparsed("town", townName)));
            return;
        }
        openForTown(player, town.get(), true);
    }

    public void openAdminVaultForTargetTown(Player player, String townName) {
        if (!player.hasPermission(STAFF_PERMISSION)) {
            player.sendMessage("[CittaEXP] Permesso mancante: " + STAFF_PERMISSION);
            return;
        }
        Optional<Town> town = huskTownsApiHook.getTownByName(townName);
        if (town.isEmpty()) {
            player.sendMessage(configUtils.msg("city.players.gui.errors.town_not_found",
                    "<red>Citta non trovata:</red> <yellow>{town}</yellow>",
                    Placeholder.unparsed("town", townName)));
            return;
        }
        cityItemVaultGuiService.openAdmin(player, town.get().getId(), town.get().getName(), town.get().getMayor());
    }

    public void clearAllSessions() {
        sessionsByViewer.clear();
        cityClaimSettingsMenuService.clearAllSessions();
    }

    private void openForTown(Player viewer, Town town, boolean staffView) {
        viewer.sendMessage(configUtils.msg("city.players.gui.loading", "<gray>Caricamento membri citta in corso...</gray>"));
        loadMembersAsync(town).whenComplete((members, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (error != null) {
                viewer.sendMessage(configUtils.msg("city.players.gui.errors.load_failed",
                        "<red>Impossibile caricare la lista membri.</red>"));
                plugin.getLogger().warning("[players-gui] load failed townId=" + town.getId()
                        + " viewer=" + viewer.getUniqueId()
                        + " reason=" + error.getMessage());
                return;
            }

            CityPlayersSession session = new CityPlayersSession(
                    UUID.randomUUID(),
                    viewer.getUniqueId(),
                    town.getId(),
                    town.getName(),
                    town.getMayor(),
                    staffView,
                    CityPlayersBackTarget.LIST,
                    members
            );
            sessionsByViewer.put(viewer.getUniqueId(), session);
            cityItemVaultService.warmTown(town.getId());
            openListView(viewer, session);
        }));
    }

    private void withOwnTownSession(Player player, Consumer<CityPlayersSession> consumer) {
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty()) {
            player.sendMessage(configUtils.msg("city.players.gui.errors.no_town", "<red>Devi essere membro attivo di una citta.</red>"));
            return;
        }
        Town town = member.get().town();
        CityPlayersSession existing = sessionsByViewer.get(player.getUniqueId());
        if (existing != null && !existing.staffView() && existing.townId() == town.getId()) {
            consumer.accept(existing);
            return;
        }
        loadMembersAsync(town).whenComplete((members, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (error != null) {
                player.sendMessage(configUtils.msg("city.players.gui.errors.load_failed",
                        "<red>Impossibile caricare la lista membri.</red>"));
                plugin.getLogger().warning("[players-gui] load failed townId=" + town.getId()
                        + " viewer=" + player.getUniqueId()
                        + " reason=" + error.getMessage());
                return;
            }
            CityPlayersSession session = new CityPlayersSession(
                    UUID.randomUUID(),
                    player.getUniqueId(),
                    town.getId(),
                    town.getName(),
                    town.getMayor(),
                    false,
                    CityPlayersBackTarget.LIST,
                    members
            );
            sessionsByViewer.put(player.getUniqueId(), session);
            consumer.accept(session);
        }));
    }

    public void openMemberDetailFromHub(Player player, int townId, UUID memberId) {
        if (player == null || memberId == null) {
            return;
        }
        CityPlayersSession existing = sessionsByViewer.get(player.getUniqueId());
        if (existing != null && !existing.staffView() && existing.townId() == townId) {
            existing.backTarget(CityPlayersBackTarget.HUB);
            if (existing.members().stream().anyMatch(view -> view.playerId().equals(memberId))) {
                openMemberDialogById(player, existing, memberId);
            } else {
                reloadMembersAndOpenMember(player, existing, memberId);
            }
            return;
        }
        Optional<Town> town = huskTownsApiHook.getTownById(townId);
        if (town.isEmpty()) {
            player.sendMessage(configUtils.msg("city.players.gui.errors.town_not_found_runtime",
                    "<red>La citta non e piu disponibile.</red>"));
            return;
        }
        loadMembersAsync(town.get()).whenComplete((members, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (error != null) {
                player.sendMessage(configUtils.msg("city.players.gui.errors.load_failed",
                        "<red>Impossibile caricare la lista membri.</red>"));
                return;
            }
            CityPlayersSession session = new CityPlayersSession(
                    UUID.randomUUID(),
                    player.getUniqueId(),
                    town.get().getId(),
                    town.get().getName(),
                    town.get().getMayor(),
                    false,
                    CityPlayersBackTarget.HUB,
                    members
            );
            sessionsByViewer.put(player.getUniqueId(), session);
            openMemberDialogById(player, session, memberId);
        }));
    }

    private void reloadMembersAndOpenMember(Player player, CityPlayersSession session, UUID memberId) {
        Optional<Town> town = huskTownsApiHook.getTownById(session.townId());
        if (town.isEmpty()) {
            player.sendMessage(configUtils.msg("city.players.gui.errors.town_not_found_runtime",
                    "<red>La citta non e piu disponibile.</red>"));
            return;
        }
        loadMembersAsync(town.get()).whenComplete((members, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (error != null) {
                player.sendMessage(configUtils.msg("city.players.gui.errors.load_failed",
                        "<red>Impossibile caricare la lista membri.</red>"));
                return;
            }
            session.updateTown(town.get().getId(), town.get().getName(), town.get().getMayor(), members);
            sessionsByViewer.put(player.getUniqueId(), session);
            openMemberDialogById(player, session, memberId);
        }));
    }

    private CompletableFuture<List<CityPlayersMemberView>> loadMembersAsync(Town town) {
        Map<UUID, Integer> snapshotMembers = new HashMap<>(town.getMembers());
        List<CompletableFuture<CityPlayersMemberView>> futures = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : snapshotMembers.entrySet()) {
            UUID userId = entry.getKey();
            int weight = entry.getValue();
            CompletableFuture<CityPlayersMemberView> memberFuture = resolveDisplayName(userId)
                    .exceptionally(ex -> "Sconosciuto-" + userId.toString().substring(0, 8))
                    .thenApply(name -> new CityPlayersMemberView(userId, name, weight));
            futures.add(memberFuture);
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(unused -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toCollection(ArrayList::new)));
    }

    private CompletableFuture<String> resolveDisplayName(UUID userId) {
        CachedName cached = displayNameCache.get(userId);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtMillis() > now) {
            return CompletableFuture.completedFuture(cached.name());
        }
        return huskTownsApiHook.getUsername(userId)
                .thenApply(optional -> optional.orElse("Sconosciuto-" + userId.toString().substring(0, 8)))
                .thenApply(name -> {
                    displayNameCache.put(userId, new CachedName(name, now + Duration.ofMinutes(5).toMillis()));
                    return name;
                });
    }

    private void openListView(Player player, CityPlayersSession session) {
        List<CityPlayersMemberView> sorted = sortedMembers(session, session.members());
        int pageCount = Math.max(1, (sorted.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (session.page() >= pageCount) {
            session.page(pageCount - 1);
        }
        int from = session.page() * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, sorted.size());

        SessionHolder holder = SessionHolder.list(session.sessionId());
        Inventory inventory = Bukkit.createInventory(holder, GUI_SIZE, configUtils.msg("city.players.gui.list.title",
                "<gold>Membri Citta</gold> <gray>{town}</gray>",
                Placeholder.unparsed("town", session.townName())));
        holder.bind(inventory);

        GuiLayoutUtils.fillAllExcept(
                inventory,
                GuiItemFactory.fillerPane(),
                SLOT_PREV, SLOT_ACTIONS, SLOT_PAGE, SLOT_NEXT
        );
        inventory.setItem(SLOT_PREV, navPrev(session.page() > 0));
        inventory.setItem(SLOT_NEXT, navNext(session.page() + 1 < pageCount));
        inventory.setItem(SLOT_PAGE, pageIndicator(session.page() + 1, pageCount, sorted.size()));
        if (!session.staffView()) {
            inventory.setItem(SLOT_ACTIONS, actionHead(GuiHeadTextures.ACTIONS,
                    "city.players.gui.list.hub",
                    "<aqua>Torna al City Hub</aqua>"));
        } else {
            inventory.setItem(SLOT_ACTIONS, actionHead(GuiHeadTextures.RELATIONS,
                    "city.players.gui.list.relations",
                    "<aqua>Relazioni (read-only)</aqua>"));
        }

        int slot = 0;
        for (int i = from; i < to; i++) {
            CityPlayersMemberView view = sorted.get(i);
            inventory.setItem(slot, memberHead(view, session, player));
            slot++;
        }
        player.openInventory(inventory);
    }

    private void openMemberDetailById(Player player, CityPlayersSession session, UUID memberId, DetailPage page) {
        Optional<CityPlayersMemberView> target = session.members().stream()
                .filter(view -> view.playerId().equals(memberId))
                .findFirst();
        if (target.isEmpty()) {
            player.sendMessage(configUtils.msg("city.players.gui.errors.member_not_found", "<red>Membro non trovato.</red>"));
            if (session.backTarget() == CityPlayersBackTarget.HUB) {
                GuiNavigationUtils.openCityHub(plugin, player);
            } else {
                openListView(player, session);
            }
            return;
        }
        openDetailView(player, session, target.get(), page);
    }

    private void openDetailView(Player player, CityPlayersSession session, CityPlayersMemberView memberView, DetailPage page) {
        SessionHolder holder = SessionHolder.detail(session.sessionId(), memberView.playerId(), page);
        Inventory inventory = Bukkit.createInventory(holder, GUI_SIZE, configUtils.msg("city.players.gui.detail.title",
                "<gold>Gestione Membro</gold> <gray>{name}</gray>",
                Placeholder.unparsed("name", memberView.displayName())));
        holder.bind(inventory);

        GuiLayoutUtils.fillAllExcept(
                inventory,
                GuiItemFactory.fillerPane(),
                SLOT_BACK, SLOT_CLOSE,
                SLOT_DETAIL_TAB_OVERVIEW, SLOT_DETAIL_TAB_NATIVE, SLOT_DETAIL_TAB_PLUGIN,
                SLOT_PROMOTE, SLOT_DEMOTE, SLOT_EVICT, SLOT_TRANSFER,
                13, 22, 31,
                19, 20, 21, 23, 24,
                28, 29, 30, 32, 33, 34
        );

        inventory.setItem(SLOT_DETAIL_TAB_OVERVIEW, detailTabButton("city.players.gui.detail.tabs.overview", "<gold>Panoramica</gold>", page == DetailPage.OVERVIEW));
        inventory.setItem(SLOT_DETAIL_TAB_NATIVE, detailTabButton("city.players.gui.detail.tabs.native", "<aqua>Cosa puo fare</aqua>", page == DetailPage.NATIVE_PRIVILEGES));
        inventory.setItem(SLOT_DETAIL_TAB_PLUGIN, detailTabButton("city.players.gui.detail.tabs.plugin", "<light_purple>Permessi speciali</light_purple>", page == DetailPage.PLUGIN_PERMISSIONS));
        inventory.setItem(13, memberHead(memberView, session, player));
        inventory.setItem(SLOT_BACK, backButton());
        inventory.setItem(SLOT_CLOSE, closeButton());

        switch (page) {
            case OVERVIEW -> {
                inventory.setItem(22, memberInfo(memberView, session, player));
                inventory.setItem(31, memberStatus(memberView, player));
                if (canPromote(player, session, memberView)) {
                    inventory.setItem(SLOT_PROMOTE, actionHead(GuiHeadTextures.PROMOTE,
                            "city.players.gui.detail.actions.promote",
                            "<green>Promuovi</green>"));
                }
                if (canDemote(player, session, memberView)) {
                    inventory.setItem(SLOT_DEMOTE, actionHead(GuiHeadTextures.DEMOTE,
                            "city.players.gui.detail.actions.demote",
                            "<gold>Demuovi</gold>"));
                }
                if (canEvict(player, session, memberView)) {
                    inventory.setItem(SLOT_EVICT, actionHead(GuiHeadTextures.DANGER,
                            "city.players.gui.detail.actions.evict",
                            "<red>Espelli</red>"));
                }
                if (canTransfer(player, session, memberView)) {
                    inventory.setItem(SLOT_TRANSFER, actionHead(GuiHeadTextures.TRANSFER,
                            "city.players.gui.detail.actions.transfer",
                            "<yellow>Trasferisci citta</yellow>"));
                }
            }
            case NATIVE_PRIVILEGES -> {
                inventory.setItem(22, memberNativePrivileges(memberView, session));
                inventory.setItem(31, memberStatus(memberView, player));
            }
            case PLUGIN_PERMISSIONS -> {
                inventory.setItem(22, memberPluginPermissionsInfo(memberView, session, player));
                populatePluginPermissionButtons(inventory, session, memberView);
            }
        }
        player.openInventory(inventory);
    }

    private void openCityActionsView(Player player, CityPlayersSession session) {
        SessionHolder holder = SessionHolder.actions(session.sessionId());
        Inventory inventory = Bukkit.createInventory(holder, ACTIONS_GUI_SIZE, configUtils.msg(
                "city.players.gui.city_actions.title",
                "<gold>Azioni Citta</gold> <gray>{town}</gray>",
                Placeholder.unparsed("town", session.townName())));
        holder.bind(inventory);

        GuiLayoutUtils.fillAllExcept(
                inventory,
                GuiItemFactory.fillerPane(),
                ACTIONS_SLOT_RELATIONS, ACTIONS_SLOT_BANK, ACTIONS_SLOT_LEAVE, ACTIONS_SLOT_TOWN_LIST, ACTIONS_SLOT_SECTION,
                ACTIONS_SLOT_CLAIM_SETTINGS, ACTIONS_SLOT_EDIT, ACTIONS_SLOT_DISBAND, ACTIONS_SLOT_ITEM_VAULT,
                ACTIONS_SLOT_BACK, ACTIONS_SLOT_CLOSE
        );
        inventory.setItem(ACTIONS_SLOT_RELATIONS, actionHead(
                GuiHeadTextures.RELATIONS,
                "city.players.gui.city_actions.relations",
                "<aqua>Relazioni</aqua>"));
        inventory.setItem(ACTIONS_SLOT_BANK, actionHead(
                GuiHeadTextures.BANK,
                "city.players.gui.city_actions.bank",
                "<gold>Banca Citta</gold>"));
        inventory.setItem(ACTIONS_SLOT_LEAVE, actionHead(
                GuiHeadTextures.DANGER,
                "city.players.gui.city_actions.leave",
                "<red>Abbandona citta</red>"));
        inventory.setItem(ACTIONS_SLOT_TOWN_LIST, actionHead(
                GuiHeadTextures.TOWN_LIST,
                "city.players.gui.city_actions.town_list",
                "<aqua>Lista Citta</aqua>"));
        inventory.setItem(ACTIONS_SLOT_SECTION, actionHead(
                GuiHeadTextures.SECTION,
                "city.players.gui.city_actions.section",
                "<aqua>Gestione territori</aqua>"));
        inventory.setItem(ACTIONS_SLOT_CLAIM_SETTINGS, actionHead(
                GuiHeadTextures.ACTIONS,
                "city.players.gui.city_actions.claim_settings",
                "<aqua>Impostazioni claim</aqua>"));
        inventory.setItem(ACTIONS_SLOT_EDIT, actionHead(
                GuiHeadTextures.EDIT,
                "city.players.gui.city_actions.edit",
                "<yellow>Modifica citta</yellow>"));
        inventory.setItem(ACTIONS_SLOT_DISBAND, actionHead(
                GuiHeadTextures.DISBAND,
                "city.players.gui.city_actions.disband",
                "<red>Elimina citta</red>"));
        inventory.setItem(ACTIONS_SLOT_ITEM_VAULT, actionHead(
                GuiHeadTextures.BANK,
                "city.players.gui.city_actions.item_vault",
                "<gold>Item Vault</gold>"));
        inventory.setItem(ACTIONS_SLOT_BACK, actionHead(
                GuiHeadTextures.BACK,
                "city.players.gui.city_actions.back",
                "<yellow>Indietro</yellow>"));
        inventory.setItem(ACTIONS_SLOT_CLOSE, actionHead(
                GuiHeadTextures.CLOSE,
                "city.players.gui.city_actions.close",
                "<red>Chiudi</red>"));
        player.openInventory(inventory);
    }

    private void openBankView(Player player, CityPlayersSession session) {
        if (session.staffView()) {
            player.sendMessage(configUtils.msg("city.players.gui.bank.errors.staff_forbidden",
                    "<red>Questa azione non e disponibile in modalita staff.</red>"));
            return;
        }
        Optional<Town> town = huskTownsApiHook.getTownById(session.townId());
        if (town.isEmpty()) {
            player.sendMessage(configUtils.msg("city.players.gui.errors.town_not_found_runtime",
                    "<red>La citta non e piu disponibile.</red>"));
            player.closeInventory();
            sessionsByViewer.remove(player.getUniqueId());
            return;
        }

        SessionHolder holder = SessionHolder.bank(session.sessionId());
        BankOpenResult openResult = createBankInventory(
                holder,
                configUtils.msg(
                        "city.players.gui.bank.title",
                        "<gold>Banca Citta</gold> <gray>{town}</gray>",
                        Placeholder.unparsed("town", town.get().getName())
                )
        );
        Inventory inventory = openResult.inventory();
        GuiLayoutUtils.fillAllExcept(inventory, GuiItemFactory.fillerPane(), bankInteractiveSlots());

        boolean canDeposit = huskTownsApiHook.hasPrivilege(player, Privilege.DEPOSIT);
        boolean canWithdraw = huskTownsApiHook.hasPrivilege(player, Privilege.WITHDRAW);
        ItemStack transparentBase = TexturedGuiSupport.transparentButtonBase(plugin, TRANSPARENT_BUTTON_ID);
        TexturedGuiSupport.fillGroup(inventory, BANK_SLOTS_BALANCE, bankBalanceItem(town.get(), transparentBase));
        TexturedGuiSupport.fillGroup(inventory, BANK_SLOTS_DEPOSIT_CUSTOM, bankActionButton(
                transparentBase,
                canDeposit,
                GuiHeadTextures.DEPOSIT,
                GuiHeadTextures.LOCKED,
                "city.players.gui.bank.deposit",
                "city.players.gui.bank.deposit_locked",
                "<green>Deposita somma libera</green>",
                "<gray>Deposita (permesso mancante)</gray>"
        ));
        TexturedGuiSupport.fillGroup(inventory, BANK_SLOTS_WITHDRAW_CUSTOM, bankActionButton(
                transparentBase,
                canWithdraw,
                GuiHeadTextures.WITHDRAW,
                GuiHeadTextures.LOCKED,
                "city.players.gui.bank.withdraw",
                "city.players.gui.bank.withdraw_locked",
                "<gold>Preleva somma libera</gold>",
                "<gray>Preleva (permesso mancante)</gray>"
        ));
        TexturedGuiSupport.fillGroup(inventory, BANK_SLOTS_DEPOSIT_10K, bankActionButton(
                transparentBase,
                canDeposit,
                GuiHeadTextures.DEPOSIT,
                GuiHeadTextures.LOCKED,
                "city.players.gui.bank.deposit_10k",
                "city.players.gui.bank.deposit_10k_locked",
                "<green>Deposita 10K</green>",
                "<gray>Deposita 10K (permesso mancante)</gray>"
        ));
        TexturedGuiSupport.fillGroup(inventory, BANK_SLOTS_WITHDRAW_10K, bankActionButton(
                transparentBase,
                canWithdraw,
                GuiHeadTextures.WITHDRAW,
                GuiHeadTextures.LOCKED,
                "city.players.gui.bank.withdraw_10k",
                "city.players.gui.bank.withdraw_10k_locked",
                "<gold>Preleva 10K</gold>",
                "<gray>Preleva 10K (permesso mancante)</gray>"
        ));
        TexturedGuiSupport.fillGroup(inventory, BANK_SLOTS_DEPOSIT_1K, bankActionButton(
                transparentBase,
                canDeposit,
                GuiHeadTextures.DEPOSIT,
                GuiHeadTextures.LOCKED,
                "city.players.gui.bank.deposit_1k",
                "city.players.gui.bank.deposit_1k_locked",
                "<green>Deposita 1K</green>",
                "<gray>Deposita 1K (permesso mancante)</gray>"
        ));
        TexturedGuiSupport.fillGroup(inventory, BANK_SLOTS_WITHDRAW_1K, bankActionButton(
                transparentBase,
                canWithdraw,
                GuiHeadTextures.WITHDRAW,
                GuiHeadTextures.LOCKED,
                "city.players.gui.bank.withdraw_1k",
                "city.players.gui.bank.withdraw_1k_locked",
                "<gold>Preleva 1K</gold>",
                "<gray>Preleva 1K (permesso mancante)</gray>"
        ));
        TexturedGuiSupport.fillGroup(inventory, BANK_SLOTS_VAULT, bankVaultButton(transparentBase));
        TexturedGuiSupport.fillGroup(inventory, BANK_SLOTS_BACK, bankBackButton(transparentBase));
        TexturedGuiSupport.fillGroup(inventory, BANK_SLOTS_HELP, bankHelpButton(transparentBase));

        if (openResult.texturedWrapper() != null) {
            openResult.texturedWrapper().showInventory(player);
            return;
        }
        player.openInventory(inventory);
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
        InventoryHolder holder = top.getHolder();
        boolean holderSupported = holder instanceof SessionHolder || cityClaimSettingsMenuService.isClaimSettingsHolder(holder);
        if (!holderSupported) {
            return;
        }
        UUID holderSessionId;
        if (holder instanceof SessionHolder sessionHolder) {
            holderSessionId = sessionHolder.sessionId();
        } else if (holder instanceof CityClaimSettingsMenuService.ClaimSettingsHolder claimSettingsHolder) {
            holderSessionId = claimSettingsHolder.sessionId();
        } else {
            return;
        }

        if (event.getClickedInventory() != top) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);

        CityPlayersSession session = sessionsByViewer.get(player.getUniqueId());
        if (session == null || !session.sessionId().equals(holderSessionId)) {
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        if (cityClaimSettingsMenuService.isClaimSettingsHolder(holder)) {
            handleClaimSettingsClick(player, session, slot);
            return;
        }
        if (!(holder instanceof SessionHolder sessionHolder)) {
            return;
        }
        switch (sessionHolder.view()) {
            case LIST -> handleListClick(player, session, slot);
            case DETAIL -> {
                if (sessionHolder.memberId() != null) {
                    handleDetailClick(player, session, slot, sessionHolder.memberId());
                }
            }
            case ACTIONS -> handleCityActionsClick(player, session, slot);
            case BANK -> handleBankClick(player, session, slot);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessionsByViewer.remove(event.getPlayer().getUniqueId());
        cityClaimSettingsMenuService.clearViewerSession(event.getPlayer().getUniqueId());
    }

    private void handleListClick(Player player, CityPlayersSession session, int slot) {
        List<CityPlayersMemberView> sorted = sortedMembers(session, session.members());
        int pageCount = Math.max(1, (sorted.size() + PAGE_SIZE - 1) / PAGE_SIZE);

        if (slot == SLOT_PREV) {
            if (session.page() > 0) {
                session.page(session.page() - 1);
            }
            openListView(player, session);
            return;
        }
        if (slot == SLOT_NEXT) {
            if (session.page() + 1 < pageCount) {
                session.page(session.page() + 1);
            }
            openListView(player, session);
            return;
        }
        if (slot == SLOT_ACTIONS) {
            if (session.staffView()) {
                cityRelationsGuiService.open(player, session);
            } else {
                GuiNavigationUtils.openCityHub(plugin, player);
            }
            return;
        }
        if (slot < 0 || slot >= PAGE_SIZE) {
            return;
        }

        int index = session.page() * PAGE_SIZE + slot;
        if (index >= sorted.size()) {
            return;
        }
        openMemberDialog(player, session, sorted.get(index));
    }

    private void handleDetailClick(Player player, CityPlayersSession session, int slot, UUID memberId) {
        Optional<CityPlayersMemberView> target = session.members().stream()
                .filter(view -> view.playerId().equals(memberId))
                .findFirst();
        if (target.isEmpty()) {
            player.sendMessage(configUtils.msg("city.players.gui.errors.member_not_found", "<red>Membro non trovato.</red>"));
            refreshSession(player, session);
            return;
        }
        CityPlayersMemberView memberView = target.get();
        DetailPage page = currentDetailPage(player, session.sessionId());
        if (slot == SLOT_BACK) {
            handleDetailBack(player, session);
            return;
        }
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_DETAIL_TAB_OVERVIEW) {
            openDetailView(player, session, memberView, DetailPage.OVERVIEW);
            return;
        }
        if (slot == SLOT_DETAIL_TAB_NATIVE) {
            openDetailView(player, session, memberView, DetailPage.NATIVE_PRIVILEGES);
            return;
        }
        if (slot == SLOT_DETAIL_TAB_PLUGIN) {
            openDetailView(player, session, memberView, DetailPage.PLUGIN_PERMISSIONS);
            return;
        }
        if (page == DetailPage.PLUGIN_PERMISSIONS) {
            TownMemberPermission permission = permissionForSlot(slot);
            if (permission != null) {
                handlePluginPermissionToggle(player, session, memberView, permission);
                return;
            }
        }

        if (slot == SLOT_PROMOTE && canPromote(player, session, memberView)) {
            openConfirmDialog(player, session, memberView, ActionType.PROMOTE);
            return;
        }
        if (slot == SLOT_DEMOTE && canDemote(player, session, memberView)) {
            openConfirmDialog(player, session, memberView, ActionType.DEMOTE);
            return;
        }
        if (slot == SLOT_EVICT && canEvict(player, session, memberView)) {
            openConfirmDialog(player, session, memberView, ActionType.EVICT);
            return;
        }
        if (slot == SLOT_TRANSFER && canTransfer(player, session, memberView)) {
            openConfirmDialog(player, session, memberView, ActionType.TRANSFER);
            return;
        }
    }

    private void handleCityActionsClick(Player player, CityPlayersSession session, int slot) {
        if (slot == ACTIONS_SLOT_BACK) {
            openListView(player, session);
            return;
        }
        if (slot == ACTIONS_SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == ACTIONS_SLOT_EDIT) {
            townEditDialogFlow.openRoot(player);
            return;
        }
        if (slot == ACTIONS_SLOT_DISBAND) {
            if (session.staffView()) {
                player.sendMessage(configUtils.msg("city.players.gui.actions.disband_staff_forbidden",
                        "<red>Questa azione non e disponibile in modalita staff.</red>"));
                return;
            }
            townDisbandDialogFlow.openConfirm(player);
            return;
        }
        if (slot == ACTIONS_SLOT_ITEM_VAULT) {
            cityItemVaultGuiService.open(player, session);
            return;
        }
        if (slot == ACTIONS_SLOT_RELATIONS) {
            cityRelationsGuiService.open(player, session);
            return;
        }
        if (slot == ACTIONS_SLOT_BANK) {
            openBankView(player, session);
            return;
        }
        if (slot == ACTIONS_SLOT_SECTION) {
            citySectionMapGuiService.openForOwnTown(player);
            return;
        }
        if (slot == ACTIONS_SLOT_TOWN_LIST) {
            cityTownListGuiService.open(player);
            return;
        }
        if (slot == ACTIONS_SLOT_CLAIM_SETTINGS) {
            cityClaimSettingsMenuService.open(player, session);
            return;
        }
        if (slot == ACTIONS_SLOT_LEAVE) {
            if (session.staffView()) {
                player.sendMessage(configUtils.msg("city.players.gui.actions.leave_staff_forbidden",
                        "<red>Questa azione non e disponibile in modalita staff.</red>"));
                return;
            }
            DialogConfirmHelper.open(
                    plugin,
                    player,
                    configUtils.msg("city.players.dialog.leave_confirm.title", "<red>Conferma uscita</red>"),
                    configUtils.msg("city.players.dialog.leave_confirm.body",
                            "<gray>Vuoi davvero abbandonare la citta?</gray>"),
                    configUtils.msg("city.players.dialog.leave_confirm.buttons.confirm", "<red>Abbandona</red>"),
                    configUtils.msg("city.players.dialog.leave_confirm.buttons.cancel", "<gray>Annulla</gray>"),
                    actor -> executeLeaveAction(actor, session),
                    configUtils.msg("city.players.dialog.leave_confirm.errors.unavailable",
                            "<red>Dialog non disponibile su questo server.</red>")
            );
        }
    }

    private void handleClaimSettingsClick(Player player, CityPlayersSession session, int slot) {
        cityClaimSettingsMenuService.handleClick(
                player,
                session,
                slot,
                () -> GuiNavigationUtils.openCityHub(plugin, player)
        );
    }

    private void handleBankClick(Player player, CityPlayersSession session, int slot) {
        if (containsSlot(BANK_SLOTS_BACK, slot)) {
            if (session.backTarget() == CityPlayersBackTarget.HUB) {
                GuiNavigationUtils.openCityHub(plugin, player);
            } else {
                openCityActionsView(player, session);
            }
            return;
        }
        if (containsSlot(BANK_SLOTS_HELP, slot)) {
            openBankHelpDialog(player, session);
            return;
        }
        if (containsSlot(BANK_SLOTS_VAULT, slot)) {
            cityItemVaultGuiService.openFromBank(player, session);
            return;
        }
        if (containsSlot(BANK_SLOTS_DEPOSIT_CUSTOM, slot)) {
            if (!huskTownsApiHook.hasPrivilege(player, Privilege.DEPOSIT)) {
                player.sendMessage(configUtils.msg("city.players.gui.bank.errors.no_permission_deposit",
                        "<red>Non hai i permessi per depositare nella banca citta.</red>"));
                return;
            }
            openBankAmountDialog(player, session, "deposit");
            return;
        }
        if (containsSlot(BANK_SLOTS_WITHDRAW_CUSTOM, slot)) {
            if (!huskTownsApiHook.hasPrivilege(player, Privilege.WITHDRAW)) {
                player.sendMessage(configUtils.msg("city.players.gui.bank.errors.no_permission_withdraw",
                        "<red>Non hai i permessi per prelevare dalla banca citta.</red>"));
                return;
            }
            openBankAmountDialog(player, session, "withdraw");
            return;
        }
        if (containsSlot(BANK_SLOTS_DEPOSIT_10K, slot)) {
            dispatchBankQuickAmount(player, session, "deposit", BigDecimal.valueOf(10_000L), Privilege.DEPOSIT,
                    "city.players.gui.bank.errors.no_permission_deposit");
            return;
        }
        if (containsSlot(BANK_SLOTS_DEPOSIT_1K, slot)) {
            dispatchBankQuickAmount(player, session, "deposit", BigDecimal.valueOf(1_000L), Privilege.DEPOSIT,
                    "city.players.gui.bank.errors.no_permission_deposit");
            return;
        }
        if (containsSlot(BANK_SLOTS_WITHDRAW_10K, slot)) {
            dispatchBankQuickAmount(player, session, "withdraw", BigDecimal.valueOf(10_000L), Privilege.WITHDRAW,
                    "city.players.gui.bank.errors.no_permission_withdraw");
            return;
        }
        if (containsSlot(BANK_SLOTS_WITHDRAW_1K, slot)) {
            dispatchBankQuickAmount(player, session, "withdraw", BigDecimal.valueOf(1_000L), Privilege.WITHDRAW,
                    "city.players.gui.bank.errors.no_permission_withdraw");
        }
    }

    public void openBankFromVault(Player player, CityPlayersSession session) {
        CityPlayersSession current = sessionsByViewer.get(player.getUniqueId());
        if (current != null && current.sessionId().equals(session.sessionId())) {
            openBankView(player, current);
            return;
        }
        openBankView(player, session);
    }

    private void openBankAmountDialog(Player player, CityPlayersSession session, String action) {
        if (session.staffView()) {
            player.sendMessage(configUtils.msg("city.players.gui.bank.errors.staff_forbidden",
                    "<red>Questa azione non e disponibile in modalita staff.</red>"));
            return;
        }
        if (!action.equals("deposit") && !action.equals("withdraw")) {
            return;
        }

        Component title = configUtils.msg(
                action.equals("deposit")
                        ? "city.players.dialog.bank.deposit.title"
                        : "city.players.dialog.bank.withdraw.title",
                action.equals("deposit") ? "<gold>Deposita in banca</gold>" : "<gold>Preleva dalla banca</gold>"
        );
        Component body = configUtils.msg(
                action.equals("deposit")
                        ? "city.players.dialog.bank.deposit.body"
                        : "city.players.dialog.bank.withdraw.body",
                "<gray>Inserisci l'importo.</gray>"
        );
        int width = configUtils.cfgInt("city.players.dialog.bank.amount.width", 320);
        int maxLength = Math.max(3, Integer.toString(Math.max(1, configUtils.cfgInt("city.players.dialog.bank.amount.max", 1_000_000_000))).length() + 3);

        DialogBase base = DialogBase.builder(title)
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(body)))
                .inputs(List.of(DialogInputUtils.text(
                                "amount",
                                width,
                                configUtils.msg("city.players.dialog.bank.amount.label", "<yellow>Importo</yellow>"),
                                true,
                                "",
                                maxLength)))
                .build();
        Dialog dialog = Dialog.create(factory -> {
            ActionButton confirmButton = ActionButton.create(
                    configUtils.msg("city.players.dialog.bank.buttons.confirm", "<green>CONFERMA</green>"),
                    null,
                    150,
                    DialogAction.customClick((response, targetAudience) -> {
                        if (!(targetAudience instanceof Player actor)) {
                            return;
                        }
                        CityPlayersSession current = sessionsByViewer.get(actor.getUniqueId());
                        if (current == null || !current.sessionId().equals(session.sessionId())) {
                            actor.sendMessage(configUtils.msg("city.players.gui.errors.session_expired",
                                    "<red>Sessione GUI scaduta. Riapri /city.</red>"));
                            return;
                        }
                        Optional<BigDecimal> maybeAmount = DialogInputUtils.parsePositiveAmount(response, "amount", configUtils);
                        if (maybeAmount.isEmpty()) {
                            actor.sendMessage(configUtils.msg("city.players.dialog.bank.errors.non_positive_amount",
                                    "<red>L'importo deve essere maggiore di zero.</red>"));
                            openBankAmountDialog(actor, current, action);
                            return;
                        }
                        BigDecimal amount = maybeAmount.get();
                        if ("deposit".equals(action)) {
                            huskTownsApiHook.depositTownMoney(actor, amount);
                        } else {
                            huskTownsApiHook.withdrawTownMoney(actor, amount);
                        }
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            CityPlayersSession updated = sessionsByViewer.get(actor.getUniqueId());
                            if (updated == null) {
                                return;
                            }
                            openBankView(actor, updated);
                        }, 2L);
                    }, CLICK_OPTIONS)
            );
            ActionButton cancelButton = ActionButton.create(
                    configUtils.msg("city.players.dialog.bank.buttons.cancel", "<gray>ANNULLA</gray>"),
                    null,
                    150,
                    DialogAction.customClick((response, targetAudience) -> {
                        if (targetAudience instanceof Player actor) {
                            CityPlayersSession current = sessionsByViewer.get(actor.getUniqueId());
                            if (current != null) {
                                openBankView(actor, current);
                            }
                        }
                    }, CLICK_OPTIONS)
            );
            factory.empty()
                    .base(base)
                    .type(DialogType.multiAction(List.of(confirmButton), cancelButton, 2));
        });
        DialogViewUtils.showDialog(
                plugin,
                player,
                dialog,
                configUtils.msg("city.players.dialog.bank.errors.unavailable",
                        "<red>Dialog non disponibile su questo server.</red>"),
                "players-bank"
        );
    }

    private ItemStack bankBalanceItem(Town town, ItemStack transparentBase) {
        Component title = configUtils.msg("city.players.gui.bank.balance.title", "<gold>Bilancio Citta</gold>");
        List<Component> lore = List.of(
                configUtils.msg(
                        "city.players.gui.bank.balance.value",
                        "<gray>Saldo:</gray> <white>{amount}</white>",
                        Placeholder.unparsed("amount", formatMoney(town.getMoney()))
                )
        );
        return TexturedGuiSupport.overlayButton(
                transparentBase,
                title,
                lore,
                GuiItemFactory.customHead(GuiHeadTextures.BANK, Component.empty())
        );
    }

    private ItemStack bankActionButton(
            ItemStack transparentBase,
            boolean enabled,
            String enabledTexture,
            String disabledTexture,
            String enabledPath,
            String disabledPath,
            String enabledFallback,
            String disabledFallback
    ) {
        return TexturedGuiSupport.overlayButton(
                transparentBase,
                configUtils.msg(enabled ? enabledPath : disabledPath, enabled ? enabledFallback : disabledFallback),
                List.of(),
                GuiItemFactory.customHead(enabled ? enabledTexture : disabledTexture, Component.empty())
        );
    }

    private ItemStack bankVaultButton(ItemStack transparentBase) {
        return TexturedGuiSupport.overlayButton(
                transparentBase,
                configUtils.msg("city.players.gui.bank.vault", "<gold>Apri Item Vault</gold>"),
                configUtils.lore(
                        "city.players.gui.bank.vault_lore",
                        List.of(
                                "Apri il deposito oggetti della citta",
                                "Qui trovi materiali, premi e ricompense"
                        )
                ),
                GuiItemFactory.customHead(GuiHeadTextures.BANK, Component.empty())
        );
    }

    private ItemStack bankBackButton(ItemStack transparentBase) {
        return TexturedGuiSupport.overlayButton(
                transparentBase,
                configUtils.msg("city.players.gui.bank.back", "<yellow>Indietro</yellow>"),
                List.of(),
                GuiItemFactory.customHead(GuiHeadTextures.ACTIONS, Component.empty())
        );
    }

    private ItemStack bankHelpButton(ItemStack transparentBase) {
        return TexturedGuiSupport.overlayButton(
                transparentBase,
                configUtils.msg("city.players.gui.bank.help", "<aqua>Aiuto</aqua>"),
                configUtils.lore(
                        "city.players.gui.bank.help_lore",
                        List.of(
                                "Spiega deposito, prelievo e vault",
                                "Apri una guida veloce della banca"
                        )
                ),
                GuiItemFactory.customHead(GuiHeadTextures.INFO, Component.empty())
        );
    }

    private void dispatchBankQuickAmount(
            Player player,
            CityPlayersSession session,
            String action,
            BigDecimal amount,
            Privilege privilege,
            String permissionPath
    ) {
        if (!huskTownsApiHook.hasPrivilege(player, privilege)) {
            player.sendMessage(configUtils.msg(permissionPath,
                    privilege == Privilege.DEPOSIT
                            ? "<red>Non hai i permessi per depositare nella banca citta.</red>"
                            : "<red>Non hai i permessi per prelevare dalla banca citta.</red>"));
            return;
        }
        if ("deposit".equals(action)) {
            huskTownsApiHook.depositTownMoney(player, amount);
        } else {
            huskTownsApiHook.withdrawTownMoney(player, amount);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            CityPlayersSession updated = sessionsByViewer.get(player.getUniqueId());
            if (updated != null) {
                openBankView(player, updated);
            }
        }, 2L);
    }

    private void openBankHelpDialog(Player player, CityPlayersSession session) {
        cityWikiDialogService.openPage(player, "use_bank", CityWikiBackTarget.BANK);
    }

    private BankOpenResult createBankInventory(SessionHolder holder, Component fallbackTitle) {
        TexturedGuiSupport.OpenResult result = TexturedGuiSupport.createInventory(
                plugin,
                holder,
                holder::bind,
                BANK_GUI_SIZE,
                fallbackTitle,
                configUtils.missingKeyFallback("city.players.gui.bank.itemsadder.title_plain", "Banca Citta"),
                configUtils.cfgInt("city.players.gui.bank.itemsadder.title_offset", 0),
                configUtils.cfgInt("city.players.gui.bank.itemsadder.texture_offset", -8),
                configUtils.cfgString("city.players.gui.bank.itemsadder.font_image_id", BANK_FONT_IMAGE_ID),
                "players-bank"
        );
        return new BankOpenResult(result.inventory(), result.texturedWrapper());
    }

    private static boolean containsSlot(int[] slots, int target) {
        for (int slot : slots) {
            if (slot == target) {
                return true;
            }
        }
        return false;
    }

    private static int[] bankInteractiveSlots() {
        return new int[] {
                1, 2, 3, 10, 11, 12, 19, 20, 21, 28, 29, 30,
                9, 13, 18, 22, 27, 31,
                14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35,
                37, 38, 39, 41, 42, 43
        };
    }

    private record BankOpenResult(Inventory inventory, dev.lone.itemsadder.api.FontImages.TexturedInventoryWrapper texturedWrapper) {
    }

    private void executeLeaveAction(Player player, CityPlayersSession session) {
        huskTownsApiHook.leaveTown(player);
        plugin.getLogger().info("[CittaEXP][audit][players] action=leave"
                + " actor=" + player.getUniqueId()
                + " townId=" + session.townId()
                + " result=queued"
                + " reason=husktowns-api"
                + " source=dialog-confirm");
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            boolean leftTown = huskTownsApiHook.getUserTown(player)
                    .map(member -> member.town().getId() != session.townId())
                    .orElse(true);
            if (!leftTown) {
                return;
            }
            player.closeInventory();
            sessionsByViewer.remove(player.getUniqueId());
        }, 10L);
    }

    private void executeAction(
            Player player,
            CityPlayersSession session,
            CityPlayersMemberView memberView,
            ActionType actionType,
            boolean fromDialog
    ) {
        if (session.staffView() && player.hasPermission(STAFF_PERMISSION)) {
            ActionResult result = actionService.executeStaffOverride(player, session.townId(), memberView.playerId(), actionType);
            if (!result.success()) {
                player.sendMessage(configUtils.msg("city.players.gui.actions.failed",
                        "<red>Azione fallita:</red> <gray>{reason}</gray>",
                        Placeholder.unparsed("reason", mapActionReason(result.reasonCode()))));
                return;
            }
            player.sendMessage(configUtils.msg("city.players.gui.actions.success",
                    "<green>Azione completata:</green> <yellow>{action}</yellow> <gray>su</gray> <white>{target}</white>",
                    Placeholder.unparsed("action", actionName(actionType)),
                    Placeholder.unparsed("target", memberView.displayName())));
            refreshSession(player, session);
            return;
        }

        String targetName = memberView.displayName();
        if (targetName.startsWith("Sconosciuto-")) {
            player.sendMessage(configUtils.msg("city.players.gui.errors.username_unresolved",
                    "<red>Impossibile risolvere il nome utente del target.</red>"));
            return;
        }
        switch (actionType) {
            case PROMOTE -> huskTownsApiHook.promoteMember(player, targetName);
            case DEMOTE -> huskTownsApiHook.demoteMember(player, targetName);
            case EVICT -> huskTownsApiHook.evictMember(player, targetName);
            case TRANSFER -> huskTownsApiHook.transferTownOwnership(player, targetName);
        }
        plugin.getLogger().info("[CittaEXP][audit][players] action=" + actionType.name().toLowerCase()
                + " actor=" + player.getUniqueId()
                + " townId=" + session.townId()
                + " target=" + memberView.playerId()
                + " result=queued"
                + " reason=husktowns-api"
                + " source=" + (fromDialog ? "dialog-confirm" : "gui-click"));
        refreshSession(player, session);
    }

    private void refreshSession(Player player, CityPlayersSession session) {
        Inventory top = player.getOpenInventory().getTopInventory();
        UUID detailMemberId = null;
        DetailPage detailPage = DetailPage.OVERVIEW;
        View currentView = View.LIST;
        boolean reopenInventory = false;
        if (top.getHolder() instanceof SessionHolder holder && holder.sessionId().equals(session.sessionId())) {
            currentView = holder.view();
            detailMemberId = holder.memberId();
            if (holder.detailPage() != null) {
                detailPage = holder.detailPage();
            }
            reopenInventory = true;
        }
        final View viewToRestore = currentView;
        final UUID detailMemberToRestore = detailMemberId;
        final DetailPage detailPageToRestore = detailPage;
        final boolean shouldReopenInventory = reopenInventory;
        Optional<Town> town = huskTownsApiHook.getTownById(session.townId());
        if (town.isEmpty()) {
            player.sendMessage(configUtils.msg("city.players.gui.errors.town_not_found_runtime",
                    "<red>La citta non e piu disponibile.</red>"));
            player.closeInventory();
            sessionsByViewer.remove(player.getUniqueId());
            return;
        }
        loadMembersAsync(town.get()).whenComplete((members, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (error != null) {
                player.sendMessage(configUtils.msg("city.players.gui.errors.refresh_failed",
                        "<red>Impossibile aggiornare la GUI membri.</red>"));
                plugin.getLogger().warning("[players-gui] refresh failed townId=" + session.townId()
                        + " viewer=" + player.getUniqueId()
                        + " reason=" + error.getMessage());
                return;
            }
            CityPlayersSession current = sessionsByViewer.get(player.getUniqueId());
            if (current == null || !current.sessionId().equals(session.sessionId())) {
                return;
            }
            current.updateTown(town.get().getId(), town.get().getName(), town.get().getMayor(), members);
            if (!shouldReopenInventory) {
                return;
            }
            if (viewToRestore == View.DETAIL && detailMemberToRestore != null) {
                openMemberDetailById(player, current, detailMemberToRestore, detailPageToRestore);
                return;
            }
            openListView(player, current);
        }));
    }

    private void openConfirmDialog(
            Player player,
            CityPlayersSession session,
            CityPlayersMemberView memberView,
            ActionType actionType
    ) {
        DialogConfirmHelper.open(
                plugin,
                player,
                configUtils.msg("city.players.dialog.confirm.title", "<red>Conferma azione</red>"),
                configUtils.msg("city.players.dialog.confirm.body",
                        "<gray>Confermi l'azione <yellow>{action}</yellow> su <white>{target}</white>?</gray>",
                        Placeholder.unparsed("action", actionName(actionType)),
                        Placeholder.unparsed("target", memberView.displayName())),
                configUtils.msg("city.players.dialog.confirm.buttons.confirm", "<red>CONFERMA</red>"),
                configUtils.msg("city.players.dialog.confirm.buttons.cancel", "<gray>ANNULLA</gray>"),
                actor -> {
                    CityPlayersSession current = sessionsByViewer.get(actor.getUniqueId());
                    if (current == null || !current.sessionId().equals(session.sessionId())) {
                        actor.sendMessage(configUtils.msg("city.players.gui.errors.session_expired",
                                "<red>Sessione GUI scaduta. Riapri /city.</red>"));
                        return;
                    }
                    Optional<CityPlayersMemberView> member = current.members().stream()
                            .filter(view -> view.playerId().equals(memberView.playerId()))
                            .findFirst();
                    if (member.isEmpty()) {
                        actor.sendMessage(configUtils.msg("city.players.gui.errors.member_not_found",
                                "<red>Membro non trovato.</red>"));
                        return;
                    }
                    executeAction(actor, current, member.get(), actionType, true);
                },
                configUtils.msg("city.players.dialog.confirm.errors.unavailable",
                        "<red>Dialog non disponibile su questo server.</red>")
        );
    }

    private boolean canPromote(Player player, CityPlayersSession session, CityPlayersMemberView target) {
        if (target.playerId().equals(player.getUniqueId())) {
            return false;
        }
        if (session.staffView() && player.hasPermission(STAFF_PERMISSION)) {
            return true;
        }
        return huskTownsApiHook.hasPrivilege(player, Privilege.PROMOTE);
    }

    private boolean canDemote(Player player, CityPlayersSession session, CityPlayersMemberView target) {
        if (target.playerId().equals(player.getUniqueId())) {
            return false;
        }
        if (session.staffView() && player.hasPermission(STAFF_PERMISSION)) {
            return true;
        }
        return huskTownsApiHook.hasPrivilege(player, Privilege.DEMOTE);
    }

    private boolean canEvict(Player player, CityPlayersSession session, CityPlayersMemberView target) {
        if (target.playerId().equals(player.getUniqueId())) {
            return false;
        }
        if (target.playerId().equals(session.mayorId())) {
            return false;
        }
        if (session.staffView() && player.hasPermission(STAFF_PERMISSION)) {
            return true;
        }
        return huskTownsApiHook.hasPrivilege(player, Privilege.EVICT);
    }

    private boolean canTransfer(Player player, CityPlayersSession session, CityPlayersMemberView target) {
        if (target.playerId().equals(player.getUniqueId())) {
            return false;
        }
        if (session.staffView() && player.hasPermission(STAFF_PERMISSION)) {
            return true;
        }
        return player.getUniqueId().equals(session.mayorId());
    }

    private boolean canManageMemberPermissions(Player player, CityPlayersSession session, CityPlayersMemberView target) {
        if (target.playerId().equals(session.mayorId())) {
            return false;
        }
        if (session.staffView()) {
            return false;
        }
        return player.getUniqueId().equals(session.mayorId());
    }

    private void openMemberDialog(Player player, CityPlayersSession session, CityPlayersMemberView memberView) {
        openMemberOverviewDialog(player, session, memberView);
    }

    private void openMemberDialogById(Player player, CityPlayersSession session, UUID memberId) {
        Optional<CityPlayersMemberView> target = session.members().stream()
                .filter(view -> view.playerId().equals(memberId))
                .findFirst();
        if (target.isEmpty()) {
            player.sendMessage(configUtils.msg("city.players.gui.errors.member_not_found", "<red>Membro non trovato.</red>"));
            if (session.backTarget() == CityPlayersBackTarget.HUB) {
                GuiNavigationUtils.openCityHub(plugin, player);
            } else {
                openListView(player, session);
            }
            return;
        }
        openMemberOverviewDialog(player, session, target.get());
    }

    private void openMemberOverviewDialog(Player player, CityPlayersSession session, CityPlayersMemberView memberView) {
        Map<TownMemberPermission, Boolean> pluginPermissions = permissionService.effectivePermissionsForPlayer(
                session.townId(),
                session.mayorId(),
                memberView.playerId()
        );
        long granted = pluginPermissions.values().stream().filter(Boolean::booleanValue).count();
        List<DialogBody> body = List.of(
                DialogBody.plainMessage(configUtils.msg("city.players.dialog.member.body_1",
                        "<gray>Nome:</gray> <white>{name}</white>",
                        Placeholder.unparsed("name", memberView.displayName()))),
                DialogBody.plainMessage(configUtils.msg("city.players.dialog.member.body_2",
                        "<gray>Ruolo:</gray> <white>{role}</white>",
                        Placeholder.unparsed("role", displayRoleName(session, memberView)))),
                DialogBody.plainMessage(configUtils.msg("city.players.dialog.member.body_3",
                        "<gray>Stato:</gray> <white>{status}</white>",
                        Placeholder.unparsed("status", Bukkit.getPlayer(memberView.playerId()) != null ? "Online" : "Offline"))),
                DialogBody.plainMessage(configUtils.msg("city.players.dialog.member.body_4",
                        "<gray>Ultimo accesso:</gray> <white>{last_seen}</white>",
                        Placeholder.unparsed("last_seen", formatLastSeen(memberView.playerId(), Bukkit.getPlayer(memberView.playerId()) != null)))),
                DialogBody.plainMessage(configUtils.msg("city.players.dialog.member.body_5",
                        "<gray>Permessi speciali:</gray> <white>{granted}/{total}</white>",
                        Placeholder.unparsed("granted", Long.toString(granted)),
                        Placeholder.unparsed("total", Integer.toString(TownMemberPermission.values().length))))
        );
        DialogBase base = DialogBase.builder(configUtils.msg("city.players.dialog.member.title",
                        "<gold>Scheda membro</gold> <gray>{name}</gray>",
                        Placeholder.unparsed("name", memberView.displayName())))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body)
                .build();
        Dialog dialog = Dialog.create(factory -> {
            ActionButton hierarchyButton = ActionButton.create(
                    configUtils.msg("city.players.dialog.member.buttons.hierarchy", "<yellow>Ruolo in citta</yellow>"),
                    null,
                    130,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player actor) {
                            openMemberHierarchyDialog(actor, session, memberView);
                        }
                }, CLICK_OPTIONS)
            );
            ActionButton nativeButton = ActionButton.create(
                    configUtils.msg("city.players.dialog.member.buttons.native", "<aqua>Cosa puo fare</aqua>"),
                    null,
                    130,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player actor) {
                            openMemberNativeDialog(actor, session, memberView);
                        }
                }, CLICK_OPTIONS)
            );
            ActionButton pluginButton = ActionButton.create(
                    configUtils.msg("city.players.dialog.member.buttons.plugin", "<light_purple>Permessi speciali</light_purple>"),
                    null,
                    130,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player actor) {
                            openMemberPluginDialog(actor, session, memberView);
                        }
                    }, CLICK_OPTIONS)
            );
            ActionButton closeButton = ActionButton.create(
                    configUtils.msg("city.players.dialog.member.buttons.close", "<gray>Chiudi</gray>"),
                    null,
                    120,
                    null
            );
            factory.empty().base(base).type(DialogType.multiAction(List.of(hierarchyButton, nativeButton, pluginButton), closeButton, 2));
        });
        DialogViewUtils.showDialog(
                plugin,
                player,
                dialog,
                configUtils.msg("city.players.dialog.member.errors.unavailable", "<red>Dialog non disponibile su questo server.</red>"),
                "players-member-overview"
        );
    }

    private void openMemberHierarchyDialog(Player player, CityPlayersSession session, CityPlayersMemberView memberView) {
        List<DialogBody> body = List.of(
                DialogBody.plainMessage(configUtils.msg("city.players.dialog.member.hierarchy.body_1",
                        "<gray>Ruolo attuale:</gray> <white>{role}</white>",
                        Placeholder.unparsed("role", displayRoleName(session, memberView)))),
                DialogBody.plainMessage(configUtils.msg("city.players.dialog.member.hierarchy.body_2",
                        "<gray>E il capo:</gray> <white>{value}</white>",
                        Placeholder.unparsed("value", memberView.playerId().equals(session.mayorId()) ? "Si" : "No"))),
                DialogBody.plainMessage(configUtils.msg("city.players.dialog.member.hierarchy.body_3",
                        canManageMemberPermissions(player, session, memberView)
                                ? "<gray>Da qui puoi cambiare grado o rimuovere questo membro.</gray>"
                                : "<gray>Questa pagina e in sola lettura.</gray>"))
        );
        DialogBase base = DialogBase.builder(configUtils.msg("city.players.dialog.member.hierarchy.title",
                        "<yellow>Ruolo in citta</yellow> <gray>{name}</gray>",
                        Placeholder.unparsed("name", memberView.displayName())))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body)
                .build();
        List<ActionButton> actions = new ArrayList<>();
        if (canPromote(player, session, memberView)) {
            actions.add(actionButton(configUtils.msg("city.players.gui.detail.actions.promote", "<green>Promuovi</green>"),
                    actor -> openMemberActionConfirmDialog(actor, session, memberView, ActionType.PROMOTE)));
        }
        if (canDemote(player, session, memberView)) {
            actions.add(actionButton(configUtils.msg("city.players.gui.detail.actions.demote", "<gold>Abbassa grado</gold>"),
                    actor -> openMemberActionConfirmDialog(actor, session, memberView, ActionType.DEMOTE)));
        }
        if (canEvict(player, session, memberView)) {
            actions.add(actionButton(configUtils.msg("city.players.gui.detail.actions.evict", "<red>Rimuovi</red>"),
                    actor -> openMemberActionConfirmDialog(actor, session, memberView, ActionType.EVICT)));
        }
        if (canTransfer(player, session, memberView)) {
            actions.add(actionButton(MiniMessageHelper.parse("<yellow>Trasferisci citta</yellow>"),
                    actor -> openMemberActionConfirmDialog(actor, session, memberView, ActionType.TRANSFER)));
        }
        ActionButton backButton = actionButton(
                configUtils.msg("city.players.dialog.member.buttons.back", "<yellow>Indietro</yellow>"),
                actor -> openMemberOverviewDialog(actor, session, memberView)
        );
        Dialog dialog = Dialog.create(factory -> {
            if (actions.isEmpty()) {
                factory.empty().base(base).type(DialogType.notice(backButton));
                return;
            }
            factory.empty().base(base).type(DialogType.multiAction(actions, backButton, 2));
        });
        DialogViewUtils.showDialog(
                plugin,
                player,
                dialog,
                configUtils.msg("city.players.dialog.member.errors.unavailable", "<red>Dialog non disponibile su questo server.</red>"),
                "players-member-hierarchy"
        );
    }

    private void openMemberNativeDialog(Player player, CityPlayersSession session, CityPlayersMemberView memberView) {
        List<DialogBody> body = new ArrayList<>();
        huskTownsApiHook.plugin().getRoles().fromWeight(displayRoleWeight(session, memberView)).ifPresentOrElse(role -> {
            body.add(DialogBody.plainMessage(configUtils.msg(
                    "city.players.gui.detail.native.role",
                    "<gray>Ruolo:</gray> <white>{role}</white>",
                    Placeholder.unparsed("role", role.getName())
            )));
            body.add(DialogBody.plainMessage(configUtils.msg(
                    "city.players.dialog.member.native.summary",
                    "<gray>Qui vedi cosa questo ruolo puo fare in citta.</gray>"
            )));
            java.util.Arrays.stream(Privilege.values())
                    .filter(privilege -> role.hasPrivilege(huskTownsApiHook.plugin(), privilege))
                    .map(this::privilegeLabel)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(label -> body.add(DialogBody.plainMessage(configUtils.msg(
                            "city.players.gui.detail.native.entry",
                            "<aqua>•</aqua> <white>{permission}</white>",
                            Placeholder.unparsed("permission", label)
                    ))));
        }, () -> body.add(DialogBody.plainMessage(configUtils.msg(
                "city.players.gui.detail.native.missing",
                "<red>Non riesco a leggere questo ruolo adesso.</red>"
        ))));
        DialogBase base = DialogBase.builder(configUtils.msg(
                        "city.players.dialog.member.native.title",
                        "<aqua>Cosa puo fare</aqua> <gray>{name}</gray>",
                        Placeholder.unparsed("name", memberView.displayName())))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body)
                .build();
        Dialog dialog = Dialog.create(factory -> factory.empty().base(base).type(DialogType.notice(
                actionButton(configUtils.msg("city.players.dialog.member.buttons.back", "<yellow>Indietro</yellow>"),
                        actor -> openMemberOverviewDialog(actor, session, memberView))
        )));
        DialogViewUtils.showDialog(
                plugin,
                player,
                dialog,
                configUtils.msg("city.players.dialog.member.errors.unavailable", "<red>Dialog non disponibile su questo server.</red>"),
                "players-member-native"
        );
    }

    private void openMemberPluginDialog(Player player, CityPlayersSession session, CityPlayersMemberView memberView) {
        boolean canManage = canManageMemberPermissions(player, session, memberView);
        Map<TownMemberPermission, Boolean> values = permissionService.effectivePermissionsForPlayer(
                session.townId(),
                session.mayorId(),
                memberView.playerId()
        );
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(configUtils.msg(
                "city.players.gui.detail.plugin.summary",
                "<gray>Permessi speciali attivi:</gray> <white>{granted}/{total}</white>",
                Placeholder.unparsed("granted", Long.toString(values.values().stream().filter(Boolean::booleanValue).count())),
                Placeholder.unparsed("total", Integer.toString(TownMemberPermission.values().length))
        )));
        body.add(DialogBody.plainMessage(configUtils.msg(
                canManage
                        ? "city.players.dialog.member.plugin.summary_manage"
                        : "city.players.dialog.member.plugin.summary_readonly",
                canManage
                        ? "<gray>Scegli cosa puo fare questo membro.</gray>"
                        : "<gray>Solo il capo puo cambiare questi permessi.</gray>"
        )));
        for (MemberPermissionGroup group : MemberPermissionGroup.values()) {
            body.add(DialogBody.plainMessage(MiniMessageHelper.parse(
                    "<gray>" + group.label() + ":</gray> <white>"
                            + group.permissions().stream().filter(permission -> values.getOrDefault(permission, false)).count()
                            + "/" + group.permissions().size() + "</white>"
            )));
        }
        DialogBase base = DialogBase.builder(configUtils.msg(
                        "city.players.dialog.member.plugin.title",
                        "<light_purple>Permessi speciali</light_purple> <gray>{name}</gray>",
                        Placeholder.unparsed("name", memberView.displayName())))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body)
                .build();
        List<ActionButton> buttons = new ArrayList<>();
        for (MemberPermissionGroup group : MemberPermissionGroup.values()) {
            Component label = MiniMessageHelper.parse(
                    "<white>" + group.label() + "</white> <dark_gray>·</dark_gray> <white>"
                            + group.permissions().stream().filter(permission -> values.getOrDefault(permission, false)).count()
                            + "/" + group.permissions().size() + "</white>"
            );
            if (canManage) {
                buttons.add(actionButton(label, actor -> openMemberPermissionGroupDialog(actor, session, memberView, group)));
            } else {
                buttons.add(ActionButton.create(label, null, 170, null));
            }
        }
        ActionButton backButton = actionButton(
                configUtils.msg("city.players.dialog.member.buttons.back", "<yellow>Indietro</yellow>"),
                actor -> openMemberOverviewDialog(actor, session, memberView)
        );
        Dialog dialog = Dialog.create(factory -> factory.empty().base(base).type(DialogType.multiAction(buttons, backButton, 2)));
        DialogViewUtils.showDialog(
                plugin,
                player,
                dialog,
                configUtils.msg("city.players.dialog.member.errors.unavailable", "<red>Dialog non disponibile su questo server.</red>"),
                "players-member-plugin"
        );
    }

    private void openMemberPermissionGroupDialog(
            Player player,
            CityPlayersSession session,
            CityPlayersMemberView memberView,
            MemberPermissionGroup group
    ) {
        boolean canManage = canManageMemberPermissions(player, session, memberView);
        Map<TownMemberPermission, Boolean> values = permissionService.effectivePermissionsForPlayer(
                session.townId(),
                session.mayorId(),
                memberView.playerId()
        );

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(configUtils.msg(
                "city.players.dialog.member.plugin.group_summary",
                "<gray>Sezione:</gray> <white>{group}</white>",
                Placeholder.unparsed("group", group.label())
        )));
        body.add(DialogBody.plainMessage(configUtils.msg(
                canManage
                        ? "city.players.dialog.member.plugin.summary_manage"
                        : "city.players.dialog.member.plugin.summary_readonly",
                canManage
                        ? "<gray>Scegli cosa puo fare questo membro.</gray>"
                        : "<gray>Solo il capo puo cambiare questi permessi.</gray>"
        )));
        for (TownMemberPermission permission : group.permissions()) {
            body.add(DialogBody.plainMessage(MiniMessageHelper.parse(
                    "<gray>" + permissionLabel(permission) + ":</gray> <white>"
                            + (values.getOrDefault(permission, false) ? "Attivo" : "Bloccato") + "</white>"
            )));
        }

        DialogBase base = DialogBase.builder(configUtils.msg(
                        "city.players.dialog.member.plugin.group_title",
                        "<light_purple>{group}</light_purple> <gray>{name}</gray>",
                        Placeholder.unparsed("group", group.label()),
                        Placeholder.unparsed("name", memberView.displayName())))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body)
                .build();

        List<ActionButton> buttons = new ArrayList<>();
        for (TownMemberPermission permission : group.permissions()) {
            boolean allowed = values.getOrDefault(permission, false);
            Component label = MiniMessageHelper.parse(
                    "<white>" + permissionLabel(permission) + "</white> <dark_gray>·</dark_gray> <white>"
                            + (allowed ? "Attivo" : "Bloccato") + "</white>"
            );
            if (canManage) {
                buttons.add(actionButton(label, actor -> openMemberPermissionDecisionDialog(actor, session, memberView, permission, group)));
            } else {
                buttons.add(ActionButton.create(label, null, 170, null));
            }
        }
        ActionButton backButton = actionButton(
                configUtils.msg("city.players.dialog.member.buttons.back", "<yellow>Indietro</yellow>"),
                actor -> openMemberPluginDialog(actor, session, memberView)
        );
        Dialog dialog = Dialog.create(factory -> factory.empty().base(base).type(DialogType.multiAction(buttons, backButton, 2)));
        DialogViewUtils.showDialog(
                plugin,
                player,
                dialog,
                configUtils.msg("city.players.dialog.member.errors.unavailable", "<red>Dialog non disponibile su questo server.</red>"),
                "players-member-plugin-group"
        );
    }

    private void openMemberPermissionDecisionDialog(
            Player player,
            CityPlayersSession session,
            CityPlayersMemberView memberView,
            TownMemberPermission permission,
            MemberPermissionGroup group
    ) {
        Map<TownMemberPermission, Boolean> values = permissionService.effectivePermissionsForPlayer(
                session.townId(),
                session.mayorId(),
                memberView.playerId()
        );
        boolean current = values.getOrDefault(permission, false);
        boolean canManage = canManageMemberPermissions(player, session, memberView);

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(MiniMessageHelper.parse(
                "<gray>Permesso:</gray> <white>" + permissionLabel(permission) + "</white>"
        )));
        body.add(DialogBody.plainMessage(MiniMessageHelper.parse(
                "<gray>Stato attuale:</gray> <white>" + (current ? "Attivo" : "Bloccato") + "</white>"
        )));
        body.add(DialogBody.plainMessage(configUtils.msg(
                canManage
                        ? "city.players.dialog.member.plugin.permission_hint_manage"
                        : "city.players.dialog.member.plugin.permission_hint_readonly",
                canManage
                        ? "<gray>Scegli se consentire o bloccare questo permesso.</gray>"
                        : "<gray>Solo il capo puo cambiare questo permesso.</gray>"
        )));

        DialogBase base = DialogBase.builder(configUtils.msg(
                        "city.players.dialog.member.plugin.permission_title",
                        "<light_purple>Permesso speciale</light_purple> <gray>{name}</gray>",
                        Placeholder.unparsed("name", memberView.displayName())))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body)
                .build();

        ActionButton enableButton = canManage
                ? actionButton(
                configUtils.msg(
                        "city.players.dialog.member.plugin.enable",
                        "<green>Consenti</green> <white>{permission}</white>",
                        Placeholder.unparsed("permission", permissionLabel(permission))
                ),
                actor -> setMemberPluginPermission(actor, session, memberView, permission, true, group)
        )
                : ActionButton.create(
                configUtils.msg(
                        "city.players.dialog.member.plugin.enable_disabled",
                        "<gray>Consenti</gray> <white>{permission}</white>",
                        Placeholder.unparsed("permission", permissionLabel(permission))
                ),
                null,
                170,
                null
        );

        ActionButton disableButton = canManage
                ? actionButton(
                configUtils.msg(
                        "city.players.dialog.member.plugin.disable",
                        "<red>Blocca</red> <white>{permission}</white>",
                        Placeholder.unparsed("permission", permissionLabel(permission))
                ),
                actor -> setMemberPluginPermission(actor, session, memberView, permission, false, group)
        )
                : ActionButton.create(
                configUtils.msg(
                        "city.players.dialog.member.plugin.disable_disabled",
                        "<gray>Blocca</gray> <white>{permission}</white>",
                        Placeholder.unparsed("permission", permissionLabel(permission))
                ),
                null,
                170,
                null
        );

        ActionButton backButton = actionButton(
                configUtils.msg("city.players.dialog.member.buttons.back", "<yellow>Indietro</yellow>"),
                actor -> openMemberPermissionGroupDialog(actor, session, memberView, group)
        );

        Dialog dialog = Dialog.create(factory -> factory.empty().base(base).type(DialogType.multiAction(List.of(enableButton, disableButton), backButton, 2)));
        DialogViewUtils.showDialog(
                plugin,
                player,
                dialog,
                configUtils.msg("city.players.dialog.member.errors.unavailable", "<red>Dialog non disponibile su questo server.</red>"),
                "players-member-plugin-permission"
        );
    }

    private void setMemberPluginPermission(
            Player player,
            CityPlayersSession session,
            CityPlayersMemberView memberView,
            TownMemberPermission permission,
            boolean enabled,
            MemberPermissionGroup group
    ) {
        if (!canManageMemberPermissions(player, session, memberView)) {
            player.sendMessage(configUtils.msg(
                    "city.players.gui.detail.plugin.errors.no_permission",
                    "<red>Solo il capo puo cambiare questi permessi.</red>"
            ));
            return;
        }
        boolean current = permissionService.isAllowed(session.townId(), session.mayorId(), memberView.playerId(), permission);
        permissionService.setPermission(
                session.townId(),
                player.getUniqueId(),
                session.mayorId(),
                memberView.playerId(),
                permission,
                enabled
        ).whenComplete((updated, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (error != null || !Boolean.TRUE.equals(updated)) {
                player.sendMessage(configUtils.msg(
                        "city.players.gui.detail.plugin.errors.update_failed",
                        "<red>Non riesco ad aggiornare questi permessi adesso.</red>"
                ));
                return;
            }
            reopenMemberDialog(player, session, memberView.playerId(), currentSession ->
                    openMemberPermissionGroupDialog(player, currentSession, refreshedView(currentSession, memberView.playerId()), group));
        }));
    }

    private CityPlayersMemberView refreshedView(CityPlayersSession session, UUID memberId) {
        return session.members().stream()
                .filter(view -> view.playerId().equals(memberId))
                .findFirst()
                .orElse(new CityPlayersMemberView(memberId, "Sconosciuto-" + memberId.toString().substring(0, 8), 0));
    }

    private void openMemberActionConfirmDialog(
            Player player,
            CityPlayersSession session,
            CityPlayersMemberView memberView,
            ActionType actionType
    ) {
        DialogConfirmHelper.open(
                plugin,
                player,
                configUtils.msg("city.players.dialog.confirm.title", "<red>Conferma azione</red>"),
                configUtils.msg("city.players.dialog.confirm.body",
                        "<gray>Confermi l'azione <yellow>{action}</yellow> su <white>{target}</white>?</gray>",
                        Placeholder.unparsed("action", actionName(actionType)),
                        Placeholder.unparsed("target", memberView.displayName())),
                configUtils.msg("city.players.dialog.confirm.buttons.confirm", "<red>CONFERMA</red>"),
                configUtils.msg("city.players.dialog.confirm.buttons.cancel", "<gray>ANNULLA</gray>"),
                actor -> {
                    CityPlayersSession current = sessionsByViewer.get(actor.getUniqueId());
                    if (current == null || !current.sessionId().equals(session.sessionId())) {
                        actor.sendMessage(configUtils.msg("city.players.gui.errors.session_expired",
                                "<red>Sessione GUI scaduta. Riapri /city.</red>"));
                        return;
                    }
                    Optional<CityPlayersMemberView> target = current.members().stream()
                            .filter(view -> view.playerId().equals(memberView.playerId()))
                            .findFirst();
                    if (target.isEmpty()) {
                        actor.sendMessage(configUtils.msg("city.players.gui.errors.member_not_found", "<red>Membro non trovato.</red>"));
                        return;
                    }
                    executeAction(actor, current, target.get(), actionType, true);
                    plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                            reopenMemberDialog(actor, current, memberView.playerId(), refreshed ->
                                    openMemberOverviewDialog(actor, refreshed, refreshedView(refreshed, memberView.playerId()))), 5L);
                },
                configUtils.msg("city.players.dialog.confirm.errors.unavailable",
                        "<red>Dialog non disponibile su questo server.</red>")
        );
    }

    private void reopenMemberDialog(
            Player player,
            CityPlayersSession session,
            UUID memberId,
            Consumer<CityPlayersSession> onReady
    ) {
        Optional<Town> town = huskTownsApiHook.getTownById(session.townId());
        if (town.isEmpty()) {
            player.sendMessage(configUtils.msg("city.players.gui.errors.town_not_found_runtime",
                    "<red>La citta non e piu disponibile.</red>"));
            return;
        }
        loadMembersAsync(town.get()).whenComplete((members, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (error != null) {
                player.sendMessage(configUtils.msg("city.players.gui.errors.refresh_failed",
                        "<red>Impossibile aggiornare la GUI membri.</red>"));
                return;
            }
            CityPlayersSession current = sessionsByViewer.get(player.getUniqueId());
            if (current == null || !current.sessionId().equals(session.sessionId())) {
                return;
            }
            current.updateTown(town.get().getId(), town.get().getName(), town.get().getMayor(), members);
            if (current.members().stream().noneMatch(view -> view.playerId().equals(memberId))) {
                if (current.backTarget() == CityPlayersBackTarget.HUB) {
                    GuiNavigationUtils.openCityHub(plugin, player);
                } else {
                    openListView(player, current);
                }
                return;
            }
            onReady.accept(current);
        }));
    }

    private ActionButton actionButton(Component label, Consumer<Player> consumer) {
        return ActionButton.create(
                label,
                null,
                160,
                DialogAction.customClick((response, targetAudience) -> {
                    if (targetAudience instanceof Player actor) {
                        consumer.accept(actor);
                    }
                }, CLICK_OPTIONS)
        );
    }

    private String permissionLabel(TownMemberPermission permission) {
        return switch (permission) {
            case ITEM_VAULT_OPEN -> "Aprire il vault";
            case ITEM_VAULT_DEPOSIT -> "Depositare nel vault";
            case ITEM_VAULT_WITHDRAW -> "Prelevare dal vault";
            case ITEM_VAULT_MANAGE_PERMISSIONS -> "Gestire i permessi del vault";
            case CITY_SHOP_CREATE -> "Creare negozi";
            case CITY_SHOP_CONFIGURE -> "Modificare negozi";
            case CITY_SHOP_DESTROY -> "Rimuovere negozi";
            case CITY_DEFENSE_MANAGE -> "Gestire la difesa";
            case CITY_DEFENSE_SPEND -> "Spendere per la difesa";
            case MOB_INVASION_START -> "Avviare invasioni";
            case MOB_INVASION_MANAGE -> "Gestire invasioni";
            case CITY_CHALLENGES_MANAGE -> "Gestire sfide";
            case CITY_CHALLENGES_REDEEM -> "Riscattare reward residue";
            case CITY_EVENTS_SUBMIT -> "Inviare partecipazioni eventi";
        };
    }

    private String privilegeLabel(Privilege privilege) {
        return switch (privilege) {
            case EVICT -> "Può rimuovere membri";
            case PROMOTE -> "Può promuovere membri";
            case DEMOTE -> "Può abbassare il grado dei membri";
            case SET_RULES -> "Può cambiare le regole dei territori";
            case RENAME -> "Può cambiare il nome della città";
            case SET_FARM -> "Può cambiare i territori agricoli";
            case SET_PLOT -> "Può cambiare i lotti";
            case CLAIM_PLOT -> "Può prendere un lotto libero";
            case MANAGE_PLOT_MEMBERS -> "Può gestire i membri dei lotti";
            case TRUSTED_ACCESS -> "Può costruire nei territori della città";
            case CLAIM -> "Può claimare territori";
            case UNCLAIM -> "Può togliere claim";
            case VIEW_LOGS -> "Può vedere il registro della città";
            case SET_BIO -> "Può cambiare la descrizione della città";
            case SET_GREETING -> "Può cambiare il messaggio di benvenuto";
            case SET_FAREWELL -> "Può cambiare il messaggio di uscita";
            case SET_COLOR -> "Può cambiare il colore della città";
            case INVITE -> "Può invitare membri";
            case SPAWN -> "Può usare il warp della città";
            case SET_SPAWN -> "Può impostare il warp della città";
            case SPAWN_PRIVACY -> "Può cambiare le regole del warp";
            case DEPOSIT -> "Può depositare soldi";
            case CHAT -> "Può usare la chat della città";
            case WITHDRAW -> "Può prelevare soldi";
            case LEVEL_UP -> "Può far salire di livello la città";
            case MANAGE_RELATIONS -> "Può cambiare la diplomazia";
            case DECLARE_WAR -> "Può dichiarare guerra";
        };
    }

    private enum MemberPermissionGroup {
        VAULT("Vault", List.of(
                TownMemberPermission.ITEM_VAULT_OPEN,
                TownMemberPermission.ITEM_VAULT_DEPOSIT,
                TownMemberPermission.ITEM_VAULT_WITHDRAW,
                TownMemberPermission.ITEM_VAULT_MANAGE_PERMISSIONS
        )),
        SHOPS("Negozi", List.of(
                TownMemberPermission.CITY_SHOP_CREATE,
                TownMemberPermission.CITY_SHOP_CONFIGURE,
                TownMemberPermission.CITY_SHOP_DESTROY
        )),
        DEFENSE("Difesa", List.of(
                TownMemberPermission.CITY_DEFENSE_MANAGE,
                TownMemberPermission.CITY_DEFENSE_SPEND
        )),
        INVASIONS("Invasioni", List.of(
                TownMemberPermission.MOB_INVASION_START,
                TownMemberPermission.MOB_INVASION_MANAGE
        )),
        CHALLENGES("Sfide", List.of(
                TownMemberPermission.CITY_CHALLENGES_MANAGE,
                TownMemberPermission.CITY_CHALLENGES_REDEEM,
                TownMemberPermission.CITY_EVENTS_SUBMIT
        ));

        private final String label;
        private final List<TownMemberPermission> permissions;

        MemberPermissionGroup(String label, List<TownMemberPermission> permissions) {
            this.label = label;
            this.permissions = List.copyOf(permissions);
        }

        public String label() {
            return label;
        }

        public List<TownMemberPermission> permissions() {
            return permissions;
        }
    }

    private List<CityPlayersMemberView> sortedMembers(CityPlayersSession session, List<CityPlayersMemberView> input) {
        return input.stream()
                .sorted(Comparator
                        .comparing((CityPlayersMemberView view) -> isDisplayMayor(session, view) ? 0 : 1)
                        .thenComparing((CityPlayersMemberView view) -> Bukkit.getPlayer(view.playerId()) == null ? 1 : 0)
                        .thenComparing((CityPlayersMemberView view) -> displayRoleWeight(session, view), Comparator.reverseOrder())
                        .thenComparing(CityPlayersMemberView::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private ItemStack memberHead(CityPlayersMemberView memberView, CityPlayersSession session, Player viewer) {
        String roleName = displayRoleName(session, memberView);
        boolean online = Bukkit.getPlayer(memberView.playerId()) != null;

        Component displayName = configUtils.msg("city.players.gui.member.name",
                "<yellow>{name}</yellow> <gray>({role})</gray>",
                Placeholder.unparsed("name", memberView.displayName()),
                Placeholder.unparsed("role", roleName));
        boolean editable = canPromote(viewer, session, memberView)
                || canDemote(viewer, session, memberView)
                || canEvict(viewer, session, memberView)
                || canTransfer(viewer, session, memberView)
                || canManageMemberPermissions(viewer, session, memberView);
        List<Component> lore = UiLoreStyle.renderComponents(
                UiLoreStyle.line(UiLoreStyle.LineType.STATUS, "Stato: " + (online ? "Online" : "Offline") + " • Ruolo: " + roleName),
                UiLoreStyle.line(UiLoreStyle.LineType.INFO, "Ultimo accesso: " + formatLastSeen(memberView.playerId(), online)),
                List.of(UiLoreStyle.line(
                        editable ? UiLoreStyle.LineType.ACTION : UiLoreStyle.LineType.INFO,
                        editable ? "Apri la scheda del membro per gestirlo" : "Apri la scheda del membro in sola lettura"
                ))
        );
        return GuiItemFactory.playerHead(memberView.playerId(), skullMeta -> {
            skullMeta.displayName(displayName);
            skullMeta.lore(lore);
        });
    }

    private ItemStack memberInfo(CityPlayersMemberView memberView, CityPlayersSession session, Player viewer) {
        String roleName = displayRoleName(session, memberView);
        Map<TownMemberPermission, Boolean> pluginPermissions = permissionService.effectivePermissionsForPlayer(
                session.townId(),
                session.mayorId(),
                memberView.playerId()
        );
        long granted = pluginPermissions.values().stream().filter(Boolean::booleanValue).count();
        List<UiLoreStyle.StyledLine> lines = new ArrayList<>();
        lines.add(UiLoreStyle.line(UiLoreStyle.LineType.INFO, "Peso ruolo: " + displayRoleWeight(session, memberView)));
        lines.add(UiLoreStyle.line(UiLoreStyle.LineType.SPECIAL,
                "Permessi speciali: " + granted + "/" + TownMemberPermission.values().length));
        if (session.staffView() && viewer.hasPermission(STAFF_PERMISSION)) {
            lines.add(UiLoreStyle.line(UiLoreStyle.LineType.SPECIAL, "Modalita staff target attiva"));
        }
        return GuiItemFactory.customHead(
                GuiHeadTextures.INFO,
                configUtils.msg("city.players.gui.detail.info.title", "<yellow>Dati membro</yellow>"),
                UiLoreStyle.renderComponents(
                        UiLoreStyle.line(UiLoreStyle.LineType.FOCUS, "Nome: " + memberView.displayName()),
                        UiLoreStyle.line(UiLoreStyle.LineType.STATUS, "Ruolo: " + roleName),
                        lines
                )
        );
    }

    private ItemStack memberStatus(CityPlayersMemberView memberView, Player viewer) {
        boolean online = Bukkit.getPlayer(memberView.playerId()) != null;
        return GuiItemFactory.customHead(
                online ? GuiHeadTextures.STATUS_ONLINE : GuiHeadTextures.STATUS_OFFLINE,
                configUtils.msg("city.players.gui.detail.status.title", "<yellow>Stato</yellow>"),
                List.of(configUtils.msg(
                        "city.players.gui.detail.status.value",
                        "<gray>{status}</gray>",
                        Placeholder.unparsed("status", online ? "Online" : "Offline")
                ))
        );
    }

    private ItemStack memberNativePrivileges(CityPlayersMemberView memberView, CityPlayersSession session) {
        List<UiLoreStyle.StyledLine> lines = new ArrayList<>();
        huskTownsApiHook.plugin().getRoles().fromWeight(displayRoleWeight(session, memberView)).ifPresentOrElse(role -> {
            lines.add(UiLoreStyle.line(UiLoreStyle.LineType.INFO, "Azioni base del ruolo"));
            java.util.Arrays.stream(Privilege.values())
                    .filter(privilege -> role.hasPrivilege(huskTownsApiHook.plugin(), privilege))
                    .map(this::privilegeLabel)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(label -> lines.add(UiLoreStyle.line(UiLoreStyle.LineType.STATUS, label)));
        }, () -> lines.add(UiLoreStyle.line(UiLoreStyle.LineType.WARNING, "Non riesco a leggere questo ruolo adesso.")));
        return GuiItemFactory.customHead(
                GuiHeadTextures.INFO,
                configUtils.msg("city.players.gui.detail.native.title", "<aqua>Cosa puo fare</aqua>"),
                UiLoreStyle.renderComponents(
                        UiLoreStyle.line(UiLoreStyle.LineType.FOCUS, "Ruolo: " + displayRoleName(session, memberView)),
                        UiLoreStyle.line(UiLoreStyle.LineType.INFO, "Azioni base della citta"),
                        lines
                )
        );
    }

    private boolean isDisplayMayor(CityPlayersSession session, CityPlayersMemberView memberView) {
        return session != null && memberView.playerId().equals(session.mayorId());
    }

    private int displayRoleWeight(CityPlayersSession session, CityPlayersMemberView memberView) {
        if (isDisplayMayor(session, memberView)) {
            return roleLadderService.snapshot().mayorWeight();
        }
        return memberView.roleWeight();
    }

    private String displayRoleName(CityPlayersSession session, CityPlayersMemberView memberView) {
        return roleLadderService.snapshot().roleName(displayRoleWeight(session, memberView));
    }

    private ItemStack memberPluginPermissionsInfo(CityPlayersMemberView memberView, CityPlayersSession session, Player viewer) {
        Map<TownMemberPermission, Boolean> permissions = permissionService.effectivePermissionsForPlayer(
                session.townId(),
                session.mayorId(),
                memberView.playerId()
        );
        long granted = permissions.values().stream().filter(Boolean::booleanValue).count();
        return GuiItemFactory.customHead(
                GuiHeadTextures.ACTIONS,
                configUtils.msg("city.players.gui.detail.plugin.title", "<light_purple>Permessi speciali</light_purple>"),
                UiLoreStyle.renderComponents(
                        UiLoreStyle.line(UiLoreStyle.LineType.SPECIAL,
                                "Permessi speciali: " + granted + "/" + TownMemberPermission.values().length),
                        UiLoreStyle.line(
                                canManageMemberPermissions(viewer, session, memberView)
                                        ? UiLoreStyle.LineType.ACTION
                                        : UiLoreStyle.LineType.INFO,
                                canManageMemberPermissions(viewer, session, memberView)
                                        ? "Scegli cosa puo fare questo membro"
                                        : "Solo il capo puo cambiare questi permessi"
                        ),
                        List.of()
                )
        );
    }

    private void populatePluginPermissionButtons(Inventory inventory, CityPlayersSession session, CityPlayersMemberView memberView) {
        Map<TownMemberPermission, Boolean> permissions = permissionService.effectivePermissionsForPlayer(
                session.townId(),
                session.mayorId(),
                memberView.playerId()
        );
        for (int index = 0; index < SLOT_PLUGIN_PERMISSIONS.length; index++) {
            if (index >= TownMemberPermission.values().length) {
                inventory.setItem(SLOT_PLUGIN_PERMISSIONS[index], null);
                continue;
            }
            TownMemberPermission permission = TownMemberPermission.values()[index];
            boolean allowed = permissions.getOrDefault(permission, false);
            inventory.setItem(SLOT_PLUGIN_PERMISSIONS[index], pluginPermissionItem(permission, allowed));
        }
    }

    private ItemStack pluginPermissionItem(TownMemberPermission permission, boolean allowed) {
        String label = permissionLabel(permission);
        return GuiItemFactory.customHead(
                allowed ? GuiHeadTextures.STATUS_ONLINE : GuiHeadTextures.LOCKED,
                configUtils.msg(
                        "city.players.gui.detail.plugin.permission." + permission.name().toLowerCase(),
                        (allowed ? "<green>" : "<gray>") + label + (allowed ? " • Attivo</green>" : " • Bloccato</gray>")
                ),
                UiLoreStyle.renderComponents(
                        label,
                        allowed ? "Attivo per questo membro" : "Bloccato per questo membro",
                        allowed ? "Click per bloccarlo" : "Click per consentirlo"
                )
        );
    }

    private ItemStack detailTabButton(String path, String fallback, boolean active) {
        return actionHead(
                active ? GuiHeadTextures.INFO : GuiHeadTextures.ACTIONS,
                path,
                fallback
        );
    }

    private DetailPage currentDetailPage(Player player, UUID sessionId) {
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof SessionHolder holder
                && holder.view() == View.DETAIL
                && holder.sessionId().equals(sessionId)
                && holder.detailPage() != null) {
            return holder.detailPage();
        }
        return DetailPage.OVERVIEW;
    }

    private void handleDetailBack(Player player, CityPlayersSession session) {
        if (session.backTarget() == CityPlayersBackTarget.HUB) {
            GuiNavigationUtils.openCityHub(plugin, player);
            return;
        }
        openListView(player, session);
    }

    private TownMemberPermission permissionForSlot(int slot) {
        for (int index = 0; index < SLOT_PLUGIN_PERMISSIONS.length && index < TownMemberPermission.values().length; index++) {
            if (SLOT_PLUGIN_PERMISSIONS[index] == slot) {
                return TownMemberPermission.values()[index];
            }
        }
        return null;
    }

    private void handlePluginPermissionToggle(
            Player player,
            CityPlayersSession session,
            CityPlayersMemberView memberView,
            TownMemberPermission permission
    ) {
        if (!canManageMemberPermissions(player, session, memberView)) {
            player.sendMessage(configUtils.msg(
                    "city.players.gui.detail.plugin.errors.no_permission",
                    "<red>Solo il capo puo cambiare questi permessi.</red>"
            ));
            return;
        }
        boolean current = permissionService.isAllowed(session.townId(), session.mayorId(), memberView.playerId(), permission);
        permissionService.setPermission(
                session.townId(),
                player.getUniqueId(),
                session.mayorId(),
                memberView.playerId(),
                permission,
                !current
        ).whenComplete((updated, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (error != null || !Boolean.TRUE.equals(updated)) {
                player.sendMessage(configUtils.msg(
                        "city.players.gui.detail.plugin.errors.update_failed",
                        "<red>Non riesco ad aggiornare questi permessi adesso.</red>"
                ));
                return;
            }
            openDetailView(player, session, memberView, DetailPage.PLUGIN_PERMISSIONS);
        }));
    }

    private String formatLastSeen(UUID playerId, boolean online) {
        if (online) {
            return "ora";
        }
        OptionalLong lastSeen = playerActivityService.lastSeenMillis(playerId);
        if (lastSeen.isEmpty()) {
            return "sconosciuto";
        }
        long deltaMillis = Math.max(0L, System.currentTimeMillis() - lastSeen.getAsLong());
        long minutes = Math.max(1L, Duration.ofMillis(deltaMillis).toMinutes());
        if (minutes < 60L) {
            return minutes + "m fa";
        }
        long hours = minutes / 60L;
        if (hours < 24L) {
            return hours + "h fa";
        }
        long days = hours / 24L;
        return days + "g fa";
    }

    private ItemStack actionHead(String texture, String path, String fallback, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... tags) {
        return GuiItemFactory.customHead(texture, configUtils.msg(path, fallback, tags));
    }

    private ItemStack navPrev(boolean enabled) {
        return GuiItemFactory.customHead(
                enabled ? GuiHeadTextures.NAV_LEFT : GuiHeadTextures.NAV_DISABLED,
                configUtils.msg(
                        enabled ? "city.players.gui.nav.prev" : "city.players.gui.nav.prev_disabled",
                        enabled ? "<yellow>Pagina precedente</yellow>" : "<gray>Pagina precedente</gray>"
                )
        );
    }

    private ItemStack navNext(boolean enabled) {
        return GuiItemFactory.customHead(
                enabled ? GuiHeadTextures.NAV_RIGHT : GuiHeadTextures.NAV_DISABLED_RIGHT,
                configUtils.msg(
                        enabled ? "city.players.gui.nav.next" : "city.players.gui.nav.next_disabled",
                        enabled ? "<yellow>Pagina successiva</yellow>" : "<gray>Pagina successiva</gray>"
                )
        );
    }

    private ItemStack pageIndicator(int current, int total, int size) {
        return GuiItemFactory.customHead(GuiHeadTextures.PAGE, configUtils.msg(
                "city.players.gui.nav.page",
                "<gold>Pagina {current}/{total}</gold>",
                Placeholder.unparsed("current", Integer.toString(current)),
                Placeholder.unparsed("total", Integer.toString(total))
        ), List.of(configUtils.msg(
                "city.players.gui.nav.count",
                "<gray>Membri:</gray> <white>{count}</white>",
                Placeholder.unparsed("count", Integer.toString(size))
        )));
    }

    private ItemStack backButton() {
        return GuiItemFactory.plainHead(configUtils.msg("city.players.gui.detail.back", "<yellow>Indietro</yellow>"));
    }

    private ItemStack closeButton() {
        return GuiItemFactory.customHead(GuiHeadTextures.CLOSE, configUtils.msg("city.players.gui.detail.close", "<red>Chiudi</red>"));
    }

    private String actionName(ActionType actionType) {
        return switch (actionType) {
            case PROMOTE -> "promozione";
            case DEMOTE -> "retrocessione";
            case EVICT -> "espulsione";
            case TRANSFER -> "trasferimento del vessillo";
        };
    }

    private String mapActionReason(String reasonCode) {
        return ReasonMessageMapper.text(configUtils, "city.players.gui.actions.reasons.", reasonCode);
    }

    private String formatMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private record CachedName(String name, long expiresAtMillis) {
    }

    private enum View {
        LIST,
        DETAIL,
        ACTIONS,
        BANK
    }

    private enum DetailPage {
        OVERVIEW,
        NATIVE_PRIVILEGES,
        PLUGIN_PERMISSIONS
    }

    private static final class SessionHolder implements InventoryHolder {
        private final UUID sessionId;
        private final View view;
        private final UUID memberId;
        private final DetailPage detailPage;
        private Inventory inventory;

        private SessionHolder(UUID sessionId, View view, UUID memberId, DetailPage detailPage) {
            this.sessionId = sessionId;
            this.view = view;
            this.memberId = memberId;
            this.detailPage = detailPage;
        }

        private static SessionHolder list(UUID sessionId) {
            return new SessionHolder(sessionId, View.LIST, null, null);
        }

        private static SessionHolder detail(UUID sessionId, UUID memberId, DetailPage detailPage) {
            return new SessionHolder(sessionId, View.DETAIL, memberId, detailPage);
        }

        private static SessionHolder actions(UUID sessionId) {
            return new SessionHolder(sessionId, View.ACTIONS, null, null);
        }

        private static SessionHolder bank(UUID sessionId) {
            return new SessionHolder(sessionId, View.BANK, null, null);
        }

        void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        private UUID sessionId() {
            return sessionId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
        
        private View view() {
            return view;
        }

        private UUID memberId() {
            return memberId;
        }

        private DetailPage detailPage() {
            return detailPage;
        }
    }
}
