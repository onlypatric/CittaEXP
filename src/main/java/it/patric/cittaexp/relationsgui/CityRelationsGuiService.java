package it.patric.cittaexp.relationsgui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.playersgui.CityPlayersSession;
import it.patric.cittaexp.text.UiLoreStyle;
import it.patric.cittaexp.utils.DialogBodyUtils;
import it.patric.cittaexp.utils.DialogViewUtils;
import it.patric.cittaexp.utils.GuiHeadTextures;
import it.patric.cittaexp.utils.GuiItemFactory;
import it.patric.cittaexp.utils.GuiNavigationUtils;
import it.patric.cittaexp.utils.PluginConfigUtils;
import it.patric.cittaexp.utils.TexturedGuiSupport;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.william278.husktowns.town.Member;
import net.william278.husktowns.town.Privilege;
import net.william278.husktowns.town.Town;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class CityRelationsGuiService implements Listener {

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
    private static final ClickCallback.Options CLICK_OPTIONS = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(2))
            .build();
    private static final String RELATIONS_FONT_IMAGE_ID = "cittaexp_gui:diplomazia_9x6";
    private static final String TRANSPARENT_BUTTON_ID = "cittaexp_gui:transparent_button";

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final PluginConfigUtils configUtils;
    private final ConcurrentMap<UUID, CityRelationsSession> sessionsByViewer = new ConcurrentHashMap<>();

    public CityRelationsGuiService(Plugin plugin, HuskTownsApiHook huskTownsApiHook) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.configUtils = new PluginConfigUtils(plugin);
    }

    public void clearAllSessions() {
        sessionsByViewer.clear();
    }

    public void open(Player player, CityPlayersSession citySession) {
        if (!huskTownsApiHook.isRelationsEnabled()) {
            player.sendMessage(configUtils.msg(
                    "city.players.gui.relations.errors.feature_disabled",
                    "<red>Relazioni citta disabilitate su questo server.</red>"
            ));
            return;
        }

        Optional<Town> town = huskTownsApiHook.getTownById(citySession.townId());
        if (town.isEmpty()) {
            player.sendMessage(configUtils.msg(
                    "city.players.gui.errors.town_not_found_runtime",
                    "<red>La citta non e piu disponibile.</red>"
            ));
            return;
        }

        CityRelationsSession session = new CityRelationsSession(
                UUID.randomUUID(),
                player.getUniqueId(),
                town.get().getId(),
                town.get().getName(),
                citySession.staffView(),
                false
        );
        sessionsByViewer.put(player.getUniqueId(), session);
        openList(player, session);
    }

    public void openSamplePreview(Player player) {
        CityRelationsSession session = new CityRelationsSession(
                UUID.randomUUID(),
                player.getUniqueId(),
                -1,
                "Preview",
                true,
                true
        );
        sessionsByViewer.put(player.getUniqueId(), session);
        openList(player, session);
    }

    private void openList(Player player, CityRelationsSession session) {
        List<CityRelationEntryView> entries = entriesFor(session);
        int pageCount = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (session.page() >= pageCount) {
            session.page(pageCount - 1);
        }

        RelationsHolder holder = new RelationsHolder(session.sessionId());
        RelationsOpenResult openResult = createRelationsInventory(
                holder,
                configUtils.msg(
                        session.samplePreview() ? "city.players.gui.relations.sample_title" : "city.players.gui.relations.title",
                        session.samplePreview()
                                ? "<gold>Registro Diplomatico</gold> <gray>Preview Vanilla</gray>"
                                : "<gold>Relazioni Citta</gold> <gray>{town}</gray>",
                        Placeholder.unparsed("town", session.townName())
                )
        );
        Inventory inventory = openResult.inventory();
        ItemStack transparentBase = TexturedGuiSupport.transparentButtonBase(plugin, TRANSPARENT_BUTTON_ID);
        TexturedGuiSupport.fillGroup(inventory, SLOT_PREV, navPrev(session.page() > 0, transparentBase, session.page() + 1, pageCount, entries.size()));
        TexturedGuiSupport.fillGroup(inventory, SLOT_NEXT, navNext(session.page() + 1 < pageCount, transparentBase, session.page() + 1, pageCount, entries.size()));
        TexturedGuiSupport.fillGroup(inventory, SLOT_CLOSE, closeButton(transparentBase, session));

        int from = session.page() * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, entries.size());
        int slot = 0;
        for (int i = from; i < to; i++) {
            inventory.setItem(SLOT_CONTENT[slot], relationHead(entries.get(i), session.staffView() || session.samplePreview()));
            slot++;
        }
        if (entries.isEmpty()) {
            inventory.setItem(22, GuiItemFactory.customHead(
                    GuiHeadTextures.INFO,
                    configUtils.msg(
                            "city.players.gui.relations.empty",
                            "<gray>Nessuna citta disponibile per le relazioni.</gray>"
                )
            ));
        }
        if (openResult.texturedWrapper() != null) {
            openResult.texturedWrapper().showInventory(player);
            return;
        }
        player.openInventory(inventory);
    }

    private List<CityRelationEntryView> buildEntries(int townId) {
        return huskTownsApiHook.getTownRelationsView(townId).stream()
                .map(view -> {
                    Town town = view.town();
                    String tag = town.getMetadataTag(CITY_TAG_KEY)
                            .map(String::trim)
                            .filter(text -> !text.isBlank())
                            .orElse("---");
                    return new CityRelationEntryView(
                            town.getId(),
                            town.getName(),
                            safeMayorId(town),
                            tag,
                            town.getColorRgb(),
                            view.relation(),
                            view.reciprocalRelation()
                    );
                })
                .sorted(Comparator
                        .comparingInt((CityRelationEntryView entry) -> relationOrder(entry.relation()))
                        .thenComparing(entry -> entry.townName().toLowerCase()))
                .toList();
    }

    private List<CityRelationEntryView> entriesFor(CityRelationsSession session) {
        return session.samplePreview() ? sampleEntries() : buildEntries(session.townId());
    }

    private List<CityRelationEntryView> sampleEntries() {
        return List.of(
                sampleEntry(11, "Aurelia", "AUR", "#f4c542", Town.Relation.ALLY, Town.Relation.ALLY),
                sampleEntry(12, "Nordhaven", "NRD", "#78c4ff", Town.Relation.ALLY, Town.Relation.NEUTRAL),
                sampleEntry(13, "Valtora", "VAL", "#7fe08a", Town.Relation.NEUTRAL, Town.Relation.ALLY),
                sampleEntry(14, "Roccaflama", "RFL", "#ff7a59", Town.Relation.ENEMY, Town.Relation.ENEMY),
                sampleEntry(15, "Selvaria", "SLV", "#5bd19d", Town.Relation.NEUTRAL, Town.Relation.NEUTRAL),
                sampleEntry(16, "Drakemoor", "DRK", "#8a7dff", Town.Relation.ENEMY, Town.Relation.NEUTRAL),
                sampleEntry(17, "Porto Sole", "SOL", "#ffd166", Town.Relation.ALLY, Town.Relation.ALLY),
                sampleEntry(18, "Argentum", "ARG", "#d9e2ec", Town.Relation.NEUTRAL, Town.Relation.ENEMY),
                sampleEntry(19, "Myrgard", "MYR", "#c77dff", Town.Relation.ENEMY, Town.Relation.ENEMY),
                sampleEntry(20, "Castelverde", "CSV", "#80ed99", Town.Relation.ALLY, Town.Relation.NEUTRAL),
                sampleEntry(21, "Sabbianera", "SBN", "#f28482", Town.Relation.NEUTRAL, Town.Relation.ALLY),
                sampleEntry(22, "Borealis", "BOR", "#90dbf4", Town.Relation.ENEMY, Town.Relation.NEUTRAL)
        );
    }

    private CityRelationEntryView sampleEntry(
            int townId,
            String townName,
            String tag,
            String colorRgb,
            Town.Relation relation,
            Town.Relation reciprocalRelation
    ) {
        return new CityRelationEntryView(
                townId,
                townName,
                new UUID(0L, 0L),
                tag,
                colorRgb,
                relation,
                reciprocalRelation
        );
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
        if (!(top.getHolder() instanceof RelationsHolder holder)) {
            return;
        }
        if (event.getClickedInventory() != top) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);

        CityRelationsSession session = sessionsByViewer.get(player.getUniqueId());
        if (session == null || !session.sessionId().equals(holder.sessionId())) {
            player.closeInventory();
            return;
        }

        List<CityRelationEntryView> entries = entriesFor(session);
        int pageCount = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int slot = event.getRawSlot();

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
            returnFromRelations(player, session);
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

        CityRelationEntryView entry = entries.get(index);
        if (session.staffView() || session.samplePreview()) {
            player.sendMessage(configUtils.msg(
                    "city.players.gui.relations.errors.read_only_staff",
                    "<red>In visuale staff le relazioni sono in sola lettura.</red>"
            ));
            return;
        }
        openSetDialog(player, session, entry);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessionsByViewer.remove(event.getPlayer().getUniqueId());
    }

    private void openSetDialog(Player player, CityRelationsSession session, CityRelationEntryView entry) {
        DialogBase base = DialogBase.builder(configUtils.msg(
                        "city.players.dialog.relations.title",
                        "<gold>Imposta relazione</gold>"
                ))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(DialogBodyUtils.plainMessages(
                        configUtils,
                        "city.players.dialog.relations.body",
                        "<gray>Target:</gray> <white>{target_town}</white><newline>" +
                                "<gray>Relazione attuale:</gray> <white>{current_relation}</white><newline>" +
                                "<gray>Loro verso di voi:</gray> <white>{reciprocal_relation}</white>",
                        Placeholder.unparsed("target_town", entry.townName()),
                        Placeholder.unparsed("current_relation", relationLabel(entry.relation())),
                        Placeholder.unparsed("reciprocal_relation", relationLabel(entry.reciprocalRelation()))
                ))
                .build();

        Dialog dialog = Dialog.create(factory -> {
            ActionButton ally = relationButton("ally", Town.Relation.ALLY, player, session, entry);
            ActionButton enemy = relationButton("enemy", Town.Relation.ENEMY, player, session, entry);
            ActionButton neutral = relationButton("neutral", Town.Relation.NEUTRAL, player, session, entry);
            ActionButton cancel = ActionButton.create(
                    configUtils.msg("city.players.dialog.relations.buttons.cancel", "<gray>ANNULLA</gray>"),
                    null,
                    130,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player actor) {
                            CityRelationsSession current = sessionsByViewer.get(actor.getUniqueId());
                            if (current != null && current.sessionId().equals(session.sessionId())) {
                                openList(actor, current);
                            }
                        }
                    }, CLICK_OPTIONS)
            );
            factory.empty().base(base).type(DialogType.multiAction(List.of(ally, enemy, neutral), cancel, 2));
        });

        DialogViewUtils.showDialog(
                plugin,
                player,
                dialog,
                configUtils.msg(
                        "city.players.dialog.relations.errors.unavailable",
                        "<red>Dialog non disponibile su questo server.</red>"
                ),
                "relations-set"
        );
    }

    private ActionButton relationButton(
            String relationKey,
            Town.Relation relation,
            Player player,
            CityRelationsSession session,
            CityRelationEntryView entry
    ) {
        return ActionButton.create(
                configUtils.msg(
                        "city.players.dialog.relations.buttons." + relationKey,
                        switch (relation) {
                            case ALLY -> "<green>ALLY</green>";
                            case ENEMY -> "<red>ENEMY</red>";
                            default -> "<yellow>NEUTRAL</yellow>";
                        }
                ),
                null,
                120,
                DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player actor) {
                        applyRelation(actor, session, entry, relation);
                    }
                }, CLICK_OPTIONS)
        );
    }

    private void applyRelation(
            Player player,
            CityRelationsSession expectedSession,
            CityRelationEntryView entry,
            Town.Relation relation
    ) {
        CityRelationsSession session = sessionsByViewer.get(player.getUniqueId());
        if (session == null || !session.sessionId().equals(expectedSession.sessionId())) {
            player.sendMessage(configUtils.msg(
                    "city.players.gui.errors.session_expired",
                    "<red>Sessione GUI scaduta. Riapri /city.</red>"
            ));
            return;
        }
        if (!huskTownsApiHook.isRelationsEnabled()) {
            player.sendMessage(configUtils.msg(
                    "city.players.gui.relations.errors.feature_disabled",
                    "<red>Relazioni citta disabilitate su questo server.</red>"
            ));
            return;
        }
        if (!huskTownsApiHook.hasPrivilege(player, Privilege.MANAGE_RELATIONS)) {
            player.sendMessage(configUtils.msg(
                    "city.players.gui.relations.errors.no_permission",
                    "<red>Non hai i permessi per gestire le relazioni citta.</red>"
            ));
            return;
        }
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty() || member.get().town().getId() != session.townId()) {
            player.sendMessage(configUtils.msg(
                    "city.players.gui.relations.errors.no_town",
                    "<red>Devi essere membro attivo della citta per modificare le relazioni.</red>"
            ));
            return;
        }

        boolean dispatched = huskTownsApiHook.setTownRelation(
                player,
                session.townId(),
                entry.townName(),
                relation
        );
        if (!dispatched) {
            player.sendMessage(configUtils.msg(
                    "city.players.gui.relations.errors.set_failed",
                    "<red>Impossibile aggiornare la relazione adesso.</red>"
            ));
            return;
        }

        player.sendMessage(configUtils.msg(
                "city.players.gui.relations.actions.set_success",
                "<green>Relazione aggiornata:</green> <white>{target_town}</white> <gray>-></gray> <yellow>{relation}</yellow>",
                Placeholder.unparsed("target_town", entry.townName()),
                Placeholder.unparsed("relation", relationLabel(relation))
        ));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            CityRelationsSession current = sessionsByViewer.get(player.getUniqueId());
            if (current != null && current.sessionId().equals(session.sessionId())) {
                openList(player, current);
            }
        }, 2L);
    }

    private ItemStack relationHead(CityRelationEntryView entry, boolean staffView) {
        Component titlePrefix = configUtils.msg(
                "city.players.gui.relations.entry.title_prefix",
                "<bold>{town}</bold> <gray>|</gray>",
                Placeholder.unparsed("town", entry.townName())
        );
        TextColor tagColor = parseTownColor(entry.colorRgb()).orElse(TextColor.color(170, 170, 170));
        Component displayName = titlePrefix
                .append(Component.space())
                .append(Component.text(entry.tag()).color(tagColor));

        List<Component> lore = UiLoreStyle.renderComponents(
                UiLoreStyle.line(UiLoreStyle.LineType.STATUS, "Relazione attuale: " + relationLabel(entry.relation())),
                UiLoreStyle.line(UiLoreStyle.LineType.STATUS, "Loro verso di voi: " + relationLabel(entry.reciprocalRelation())),
                List.of(UiLoreStyle.line(
                        staffView ? UiLoreStyle.LineType.INFO : UiLoreStyle.LineType.ACTION,
                        staffView ? "Sola lettura in modalita staff" : "Click sinistro o destro per cambiare rapporto"
                ))
        );

        String relationTexture = switch (entry.relation()) {
            case ALLY -> GuiHeadTextures.RELATION_ALLY;
            case ENEMY -> GuiHeadTextures.RELATION_ENEMY;
            case NEUTRAL -> GuiHeadTextures.RELATION_NEUTRAL;
        };
        if (entry.mayorId().getMostSignificantBits() == 0L && entry.mayorId().getLeastSignificantBits() == 0L) {
            return GuiItemFactory.customHead(relationTexture, displayName, lore);
        }
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

    private UUID safeMayorId(Town town) {
        try {
            return town.getMayor();
        } catch (RuntimeException ignored) {
            return new UUID(0L, 0L);
        }
    }

    private int relationOrder(Town.Relation relation) {
        return switch (relation) {
            case ALLY -> 0;
            case ENEMY -> 1;
            case NEUTRAL -> 2;
        };
    }

    private String relationLabel(Town.Relation relation) {
        return configUtils.cfgString(
                "city.players.gui.relations.labels." + relation.name().toLowerCase(),
                relation.name()
        );
    }

    private ItemStack navPrev(boolean enabled, ItemStack transparentBase, int currentPage, int pageCount, int totalEntries) {
        return TexturedGuiSupport.overlayButton(
                transparentBase,
                configUtils.msg(
                        enabled ? "city.players.gui.relations.nav.prev" : "city.players.gui.relations.nav.prev_disabled",
                        enabled ? "<yellow>Pagina precedente</yellow>" : "<gray>Pagina precedente</gray>"
                ),
                pageNavLore(currentPage, pageCount, totalEntries),
                GuiItemFactory.customHead(enabled ? GuiHeadTextures.NAV_LEFT : GuiHeadTextures.NAV_DISABLED, Component.empty())
        );
    }

    private ItemStack navNext(boolean enabled, ItemStack transparentBase, int currentPage, int pageCount, int totalEntries) {
        return TexturedGuiSupport.overlayButton(
                transparentBase,
                configUtils.msg(
                        enabled ? "city.players.gui.relations.nav.next" : "city.players.gui.relations.nav.next_disabled",
                        enabled ? "<yellow>Pagina successiva</yellow>" : "<gray>Pagina successiva</gray>"
                ),
                pageNavLore(currentPage, pageCount, totalEntries),
                GuiItemFactory.customHead(enabled ? GuiHeadTextures.NAV_RIGHT : GuiHeadTextures.NAV_DISABLED_RIGHT, Component.empty())
        );
    }

    private List<Component> pageNavLore(int currentPage, int pageCount, int totalEntries) {
        return List.of(
                configUtils.msg(
                        "city.players.gui.relations.nav.page",
                        "<gold>Pagina {current}/{total}</gold>",
                        Placeholder.unparsed("current", Integer.toString(currentPage)),
                        Placeholder.unparsed("total", Integer.toString(pageCount))
                ),
                configUtils.msg(
                        "city.players.gui.relations.nav.count",
                        "<gray>Citta:</gray> <white>{count}</white>",
                        Placeholder.unparsed("count", Integer.toString(totalEntries))
                )
        );
    }

    private ItemStack closeButton(ItemStack transparentBase, CityRelationsSession session) {
        return TexturedGuiSupport.overlayButton(
                transparentBase,
                configUtils.msg(
                        session.samplePreview() ? "city.players.gui.relations.nav.close" : "city.players.gui.relations.nav.back",
                        session.samplePreview() ? "<red>Chiudi</red>" : "<yellow>Indietro</yellow>"
                ),
                List.of(),
                GuiItemFactory.customHead(GuiHeadTextures.CLOSE, Component.empty())
        );
    }

    private void returnFromRelations(Player player, CityRelationsSession session) {
        if (session.samplePreview()) {
            player.closeInventory();
            return;
        }
        if (session.staffView()) {
            player.performCommand("cittaexp admin town players " + session.townName());
            return;
        }
        GuiNavigationUtils.openCityHub(plugin, player);
    }

    private RelationsOpenResult createRelationsInventory(RelationsHolder holder, Component fallbackTitle) {
        TexturedGuiSupport.OpenResult result = TexturedGuiSupport.createInventory(
                plugin,
                holder,
                holder::bind,
                GUI_SIZE,
                fallbackTitle,
                configUtils.missingKeyFallback("city.players.gui.relations.itemsadder.title_plain", "Registro Diplomatico"),
                configUtils.cfgInt("city.players.gui.relations.itemsadder.title_offset", 0),
                configUtils.cfgInt("city.players.gui.relations.itemsadder.texture_offset", -8),
                configUtils.cfgString("city.players.gui.relations.itemsadder.font_image_id", RELATIONS_FONT_IMAGE_ID),
                "city-relations"
        );
        return new RelationsOpenResult(result.inventory(), result.texturedWrapper());
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

    private static final class RelationsHolder implements InventoryHolder {
        private final UUID sessionId;
        private Inventory inventory;

        private RelationsHolder(UUID sessionId) {
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

    private record RelationsOpenResult(Inventory inventory, dev.lone.itemsadder.api.FontImages.TexturedInventoryWrapper texturedWrapper) {
    }
}
