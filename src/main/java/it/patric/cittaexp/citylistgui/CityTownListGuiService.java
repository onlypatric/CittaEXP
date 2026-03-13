package it.patric.cittaexp.citylistgui;

import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.utils.ExceptionUtils;
import it.patric.cittaexp.utils.GuiHeadTextures;
import it.patric.cittaexp.utils.GuiItemFactory;
import it.patric.cittaexp.utils.GuiLayoutUtils;
import it.patric.cittaexp.utils.PluginConfigUtils;
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
    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_SORT = 47;
    private static final int SLOT_PAGE = 49;
    private static final int SLOT_NEXT = 53;
    private static final int SLOT_CLOSE = 51;
    private static final Key CITY_TAG_KEY = Key.key("cittaexp", "tag");
    private static final DateTimeFormatter FOUNDED_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yy");

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
        Inventory inventory = Bukkit.createInventory(
                holder,
                GUI_SIZE,
                configUtils.msg("city.list.gui.title", "<gold>Lista Citta</gold>")
        );
        holder.bind(inventory);

        GuiLayoutUtils.fillAllExcept(
                inventory,
                GuiItemFactory.fillerPane(),
                SLOT_PREV, SLOT_SORT, SLOT_PAGE, SLOT_NEXT, SLOT_CLOSE
        );

        inventory.setItem(SLOT_PREV, navPrev(session.page() > 0));
        inventory.setItem(SLOT_NEXT, navNext(session.page() + 1 < pageCount));
        inventory.setItem(SLOT_SORT, sortItem(session.sortMode()));
        inventory.setItem(SLOT_PAGE, pageIndicator(session.page() + 1, pageCount, entries.size(), session.sortMode()));
        inventory.setItem(SLOT_CLOSE, closeButton());

        int from = session.page() * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, entries.size());
        int slot = 0;
        for (int i = from; i < to; i++) {
            inventory.setItem(slot, townHead(entries.get(i)));
            slot++;
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
        boolean isOwnTown = viewerTownId != null && viewerTownId == town.getId();
        if (!isOwnTown && !town.getSpawn().get().isPublic()) {
            return WarpAvailability.PRIVATE_SPAWN;
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

        if (slot == SLOT_PREV) {
            if (session.page() > 0) {
                session.page(session.page() - 1);
            }
            openList(player, session);
            return;
        }
        if (slot == SLOT_NEXT) {
            if (session.page() + 1 < pageCount) {
                session.page(session.page() + 1);
            }
            openList(player, session);
            return;
        }
        if (slot == SLOT_SORT) {
            session.sortMode(session.sortMode().next());
            session.page(0);
            openList(player, session);
            return;
        }
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot < 0 || slot >= PAGE_SIZE) {
            return;
        }

        int index = session.page() * PAGE_SIZE + slot;
        if (index < 0 || index >= entries.size()) {
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
            case PRIVATE_SPAWN -> {
                player.sendMessage(configUtils.msg(
                        "city.list.gui.actions.warp_denied_private",
                        "<red>Il warp di questa citta e privato.</red>"
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
        List<Component> lore = new ArrayList<>();
        lore.add(configUtils.msg(
                "city.list.gui.entry.lore.custom_level",
                "<gray>Livello XP:</gray> <white>{level}</white> <gray>(XP {xp})</gray> <gray>| Stage:</gray> <yellow>{stage}</yellow>",
                Placeholder.unparsed("level", Integer.toString(status.level())),
                Placeholder.unparsed("xp", formatXp(status.xp())),
                Placeholder.unparsed("stage", status.stage().displayName())
        ));
        lore.add(configUtils.msg(
                "city.list.gui.entry.lore.tier",
                "<gray>Tier HuskTowns:</gray> <white>{tier}</white>",
                Placeholder.unparsed("tier", Integer.toString(entry.town().getLevel()))
        ));
        lore.add(configUtils.msg(
                "city.list.gui.entry.lore.members",
                "<gray>Membri:</gray> <white>{current}/{max}</white> <gray>| Capo:</gray> <white>{leader}</white>",
                Placeholder.unparsed("current", Integer.toString(entry.town().getMembers().size())),
                Placeholder.unparsed("max", Integer.toString(entry.town().getMaxMembers(huskTownsApiHook.plugin()))),
                Placeholder.unparsed("leader", entry.mayorName())
        ));
        lore.add(configUtils.msg(
                "city.list.gui.entry.lore.claims",
                "<gray>Claim:</gray> <white>{current}/{max}</white>",
                Placeholder.unparsed("current", Integer.toString(entry.town().getClaimCount())),
                Placeholder.unparsed("max", Integer.toString(entry.town().getMaxClaims(huskTownsApiHook.plugin())))
        ));
        lore.add(configUtils.msg(
                "city.list.gui.entry.lore.money",
                "<gray>Bilancio:</gray> <white>{money}</white>",
                Placeholder.unparsed("money", formatMoney(entry.town().getMoney()))
        ));
        lore.add(configUtils.msg(
                "city.list.gui.entry.lore.founded",
                "<gray>Fondata:</gray> <white>{date}</white>",
                Placeholder.unparsed("date", entry.town().getFoundedTime().format(FOUNDED_FORMAT))
        ));
        lore.add(configUtils.msg(
                "city.list.gui.entry.lore.warp_status." + entry.warpAvailability().configSuffix,
                entry.warpAvailability().fallback
        ));
        lore.add(configUtils.msg(
                "city.list.gui.entry.lore.click",
                "<green>Clicca per teletrasportarti al warp della citta.</green>"
        ));
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

    private ItemStack navPrev(boolean enabled) {
        return GuiItemFactory.customHead(
                enabled ? GuiHeadTextures.NAV_LEFT : GuiHeadTextures.NAV_DISABLED,
                configUtils.msg(
                        enabled ? "city.list.gui.nav.prev" : "city.list.gui.nav.prev_disabled",
                        enabled ? "<yellow>Pagina precedente</yellow>" : "<gray>Pagina precedente</gray>"
                )
        );
    }

    private ItemStack navNext(boolean enabled) {
        return GuiItemFactory.customHead(
                enabled ? GuiHeadTextures.NAV_RIGHT : GuiHeadTextures.NAV_DISABLED_RIGHT,
                configUtils.msg(
                        enabled ? "city.list.gui.nav.next" : "city.list.gui.nav.next_disabled",
                        enabled ? "<yellow>Pagina successiva</yellow>" : "<gray>Pagina successiva</gray>"
                )
        );
    }

    private ItemStack sortItem(SortMode sortMode) {
        return GuiItemFactory.item(Material.HOPPER, configUtils.msg(
                "city.list.gui.sort.title",
                "<gold>Ordina per:</gold> <white>{sort}</white>",
                Placeholder.unparsed("sort", sortMode.displayName(plugin))
        ), List.of(configUtils.msg(
                "city.list.gui.sort.hint",
                "<gray>Clicca per cambiare ordinamento.</gray>"
        )));
    }

    private ItemStack pageIndicator(int current, int total, int count, SortMode sortMode) {
        return GuiItemFactory.customHead(GuiHeadTextures.PAGE, configUtils.msg(
                "city.list.gui.nav.page",
                "<gold>Pagina {current}/{total}</gold>",
                Placeholder.unparsed("current", Integer.toString(current)),
                Placeholder.unparsed("total", Integer.toString(total))
        ), List.of(
                configUtils.msg(
                        "city.list.gui.nav.count",
                        "<gray>Citta:</gray> <white>{count}</white>",
                        Placeholder.unparsed("count", Integer.toString(count))
                ),
                configUtils.msg(
                        "city.list.gui.nav.sort",
                        "<gray>Sort:</gray> <white>{sort}</white>",
                        Placeholder.unparsed("sort", sortMode.displayName(plugin))
                )
        ));
    }

    private ItemStack closeButton() {
        return GuiItemFactory.customHead(GuiHeadTextures.CLOSE, configUtils.msg("city.list.gui.nav.close", "<red>Chiudi</red>"));
    }

    private String formatMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String formatXp(double xp) {
        return BigDecimal.valueOf(xp).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private enum WarpAvailability {
        AVAILABLE("available", "<green>Warp:</green> <white>Disponibile</white>"),
        NO_SPAWN("no_spawn", "<gold>Warp:</gold> <gray>Non impostato</gray>"),
        PRIVATE_SPAWN("private_spawn", "<red>Warp:</red> <gray>Privato</gray>");

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

    private static final class ListHolder extends BaseHolder {
        private ListHolder(UUID sessionId) {
            super(sessionId);
        }
    }
}
