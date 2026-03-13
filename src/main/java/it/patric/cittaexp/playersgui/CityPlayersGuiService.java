package it.patric.cittaexp.playersgui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import it.patric.cittaexp.claimsettings.CityClaimSettingsMenuService;
import it.patric.cittaexp.claimsettings.ClaimRulesDialogService;
import it.patric.cittaexp.citylistgui.CityTownListGuiService;
import it.patric.cittaexp.disbandtown.TownDisbandDialogFlow;
import it.patric.cittaexp.edittown.TownEditDialogFlow;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.manageclaim.CitySectionMapGuiService;
import it.patric.cittaexp.playersgui.CityPlayersActionService.ActionResult;
import it.patric.cittaexp.playersgui.CityPlayersActionService.ActionType;
import it.patric.cittaexp.utils.CommandBridgeUtils;
import it.patric.cittaexp.utils.DialogConfirmHelper;
import it.patric.cittaexp.utils.DialogInputUtils;
import it.patric.cittaexp.utils.DialogViewUtils;
import it.patric.cittaexp.utils.GuiHeadTextures;
import it.patric.cittaexp.utils.GuiItemFactory;
import it.patric.cittaexp.utils.GuiLayoutUtils;
import it.patric.cittaexp.utils.PluginConfigUtils;
import it.patric.cittaexp.utils.ReasonMessageMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.william278.husktowns.town.Member;
import net.william278.husktowns.town.Privilege;
import net.william278.husktowns.town.Town;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
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
    private static final int ACTIONS_GUI_SIZE = 27;
    private static final int BANK_GUI_SIZE = 27;
    private static final int BANK_SLOT_DEPOSIT = 11;
    private static final int BANK_SLOT_BALANCE = 13;
    private static final int BANK_SLOT_WITHDRAW = 15;
    private static final int BANK_SLOT_BACK = 18;
    private static final int BANK_SLOT_CLOSE = 26;
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
    private final CityLevelService cityLevelService;
    private final CityClaimSettingsMenuService cityClaimSettingsMenuService;
    private final Map<UUID, CityPlayersSession> sessionsByViewer = new ConcurrentHashMap<>();
    private final Map<UUID, CachedName> displayNameCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSeenByPlayer = new ConcurrentHashMap<>();

    public CityPlayersGuiService(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            CityPlayersActionService actionService,
            HuskTownsRoleLadderService roleLadderService,
            TownEditDialogFlow townEditDialogFlow,
            TownDisbandDialogFlow townDisbandDialogFlow,
            CitySectionMapGuiService citySectionMapGuiService,
            CityTownListGuiService cityTownListGuiService,
            CityLevelService cityLevelService
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
        this.cityLevelService = cityLevelService;
        this.cityClaimSettingsMenuService = new CityClaimSettingsMenuService(
                plugin,
                huskTownsApiHook,
                cityLevelService,
                new ClaimRulesDialogService(plugin, huskTownsApiHook)
        );
        long now = System.currentTimeMillis();
        Bukkit.getOnlinePlayers().forEach(player -> lastSeenByPlayer.put(player.getUniqueId(), now));
    }

    public void openForOwnTown(Player player) {
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty()) {
            player.sendMessage(configUtils.msg("city.players.gui.errors.no_town", "<red>Devi essere membro attivo di una citta.</red>"));
            return;
        }
        openForTown(player, member.get().town(), false);
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
                    members
            );
            sessionsByViewer.put(viewer.getUniqueId(), session);
            openListView(viewer, session);
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
        List<CityPlayersMemberView> sorted = sortedMembers(session.members());
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
                    "city.players.gui.list.actions",
                    "<gold>Azioni</gold>"));
        } else {
            inventory.setItem(SLOT_ACTIONS, GuiItemFactory.fillerPane());
        }

        int slot = 0;
        for (int i = from; i < to; i++) {
            CityPlayersMemberView view = sorted.get(i);
            inventory.setItem(slot, memberHead(view, session, player));
            slot++;
        }
        player.openInventory(inventory);
    }

    private void openDetailView(Player player, CityPlayersSession session, CityPlayersMemberView memberView) {
        SessionHolder holder = SessionHolder.detail(session.sessionId(), memberView.playerId());
        Inventory inventory = Bukkit.createInventory(holder, GUI_SIZE, configUtils.msg("city.players.gui.detail.title",
                "<gold>Gestione Membro</gold> <gray>{name}</gray>",
                Placeholder.unparsed("name", memberView.displayName())));
        holder.bind(inventory);

        GuiLayoutUtils.fillAllExcept(
                inventory,
                GuiItemFactory.fillerPane(),
                SLOT_BACK, SLOT_CLOSE, SLOT_PROMOTE, SLOT_DEMOTE, SLOT_EVICT, SLOT_TRANSFER, 13, 22, 31
        );

        inventory.setItem(13, memberHead(memberView, session, player));
        inventory.setItem(22, memberInfo(memberView, session, player));
        inventory.setItem(31, memberStatus(memberView, player));
        inventory.setItem(SLOT_BACK, backButton());
        inventory.setItem(SLOT_CLOSE, closeButton());

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
                    "<yellow>Trasferisci capo</yellow>"));
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
                ACTIONS_SLOT_BANK, ACTIONS_SLOT_LEAVE, ACTIONS_SLOT_TOWN_LIST, ACTIONS_SLOT_SECTION,
                ACTIONS_SLOT_CLAIM_SETTINGS, ACTIONS_SLOT_EDIT, ACTIONS_SLOT_DISBAND, ACTIONS_SLOT_BACK, ACTIONS_SLOT_CLOSE
        );
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
        Inventory inventory = Bukkit.createInventory(holder, BANK_GUI_SIZE, configUtils.msg(
                "city.players.gui.bank.title",
                "<gold>Banca Citta</gold> <gray>{town}</gray>",
                Placeholder.unparsed("town", town.get().getName())));
        holder.bind(inventory);
        GuiLayoutUtils.fillAllExcept(
                inventory,
                GuiItemFactory.fillerPane(),
                BANK_SLOT_DEPOSIT, BANK_SLOT_BALANCE, BANK_SLOT_WITHDRAW, BANK_SLOT_BACK, BANK_SLOT_CLOSE
        );

        inventory.setItem(BANK_SLOT_BALANCE, bankBalanceItem(town.get()));
        if (huskTownsApiHook.hasPrivilege(player, Privilege.DEPOSIT)) {
            inventory.setItem(BANK_SLOT_DEPOSIT, actionHead(
                    GuiHeadTextures.DEPOSIT,
                    "city.players.gui.bank.deposit",
                    "<green>Deposita</green>"));
        } else {
            inventory.setItem(BANK_SLOT_DEPOSIT, actionHead(
                    GuiHeadTextures.LOCKED,
                    "city.players.gui.bank.deposit_locked",
                    "<gray>Deposita (permesso mancante)</gray>"));
        }
        if (huskTownsApiHook.hasPrivilege(player, Privilege.WITHDRAW)) {
            inventory.setItem(BANK_SLOT_WITHDRAW, actionHead(
                    GuiHeadTextures.WITHDRAW,
                    "city.players.gui.bank.withdraw",
                    "<gold>Preleva</gold>"));
        } else {
            inventory.setItem(BANK_SLOT_WITHDRAW, actionHead(
                    GuiHeadTextures.LOCKED,
                    "city.players.gui.bank.withdraw_locked",
                    "<gray>Preleva (permesso mancante)</gray>"));
        }
        inventory.setItem(BANK_SLOT_BACK, actionHead(
                GuiHeadTextures.ACTIONS,
                "city.players.gui.bank.back",
                "<yellow>Indietro</yellow>"));
        inventory.setItem(BANK_SLOT_CLOSE, actionHead(
                GuiHeadTextures.CLOSE,
                "city.players.gui.bank.close",
                "<red>Chiudi</red>"));
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
        lastSeenByPlayer.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        sessionsByViewer.remove(event.getPlayer().getUniqueId());
        cityClaimSettingsMenuService.clearViewerSession(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        lastSeenByPlayer.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    private void handleListClick(Player player, CityPlayersSession session, int slot) {
        List<CityPlayersMemberView> sorted = sortedMembers(session.members());
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
        if (slot == SLOT_ACTIONS && !session.staffView()) {
            openCityActionsView(player, session);
            return;
        }
        if (slot < 0 || slot >= PAGE_SIZE) {
            return;
        }

        int index = session.page() * PAGE_SIZE + slot;
        if (index >= sorted.size()) {
            return;
        }
        openDetailView(player, session, sorted.get(index));
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
        if (slot == SLOT_BACK) {
            openListView(player, session);
            return;
        }
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
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
                () -> openCityActionsView(player, session)
        );
    }

    private void handleBankClick(Player player, CityPlayersSession session, int slot) {
        if (slot == BANK_SLOT_BACK) {
            openCityActionsView(player, session);
            return;
        }
        if (slot == BANK_SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == BANK_SLOT_DEPOSIT) {
            if (!huskTownsApiHook.hasPrivilege(player, Privilege.DEPOSIT)) {
                player.sendMessage(configUtils.msg("city.players.gui.bank.errors.no_permission_deposit",
                        "<red>Non hai i permessi per depositare nella banca citta.</red>"));
                return;
            }
            openBankAmountDialog(player, session, "deposit");
            return;
        }
        if (slot == BANK_SLOT_WITHDRAW) {
            if (!huskTownsApiHook.hasPrivilege(player, Privilege.WITHDRAW)) {
                player.sendMessage(configUtils.msg("city.players.gui.bank.errors.no_permission_withdraw",
                        "<red>Non hai i permessi per prelevare dalla banca citta.</red>"));
                return;
            }
            openBankAmountDialog(player, session, "withdraw");
        }
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
                        boolean dispatched = CommandBridgeUtils.dispatchTown(plugin, actor, action, amount);
                        if (!dispatched) {
                            actor.sendMessage(configUtils.msg("city.players.dialog.bank.errors.dispatch_failed",
                                    "<red>Operazione non eseguita. Riprova tra poco.</red>"));
                            return;
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

    private ItemStack bankBalanceItem(Town town) {
        return GuiItemFactory.customHead(
                GuiHeadTextures.BANK,
                configUtils.msg("city.players.gui.bank.balance.title", "<gold>Bilancio Citta</gold>"),
                List.of(
                configUtils.msg(
                        "city.players.gui.bank.balance.value",
                        "<gray>Saldo:</gray> <white>{amount}</white>",
                        Placeholder.unparsed("amount", formatMoney(town.getMoney()))
                )
                )
        );
    }

    private void executeLeaveAction(Player player, CityPlayersSession session) {
        boolean dispatched = CommandBridgeUtils.dispatchTown(plugin, player, "leave");
        plugin.getLogger().info("[CittaEXP][audit][players] action=leave"
                + " actor=" + player.getUniqueId()
                + " townId=" + session.townId()
                + " result=" + (dispatched ? "success" : "failed")
                + " reason=" + (dispatched ? "bridge-ok" : "bridge-dispatch-failed")
                + " source=dialog-confirm");
        if (!dispatched) {
            player.sendMessage(configUtils.msg("city.players.gui.actions.leave_dispatch_failed",
                    "<red>Uscita non eseguita. Riprova tra poco.</red>"));
            return;
        }
        player.sendMessage(configUtils.msg("city.players.gui.actions.leave_success",
                "<green>Hai abbandonato la citta con successo.</green>"));
        player.closeInventory();
        sessionsByViewer.remove(player.getUniqueId());
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
        boolean dispatched = switch (actionType) {
            case PROMOTE -> CommandBridgeUtils.dispatchTown(plugin, player, "promote", targetName);
            case DEMOTE -> CommandBridgeUtils.dispatchTown(plugin, player, "demote", targetName);
            case EVICT -> CommandBridgeUtils.dispatchTown(plugin, player, "evict", targetName);
            case TRANSFER -> CommandBridgeUtils.dispatchTown(plugin, player, "transfer", targetName);
        };
        plugin.getLogger().info("[CittaEXP][audit][players] action=" + actionType.name().toLowerCase()
                + " actor=" + player.getUniqueId()
                + " townId=" + session.townId()
                + " target=" + memberView.playerId()
                + " result=" + (dispatched ? "success" : "failed")
                + " reason=" + (dispatched ? "bridge-ok" : "bridge-dispatch-failed")
                + " source=" + (fromDialog ? "dialog-confirm" : "gui-click"));
        if (!dispatched) {
            player.sendMessage(configUtils.msg("city.players.gui.actions.dispatch_failed",
                    "<red>Azione non eseguita. Riprova tra poco.</red>"));
            return;
        }
        refreshSession(player, session);
    }

    private void refreshSession(Player player, CityPlayersSession session) {
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

    private List<CityPlayersMemberView> sortedMembers(List<CityPlayersMemberView> input) {
        return input.stream()
                .sorted(Comparator
                        .comparing((CityPlayersMemberView view) -> Bukkit.getPlayer(view.playerId()) == null ? 1 : 0)
                        .thenComparing(CityPlayersMemberView::roleWeight, Comparator.reverseOrder())
                        .thenComparing(CityPlayersMemberView::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private ItemStack memberHead(CityPlayersMemberView memberView, CityPlayersSession session, Player viewer) {
        String roleName = roleLadderService.snapshot().roleName(memberView.roleWeight());
        boolean online = Bukkit.getPlayer(memberView.playerId()) != null;

        Component displayName = configUtils.msg("city.players.gui.member.name",
                "<yellow>{name}</yellow> <gray>({role})</gray>",
                Placeholder.unparsed("name", memberView.displayName()),
                Placeholder.unparsed("role", roleName));
        List<Component> lore = new ArrayList<>();
        lore.add(configUtils.msg("city.players.gui.member.lore.status",
                "<gray>Stato:</gray> <white>{status}</white>",
                Placeholder.unparsed("status", online ? "Online" : "Offline")));
        lore.add(configUtils.msg("city.players.gui.member.lore.role",
                "<gray>Ruolo:</gray> <white>{role}</white>",
                Placeholder.unparsed("role", roleName)));
        lore.add(configUtils.msg("city.players.gui.member.lore.last_seen",
                "<gray>Ultimo accesso:</gray> <white>{last_seen}</white>",
                Placeholder.unparsed("last_seen", formatLastSeen(memberView.playerId(), online))));
        if (canPromote(viewer, session, memberView)
                || canDemote(viewer, session, memberView)
                || canEvict(viewer, session, memberView)
                || canTransfer(viewer, session, memberView)) {
            lore.add(configUtils.msg("city.players.gui.member.lore.click", "<green>Clicca per gestire il membro</green>"));
        } else {
            lore.add(configUtils.msg("city.players.gui.member.lore.readonly", "<gray>Solo visualizzazione</gray>"));
        }
        return GuiItemFactory.playerHead(memberView.playerId(), skullMeta -> {
            skullMeta.displayName(displayName);
            skullMeta.lore(lore);
        });
    }

    private ItemStack memberInfo(CityPlayersMemberView memberView, CityPlayersSession session, Player viewer) {
        String roleName = roleLadderService.snapshot().roleName(memberView.roleWeight());
        List<Component> lore = new ArrayList<>();
        lore.add(configUtils.msg("city.players.gui.detail.info.name", "<gray>Nome:</gray> <white>{name}</white>",
                Placeholder.unparsed("name", memberView.displayName())));
        lore.add(configUtils.msg("city.players.gui.detail.info.role", "<gray>Ruolo:</gray> <white>{role}</white>",
                Placeholder.unparsed("role", roleName)));
        lore.add(configUtils.msg("city.players.gui.detail.info.weight", "<gray>Peso ruolo:</gray> <white>{weight}</white>",
                Placeholder.unparsed("weight", Integer.toString(memberView.roleWeight()))));
        if (session.staffView() && viewer.hasPermission(STAFF_PERMISSION)) {
            lore.add(configUtils.msg("city.players.gui.detail.info.staff_mode", "<gold>Modalita staff target attiva</gold>"));
        }
        return GuiItemFactory.customHead(
                GuiHeadTextures.INFO,
                configUtils.msg("city.players.gui.detail.info.title", "<yellow>Dati membro</yellow>"),
                lore
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

    private String formatLastSeen(UUID playerId, boolean online) {
        if (online) {
            return "ora";
        }
        Long lastSeen = lastSeenByPlayer.get(playerId);
        if (lastSeen == null) {
            return "sconosciuto";
        }
        long deltaMillis = Math.max(0L, System.currentTimeMillis() - lastSeen);
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
            case PROMOTE -> "promote";
            case DEMOTE -> "demote";
            case EVICT -> "evict";
            case TRANSFER -> "transfer";
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

    private static final class SessionHolder implements InventoryHolder {
        private final UUID sessionId;
        private final View view;
        private final UUID memberId;
        private Inventory inventory;

        private SessionHolder(UUID sessionId, View view, UUID memberId) {
            this.sessionId = sessionId;
            this.view = view;
            this.memberId = memberId;
        }

        private static SessionHolder list(UUID sessionId) {
            return new SessionHolder(sessionId, View.LIST, null);
        }

        private static SessionHolder detail(UUID sessionId, UUID memberId) {
            return new SessionHolder(sessionId, View.DETAIL, memberId);
        }

        private static SessionHolder actions(UUID sessionId) {
            return new SessionHolder(sessionId, View.ACTIONS, null);
        }

        private static SessionHolder bank(UUID sessionId) {
            return new SessionHolder(sessionId, View.BANK, null);
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
    }
}
