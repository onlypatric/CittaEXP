package it.patric.cittaexp.manageclaim;

import it.patric.cittaexp.integration.huskclaims.HuskClaimsChunkGuard;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.utils.ExceptionUtils;
import it.patric.cittaexp.utils.GuiHeadTextures;
import it.patric.cittaexp.utils.GuiItemFactory;
import it.patric.cittaexp.utils.PluginConfigUtils;
import it.patric.cittaexp.utils.ReasonMessageMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.william278.husktowns.claim.Claim;
import net.william278.husktowns.claim.TownClaim;
import net.william278.husktowns.town.Member;
import net.william278.husktowns.town.Privilege;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class CitySectionMapGuiService implements Listener {

    private static final int MAP_WIDTH = 9;
    private static final int MAP_HEIGHT = 5;
    private static final int MAP_SLOTS = 45;
    private static final int MAX_RANGE = 8;
    private static final int SLOT_UP = 45;
    private static final int SLOT_LEFT = 46;
    private static final int SLOT_CENTER = 47;
    private static final int SLOT_RIGHT = 48;
    private static final int SLOT_DOWN = 49;
    private static final int SLOT_LEGEND_OWN = 50;
    private static final int SLOT_LEGEND_WILD = 51;
    private static final int SLOT_LEGEND_BLOCKED = 52;
    private static final int SLOT_CLOSE = 53;

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final HuskClaimsChunkGuard huskClaimsChunkGuard;
    private final CityLevelService cityLevelService;
    private final PluginConfigUtils cfg;
    private final Map<UUID, CitySectionMapSession> sessionsByViewer = new ConcurrentHashMap<>();

    public CitySectionMapGuiService(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            HuskClaimsChunkGuard huskClaimsChunkGuard,
            CityLevelService cityLevelService
    ) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.huskClaimsChunkGuard = huskClaimsChunkGuard;
        this.cityLevelService = cityLevelService;
        this.cfg = new PluginConfigUtils(plugin);
    }

    public void openForOwnTown(Player player) {
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty()) {
            player.sendMessage(cfg.msg("city.section.gui.errors.no_town",
                    "<red>Devi essere membro attivo di una citta.</red>"));
            return;
        }
        CitySectionMapSession session = new CitySectionMapSession(
                UUID.randomUUID(),
                player.getUniqueId(),
                member.get().town().getId(),
                member.get().town().getName(),
                player.getWorld().getName(),
                player.getChunk().getX(),
                player.getChunk().getZ()
        );
        sessionsByViewer.put(player.getUniqueId(), session);
        openMap(player, session);
    }

    public void clearAllSessions() {
        sessionsByViewer.clear();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof MapHolder mapHolder)) {
            return;
        }
        if (event.getClickedInventory() != top || event.getRawSlot() < 0) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);

        CitySectionMapSession session = sessionsByViewer.get(player.getUniqueId());
        if (session == null || !session.sessionId().equals(mapHolder.sessionId())) {
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_UP) {
            session.move(0, -1);
            openMap(player, session);
            return;
        }
        if (slot == SLOT_DOWN) {
            session.move(0, 1);
            openMap(player, session);
            return;
        }
        if (slot == SLOT_LEFT) {
            session.move(-1, 0);
            openMap(player, session);
            return;
        }
        if (slot == SLOT_RIGHT) {
            session.move(1, 0);
            openMap(player, session);
            return;
        }
        if (slot == SLOT_CENTER) {
            session.centerTo(player.getChunk().getX(), player.getChunk().getZ());
            openMap(player, session);
            return;
        }
        if (slot < 0 || slot >= MAP_SLOTS) {
            return;
        }

        int row = slot / MAP_WIDTH;
        int col = slot % MAP_WIDTH;
        int chunkX = session.centerChunkX() + (col - 4);
        int chunkZ = session.centerChunkZ() + (row - 2);
        World world = Bukkit.getWorld(session.worldName());
        if (world == null) {
            player.sendMessage(cfg.msg("city.section.gui.errors.world_unavailable",
                    "<red>Mondo non disponibile per la mappa claim.</red>"));
            player.closeInventory();
            return;
        }

        if (event.isLeftClick()) {
            handleLeftClick(player, session, world, chunkX, chunkZ);
            return;
        }
        if (event.isRightClick()) {
            handleRightClick(player, session, world, chunkX, chunkZ);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        // No-op: non rimuoviamo qui la sessione per consentire refresh/reopen sicuri.
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessionsByViewer.remove(event.getPlayer().getUniqueId());
    }

    private void openMap(Player player, CitySectionMapSession session) {
        World world = Bukkit.getWorld(session.worldName());
        if (world == null) {
            player.sendMessage(cfg.msg("city.section.gui.errors.world_unavailable",
                    "<red>Mondo non disponibile per la mappa claim.</red>"));
            player.closeInventory();
            sessionsByViewer.remove(player.getUniqueId());
            return;
        }

        MapHolder holder = new MapHolder(session.sessionId());
        Inventory inventory = Bukkit.createInventory(holder, 54, cfg.msg("city.section.gui.title",
                "<gold>Mappa Claim</gold> <gray>{town}</gray> <dark_gray>[</dark_gray><white>{x}</white><gray>,</gray><white>{z}</white><dark_gray>]</dark_gray>",
                Placeholder.unparsed("town", session.townName()),
                Placeholder.unparsed("x", Integer.toString(session.centerChunkX())),
                Placeholder.unparsed("z", Integer.toString(session.centerChunkZ()))));
        holder.bind(inventory);

        for (int slot = 0; slot < MAP_SLOTS; slot++) {
            int row = slot / MAP_WIDTH;
            int col = slot % MAP_WIDTH;
            int chunkX = session.centerChunkX() + (col - 4);
            int chunkZ = session.centerChunkZ() + (row - 2);
            CitySectionMapCell cell = resolveCell(player, session, world, chunkX, chunkZ);
            inventory.setItem(slot, toItem(cell));
        }

        inventory.setItem(SLOT_UP, navHead(GuiHeadTextures.SECTION_NAV_UP, "city.section.gui.nav.up", "<yellow>Su</yellow>"));
        inventory.setItem(SLOT_DOWN, navHead(GuiHeadTextures.SECTION_NAV_DOWN, "city.section.gui.nav.down", "<yellow>Giu</yellow>"));
        inventory.setItem(SLOT_LEFT, navHead(GuiHeadTextures.NAV_LEFT, "city.section.gui.nav.left", "<yellow>Sinistra</yellow>"));
        inventory.setItem(SLOT_RIGHT, navHead(GuiHeadTextures.NAV_RIGHT, "city.section.gui.nav.right", "<yellow>Destra</yellow>"));
        inventory.setItem(SLOT_CENTER, navHead(GuiHeadTextures.SECTION_CENTER, "city.section.gui.nav.center", "<aqua>Centra su di te</aqua>"));
        inventory.setItem(SLOT_LEGEND_OWN, legendHead(GuiHeadTextures.SECTION_OWN,
                "city.section.gui.legend.own", "<green>Tuo claim</green>"));
        inventory.setItem(SLOT_LEGEND_WILD, legendHead(GuiHeadTextures.SECTION_WILD,
                "city.section.gui.legend.wilderness", "<gray>Wilderness claimabile</gray>"));
        inventory.setItem(SLOT_LEGEND_BLOCKED, legendHead(GuiHeadTextures.SECTION_BLOCKED,
                "city.section.gui.legend.blocked", "<red>Bloccato/altrui</red>"));
        inventory.setItem(SLOT_CLOSE, navHead(GuiHeadTextures.CLOSE, "city.section.gui.nav.close", "<red>Chiudi</red>"));

        player.openInventory(inventory);
    }

    private void handleLeftClick(Player player, CitySectionMapSession session, World world, int chunkX, int chunkZ) {
        CitySectionMapCell cell = resolveCell(player, session, world, chunkX, chunkZ);
        switch (cell.state()) {
            case WILDERNESS_CLAIMABLE -> claimChunk(player, session, world, chunkX, chunkZ);
            case OWN_CLAIM -> unclaimChunk(player, session, world, chunkX, chunkZ, cell);
            case BLOCKED_HUSKCLAIMS -> player.sendMessage(cfg.msg("city.section.gui.errors.huskclaims_blocked",
                    "<red>Chunk bloccato: area protetta da HuskClaims.</red>"));
            case OTHER_CLAIM -> player.sendMessage(cfg.msg("city.section.gui.errors.claimed_other",
                    "<red>Chunk gia claimato da:</red> <yellow>{owner}</yellow>",
                    Placeholder.unparsed("owner", cell.ownerName())));
            case BLOCKED_POLICY -> player.sendMessage(reasonMessage(cell.reasonCode(), chunkX, chunkZ));
        }
    }

    private void handleRightClick(Player player, CitySectionMapSession session, World world, int chunkX, int chunkZ) {
        CitySectionMapCell cell = resolveCell(player, session, world, chunkX, chunkZ);
        if (cell.state() != CitySectionMapCell.State.OWN_CLAIM) {
            player.sendMessage(cfg.msg("city.section.gui.errors.edit_not_allowed",
                    "<red>Puoi modificare il tipo solo sui claim della tua citta.</red>"));
            return;
        }
        if (chunkDistance(player.getChunk().getX(), player.getChunk().getZ(), chunkX, chunkZ) > MAX_RANGE) {
            player.sendMessage(reasonMessage("out-of-range", chunkX, chunkZ));
            return;
        }

        Claim.Type currentType = cell.claimType() == null ? Claim.Type.CLAIM : cell.claimType();
        Claim.Type nextType = switch (currentType) {
            case CLAIM -> Claim.Type.FARM;
            case FARM -> Claim.Type.PLOT;
            case PLOT -> Claim.Type.CLAIM;
        };
        Privilege required = (nextType == Claim.Type.PLOT) ? Privilege.SET_PLOT : Privilege.SET_FARM;
        if (!huskTownsApiHook.hasPrivilege(player, required)) {
            player.sendMessage(cfg.msg("city.section.gui.errors.no_edit_privilege",
                    "<red>Permessi insufficienti per modificare il tipo claim.</red>"));
            return;
        }

        try {
            huskTownsApiHook.setClaimTypeAt(player, world.getChunkAt(chunkX, chunkZ), world, nextType);
            player.sendMessage(cfg.msg("city.section.gui.actions.edit_success",
                    "<green>Tipo claim aggiornato a:</green> <yellow>{type}</yellow> <gray>({x},{z})</gray>",
                    Placeholder.unparsed("type", nextType.name()),
                    Placeholder.unparsed("x", Integer.toString(chunkX)),
                    Placeholder.unparsed("z", Integer.toString(chunkZ))));
        } catch (RuntimeException ex) {
            player.sendMessage(cfg.msg("city.section.gui.actions.edit_failed",
                    "<red>Modifica claim fallita:</red> <gray>{reason}</gray>",
                    Placeholder.unparsed("reason", ExceptionUtils.safeMessage(ex, "errore sconosciuto"))));
            plugin.getLogger().warning("[section-map] edit failed player=" + player.getUniqueId()
                    + " chunk=" + chunkX + "," + chunkZ + " reason=" + ex.getMessage());
        }
        scheduleRefresh(player, session);
    }

    private void claimChunk(Player player, CitySectionMapSession session, World world, int chunkX, int chunkZ) {
        String reason = claimBlockReason(player, session, world, chunkX, chunkZ);
        if (reason != null) {
            player.sendMessage(reasonMessage(reason, chunkX, chunkZ));
            return;
        }
        try {
            huskTownsApiHook.createClaimAt(player, world.getChunkAt(chunkX, chunkZ), world, false);
            player.sendMessage(cfg.msg("city.section.gui.actions.claim_success",
                    "<green>Claim richiesto su chunk</green> <white>{x}</white><gray>,</gray><white>{z}</white><green>.</green>",
                    Placeholder.unparsed("x", Integer.toString(chunkX)),
                    Placeholder.unparsed("z", Integer.toString(chunkZ))));
        } catch (RuntimeException ex) {
            player.sendMessage(cfg.msg("city.section.gui.actions.claim_failed",
                    "<red>Claim fallito:</red> <gray>{reason}</gray>",
                    Placeholder.unparsed("reason", ExceptionUtils.safeMessage(ex, "errore sconosciuto"))));
            plugin.getLogger().warning("[section-map] claim failed player=" + player.getUniqueId()
                    + " chunk=" + chunkX + "," + chunkZ + " reason=" + ex.getMessage());
        }
        scheduleRefresh(player, session);
    }

    private void unclaimChunk(
            Player player,
            CitySectionMapSession session,
            World world,
            int chunkX,
            int chunkZ,
            CitySectionMapCell cell
    ) {
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty() || member.get().town().getId() != session.townId()) {
            player.sendMessage(cfg.msg("city.section.gui.errors.no_town", "<red>Devi essere membro attivo di una citta.</red>"));
            return;
        }
        if (!huskTownsApiHook.hasPrivilege(player, Privilege.UNCLAIM)) {
            player.sendMessage(cfg.msg("city.section.gui.errors.no_unclaim_privilege",
                    "<red>Permessi insufficienti per rimuovere claim.</red>"));
            return;
        }
        if (chunkDistance(player.getChunk().getX(), player.getChunk().getZ(), chunkX, chunkZ) > MAX_RANGE) {
            player.sendMessage(reasonMessage("out-of-range", chunkX, chunkZ));
            return;
        }
        Optional<TownClaim> claim = huskTownsApiHook.getClaimAt(world.getChunkAt(chunkX, chunkZ));
        if (claim.isEmpty() || claim.get().town().getId() != session.townId()) {
            player.sendMessage(cfg.msg("city.section.gui.errors.claim_missing_runtime",
                    "<red>Claim non trovato o non appartenente alla tua citta.</red>"));
            return;
        }
        if (member.get().town().getSpawn().isPresent()
                && claim.get().contains(member.get().town().getSpawn().get().getPosition())
                && !huskTownsApiHook.hasPrivilege(player, Privilege.SET_SPAWN)) {
            player.sendMessage(cfg.msg("city.section.gui.errors.spawn_claim_protected",
                    "<red>Non puoi rimuovere il claim che contiene lo spawn citta.</red>"));
            return;
        }

        try {
            huskTownsApiHook.deleteClaimAt(player, world.getChunkAt(chunkX, chunkZ));
            player.sendMessage(cfg.msg("city.section.gui.actions.unclaim_success",
                    "<green>Unclaim richiesto su chunk</green> <white>{x}</white><gray>,</gray><white>{z}</white><green>.</green>",
                    Placeholder.unparsed("x", Integer.toString(cell.chunkX())),
                    Placeholder.unparsed("z", Integer.toString(cell.chunkZ()))));
        } catch (RuntimeException ex) {
            player.sendMessage(cfg.msg("city.section.gui.actions.unclaim_failed",
                    "<red>Unclaim fallito:</red> <gray>{reason}</gray>",
                    Placeholder.unparsed("reason", ExceptionUtils.safeMessage(ex, "errore sconosciuto"))));
            plugin.getLogger().warning("[section-map] unclaim failed player=" + player.getUniqueId()
                    + " chunk=" + chunkX + "," + chunkZ + " reason=" + ex.getMessage());
        }
        scheduleRefresh(player, session);
    }

    private CitySectionMapCell resolveCell(
            Player player,
            CitySectionMapSession session,
            World world,
            int chunkX,
            int chunkZ
    ) {
        if (huskClaimsChunkGuard.isClaimedChunk(world, chunkX, chunkZ)) {
            return new CitySectionMapCell(
                    chunkX,
                    chunkZ,
                    CitySectionMapCell.State.BLOCKED_HUSKCLAIMS,
                    "HuskClaims",
                    null,
                    "huskclaims-blocked"
            );
        }

        Optional<TownClaim> claim = huskTownsApiHook.getClaimAt(world.getChunkAt(chunkX, chunkZ));
        if (claim.isPresent()) {
            TownClaim townClaim = claim.get();
            boolean own = townClaim.town().getId() == session.townId();
            String owner = townClaim.isAdminClaim(huskTownsApiHook.plugin())
                    ? "Admin"
                    : townClaim.town().getName();
            return new CitySectionMapCell(
                    chunkX,
                    chunkZ,
                    own ? CitySectionMapCell.State.OWN_CLAIM : CitySectionMapCell.State.OTHER_CLAIM,
                    owner,
                    townClaim.claim().getType(),
                    own ? "owned-claim" : "claimed-other"
            );
        }

        String reason = claimBlockReason(player, session, world, chunkX, chunkZ);
        if (reason != null) {
            return new CitySectionMapCell(
                    chunkX,
                    chunkZ,
                    CitySectionMapCell.State.BLOCKED_POLICY,
                    "-",
                    null,
                    reason
            );
        }

        return new CitySectionMapCell(
                chunkX,
                chunkZ,
                CitySectionMapCell.State.WILDERNESS_CLAIMABLE,
                "Wilderness",
                null,
                "claimable"
        );
    }

    private String claimBlockReason(
            Player player,
            CitySectionMapSession session,
            World world,
            int chunkX,
            int chunkZ
    ) {
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty() || member.get().town().getId() != session.townId()) {
            return "no-town";
        }
        if (!huskTownsApiHook.hasPrivilege(player, Privilege.CLAIM)) {
            return "no-claim-privilege";
        }
        if (chunkDistance(player.getChunk().getX(), player.getChunk().getZ(), chunkX, chunkZ) > MAX_RANGE) {
            return "out-of-range";
        }
        CityLevelService.LevelStatus status = cityLevelService.statusForTown(member.get().town().getId(), false);
        if (member.get().town().getClaimCount() >= status.claimCap()) {
            return "claim-cap-reached";
        }
        if (member.get().town().getClaimCount() > 0
                && !hasAdjacentOwnClaim(world, chunkX, chunkZ, member.get().town().getId())) {
            return "no-adjacent-claim";
        }
        Location anchor = chunkAnchor(player, world, chunkX, chunkZ);
        if (!huskTownsApiHook.isOperationAllowedBlockBreak(player, anchor)) {
            return "operation-denied";
        }
        return null;
    }

    private ItemStack toItem(CitySectionMapCell cell) {
        String texture = switch (cell.state()) {
            case OWN_CLAIM -> GuiHeadTextures.SECTION_OWN;
            case WILDERNESS_CLAIMABLE -> GuiHeadTextures.SECTION_WILD;
            case OTHER_CLAIM, BLOCKED_POLICY -> GuiHeadTextures.SECTION_BLOCKED;
            case BLOCKED_HUSKCLAIMS -> null;
        };
        Component display = cfg.msg("city.section.gui.cell.name",
                        "<white>Chunk</white> <yellow>{x}</yellow><gray>,</gray><yellow>{z}</yellow>",
                        Placeholder.unparsed("x", Integer.toString(cell.chunkX())),
                        Placeholder.unparsed("z", Integer.toString(cell.chunkZ())));
        List<Component> lore = List.of(
                cfg.msg("city.section.gui.cell.lore.state",
                        "<gray>Stato:</gray> <white>{state}</white>",
                        Placeholder.unparsed("state", stateLabel(cell.state()))),
                cfg.msg("city.section.gui.cell.lore.owner",
                        "<gray>Owner:</gray> <white>{owner}</white>",
                        Placeholder.unparsed("owner", cell.ownerName())),
                cfg.msg("city.section.gui.cell.lore.type",
                        "<gray>Tipo:</gray> <white>{type}</white>",
                        Placeholder.unparsed("type", cell.claimType() == null ? "-" : cell.claimType().name())),
                cfg.msg("city.section.gui.cell.lore.reason",
                        "<gray>Reason:</gray> <white>{reason}</white>",
                        Placeholder.unparsed("reason", reasonLabel(cell.reasonCode()))),
                cfg.msg("city.section.gui.cell.lore.actions",
                        "<dark_gray>LMB: claim/unclaim | RMB: edit type</dark_gray>")
        );
        if (texture != null) {
            return GuiItemFactory.customHead(texture, display, lore);
        }
        return GuiItemFactory.item(Material.RED_TERRACOTTA, display, lore);
    }

    private String stateLabel(CitySectionMapCell.State state) {
        return switch (state) {
            case OWN_CLAIM -> "Tuo claim";
            case OTHER_CLAIM -> "Claim altrui/admin";
            case WILDERNESS_CLAIMABLE -> "Wilderness claimabile";
            case BLOCKED_HUSKCLAIMS -> "Bloccato da HuskClaims";
            case BLOCKED_POLICY -> "Bloccato da policy";
        };
    }

    private String reasonLabel(String reasonCode) {
        return reasonCode == null ? "unknown" : reasonCode.replace('-', '_');
    }

    private Component reasonMessage(String reasonCode, int chunkX, int chunkZ) {
        if ("out-of-range".equals(reasonCode)) {
            return cfg.msg("city.section.gui.errors.out_of_range",
                    "<red>Chunk troppo distante. Max {range} chunk.</red>",
                    Placeholder.unparsed("range", Integer.toString(MAX_RANGE)));
        }
        if ("no-town".equals(reasonCode)
                || "no-claim-privilege".equals(reasonCode)
                || "claim-cap-reached".equals(reasonCode)
                || "no-adjacent-claim".equals(reasonCode)
                || "operation-denied".equals(reasonCode)) {
            return ReasonMessageMapper.component(
                    cfg,
                    "city.section.gui.errors.",
                    reasonCode
            );
        }
        return cfg.msg("city.section.gui.errors.generic",
                "<red>Operazione non disponibile su chunk {x},{z} ({reason}).</red>",
                Placeholder.unparsed("x", Integer.toString(chunkX)),
                Placeholder.unparsed("z", Integer.toString(chunkZ)),
                Placeholder.unparsed("reason", reasonCode));
    }

    private ItemStack navHead(String texture, String path, String fallback) {
        return GuiItemFactory.customHead(texture, cfg.msg(path, fallback));
    }

    private ItemStack legendHead(String texture, String path, String fallback) {
        return navHead(texture, path, fallback);
    }

    private int chunkDistance(int fromX, int fromZ, int toX, int toZ) {
        return Math.abs(fromX - toX) + Math.abs(fromZ - toZ);
    }

    private boolean hasAdjacentOwnClaim(World world, int chunkX, int chunkZ, int townId) {
        int[][] offsets = {
                {1, 0},
                {-1, 0},
                {0, 1},
                {0, -1}
        };
        for (int[] offset : offsets) {
            Optional<TownClaim> adjacent = huskTownsApiHook.getClaimAt(world.getChunkAt(chunkX + offset[0], chunkZ + offset[1]));
            if (adjacent.isPresent() && adjacent.get().town().getId() == townId) {
                return true;
            }
        }
        return false;
    }

    private Location chunkAnchor(Player player, World world, int chunkX, int chunkZ) {
        double x = (chunkX << 4) + 8.5D;
        double z = (chunkZ << 4) + 8.5D;
        double y = Math.max(world.getMinHeight() + 1, Math.min(player.getLocation().getY(), world.getMaxHeight() - 1));
        return new Location(world, x, y, z);
    }

    private void scheduleRefresh(Player player, CitySectionMapSession session) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            CitySectionMapSession current = sessionsByViewer.get(player.getUniqueId());
            if (current == null || !current.sessionId().equals(session.sessionId())) {
                return;
            }
            openMap(player, current);
        }, 8L);
    }

    private static final class MapHolder implements InventoryHolder {
        private final UUID sessionId;
        private Inventory inventory;

        private MapHolder(UUID sessionId) {
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
}
