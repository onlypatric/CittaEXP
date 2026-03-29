package it.patric.cittaexp.citylistgui;

import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.text.UiLoreStyle;
import it.patric.cittaexp.utils.ExceptionUtils;
import it.patric.cittaexp.utils.GuiHeadTextures;
import it.patric.cittaexp.utils.GuiItemFactory;
import it.patric.cittaexp.utils.PluginConfigUtils;
import it.patric.cittaexp.utils.TexturedGuiSupport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.william278.husktowns.town.Town;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class CityTownListGuiService implements Listener {

    private static final int GUI_SIZE = 54;
    private static final int PAGE_SIZE = 35;
    private static final int[] SLOT_PREV = {0, 9, 18, 27, 36};
    private static final int[] SLOT_CONTENT = {
            1, 2, 3, 4, 5, 6, 7,
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int[] SLOT_NEXT = {8, 17, 26, 35, 44};
    private static final int[] SLOT_CLOSE = {46, 47, 48};
    private static final Key CITY_TAG_KEY = Key.key("cittaexp", "tag");
    private static final DateTimeFormatter FOUNDED_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yy");
    private static final String CITY_LIST_FONT_IMAGE_ID = "cittaexp_gui:city_list_9x6";
    private static final String TRANSPARENT_BUTTON_ID = "cittaexp_gui:transparent_button";

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final CityLevelService cityLevelService;
    private final PluginConfigUtils configUtils;
    private final Map<UUID, TownListSession> sessionsByViewer = new ConcurrentHashMap<>();

    public CityTownListGuiService(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            CityLevelService cityLevelService
    ) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.cityLevelService = cityLevelService;
        this.configUtils = new PluginConfigUtils(plugin);
    }

    public void open(Player player) {
        TownListSession session = sessionsByViewer.computeIfAbsent(
                player.getUniqueId(),
                ignored -> new TownListSession(UUID.randomUUID(), player.getUniqueId())
        );
        openList(player, session);
    }

    public void clearAllSessions() {
        sessionsByViewer.clear();
    }

    private void openList(Player player, TownListSession session) {
        List<TownListEntry> entries = buildEntries(player, session.sortMode());
        int pageCount = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (session.page() >= pageCount) {
            session.page(pageCount - 1);
        }

        ListHolder holder = new ListHolder(session.sessionId());
        TownListOpenResult openResult = createTownListInventory(
                holder,
                configUtils.msg("city.list.gui.title", "<gold>Lista Citta</gold>")
        );
        Inventory inventory = openResult.inventory();
        ItemStack transparentBase = TexturedGuiSupport.transparentButtonBase(plugin, TRANSPARENT_BUTTON_ID);
        TexturedGuiSupport.fillGroup(inventory, SLOT_PREV, navPrev(session.page() > 0, transparentBase, session.page() + 1, pageCount, entries.size(), session.sortMode()));
        TexturedGuiSupport.fillGroup(inventory, SLOT_NEXT, navNext(session.page() + 1 < pageCount, transparentBase, session.page() + 1, pageCount, entries.size(), session.sortMode()));
        TexturedGuiSupport.fillGroup(inventory, SLOT_CLOSE, closeButton(transparentBase, session.sortMode()));

        int from = session.page() * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, entries.size());
        int slot = 0;
        for (int i = from; i < to; i++) {
            inventory.setItem(SLOT_CONTENT[slot], townHead(entries.get(i)));
            slot++;
        }
        if (openResult.texturedWrapper() != null) {
            openResult.texturedWrapper().showInventory(player);
            return;
        }
        player.openInventory(inventory);
    }

    private List<TownListEntry> buildEntries(Player viewer, SortMode sortMode) {
        int adminTownId = huskTownsApiHook.plugin().getAdminTown().getId();
        Integer viewerTownId = huskTownsApiHook.getUserTown(viewer).map(member -> member.town().getId()).orElse(null);

        List<TownListEntry> entries = huskTownsApiHook.getTowns().stream()
                .filter(town -> town.getId() != adminTownId)
                .map(town -> toEntry(town, viewerTownId))
                .toList();

        Comparator<TownListEntry> comparator = switch (sortMode) {
            case XP_CUSTOM -> Comparator
                    .comparing((TownListEntry entry) -> entry.levelStatus().level(), Comparator.reverseOrder())
                    .thenComparing(entry -> entry.levelStatus().xpScaled(), Comparator.reverseOrder())
                    .thenComparing(entry -> entry.town().getName(), String.CASE_INSENSITIVE_ORDER);
            case TIER -> Comparator
                    .comparing((TownListEntry entry) -> entry.town().getLevel(), Comparator.reverseOrder())
                    .thenComparing(entry -> entry.town().getName(), String.CASE_INSENSITIVE_ORDER);
            case MONEY -> Comparator
                    .comparing((TownListEntry entry) -> entry.town().getMoney(), Comparator.reverseOrder())
                    .thenComparing(entry -> entry.town().getName(), String.CASE_INSENSITIVE_ORDER);
            case FOUNDED -> Comparator
                    .comparing((TownListEntry entry) -> entry.town().getFoundedTime(), Comparator.reverseOrder())
                    .thenComparing(entry -> entry.town().getName(), String.CASE_INSENSITIVE_ORDER);
            case MEMBERS -> Comparator
                    .comparing((TownListEntry entry) -> entry.town().getMembers().size(), Comparator.reverseOrder())
                    .thenComparing(entry -> entry.town().getName(), String.CASE_INSENSITIVE_ORDER);
        };

        return entries.stream().sorted(comparator).toList();
    }

    private TownListEntry toEntry(Town town, Integer viewerTownId) {
        CityLevelService.LevelStatus status = cityLevelService.statusForTown(town.getId(), false);
        UUID mayorId = safeMayorId(town).orElse(new UUID(0L, 0L));
        String mayorName = resolvePlayerName(mayorId);
        WarpAvailability warpAvailability = warpAvailability(town, viewerTownId);
        String tag = town.getMetadataTag(CITY_TAG_KEY).map(String::trim).filter(s -> !s.isBlank()).orElse("---");
        return new TownListEntry(town, status, mayorId, mayorName, tag, warpAvailability);
    }

    private WarpAvailability warpAvailability(Town town, Integer viewerTownId) {
        if (town.getSpawn().isEmpty()) {
            return WarpAvailability.NO_SPAWN;
        }
        return WarpAvailability.AVAILABLE;
    }

    private Optional<UUID> safeMayorId(Town town) {
        try {
            return Optional.of(town.getMayor());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private String resolvePlayerName(UUID userId) {
        if (userId.getMostSignificantBits() == 0L && userId.getLeastSignificantBits() == 0L) {
            return "Sconosciuto";
        }
        Player online = Bukkit.getPlayer(userId);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(userId);
        if (offline.getName() != null && !offline.getName().isBlank()) {
            return offline.getName();
        }
        return "Sconosciuto-" + userId.toString().substring(0, 8);
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
        if (!(top.getHolder() instanceof ListHolder holder)) {
            return;
        }
        if (event.getClickedInventory() != top) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        TownListSession session = sessionsByViewer.get(player.getUniqueId());
        if (session == null || !session.sessionId().equals(holder.sessionId())) {
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        List<TownListEntry> entries = buildEntries(player, session.sortMode());
        int pageCount = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);

        if (containsSlot(SLOT_PREV, slot)) {
            if (session.page() > 0) {
                session.page(session.page() - 1);
            }
            openList(player, session);
            return;
        }
        if (containsSlot(SLOT_NEXT, slot)) {
            if (session.page() + 1 < pageCount) {
                session.page(session.page() + 1);
            }
            openList(player, session);
            return;
        }
        if (containsSlot(SLOT_CLOSE, slot)) {
            player.closeInventory();
            return;
        }
        int slotIndex = contentSlotIndex(slot);
        if (slotIndex < 0) {
            return;
        }

        int index = session.page() * PAGE_SIZE + slotIndex;
        if (index < 0 || index >= entries.size()) {
            return;
        }
        if (event.isRightClick()) {
            session.sortMode(session.sortMode().next());
            session.page(0);
            openList(player, session);
            return;
        }
        handleEntryClick(player, entries.get(index));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessionsByViewer.remove(event.getPlayer().getUniqueId());
    }

    private void handleEntryClick(Player player, TownListEntry entry) {
        switch (entry.warpAvailability()) {
            case NO_SPAWN -> {
                player.sendMessage(configUtils.msg(
                        "city.list.gui.actions.warp_denied_no_spawn",
                        "<red>Questa citta non ha ancora un warp impostato.</red>"
                ));
                return;
            }
            case AVAILABLE -> {
                try {
                    huskTownsApiHook.teleportToTownSpawn(player, entry.town().getName());
                    return;
                } catch (RuntimeException exception) {
                    player.sendMessage(configUtils.msg(
                            "city.list.gui.actions.warp_failed",
                            "<red>Teleport non riuscito:</red> <gray>{reason}</gray>",
                            Placeholder.unparsed("reason", ExceptionUtils.safeMessage(exception, "errore sconosciuto"))
                    ));
                    plugin.getLogger().warning("[city-list] warp failed viewer=" + player.getUniqueId()
                            + " townId=" + entry.town().getId()
                            + " reason=" + exception.getMessage());
                }
            }
        }
    }

    private ItemStack townHead(TownListEntry entry) {
        Component prefix = configUtils.msg(
                "city.list.gui.entry.display_prefix",
                "<bold>{name}</bold> <gray>|</gray>",
                Placeholder.unparsed("name", entry.town().getName())
        );
        TextColor tagColor = parseTownColor(entry.town().getColorRgb()).orElse(TextColor.color(170, 170, 170));
        Component tag = Component.text(entry.tag()).color(tagColor);
        Component displayName = prefix.append(Component.space()).append(tag);

        CityLevelService.LevelStatus status = entry.levelStatus();
        List<Component> lore = UiLoreStyle.renderComponents(
                UiLoreStyle.line(UiLoreStyle.LineType.FOCUS,
                        "Livello " + status.level() + " • Stage " + status.stage().displayName() + " • Base " + entry.town().getLevel()),
                UiLoreStyle.line(UiLoreStyle.LineType.STATUS,
                        "Membri " + entry.town().getMembers().size() + "/" + entry.town().getMaxMembers(huskTownsApiHook.plugin())
                                + " • Claim " + entry.town().getClaimCount() + "/" + entry.town().getMaxClaims(huskTownsApiHook.plugin())),
                List.of(
                        UiLoreStyle.line(UiLoreStyle.LineType.INFO, "Capo " + entry.mayorName()),
                        UiLoreStyle.line(UiLoreStyle.LineType.REWARD, "Bilancio " + formatMoney(entry.town().getMoney())),
                        UiLoreStyle.line(UiLoreStyle.LineType.STATUS, UiLoreStyle.normalizeLegacyContent(configUtils.missingKeyFallback(
                                "city.list.gui.entry.lore.warp_status." + entry.warpAvailability().configSuffix,
                                entry.warpAvailability().fallback
                        )))
                )
        );
        return GuiItemFactory.playerHead(entry.mayorId(), meta -> {
            meta.displayName(displayName);
            meta.lore(lore);
        });
    }

    private Optional<TextColor> parseTownColor(String color) {
        if (color == null || color.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(TextColor.fromHexString(color));
    }

    private ItemStack navPrev(boolean enabled, ItemStack transparentBase, int currentPage, int totalPages, int totalEntries, SortMode sortMode) {
        return TexturedGuiSupport.overlayButton(
                transparentBase,
                configUtils.msg(
                        enabled ? "city.list.gui.nav.prev" : "city.list.gui.nav.prev_disabled",
                        enabled ? "<yellow>Pagina precedente</yellow>" : "<gray>Pagina precedente</gray>"
                ),
                pageNavLore(currentPage, totalPages, totalEntries, sortMode),
                GuiItemFactory.customHead(enabled ? GuiHeadTextures.NAV_LEFT : GuiHeadTextures.NAV_DISABLED, Component.empty())
        );
    }

    private ItemStack navNext(boolean enabled, ItemStack transparentBase, int currentPage, int totalPages, int totalEntries, SortMode sortMode) {
        return TexturedGuiSupport.overlayButton(
                transparentBase,
                configUtils.msg(
                        enabled ? "city.list.gui.nav.next" : "city.list.gui.nav.next_disabled",
                        enabled ? "<yellow>Pagina successiva</yellow>" : "<gray>Pagina successiva</gray>"
                ),
                pageNavLore(currentPage, totalPages, totalEntries, sortMode),
                GuiItemFactory.customHead(enabled ? GuiHeadTextures.NAV_RIGHT : GuiHeadTextures.NAV_DISABLED_RIGHT, Component.empty())
        );
    }

    private ItemStack closeButton(ItemStack transparentBase, SortMode sortMode) {
        return TexturedGuiSupport.overlayButton(
                transparentBase,
                configUtils.msg("city.list.gui.nav.close", "<red>Chiudi</red>"),
                List.of(
                        configUtils.msg(
                                "city.list.gui.sort.title",
                                "<gold>Ordine del Registro</gold> <white>{sort}</white>",
                                Placeholder.unparsed("sort", sortMode.displayName(plugin))
                        )
                ),
                GuiItemFactory.customHead(GuiHeadTextures.CLOSE, Component.empty())
        );
    }

    private List<Component> pageNavLore(int currentPage, int totalPages, int totalEntries, SortMode sortMode) {
        return List.of(
                configUtils.msg(
                        "city.list.gui.nav.page",
                        "<gold>Pagina {current}/{total}</gold>",
                        Placeholder.unparsed("current", Integer.toString(currentPage)),
                        Placeholder.unparsed("total", Integer.toString(totalPages))
                ),
                configUtils.msg(
                        "city.list.gui.nav.count",
                        "<gray>Citta:</gray> <white>{count}</white>",
                        Placeholder.unparsed("count", Integer.toString(totalEntries))
                ),
                configUtils.msg(
                        "city.list.gui.nav.sort",
                        "<gray>Ordine del Registro:</gray> <white>{sort}</white>",
                        Placeholder.unparsed("sort", sortMode.displayName(plugin))
                )
        );
    }

    private TownListOpenResult createTownListInventory(ListHolder holder, Component fallbackTitle) {
        TexturedGuiSupport.OpenResult result = TexturedGuiSupport.createInventory(
                plugin,
                holder,
                holder::bind,
                GUI_SIZE,
                fallbackTitle,
                configUtils.missingKeyFallback("city.list.gui.itemsadder.title_plain", "Registro dei Vessilli"),
                configUtils.cfgInt("city.list.gui.itemsadder.title_offset", 0),
                configUtils.cfgInt("city.list.gui.itemsadder.texture_offset", -8),
                configUtils.cfgString("city.list.gui.itemsadder.font_image_id", CITY_LIST_FONT_IMAGE_ID),
                "city-list"
        );
        return new TownListOpenResult(result.inventory(), result.texturedWrapper());
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

    private String formatMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String formatXp(double xp) {
        return BigDecimal.valueOf(xp).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private enum WarpAvailability {
        AVAILABLE("available", "<green>Warp:</green> <white>Disponibile</white>"),
        NO_SPAWN("no_spawn", "<gold>Warp:</gold> <gray>Non impostato</gray>");

        private final String configSuffix;
        private final String fallback;

        WarpAvailability(String configSuffix, String fallback) {
            this.configSuffix = configSuffix;
            this.fallback = fallback;
        }
    }

    private enum SortMode {
        XP_CUSTOM("city.list.gui.sort.labels.xp_custom", "XP Custom"),
        TIER("city.list.gui.sort.labels.tier", "Tier"),
        MONEY("city.list.gui.sort.labels.money", "Money"),
        FOUNDED("city.list.gui.sort.labels.founded", "Founded"),
        MEMBERS("city.list.gui.sort.labels.members", "Members");

        private final String configPath;
        private final String fallback;

        SortMode(String configPath, String fallback) {
            this.configPath = configPath;
            this.fallback = fallback;
        }

        private SortMode next() {
            int nextOrdinal = (this.ordinal() + 1) % values().length;
            return values()[nextOrdinal];
        }

        private String displayName(Plugin plugin) {
            return plugin.getConfig().getString(configPath, fallback);
        }
    }

    private static final class TownListSession {
        private final UUID sessionId;
        private final UUID viewerId;
        private int page;
        private SortMode sortMode;

        private TownListSession(UUID sessionId, UUID viewerId) {
            this.sessionId = sessionId;
            this.viewerId = viewerId;
            this.page = 0;
            this.sortMode = SortMode.XP_CUSTOM;
        }

        UUID sessionId() {
            return sessionId;
        }

        @SuppressWarnings("unused")
        private UUID viewerId() {
            return viewerId;
        }

        private int page() {
            return page;
        }

        private void page(int page) {
            this.page = Math.max(0, page);
        }

        private SortMode sortMode() {
            return sortMode;
        }

        private void sortMode(SortMode sortMode) {
            this.sortMode = sortMode;
        }
    }

    private record TownListEntry(
            Town town,
            CityLevelService.LevelStatus levelStatus,
            UUID mayorId,
            String mayorName,
            String tag,
            WarpAvailability warpAvailability
    ) {
    }

    private abstract static class BaseHolder implements InventoryHolder {
        private final UUID sessionId;
        private Inventory inventory;

        private BaseHolder(UUID sessionId) {
            this.sessionId = sessionId;
        }

        void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        UUID sessionId() {
            return sessionId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private record TownListOpenResult(Inventory inventory, dev.lone.itemsadder.api.FontImages.TexturedInventoryWrapper texturedWrapper) {
    }

    private static final class ListHolder extends BaseHolder {
        private ListHolder(UUID sessionId) {
            super(sessionId);
        }
    }
}
