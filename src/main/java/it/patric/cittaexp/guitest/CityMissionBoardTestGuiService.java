package it.patric.cittaexp.guitest;

import it.patric.cittaexp.utils.GuiItemFactory;
import it.patric.cittaexp.utils.GuiLayoutUtils;
import it.patric.cittaexp.utils.PluginConfigUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class CityMissionBoardTestGuiService implements Listener {

    private static final int GUI_SIZE = 54;
    private static final int SLOT_HEADER = 4;
    private static final int SLOT_STATE = 49;
    private static final int SLOT_FILTER = 16;
    private static final int SLOT_SORT = 25;
    private static final int SLOT_ACTION = 34;
    private static final int SLOT_REFRESH = 43;
    private static final int SLOT_BACK = 52;
    private static final int SLOT_DETAIL_HEADER = 22;
    private static final int SLOT_DETAIL_BODY = 31;
    private static final int SLOT_DETAIL_REWARD = 40;

    private static final Map<String, Integer> GROUP_SLOTS = Map.of(
            "daily", 10,
            "race", 12,
            "weekly", 19,
            "monthly", 21,
            "event", 28,
            "seasonal", 30
    );

    private final Plugin plugin;
    private final PluginConfigUtils cfg;
    private final MissionBoardDataProvider dataProvider;
    private final ConcurrentMap<UUID, Session> sessionsByViewer = new ConcurrentHashMap<>();

    public CityMissionBoardTestGuiService(Plugin plugin) {
        this(plugin, new StaticMissionBoardDataProvider());
    }

    public CityMissionBoardTestGuiService(Plugin plugin, MissionBoardDataProvider dataProvider) {
        this.plugin = plugin;
        this.cfg = new PluginConfigUtils(plugin);
        this.dataProvider = dataProvider;
    }

    public void open(Player player) {
        Session session = new Session(
                UUID.randomUUID(),
                player.getUniqueId(),
                MissionFilter.ALL,
                MissionSort.PRIORITY,
                "daily",
                View.LIST
        );
        sessionsByViewer.put(player.getUniqueId(), session);
        openBoard(player, session);
    }

    public void clearAllSessions() {
        sessionsByViewer.clear();
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
        if (!(top.getHolder() instanceof Holder holder)) {
            return;
        }
        if (event.getClickedInventory() != top) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);

        Session session = sessionsByViewer.get(player.getUniqueId());
        if (session == null || !session.sessionId.equals(holder.sessionId())) {
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        if (slot == SLOT_BACK) {
            player.closeInventory();
            sessionsByViewer.remove(player.getUniqueId());
            return;
        }
        if (slot == SLOT_FILTER) {
            session.filter = session.filter.next();
            normalizeSelection(session);
            openBoard(player, session);
            return;
        }
        if (slot == SLOT_SORT) {
            session.sort = session.sort.next();
            normalizeSelection(session);
            openBoard(player, session);
            return;
        }
        if (slot == SLOT_ACTION) {
            session.view = session.view == View.LIST ? View.DETAIL : View.LIST;
            openBoard(player, session);
            return;
        }
        if (slot == SLOT_REFRESH) {
            openBoard(player, session);
            return;
        }

        Optional<String> clickedGroup = groupBySlot(slot);
        if (clickedGroup.isPresent()) {
            session.selectedGroup = clickedGroup.get();
            openBoard(player, session);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessionsByViewer.remove(event.getPlayer().getUniqueId());
    }

    private void openBoard(Player player, Session session) {
        normalizeSelection(session);
        Holder holder = new Holder(session.sessionId, session.view);
        Inventory inventory = Bukkit.createInventory(holder, GUI_SIZE, cfg.msg(
                "cittaexp.staff.gui_test.title",
                "<gold>Mission Board v2</gold> <gray>(TEST)</gray>"
        ));
        holder.bind(inventory);
        GuiLayoutUtils.fillAllExcept(
                inventory,
                GuiItemFactory.fillerPane(),
                SLOT_HEADER,
                SLOT_STATE,
                SLOT_FILTER,
                SLOT_SORT,
                SLOT_ACTION,
                SLOT_REFRESH,
                SLOT_BACK,
                SLOT_DETAIL_HEADER,
                SLOT_DETAIL_BODY,
                SLOT_DETAIL_REWARD,
                10, 12, 19, 21, 28, 30
        );

        inventory.setItem(SLOT_HEADER, headerItem(session));
        inventory.setItem(SLOT_STATE, stateItem(session));
        inventory.setItem(SLOT_FILTER, filterItem(session));
        inventory.setItem(SLOT_SORT, sortItem(session));
        inventory.setItem(SLOT_ACTION, actionItem(session));
        inventory.setItem(SLOT_REFRESH, actionButton(Material.CLOCK, "cittaexp.staff.gui_test.refresh", "<yellow>Refresh</yellow>"));
        inventory.setItem(SLOT_BACK, actionButton(Material.ARROW, "cittaexp.staff.gui_test.back", "<yellow>Indietro</yellow>"));

        Map<String, MissionBoardTestViewModel> rowsByGroup = rowsByGroup();
        for (Map.Entry<String, Integer> entry : GROUP_SLOTS.entrySet()) {
            String groupId = entry.getKey();
            MissionBoardTestViewModel row = rowsByGroup.get(groupId);
            if (row == null) {
                inventory.setItem(entry.getValue(), actionButton(Material.GRAY_DYE, "cittaexp.staff.gui_test.group.missing", "<gray>Placeholder mancante</gray>"));
                continue;
            }
            boolean selected = groupId.equals(session.selectedGroup);
            boolean visibleForFilter = matchesFilter(row, session.filter);
            inventory.setItem(entry.getValue(), missionCard(row, selected, visibleForFilter));
        }

        if (session.view == View.DETAIL) {
            MissionBoardTestViewModel selected = rowsByGroup.get(session.selectedGroup);
            if (selected != null) {
                inventory.setItem(SLOT_DETAIL_HEADER, detailHeaderItem(selected));
                inventory.setItem(SLOT_DETAIL_BODY, detailBodyItem(selected, session));
                inventory.setItem(SLOT_DETAIL_REWARD, detailRewardItem(selected));
            }
        } else {
            inventory.setItem(SLOT_DETAIL_HEADER, actionButton(Material.GRAY_STAINED_GLASS, "cittaexp.staff.gui_test.detail.hint", "<gray>Seleziona una missione e usa Azione</gray>"));
            inventory.setItem(SLOT_DETAIL_BODY, GuiItemFactory.fillerPane());
            inventory.setItem(SLOT_DETAIL_REWARD, GuiItemFactory.fillerPane());
        }

        player.openInventory(inventory);
    }

    private ItemStack headerItem(Session session) {
        return GuiItemFactory.item(
                Material.WRITABLE_BOOK,
                cfg.msg("cittaexp.staff.gui_test.header.title", "<gold>Mission Board Test</gold>"),
                List.of(
                        cfg.msg(
                                "cittaexp.staff.gui_test.header.l1",
                                "<gray>Filtro:</gray> <white>{filter}</white>",
                                Placeholder.unparsed("filter", session.filter.displayName())
                        ),
                        cfg.msg(
                                "cittaexp.staff.gui_test.header.l2",
                                "<gray>Ordine:</gray> <white>{sort}</white>",
                                Placeholder.unparsed("sort", session.sort.displayName())
                        )
                )
        );
    }

    private ItemStack stateItem(Session session) {
        List<MissionBoardTestViewModel> ranked = rankedRows(session.filter, session.sort);
        String top = ranked.isEmpty() ? "-" : ranked.getFirst().title();
        return GuiItemFactory.item(
                Material.COMPASS,
                cfg.msg("cittaexp.staff.gui_test.state.title", "<aqua>Stato vista</aqua>"),
                List.of(
                        cfg.msg(
                                "cittaexp.staff.gui_test.state.selected",
                                "<gray>Selezionata:</gray> <white>{selected}</white>",
                                Placeholder.unparsed("selected", session.selectedGroup.toUpperCase(Locale.ROOT))
                        ),
                        cfg.msg(
                                "cittaexp.staff.gui_test.state.top",
                                "<gray>Top corrente:</gray> <white>{top}</white>",
                                Placeholder.unparsed("top", top)
                        )
                )
        );
    }

    private ItemStack filterItem(Session session) {
        return GuiItemFactory.item(
                Material.HOPPER,
                cfg.msg("cittaexp.staff.gui_test.filter.title", "<gold>Filtro</gold>"),
                List.of(cfg.msg(
                        "cittaexp.staff.gui_test.filter.current",
                        "<gray>Attivo:</gray> <white>{value}</white>",
                        Placeholder.unparsed("value", session.filter.displayName())
                ))
        );
    }

    private ItemStack sortItem(Session session) {
        return GuiItemFactory.item(
                Material.REPEATER,
                cfg.msg("cittaexp.staff.gui_test.sort.title", "<gold>Ordina</gold>"),
                List.of(cfg.msg(
                        "cittaexp.staff.gui_test.sort.current",
                        "<gray>Attivo:</gray> <white>{value}</white>",
                        Placeholder.unparsed("value", session.sort.displayName())
                ))
        );
    }

    private ItemStack actionItem(Session session) {
        if (session.view == View.DETAIL) {
            return actionButton(Material.PAPER, "cittaexp.staff.gui_test.action.list", "<yellow>Torna elenco</yellow>");
        }
        return actionButton(Material.BOOK, "cittaexp.staff.gui_test.action.detail", "<yellow>Apri preview</yellow>");
    }

    private ItemStack actionButton(Material material, String path, String fallback) {
        return GuiItemFactory.item(material, cfg.msg(path, fallback));
    }

    private ItemStack missionCard(MissionBoardTestViewModel row, boolean selected, boolean visibleForFilter) {
        List<Component> lore = new ArrayList<>();
        lore.add(cfg.msg(
                "cittaexp.staff.gui_test.card.status",
                "<gray>Stato:</gray> <white>{status}</white>",
                Placeholder.unparsed("status", row.status().displayName())
        ));
        lore.add(cfg.msg(
                "cittaexp.staff.gui_test.card.progress",
                "<gray>Progresso:</gray> <white>{progress}%</white>",
                Placeholder.unparsed("progress", Integer.toString(row.progressPercent()))
        ));
        lore.add(cfg.msg(
                "cittaexp.staff.gui_test.card.time",
                "<gray>Tempo:</gray> <white>{time}</white>",
                Placeholder.unparsed("time", formatMinutes(row.remainingMinutes()))
        ));
        lore.add(cfg.msg(
                "cittaexp.staff.gui_test.card.reward",
                "<gray>Reward XP:</gray> <white>{xp}</white>",
                Placeholder.unparsed("xp", Integer.toString(row.rewardXp()))
        ));
        if (!visibleForFilter) {
            lore.add(cfg.msg("cittaexp.staff.gui_test.card.filtered", "<red>Filtrata dalla vista corrente</red>"));
        }

        ItemStack item = GuiItemFactory.item(
                row.icon(),
                cfg.msg(
                        "cittaexp.staff.gui_test.card.title",
                        "<gold>{title}</gold>",
                        Placeholder.unparsed("title", row.title())
                ),
                lore
        );
        if (selected) {
            ItemMeta meta = item.getItemMeta();
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack detailHeaderItem(MissionBoardTestViewModel row) {
        return GuiItemFactory.item(
                row.icon(),
                cfg.msg(
                        "cittaexp.staff.gui_test.detail.header",
                        "<gold>Preview:</gold> <white>{title}</white>",
                        Placeholder.unparsed("title", row.title())
                )
        );
    }

    private ItemStack detailBodyItem(MissionBoardTestViewModel row, Session session) {
        return GuiItemFactory.item(
                Material.PAPER,
                cfg.msg("cittaexp.staff.gui_test.detail.body.title", "<aqua>Dettaglio Placeholder</aqua>"),
                List.of(
                        cfg.msg(
                                "cittaexp.staff.gui_test.detail.body.status",
                                "<gray>Stato:</gray> <white>{status}</white>",
                                Placeholder.unparsed("status", row.status().displayName())
                        ),
                        cfg.msg(
                                "cittaexp.staff.gui_test.detail.body.progress",
                                "<gray>Completamento:</gray> <white>{progress}%</white>",
                                Placeholder.unparsed("progress", Integer.toString(row.progressPercent()))
                        ),
                        cfg.msg(
                                "cittaexp.staff.gui_test.detail.body.mode",
                                "<gray>Filtro/Ordine:</gray> <white>{filter}</white> <gray>/</gray> <white>{sort}</white>",
                                Placeholder.unparsed("filter", session.filter.displayName()),
                                Placeholder.unparsed("sort", session.sort.displayName())
                        )
                )
        );
    }

    private ItemStack detailRewardItem(MissionBoardTestViewModel row) {
        return GuiItemFactory.item(
                Material.CHEST,
                cfg.msg("cittaexp.staff.gui_test.detail.reward.title", "<gold>Reward Preview</gold>"),
                List.of(
                        cfg.msg(
                                "cittaexp.staff.gui_test.detail.reward.xp",
                                "<gray>XP:</gray> <white>{xp}</white>",
                                Placeholder.unparsed("xp", Integer.toString(row.rewardXp()))
                        ),
                        cfg.msg(
                                "cittaexp.staff.gui_test.detail.reward.item",
                                "<gray>Item:</gray> <white>{item}</white>",
                                Placeholder.unparsed("item", row.rewardItem())
                        )
                )
        );
    }

    private void normalizeSelection(Session session) {
        List<MissionBoardTestViewModel> ranked = rankedRows(session.filter, session.sort);
        if (ranked.isEmpty()) {
            session.selectedGroup = "daily";
            return;
        }
        boolean exists = ranked.stream().anyMatch(row -> row.groupId().equalsIgnoreCase(session.selectedGroup));
        if (!exists) {
            session.selectedGroup = ranked.getFirst().groupId();
        }
    }

    private List<MissionBoardTestViewModel> rankedRows(MissionFilter filter, MissionSort sort) {
        List<MissionBoardTestViewModel> rows = new ArrayList<>(dataProvider.load());
        List<MissionBoardTestViewModel> filtered = rows.stream().filter(row -> matchesFilter(row, filter)).toList();
        List<MissionBoardTestViewModel> source = filtered.isEmpty() ? rows : filtered;
        return source.stream().sorted(sort.comparator()).toList();
    }

    private Map<String, MissionBoardTestViewModel> rowsByGroup() {
        Map<String, MissionBoardTestViewModel> map = new LinkedHashMap<>();
        for (MissionBoardTestViewModel row : dataProvider.load()) {
            map.put(row.groupId().toLowerCase(Locale.ROOT), row);
        }
        return map;
    }

    private Optional<String> groupBySlot(int slot) {
        return GROUP_SLOTS.entrySet().stream()
                .filter(entry -> entry.getValue() == slot)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    private boolean matchesFilter(MissionBoardTestViewModel row, MissionFilter filter) {
        return switch (filter) {
            case ALL -> true;
            case AVAILABLE -> row.status() == MissionBoardStatus.AVAILABLE;
            case CLAIMABLE -> row.status() == MissionBoardStatus.CLAIMABLE;
            case COMPLETED -> row.status() == MissionBoardStatus.COMPLETED;
        };
    }

    private static String formatMinutes(int totalMinutes) {
        int safe = Math.max(0, totalMinutes);
        int days = safe / (24 * 60);
        int hours = (safe % (24 * 60)) / 60;
        int minutes = safe % 60;
        if (days > 0) {
            return days + "g " + hours + "h " + minutes + "m";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    private enum View {
        LIST,
        DETAIL
    }

    private enum MissionFilter {
        ALL("Tutte"),
        AVAILABLE("Disponibili"),
        CLAIMABLE("Claimabili"),
        COMPLETED("Completate");

        private final String displayName;

        MissionFilter(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }

        public MissionFilter next() {
            MissionFilter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private enum MissionSort {
        PRIORITY("Priorita", Comparator.comparingInt(MissionBoardTestViewModel::priorityScore).reversed()),
        TIME_LEFT("Tempo residuo", Comparator.comparingInt(MissionBoardTestViewModel::remainingMinutes)),
        PROGRESS("Progresso", Comparator.comparingInt(MissionBoardTestViewModel::progressPercent).reversed()),
        XP_REWARD("XP reward", Comparator.comparingInt(MissionBoardTestViewModel::rewardXp).reversed());

        private final String displayName;
        private final Comparator<MissionBoardTestViewModel> comparator;

        MissionSort(String displayName, Comparator<MissionBoardTestViewModel> comparator) {
            this.displayName = displayName;
            this.comparator = comparator;
        }

        public String displayName() {
            return displayName;
        }

        public Comparator<MissionBoardTestViewModel> comparator() {
            return comparator;
        }

        public MissionSort next() {
            MissionSort[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private static final class Session {
        private final UUID sessionId;
        private final UUID viewerId;
        private MissionFilter filter;
        private MissionSort sort;
        private String selectedGroup;
        private View view;

        private Session(
                UUID sessionId,
                UUID viewerId,
                MissionFilter filter,
                MissionSort sort,
                String selectedGroup,
                View view
        ) {
            this.sessionId = sessionId;
            this.viewerId = viewerId;
            this.filter = filter;
            this.sort = sort;
            this.selectedGroup = selectedGroup;
            this.view = view;
        }
    }

    private static final class Holder implements InventoryHolder {
        private final UUID sessionId;
        private final View view;
        private Inventory inventory;

        private Holder(UUID sessionId, View view) {
            this.sessionId = sessionId;
            this.view = view;
        }

        private UUID sessionId() {
            return sessionId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void bind(Inventory inventory) {
            this.inventory = inventory;
        }
    }
}
