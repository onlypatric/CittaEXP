package it.patric.cittaexp.vault;

import it.patric.cittaexp.playersgui.CityPlayersSession;
import it.patric.cittaexp.text.UiLoreStyle;
import it.patric.cittaexp.utils.GuiHeadTextures;
import it.patric.cittaexp.utils.GuiItemFactory;
import it.patric.cittaexp.utils.GuiLayoutUtils;
import it.patric.cittaexp.utils.GuiNavigationUtils;
import it.patric.cittaexp.utils.PluginConfigUtils;
import it.patric.cittaexp.utils.TexturedGuiSupport;
import java.util.function.BiConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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

public final class CityItemVaultGuiService implements Listener {

    private static final int GUI_SIZE = 54;
    private static final int PAGE_SIZE = 35;
    private static final String VAULT_FONT_IMAGE_ID = "cittaexp_gui:vault_items_9x6";
    private static final String TRANSPARENT_BUTTON_ID = "cittaexp_gui:transparent_button";
    private static final int[] SLOT_PREV = {0, 9, 18, 27, 36};
    private static final int[] SLOT_NEXT = {8, 17, 26, 35, 44};
    private static final int[] SLOT_CONTENT = {
            1, 2, 3, 4, 5, 6, 7,
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int[] SLOT_BACK = {46, 47, 48};

    private final Plugin plugin;
    private final PluginConfigUtils configUtils;
    private final CityItemVaultService vaultService;
    private final ConcurrentMap<UUID, Session> sessionsByViewer = new ConcurrentHashMap<>();
    private BiConsumer<Player, CityPlayersSession> bankReturnOpener;

    public CityItemVaultGuiService(Plugin plugin, CityItemVaultService vaultService) {
        this.plugin = plugin;
        this.configUtils = new PluginConfigUtils(plugin);
        this.vaultService = vaultService;
    }

    public void clearAllSessions() {
        sessionsByViewer.clear();
    }

    public void setBankReturnOpener(BiConsumer<Player, CityPlayersSession> bankReturnOpener) {
        this.bankReturnOpener = bankReturnOpener;
    }

    public void open(Player player, CityPlayersSession citySession) {
        open(player, citySession, BackTarget.CITY_HUB);
    }

    public void openFromBank(Player player, CityPlayersSession citySession) {
        open(player, citySession, BackTarget.BANK);
    }

    public void openAdmin(Player player, int townId, String townName, UUID mayorId) {
        player.sendMessage(configUtils.msg("city.players.gui.vault.loading", "<gray>Caricamento Item Vault...</gray>"));
        vaultService.loadVaultState(townId).whenComplete((state, error) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (error != null || state == null) {
                        player.sendMessage(configUtils.msg("city.players.gui.vault.errors.load_failed",
                                "<red>Impossibile caricare l'Item Vault.</red>"));
                        return;
                    }
                    Session session = new Session(
                            UUID.randomUUID(),
                            player.getUniqueId(),
                            townId,
                            townName,
                            mayorId,
                            state.slots(),
                            state.acl(),
                            BackTarget.ADMIN_CLOSE,
                            null,
                            true
                    );
                    sessionsByViewer.put(player.getUniqueId(), session);
                    openPage(player, session);
                }));
    }

    private void open(Player player, CityPlayersSession citySession, BackTarget backTarget) {
        if (citySession.staffView()) {
            player.sendMessage(configUtils.msg("city.players.gui.vault.errors.staff_forbidden",
                    "<red>Questa azione non e disponibile in modalita staff.</red>"));
            return;
        }
        if (!vaultService.canOpen(citySession.townId(), citySession.mayorId(), player.getUniqueId())) {
            player.sendMessage(configUtils.msg("city.players.gui.vault.errors.no_open_permission",
                    "<red>Il sigillo del vault non ti e stato concesso.</red>"));
            return;
        }
        player.sendMessage(configUtils.msg("city.players.gui.vault.loading", "<gray>Caricamento Item Vault...</gray>"));
        vaultService.loadVaultState(citySession.townId()).whenComplete((state, error) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (error != null || state == null) {
                        player.sendMessage(configUtils.msg("city.players.gui.vault.errors.load_failed",
                                "<red>Impossibile caricare l'Item Vault.</red>"));
                        return;
                    }
                    Session session = new Session(
                            UUID.randomUUID(),
                            player.getUniqueId(),
                            citySession.townId(),
                            citySession.townName(),
                            citySession.mayorId(),
                            state.slots(),
                            state.acl(),
                            backTarget,
                            citySession,
                            false
                    );
                    sessionsByViewer.put(player.getUniqueId(), session);
                    openPage(player, session);
                }));
    }

    private void openPage(Player player, Session session) {
        int pageCount = Math.max(1, (session.slots.length + PAGE_SIZE - 1) / PAGE_SIZE);
        if (session.page >= pageCount) {
            session.page = pageCount - 1;
        }
        Holder holder = new Holder(session.sessionId);
        VaultOpenResult openResult = createVaultInventory(
                holder,
                configUtils.msg(
                        "city.players.gui.vault.title",
                        "<gold>Item Vault</gold> <gray>{town}</gray>",
                        Placeholder.unparsed("town", session.townName)
                )
        );
        Inventory inventory = openResult.inventory();
        GuiLayoutUtils.fillAllExcept(inventory, GuiItemFactory.fillerPane(), vaultInteractiveSlots());

        ItemStack transparentBase = TexturedGuiSupport.transparentButtonBase(plugin, TRANSPARENT_BUTTON_ID);
        TexturedGuiSupport.fillGroup(inventory, SLOT_PREV, navPrev(session.page > 0, transparentBase));
        TexturedGuiSupport.fillGroup(inventory, SLOT_NEXT, navNext(session.page + 1 < pageCount, transparentBase));
        TexturedGuiSupport.fillGroup(inventory, SLOT_BACK, TexturedGuiSupport.overlayButton(
                transparentBase,
                configUtils.msg("city.players.gui.vault.nav.back", "<yellow>Indietro</yellow>"),
                List.of(),
                GuiItemFactory.customHead(GuiHeadTextures.BACK, Component.empty())
        ));

        int from = session.page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, session.slots.length);
        int slotIndex = 0;
        for (int i = from; i < to; i++) {
            ItemStack item = session.slots[i];
            int slot = SLOT_CONTENT[slotIndex++];
            if (item == null || item.getType().isAir()) {
                inventory.setItem(slot, null);
            } else {
                inventory.setItem(slot, item.clone());
            }
        }
        while (slotIndex < SLOT_CONTENT.length) {
            inventory.setItem(SLOT_CONTENT[slotIndex++], null);
        }

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
        Session session = sessionsByViewer.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder holder) || !holder.sessionId().equals(session.sessionId)) {
            return;
        }
        if (event.getRawSlot() < 0) {
            return;
        }

        if (event.getClickedInventory() == null) {
            event.setCancelled(true);
            return;
        }

        if (event.getClickedInventory() == top) {
            event.setCancelled(true);
            handleTopClick(player, session, event.getRawSlot());
            return;
        }

        event.setCancelled(true);
        handleBottomClick(player, session, event);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessionsByViewer.remove(event.getPlayer().getUniqueId());
    }

    private void handleTopClick(Player player, Session session, int slot) {
        int pageCount = Math.max(1, (session.slots.length + PAGE_SIZE - 1) / PAGE_SIZE);
        if (containsSlot(SLOT_PREV, slot)) {
            if (session.page > 0) {
                session.page -= 1;
            }
            openPage(player, session);
            return;
        }
        if (containsSlot(SLOT_NEXT, slot)) {
            if (session.page + 1 < pageCount) {
                session.page += 1;
            }
            openPage(player, session);
            return;
        }
        if (containsSlot(SLOT_BACK, slot)) {
            handleBack(player, session);
            return;
        }
        int contentIndex = contentSlotIndex(slot);
        if (contentIndex < 0) {
            return;
        }
        if (!canWithdraw(session)) {
            player.sendMessage(configUtils.msg("city.players.gui.vault.errors.no_withdraw",
                    "<red>Non hai il permesso di prelevare dall'Item Vault.</red>"));
            return;
        }
        int absoluteSlot = session.page * PAGE_SIZE + contentIndex;
        ItemStack stack = absoluteSlot >= 0 && absoluteSlot < session.slots.length ? session.slots[absoluteSlot] : null;
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        if (!hasInventoryRoom(player, stack)) {
            player.sendMessage(configUtils.msg("city.players.gui.vault.errors.inventory_full",
                    "<red>Inventario pieno.</red>"));
            return;
        }
        vaultService.withdrawFromSlot(session.townId, player.getUniqueId(), absoluteSlot)
                .whenComplete((result, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (error != null || result == null || !result.success() || result.transferred() == null) {
                        player.sendMessage(configUtils.msg("city.players.gui.vault.errors.withdraw_failed",
                                "<red>Prelievo non riuscito.</red>"));
                        return;
                    }
                    ItemStack transferred = result.transferred();
                    if (!hasInventoryRoom(player, transferred)) {
                        vaultService.rollbackWithdraw(session.townId, player.getUniqueId(), absoluteSlot, transferred)
                                .whenComplete((rolledBack, rollbackError) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    player.sendMessage(configUtils.msg("city.players.gui.vault.errors.inventory_full",
                                            "<red>Non hai spazio sufficiente nel tuo inventario. Nessun item e stato prelevato.</red>"));
                                    refreshSessionAndOpen(player, session);
                                }));
                        return;
                    }
                    java.util.Map<Integer, ItemStack> leftovers = player.getInventory().addItem(transferred);
                    if (!leftovers.isEmpty()) {
                        removeTransferredItems(player, transferred, leftovers);
                        vaultService.rollbackWithdraw(session.townId, player.getUniqueId(), absoluteSlot, transferred)
                                .whenComplete((rolledBack, rollbackError) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    player.sendMessage(configUtils.msg("city.players.gui.vault.errors.inventory_full",
                                            "<red>Non hai spazio sufficiente nel tuo inventario. Nessun item e stato prelevato.</red>"));
                                    refreshSessionAndOpen(player, session);
                                }));
                        return;
                    }
                    session.slots[absoluteSlot] = null;
                    player.sendMessage(configUtils.msg("city.players.gui.vault.actions.withdraw_success",
                            "<green>Item prelevato dal vault.</green>"));
                    openPage(player, session);
                }));
    }

    private void handleBottomClick(Player player, Session session, InventoryClickEvent event) {
        if (!canDeposit(session)) {
            player.sendMessage(configUtils.msg("city.players.gui.vault.errors.no_deposit",
                    "<red>Non hai il permesso di depositare nell'Item Vault.</red>"));
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || clicked.getAmount() <= 0) {
            return;
        }
        int originalAmount = clicked.getAmount();
        vaultService.depositFromPlayerInventory(session.townId, player.getUniqueId(), clicked.clone())
                .whenComplete((result, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (error != null || result == null || !result.success() || result.transferred() == null) {
                        Component message = result != null && "vault-full".equalsIgnoreCase(result.reason())
                                ? configUtils.msg("city.players.gui.vault.errors.vault_full",
                                "<red>Il vault e pieno. Nessun item e stato depositato.</red>")
                                : configUtils.msg("city.players.gui.vault.errors.deposit_failed",
                                "<red>Deposito non riuscito.</red>");
                        player.sendMessage(message);
                        return;
                    }
                    ItemStack live = event.getCurrentItem();
                    if (live != null && !live.getType().isAir()) {
                        int remaining = Math.max(0, originalAmount - result.transferred().getAmount());
                        if (remaining <= 0) {
                            event.getClickedInventory().setItem(event.getSlot(), null);
                        } else {
                            live.setAmount(remaining);
                            event.getClickedInventory().setItem(event.getSlot(), live);
                        }
                    }
                    refreshSessionAndOpen(player, session, configUtils.msg("city.players.gui.vault.actions.deposit_success",
                            "<green>Item depositato nel vault.</green>"));
                }));
    }

    private boolean canDeposit(Session session) {
        return session.adminView || vaultService.canDeposit(session.townId, session.mayorId, session.viewerId);
    }

    private boolean canWithdraw(Session session) {
        return session.adminView || vaultService.canWithdraw(session.townId, session.mayorId, session.viewerId);
    }

    private static boolean hasInventoryRoom(Player player, ItemStack itemStack) {
        ItemStack[] contents = player.getInventory().getContents();
        int remaining = itemStack.getAmount();
        int stackMax = itemStack.getMaxStackSize();
        for (ItemStack content : contents) {
            if (content == null || content.getType().isAir()) {
                return true;
            }
            if (!content.isSimilar(itemStack)) {
                continue;
            }
            int space = Math.max(0, stackMax - content.getAmount());
            remaining -= space;
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private static void removeTransferredItems(
            Player player,
            ItemStack transferred,
            java.util.Map<Integer, ItemStack> leftovers
    ) {
        if (player == null || transferred == null || transferred.getType().isAir()) {
            return;
        }
        int leftoverAmount = leftovers == null
                ? 0
                : leftovers.values().stream()
                .filter(item -> item != null && !item.getType().isAir())
                .mapToInt(ItemStack::getAmount)
                .sum();
        int insertedAmount = Math.max(0, transferred.getAmount() - leftoverAmount);
        if (insertedAmount <= 0) {
            return;
        }
        ItemStack toRemove = transferred.clone();
        toRemove.setAmount(insertedAmount);
        player.getInventory().removeItemAnySlot(toRemove);
    }

    private void refreshSessionAndOpen(Player player, Session session) {
        refreshSessionAndOpen(player, session, null);
    }

    private void refreshSessionAndOpen(Player player, Session session, Component message) {
        vaultService.loadVaultState(session.townId).whenComplete((state, refreshError) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (state != null) {
                        session.slots = state.slots();
                        session.acl = state.acl();
                    }
                    if (message != null) {
                        player.sendMessage(message);
                    }
                    openPage(player, session);
                }));
    }

    private void handleBack(Player player, Session session) {
        if (session.backTarget == BackTarget.ADMIN_CLOSE) {
            player.closeInventory();
            return;
        }
        if (session.backTarget == BackTarget.BANK && bankReturnOpener != null && session.playersSession != null) {
            bankReturnOpener.accept(player, session.playersSession);
            return;
        }
        GuiNavigationUtils.openCityHub(plugin, player);
    }

    private ItemStack navPrev(boolean enabled, ItemStack transparentBase) {
        return TexturedGuiSupport.overlayButton(
                transparentBase,
                configUtils.msg(
                        enabled ? "city.players.gui.vault.nav.prev" : "city.players.gui.vault.nav.prev_disabled",
                        enabled ? "<yellow>Pagina precedente</yellow>" : "<gray>Pagina precedente</gray>"
                ),
                pageNavLore(),
                GuiItemFactory.customHead(enabled ? GuiHeadTextures.NAV_LEFT : GuiHeadTextures.NAV_DISABLED, Component.empty())
        );
    }

    private ItemStack navNext(boolean enabled, ItemStack transparentBase) {
        return TexturedGuiSupport.overlayButton(
                transparentBase,
                configUtils.msg(
                        enabled ? "city.players.gui.vault.nav.next" : "city.players.gui.vault.nav.next_disabled",
                        enabled ? "<yellow>Pagina successiva</yellow>" : "<gray>Pagina successiva</gray>"
                ),
                pageNavLore(),
                GuiItemFactory.customHead(enabled ? GuiHeadTextures.NAV_RIGHT : GuiHeadTextures.NAV_DISABLED_RIGHT, Component.empty())
        );
    }

    private List<Component> pageNavLore() {
        return UiLoreStyle.renderComponents(
                UiLoreStyle.line(UiLoreStyle.LineType.ACTION, "Scorri il vault con i tasti laterali"),
                UiLoreStyle.line(UiLoreStyle.LineType.INFO, "Muoviti tra le pagine del deposito"),
                List.of()
        );
    }

    private VaultOpenResult createVaultInventory(Holder holder, Component fallbackTitle) {
        TexturedGuiSupport.OpenResult result = TexturedGuiSupport.createInventory(
                plugin,
                holder,
                holder::bind,
                GUI_SIZE,
                fallbackTitle,
                configUtils.missingKeyFallback("city.players.gui.vault.itemsadder.title_plain", "Item Vault"),
                configUtils.cfgInt("city.players.gui.vault.itemsadder.title_offset", 0),
                configUtils.cfgInt("city.players.gui.vault.itemsadder.texture_offset", -8),
                configUtils.cfgString("city.players.gui.vault.itemsadder.font_image_id", VAULT_FONT_IMAGE_ID),
                "city-vault"
        );
        return new VaultOpenResult(result.inventory(), result.texturedWrapper());
    }

    private static boolean containsSlot(int[] slots, int target) {
        for (int slot : slots) {
            if (slot == target) {
                return true;
            }
        }
        return false;
    }

    private static int contentSlotIndex(int slot) {
        for (int i = 0; i < SLOT_CONTENT.length; i++) {
            if (SLOT_CONTENT[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private static int[] vaultInteractiveSlots() {
        int[] merged = new int[SLOT_PREV.length + SLOT_NEXT.length + SLOT_BACK.length + SLOT_CONTENT.length];
        int index = 0;
        for (int slot : SLOT_PREV) {
            merged[index++] = slot;
        }
        for (int slot : SLOT_NEXT) {
            merged[index++] = slot;
        }
        for (int slot : SLOT_BACK) {
            merged[index++] = slot;
        }
        for (int slot : SLOT_CONTENT) {
            merged[index++] = slot;
        }
        return merged;
    }

    private static final class Session {
        private final UUID sessionId;
        private final UUID viewerId;
        private final int townId;
        private final String townName;
        private final UUID mayorId;
        private final BackTarget backTarget;
        private final CityPlayersSession playersSession;
        private final boolean adminView;
        private ItemStack[] slots;
        private Map<UUID, VaultAclEntry> acl;
        private int page;

        private Session(
                UUID sessionId,
                UUID viewerId,
                int townId,
                String townName,
                UUID mayorId,
                ItemStack[] slots,
                Map<UUID, VaultAclEntry> acl,
                BackTarget backTarget,
                CityPlayersSession playersSession,
                boolean adminView
        ) {
            this.sessionId = sessionId;
            this.viewerId = viewerId;
            this.townId = townId;
            this.townName = townName;
            this.mayorId = mayorId;
            this.backTarget = backTarget;
            this.playersSession = playersSession;
            this.adminView = adminView;
            this.slots = slots == null ? new ItemStack[0] : slots;
            this.acl = acl == null ? Map.of() : acl;
            this.page = 0;
        }
    }

    private enum BackTarget {
        CITY_HUB,
        BANK,
        ADMIN_CLOSE
    }

    private static final class Holder implements InventoryHolder {
        private final UUID sessionId;
        private Inventory inventory;

        private Holder(UUID sessionId) {
            this.sessionId = sessionId;
        }

        private UUID sessionId() {
            return sessionId;
        }

        private void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private record VaultOpenResult(Inventory inventory, dev.lone.itemsadder.api.FontImages.TexturedInventoryWrapper texturedWrapper) {
    }
}
