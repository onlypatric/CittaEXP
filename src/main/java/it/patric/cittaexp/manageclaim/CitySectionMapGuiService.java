package it.patric.cittaexp.manageclaim;

import it.patric.cittaexp.integration.huskclaims.HuskClaimsChunkGuard;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.integration.husktowns.HuskTownsClaimWorldSanityService;
import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.text.UiLoreStyle;
import it.patric.cittaexp.utils.DialogConfirmHelper;
import it.patric.cittaexp.utils.ExceptionUtils;
import it.patric.cittaexp.utils.GuiHeadTextures;
import it.patric.cittaexp.utils.GuiItemFactory;
import it.patric.cittaexp.utils.GuiNavigationUtils;
import it.patric.cittaexp.utils.PluginConfigUtils;
import it.patric.cittaexp.utils.ReasonMessageMapper;
import it.patric.cittaexp.utils.TexturedGuiSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.william278.husktowns.claim.Claim;
import net.william278.husktowns.claim.TownClaim;
import net.william278.husktowns.town.Member;
import net.william278.husktowns.town.Privilege;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
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
    private static final int GUI_SIZE = 54;
    private static final int MAX_RANGE = 8;
    private static final String SECTION_FONT_IMAGE_ID = "cittaexp_gui:territori_claim_bg";
    private static final String TRANSPARENT_BUTTON_ID = "cittaexp_gui:transparent_button";
    private static final int SLOT_UP = 45;
    private static final int SLOT_LEFT = 46;
    private static final int SLOT_CENTER = 47;
    private static final int SLOT_RIGHT = 48;
    private static final int SLOT_DOWN = 49;
    private static final int SLOT_MODE = 50;
    private static final int SLOT_BACK_A = 51;
    private static final int SLOT_BACK_B = 52;
    private static final int SLOT_BACK_C = 53;

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final HuskClaimsChunkGuard huskClaimsChunkGuard;
    private final CityLevelService cityLevelService;
    private final HuskTownsClaimWorldSanityService claimWorldSanityService;
    private final ClaimBorderOverlayService claimBorderOverlayService;
    private final PluginConfigUtils cfg;
    private final Map<UUID, CitySectionMapSession> sessionsByViewer = new ConcurrentHashMap<>();
    private Consumer<Player> claimSettingsOpener;

    public CitySectionMapGuiService(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            HuskClaimsChunkGuard huskClaimsChunkGuard,
            CityLevelService cityLevelService,
            HuskTownsClaimWorldSanityService claimWorldSanityService,
            ClaimBorderOverlayService claimBorderOverlayService
    ) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.huskClaimsChunkGuard = huskClaimsChunkGuard;
        this.cityLevelService = cityLevelService;
        this.claimWorldSanityService = claimWorldSanityService;
        this.claimBorderOverlayService = claimBorderOverlayService;
        this.cfg = new PluginConfigUtils(plugin);
    }

    public void setClaimSettingsOpener(Consumer<Player> claimSettingsOpener) {
        this.claimSettingsOpener = claimSettingsOpener;
    }

    public void openForOwnTown(Player player) {
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty()) {
            player.sendMessage(cfg.msg("city.section.gui.errors.no_town",
                    "<red>Devi essere membro attivo di una citta.</red>"));
            return;
        }
        int[] initialCenter = initialCenterChunk(player, member.get().town().getId());
        claimBorderOverlayService.clearSource(player.getUniqueId(), ClaimBorderOverlayService.Source.SECTION_MAP);
        CitySectionMapSession session = new CitySectionMapSession(
                UUID.randomUUID(),
                player.getUniqueId(),
                member.get().town().getId(),
                member.get().town().getName(),
                player.getWorld().getName(),
                initialCenter[0],
                initialCenter[1]
        );
        sessionsByViewer.put(player.getUniqueId(), session);
        openMap(player, session);
    }

    public void clearAllSessions() {
        for (UUID viewerId : sessionsByViewer.keySet()) {
            claimBorderOverlayService.clearSource(viewerId, ClaimBorderOverlayService.Source.SECTION_MAP);
        }
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
            toggleClaimBorderView(player, session);
            openMap(player, session);
            return;
        }
        if (slot == SLOT_MODE) {
            if (claimSettingsOpener != null) {
                claimSettingsOpener.accept(player);
            } else {
                player.sendMessage(cfg.msg("city.section.gui.errors.claim_settings_unavailable",
                        "<red>Regole claim non disponibili al momento.</red>"));
            }
            return;
        }
        if (slot == SLOT_BACK_A || slot == SLOT_BACK_B || slot == SLOT_BACK_C) {
            GuiNavigationUtils.openCityHub(plugin, player);
            return;
        }
        if (slot < 0 || slot >= MAP_SLOTS) {
            return;
        }
        World world = Bukkit.getWorld(session.worldName());
        if (world == null) {
            player.sendMessage(cfg.msg("city.section.gui.errors.world_unavailable",
                    "<red>Mondo non disponibile per la mappa claim.</red>"));
            player.closeInventory();
            return;
        }
        if (slot == 0) {
            teleportToCenteredClaim(player, session, world);
            return;
        }

        int row = slot / MAP_WIDTH;
        int col = slot % MAP_WIDTH;
        int chunkX = session.centerChunkX() + (col - 4);
        int chunkZ = session.centerChunkZ() + (row - 2);
        handleMapCellClick(player, session, world, chunkX, chunkZ, event);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        // No-op: non rimuoviamo qui la sessione per consentire refresh/reopen sicuri.
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        claimBorderOverlayService.clearSource(event.getPlayer().getUniqueId(), ClaimBorderOverlayService.Source.SECTION_MAP);
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
        SectionOpenResult openResult = createInventory(holder, session);
        Inventory inventory = openResult.inventory();
        holder.bind(inventory);

        for (int slot = 0; slot < MAP_SLOTS; slot++) {
            int row = slot / MAP_WIDTH;
            int col = slot % MAP_WIDTH;
            int chunkX = session.centerChunkX() + (col - 4);
            int chunkZ = session.centerChunkZ() + (row - 2);
            CitySectionMapCell cell = resolveCell(player, session.townId(), world, chunkX, chunkZ, true);
            inventory.setItem(slot, toItem(cell));
        }
        inventory.setItem(0, teleportButton(session));

        ItemStack transparentBase = transparentButtonBase();
        inventory.setItem(SLOT_UP, overlayButton(
                transparentBase,
                "city.section.gui.nav.up",
                "<yellow>Su</yellow>",
                sectionLore(
                        "<dark_gray>╭────────────╮</dark_gray>",
                        "<yellow>✦</yellow> <white>Rotta a nord</white>",
                        "<gray>Scorri la griglia verso i chunk sopra il centro attuale.</gray>",
                        "<green>➜ Clicca per salire di una riga.</green>",
                        "<dark_gray>╰────────────╯</dark_gray>"
                )
        ));
        inventory.setItem(SLOT_LEFT, overlayButton(
                transparentBase,
                "city.section.gui.nav.left",
                "<yellow>Sinistra</yellow>",
                sectionLore(
                        "<dark_gray>╭────────────╮</dark_gray>",
                        "<yellow>✦</yellow> <white>Rotta a ovest</white>",
                        "<gray>Sposta la mappa verso i confini che si trovano a sinistra del presidio corrente.</gray>",
                        "<green>➜ Clicca per muovere il focus a ovest.</green>",
                        "<dark_gray>╰────────────╯</dark_gray>"
                )
        ));
        inventory.setItem(SLOT_CENTER, overlayButton(
                transparentBase,
                "city.section.gui.nav.center",
                session.claimBorderViewEnabled()
                        ? "<gold>Nascondi bordi claim</gold>"
                        : "<aqua>Visualizza bordi claim</aqua>",
                sectionLore(
                        "<dark_gray>╭────────────╮</dark_gray>",
                        session.claimBorderViewEnabled()
                                ? "<gold>✦</gold> <white>Ritira i sigilli del confine</white>"
                                : "<aqua>✦</aqua> <white>Leva i sigilli del confine</white>",
                        session.claimBorderViewEnabled()
                                ? "<gray>Le colonne luminose che marcano i bordi reali dei claim verranno dissolte.</gray>"
                                : "<gray>Mostra nel mondo i bordi effettivi dei claim cittadini visibili in questa carta.</gray>",
                        session.claimBorderViewEnabled()
                                ? "<green>➜ Clicca per spegnere la vista dei confini.</green>"
                                : "<green>➜ Clicca per evocare i bordi claim nel mondo.</green>",
                        "<dark_gray>╰────────────╯</dark_gray>"
                )
        ));
        inventory.setItem(SLOT_RIGHT, overlayButton(
                transparentBase,
                "city.section.gui.nav.right",
                "<yellow>Destra</yellow>",
                sectionLore(
                        "<dark_gray>╭────────────╮</dark_gray>",
                        "<yellow>✦</yellow> <white>Rotta a est</white>",
                        "<gray>Esplora i chunk che si aprono lungo il fianco orientale del tuo territorio.</gray>",
                        "<green>➜ Clicca per muovere il focus a est.</green>",
                        "<dark_gray>╰────────────╯</dark_gray>"
                )
        ));
        inventory.setItem(SLOT_DOWN, overlayButton(
                transparentBase,
                "city.section.gui.nav.down",
                "<yellow>Giu</yellow>",
                sectionLore(
                        "<dark_gray>╭────────────╮</dark_gray>",
                        "<yellow>✦</yellow> <white>Rotta a sud</white>",
                        "<gray>Scorri la griglia verso i chunk che si trovano sotto il centro attuale.</gray>",
                        "<green>➜ Clicca per scendere di una riga.</green>",
                        "<dark_gray>╰────────────╯</dark_gray>"
                )
        ));
        inventory.setItem(SLOT_MODE, overlayButton(
                transparentBase,
                "city.section.gui.mode.current",
                "<gold>Regole claim</gold>",
                sectionLore(
                        "<dark_gray>╭────────────╮</dark_gray>",
                        "<gold>✦</gold> <white>Regole del territorio</white>",
                        "<gray>Apri il pannello dedicato ai permessi e al comportamento dei claim della citta.</gray>",
                        "<green>➜ Clicca per gestire le regole claim.</green>",
                        "<dark_gray>╰────────────╯</dark_gray>"
                )
        ));
        ItemStack backButton = overlayButton(
                transparentBase,
                "city.section.gui.nav.back",
                "<yellow>Indietro</yellow>",
                sectionLore(
                        "<dark_gray>╭────────────╮</dark_gray>",
                        "<yellow>✦</yellow> <white>Ritorno al comando</white>",
                        "<gray>Lascia la mappa territori e torna al pannello principale della citta.</gray>",
                        "<green>➜ Clicca per tornare al City Hub.</green>",
                        "<dark_gray>╰────────────╯</dark_gray>"
                )
        );
        inventory.setItem(SLOT_BACK_A, backButton.clone());
        inventory.setItem(SLOT_BACK_B, backButton.clone());
        inventory.setItem(SLOT_BACK_C, backButton.clone());

        if (openResult.wrapper() != null) {
            openResult.wrapper().showInventory(player);
        } else {
            player.openInventory(inventory);
        }
        if (session.claimBorderViewEnabled()) {
            claimBorderOverlayService.showSectionMapBorders(
                    player,
                    session.worldName(),
                    session.townId(),
                    session.centerChunkX(),
                    session.centerChunkZ()
            );
        } else {
            claimBorderOverlayService.clearSource(player.getUniqueId(), ClaimBorderOverlayService.Source.SECTION_MAP);
        }
    }

    private SectionOpenResult createInventory(MapHolder holder, CitySectionMapSession session) {
        TexturedGuiSupport.OpenResult result = TexturedGuiSupport.createInventory(
                plugin,
                holder,
                holder::bind,
                GUI_SIZE,
                cfg.msg("city.section.gui.title_mode",
                        "<gold>Mappa Claim</gold> <gray>{town}</gray> <dark_gray>[</dark_gray><white>{x}</white><gray>,</gray><white>{z}</white><dark_gray>]</dark_gray>",
                        Placeholder.unparsed("town", session.townName()),
                        Placeholder.unparsed("x", Integer.toString(session.centerChunkX())),
                        Placeholder.unparsed("z", Integer.toString(session.centerChunkZ()))),
                cfg.missingKeyFallback("city.section.itemsadder.title_plain", "Territori"),
                cfg.cfgInt("city.hub.itemsadder.title_offset", 0),
                cfg.cfgInt("city.hub.itemsadder.texture_offset", -8),
                SECTION_FONT_IMAGE_ID,
                "section-map"
        );
        return new SectionOpenResult(result.inventory(), result.texturedWrapper());
    }

    private void handleMapCellClick(
            Player player,
            CitySectionMapSession session,
            World world,
            int chunkX,
            int chunkZ,
            InventoryClickEvent event
    ) {
        if (event.isRightClick()) {
            handleTypeMode(player, session, world, chunkX, chunkZ);
            return;
        }
        if (event.isLeftClick()) {
            handleClaimMode(player, session, world, chunkX, chunkZ);
        }
    }

    private void handleClaimMode(Player player, CitySectionMapSession session, World world, int chunkX, int chunkZ) {
        if (!ensureClaimWritesHealthy(player)) {
            return;
        }
        CitySectionMapCell cell = resolveCell(player, session.townId(), world, chunkX, chunkZ, true);
        switch (cell.state()) {
            case WILDERNESS_CLAIMABLE -> claimChunk(player, session.townId(), world, chunkX, chunkZ, session, true);
            case OWN_CLAIM -> unclaimChunk(player, session.townId(), world, chunkX, chunkZ, cell, session, true);
            case BLOCKED_HUSKCLAIMS -> player.sendMessage(cfg.msg("city.section.gui.errors.huskclaims_blocked",
                    "<red>Chunk bloccato.</red>"));
            case OTHER_CLAIM -> player.sendMessage(cfg.msg("city.section.gui.errors.claimed_other",
                    "<red>Chunk gia claimato da:</red> <yellow>{owner}</yellow>",
                    Placeholder.unparsed("owner", cell.ownerName())));
            case BLOCKED_POLICY -> player.sendMessage(reasonMessage(cell.reasonCode(), chunkX, chunkZ));
        }
    }

    private void handleTypeMode(Player player, CitySectionMapSession session, World world, int chunkX, int chunkZ) {
        if (!ensureClaimWritesHealthy(player)) {
            return;
        }
        CitySectionMapCell cell = resolveCell(player, session.townId(), world, chunkX, chunkZ, true);
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

    private void handleInfoMode(Player player, CitySectionMapSession session, World world, int chunkX, int chunkZ) {
        CitySectionMapCell cell = resolveCell(player, session.townId(), world, chunkX, chunkZ, true);
        player.sendMessage(cfg.msg(
                "city.section.gui.info",
                "<gray>Chunk</gray> <white>{x},{z}</white> <gray>| Stato:</gray> <white>{state}</white> <gray>| Owner:</gray> <white>{owner}</white> <gray>| Tipo:</gray> <white>{type}</white> <gray>| Reason:</gray> <white>{reason}</white>",
                Placeholder.unparsed("x", Integer.toString(chunkX)),
                Placeholder.unparsed("z", Integer.toString(chunkZ)),
                Placeholder.unparsed("state", stateLabel(cell.state())),
                Placeholder.unparsed("owner", cell.ownerName()),
                Placeholder.unparsed("type", cell.claimType() == null ? "-" : cell.claimType().name()),
                Placeholder.unparsed("reason", reasonLabel(cell.reasonCode()))
        ));
    }

    public void handleClaimToolTarget(Player player, World world, int chunkX, int chunkZ) {
        if (!ensureClaimWritesHealthy(player)) {
            return;
        }
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty()) {
            player.sendMessage(cfg.msg("city.section.gui.errors.no_town",
                    "<red>Devi essere membro attivo di una citta.</red>"));
            return;
        }
        int townId = member.get().town().getId();
        CitySectionMapCell cell = resolveCell(player, townId, world, chunkX, chunkZ, false);
        switch (cell.state()) {
            case WILDERNESS_CLAIMABLE -> claimChunk(player, townId, world, chunkX, chunkZ, sessionsByViewer.get(player.getUniqueId()), false);
            case OWN_CLAIM -> openUnclaimConfirmation(player, world, chunkX, chunkZ, townId, cell);
            case BLOCKED_HUSKCLAIMS -> player.sendMessage(cfg.msg("city.section.gui.errors.huskclaims_blocked",
                    "<red>Chunk bloccato.</red>"));
            case OTHER_CLAIM -> player.sendMessage(cfg.msg("city.section.gui.errors.claimed_other",
                    "<red>Chunk gia claimato da:</red> <yellow>{owner}</yellow>",
                    Placeholder.unparsed("owner", cell.ownerName())));
            case BLOCKED_POLICY -> player.sendMessage(reasonMessage(cell.reasonCode(), chunkX, chunkZ));
        }
    }

    public Optional<CitySectionMapCell> previewClaimToolTarget(Player player, World world, int chunkX, int chunkZ) {
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(resolveCell(player, member.get().town().getId(), world, chunkX, chunkZ, false));
    }

    private void claimChunk(
            Player player,
            int townId,
            World world,
            int chunkX,
            int chunkZ,
            CitySectionMapSession session,
            boolean enforceRange
    ) {
        String reason = claimBlockReason(player, townId, world, chunkX, chunkZ, enforceRange);
        if (reason != null) {
            player.sendMessage(reasonMessage(reason, chunkX, chunkZ));
            return;
        }
        int claimCountBefore = huskTownsApiHook.getUserTown(player)
                .filter(member -> member.town().getId() == townId)
                .map(member -> member.town().getClaimCount())
                .orElse(0);
        try {
            huskTownsApiHook.createClaimAt(player, world.getChunkAt(chunkX, chunkZ), world, false);
            player.sendMessage(cfg.msg("city.section.gui.actions.claim_success",
                    "<green>Claim richiesto su chunk</green> <white>{x}</white><gray>,</gray><white>{z}</white><green>.</green>",
                    Placeholder.unparsed("x", Integer.toString(chunkX)),
                    Placeholder.unparsed("z", Integer.toString(chunkZ))));
            if (claimCountBefore == 0) {
                try {
                    huskTownsApiHook.setTownSpawn(player, townId, safeChunkCenterLocation(world, chunkX, chunkZ));
                    cityLevelService.enforceSpawnPrivacyPolicy(townId);
                    player.sendMessage(cfg.msg(
                            "city.section.gui.actions.first_claim_warp_set",
                            "<green>Warp citta impostato automaticamente sul primo claim.</green>"
                    ));
                } catch (RuntimeException warpException) {
                    player.sendMessage(cfg.msg(
                            "city.section.gui.actions.first_claim_warp_failed",
                            "<yellow>Il claim e riuscito, ma non sono riuscito a impostare automaticamente il warp citta.</yellow>"
                    ));
                    plugin.getLogger().warning("[section-map] first-claim warp set failed player=" + player.getUniqueId()
                            + " town=" + townId
                            + " chunk=" + chunkX + "," + chunkZ
                            + " reason=" + warpException.getMessage());
                }
            }
        } catch (RuntimeException ex) {
            player.sendMessage(cfg.msg("city.section.gui.actions.claim_failed",
                    "<red>Claim fallito:</red> <gray>{reason}</gray>",
                    Placeholder.unparsed("reason", ExceptionUtils.safeMessage(ex, "errore sconosciuto"))));
            plugin.getLogger().warning("[section-map] claim failed player=" + player.getUniqueId()
                    + " chunk=" + chunkX + "," + chunkZ + " reason=" + ex.getMessage());
        }
        scheduleRefresh(player, session);
    }

    private boolean ensureClaimWritesHealthy(Player player) {
        if (claimWorldSanityService == null || claimWorldSanityService.isClaimWritesSafe()) {
            return true;
        }
        HuskTownsClaimWorldSanityService.ClaimWorldDiagnostics diagnostics = claimWorldSanityService.latestDiagnostics();
        player.sendMessage(cfg.msg(
                "city.section.gui.errors.claim_runtime_degraded",
                "<red>Operazioni claim sospese in modalita sicura.</red> <gray>Il runtime territori non e coerente.</gray> <yellow>Avvisa lo staff.</yellow>"
        ));
        if (diagnostics.errorMessage() != null && !diagnostics.errorMessage().isBlank()) {
            player.sendMessage(cfg.msg(
                    "city.section.gui.errors.claim_runtime_degraded_reason",
                    "<gray>Dettaglio:</gray> <white>{reason}</white>",
                    Placeholder.unparsed("reason", diagnostics.errorMessage())
            ));
            return false;
        }
        if (diagnostics.duplicatesFound()) {
            player.sendMessage(cfg.msg(
                    "city.section.gui.errors.claim_runtime_degraded_reason",
                    "<gray>Dettaglio:</gray> <white>{reason}</white>",
                    Placeholder.unparsed("reason", "dati dei confini duplicati rilevati nel database")
            ));
            return false;
        }
        if (diagnostics.runtimeClaimMismatch()) {
            player.sendMessage(cfg.msg(
                    "city.section.gui.errors.claim_runtime_degraded_reason",
                    "<gray>Dettaglio:</gray> <white>{reason}</white>",
                    Placeholder.unparsed("reason",
                            "claim runtime=" + diagnostics.runtimeTownClaims()
                                    + " ma database=" + diagnostics.databaseTownClaims())
            ));
        }
        return false;
    }

    private void unclaimChunk(
            Player player,
            int townId,
            World world,
            int chunkX,
            int chunkZ,
            CitySectionMapCell cell,
            CitySectionMapSession session,
            boolean enforceRange
    ) {
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty() || member.get().town().getId() != townId) {
            player.sendMessage(cfg.msg("city.section.gui.errors.no_town", "<red>Devi essere membro attivo di una citta.</red>"));
            return;
        }
        if (!huskTownsApiHook.hasPrivilege(player, Privilege.UNCLAIM)) {
            player.sendMessage(cfg.msg("city.section.gui.errors.no_unclaim_privilege",
                    "<red>Permessi insufficienti per rimuovere claim.</red>"));
            return;
        }
        if (enforceRange && chunkDistance(player.getChunk().getX(), player.getChunk().getZ(), chunkX, chunkZ) > MAX_RANGE) {
            player.sendMessage(reasonMessage("out-of-range", chunkX, chunkZ));
            return;
        }
        Optional<TownClaim> claim = huskTownsApiHook.getClaimAt(world.getChunkAt(chunkX, chunkZ));
        if (claim.isEmpty() || claim.get().town().getId() != townId) {
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
            int townId,
            World world,
            int chunkX,
            int chunkZ,
            boolean enforceRange
    ) {
        if (huskClaimsChunkGuard.isClaimedChunk(world, chunkX, chunkZ)) {
            return new CitySectionMapCell(
                    chunkX,
                    chunkZ,
                    CitySectionMapCell.State.BLOCKED_HUSKCLAIMS,
                    "-",
                    null,
                    "huskclaims-blocked"
            );
        }

        Optional<TownClaim> claim = huskTownsApiHook.getClaimAt(world.getChunkAt(chunkX, chunkZ));
        if (claim.isPresent()) {
            TownClaim townClaim = claim.get();
            boolean own = townClaim.town().getId() == townId;
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

        String reason = claimBlockReason(player, townId, world, chunkX, chunkZ, enforceRange);
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
            int townId,
            World world,
            int chunkX,
            int chunkZ,
            boolean enforceRange
    ) {
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty() || member.get().town().getId() != townId) {
            return "no-town";
        }
        if (!huskTownsApiHook.hasPrivilege(player, Privilege.CLAIM)) {
            return "no-claim-privilege";
        }
        if (enforceRange && chunkDistance(player.getChunk().getX(), player.getChunk().getZ(), chunkX, chunkZ) > MAX_RANGE) {
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
        List<Component> lore = cellLore(cell);
        if (texture != null) {
            return GuiItemFactory.customHead(texture, display, lore);
        }
        return GuiItemFactory.item(Material.RED_TERRACOTTA, display, lore);
    }

    private String stateLabel(CitySectionMapCell.State state) {
        return switch (state) {
            case OWN_CLAIM -> "Tuo confine";
            case OTHER_CLAIM -> "Dominio altrui";
            case WILDERNESS_CLAIMABLE -> "Frontiera contendibile";
            case BLOCKED_HUSKCLAIMS -> "Bloccato";
            case BLOCKED_POLICY -> "Bloccato";
        };
    }

    private String reasonLabel(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            return "-";
        }
        if ("huskclaims-blocked".equals(reasonCode) || "operation-denied".equals(reasonCode)) {
            return "bloccato";
        }
        return reasonCode.replace('-', '_');
    }

    private String modeLabel(CitySectionMapSession.MapMode mode) {
        return switch (mode) {
            case CLAIM -> "Confini";
            case TYPE -> "Suolo";
            case INFO -> "Lettura";
        };
    }

    private String modeActionLabel(CitySectionMapSession.MapMode mode) {
        return switch (mode) {
            case CLAIM -> "Postura Confini: clicca per levare o ritirare un confine";
            case TYPE -> "Postura Suolo: clicca per ciclare tra Cuore, Campo e Lotto";
            case INFO -> "Postura Lettura: clicca per leggere i dettagli del settore in chat";
        };
    }

    private ItemStack modeItem(CitySectionMapSession.MapMode mode) {
        return GuiItemFactory.customHead(GuiHeadTextures.ACTIONS, cfg.msg(
                "city.section.gui.mode.current",
                "<gold>Postura:</gold> <white>{mode}</white>",
                Placeholder.unparsed("mode", modeLabel(mode))
        ), modeLore(mode));
    }

    private Component reasonMessage(String reasonCode, int chunkX, int chunkZ) {
        if ("out-of-range".equals(reasonCode)) {
            return cfg.msg("city.section.gui.errors.out_of_range",
                    "<red>Settore troppo distante. Max {range} settori.</red>",
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
                "<red>Operazione non disponibile sul settore {x},{z} ({reason}).</red>",
                Placeholder.unparsed("x", Integer.toString(chunkX)),
                Placeholder.unparsed("z", Integer.toString(chunkZ)),
                Placeholder.unparsed("reason", reasonCode));
    }

    private int chunkDistance(int fromX, int fromZ, int toX, int toZ) {
        return Math.abs(fromX - toX) + Math.abs(fromZ - toZ);
    }

    private int[] initialCenterChunk(Player player, int townId) {
        List<TownClaim> ownClaims = huskTownsApiHook.getTownClaims(player.getWorld(), townId);
        if (ownClaims.isEmpty()) {
            return new int[]{player.getChunk().getX(), player.getChunk().getZ()};
        }
        long totalX = 0L;
        long totalZ = 0L;
        for (TownClaim claim : ownClaims) {
            totalX += claim.claim().getChunk().getX();
            totalZ += claim.claim().getChunk().getZ();
        }
        return new int[]{
                Math.toIntExact(Math.round((double) totalX / ownClaims.size())),
                Math.toIntExact(Math.round((double) totalZ / ownClaims.size()))
        };
    }

    private ItemStack teleportButton(CitySectionMapSession session) {
        return GuiItemFactory.item(
                Material.ENDER_PEARL,
                cfg.msg(
                        "city.section.gui.nav.teleport_centered_claim",
                        "<aqua>Teletrasporto claim</aqua>"
                ),
                sectionLore(
                        "<dark_gray>╭────────────╮</dark_gray>",
                        "<aqua>✦</aqua> <white>Vai al claim al centro della mappa</white>",
                        "<gray>Ti porta al centro del chunk</gray> <white>"
                                + session.centerChunkX()
                                + "<gray>,</gray>"
                                + session.centerChunkZ()
                                + "</white><gray> se appartiene alla tua citta.</gray>",
                        "<gray>Se quel chunk non appartiene alla tua citta, il teleport viene bloccato.</gray>",
                        "<green>➜ Clicca per teletrasportarti.</green>",
                        "<dark_gray>╰────────────╯</dark_gray>"
                )
        );
    }

    private void teleportToCenteredClaim(Player player, CitySectionMapSession session, World world) {
        Optional<TownClaim> claim = huskTownsApiHook.getClaimAt(world.getChunkAt(session.centerChunkX(), session.centerChunkZ()));
        if (claim.isEmpty() || claim.get().town().getId() != session.townId()) {
            player.sendMessage(cfg.msg(
                    "city.section.gui.errors.center_not_own_claim",
                    "<red>Il claim al centro della mappa non appartiene alla tua citta.</red>"
            ));
            return;
        }
        Location target = safeChunkCenterLocation(world, session.centerChunkX(), session.centerChunkZ());
        if (!player.teleport(target)) {
            player.sendMessage(cfg.msg(
                    "city.section.gui.errors.center_claim_teleport_failed",
                    "<red>Non sono riuscito a teletrasportarti al centro del claim.</red>"
            ));
            return;
        }
        player.sendMessage(cfg.msg(
                "city.section.gui.actions.center_claim_teleported",
                "<green>Ti ho teletrasportato al centro del claim</green> <white>{x}</white><gray>,</gray><white>{z}</white><green>.</green>",
                Placeholder.unparsed("x", Integer.toString(session.centerChunkX())),
                Placeholder.unparsed("z", Integer.toString(session.centerChunkZ()))
        ));
    }

    private void toggleClaimBorderView(Player player, CitySectionMapSession session) {
        boolean enabled = !session.claimBorderViewEnabled();
        session.claimBorderViewEnabled(enabled);
        if (!enabled) {
            claimBorderOverlayService.clearSource(player.getUniqueId(), ClaimBorderOverlayService.Source.SECTION_MAP);
            player.sendMessage(cfg.msg(
                    "city.section.gui.actions.border_view_disabled",
                    "<gray>I sigilli luminosi dei confini sono stati ritirati.</gray>"
            ));
            return;
        }
        claimBorderOverlayService.showSectionMapBorders(
                player,
                session.worldName(),
                session.townId(),
                session.centerChunkX(),
                session.centerChunkZ()
        );
        player.sendMessage(cfg.msg(
                "city.section.gui.actions.border_view_enabled",
                "<yellow>I bordi claim visibili nella carta sono stati proiettati nel mondo.</yellow>"
        ));
    }

    private void openUnclaimConfirmation(
            Player player,
            World world,
            int chunkX,
            int chunkZ,
            int townId,
            CitySectionMapCell cell
    ) {
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty() || member.get().town().getId() != townId) {
            player.sendMessage(cfg.msg("city.section.gui.errors.no_town",
                    "<red>Devi essere membro attivo di una citta.</red>"));
            return;
        }
        if (!huskTownsApiHook.hasPrivilege(player, Privilege.UNCLAIM)) {
            player.sendMessage(cfg.msg("city.section.gui.errors.no_unclaim_privilege",
                    "<red>Permessi insufficienti per rimuovere claim.</red>"));
            return;
        }
        Optional<TownClaim> claim = huskTownsApiHook.getClaimAt(world.getChunkAt(chunkX, chunkZ));
        if (claim.isEmpty() || claim.get().town().getId() != townId) {
            player.sendMessage(cfg.msg("city.section.gui.errors.claim_missing_runtime",
                    "<red>Claim non trovato o non appartenente alla tua citta.</red>"));
            return;
        }
        boolean spawnProtected = member.get().town().getSpawn().isPresent()
                && claim.get().contains(member.get().town().getSpawn().get().getPosition())
                && !huskTownsApiHook.hasPrivilege(player, Privilege.SET_SPAWN);
        StringBuilder body = new StringBuilder()
                .append("<gray>Citta:</gray> <white>").append(cell.ownerName()).append("</white><newline>")
                .append("<gray>Chunk:</gray> <white>").append(chunkX).append("</white><gray>,</gray><white>")
                .append(chunkZ).append("</white><newline>")
                .append("<yellow>Questo chunk tornera wilderness se confermi.</yellow>");
        if (spawnProtected) {
            body.append("<newline><red>Attenzione: questo chunk contiene lo spawn della citta.</red>");
        }
        DialogConfirmHelper.open(
                plugin,
                player,
                cfg.msg("city.section.gui.dialog.unclaim_confirm.title",
                        "<gold>Rimuovere questo claim?</gold>"),
                cfg.msg("city.section.gui.dialog.unclaim_confirm.body",
                        body.toString()),
                cfg.msg("city.section.gui.dialog.unclaim_confirm.confirm",
                        "<green>Conferma</green>"),
                cfg.msg("city.section.gui.dialog.unclaim_confirm.cancel",
                        "<red>Annulla</red>"),
                confirmed -> {
                    CitySectionMapSession current = sessionsByViewer.get(confirmed.getUniqueId());
                    unclaimChunk(confirmed, townId, world, chunkX, chunkZ, cell, current, false);
                },
                cfg.msg("city.section.gui.dialog.unclaim_confirm.unavailable",
                        "<red>Dialog non disponibile.</red>")
        );
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

    private Location safeChunkCenterLocation(World world, int chunkX, int chunkZ) {
        int blockX = (chunkX << 4) + 8;
        int blockZ = (chunkZ << 4) + 8;
        int topY = world.getHighestBlockYAt(blockX, blockZ, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        double safeY = Math.max(world.getMinHeight() + 1, Math.min(topY + 1.0D, world.getMaxHeight() - 1));
        return new Location(world, blockX + 0.5D, safeY, blockZ + 0.5D);
    }

    private void scheduleRefresh(Player player, CitySectionMapSession session) {
        if (session == null) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            CitySectionMapSession current = sessionsByViewer.get(player.getUniqueId());
            if (current == null || !current.sessionId().equals(session.sessionId())) {
                return;
            }
            openMap(player, current);
        }, 8L);
    }

    private ItemStack transparentButtonBase() {
        return TexturedGuiSupport.transparentButtonBase(plugin, TRANSPARENT_BUTTON_ID);
    }

    private ItemStack overlayButton(ItemStack transparentBase, String path, String fallback, List<Component> lore) {
        return TexturedGuiSupport.overlayButton(
                transparentBase,
                noItalic(cfg.msg(path, fallback)),
                lore == null ? List.of() : lore.stream().map(this::noItalic).toList(),
                new ItemStack(Material.PAPER)
        );
    }

    private List<Component> cellLore(CitySectionMapCell cell) {
        return UiLoreStyle.renderComponents(
                UiLoreStyle.line(UiLoreStyle.LineType.STATUS, stateLabel(cell.state())),
                UiLoreStyle.line(UiLoreStyle.LineType.INFO, "Presidio: " + safeText(cell.ownerName(), "-")),
                List.of(
                        UiLoreStyle.line(UiLoreStyle.LineType.FOCUS, "Tipo chunk: " + (cell.claimType() == null ? "-" : cell.claimType().name())),
                        UiLoreStyle.line(UiLoreStyle.LineType.STATUS, "Stato: " + reasonLabel(cell.reasonCode())),
                        UiLoreStyle.line(UiLoreStyle.LineType.ACTION, "LMB: leva o ritira il confine del settore."),
                        UiLoreStyle.line(UiLoreStyle.LineType.ACTION, "RMB: cambia il tipo del settore se e tuo.")
                )
        ).stream().map(this::noItalic).toList();
    }

    private List<Component> modeLore(CitySectionMapSession.MapMode mode) {
        String next = modeLabel(mode.next());
        return UiLoreStyle.renderComponents(
                UiLoreStyle.line(UiLoreStyle.LineType.STATUS, "Modalita attuale: " + modeLabel(mode)),
                UiLoreStyle.line(UiLoreStyle.LineType.INFO, modeDescription(mode)),
                List.of(
                        UiLoreStyle.line(UiLoreStyle.LineType.FOCUS, "Modalita dopo il click: " + next),
                        UiLoreStyle.line(UiLoreStyle.LineType.ACTION, "Clicca per cambiare modalita di presidio.")
                )
        ).stream().map(this::noItalic).toList();
    }

    private String modeDescription(CitySectionMapSession.MapMode mode) {
        return switch (mode) {
            case CLAIM -> "La citta spinge i confini: clicca per levare o ritirare i settori sotto il tuo sigillo.";
            case TYPE -> "La citta organizza il territorio: clicca per ciclare tra Cuore, Campo e Lotto.";
            case INFO -> "La citta osserva il fronte: clicca per leggere i dettagli del settore in chat.";
        };
    }

    private List<Component> sectionLore(String... lines) {
        return UiLoreStyle.renderLegacyComponents(lines == null ? List.of() : List.of(lines)).stream()
                .map(this::noItalic)
                .toList();
    }

    private Component noItalic(Component component) {
        return component == null ? Component.empty() : component.decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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

    private record SectionOpenResult(
            Inventory inventory,
            dev.lone.itemsadder.api.FontImages.TexturedInventoryWrapper wrapper
    ) {
    }
}
