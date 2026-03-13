package it.patric.cittaexp.claimsettings;

import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.levels.TownStage;
import it.patric.cittaexp.playersgui.CityPlayersSession;
import it.patric.cittaexp.utils.GuiHeadTextures;
import it.patric.cittaexp.utils.GuiItemFactory;
import it.patric.cittaexp.utils.GuiLayoutUtils;
import it.patric.cittaexp.utils.PluginConfigUtils;
import it.patric.cittaexp.utils.ReasonMessageMapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.william278.husktowns.claim.Claim;
import net.william278.husktowns.town.Town;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;

public final class CityClaimSettingsMenuService {

    public static final int GUI_SIZE = 27;
    public static final int SLOT_PRIVACY = 11;
    public static final int SLOT_CLAIM = 13;
    public static final int SLOT_FARM = 14;
    public static final int SLOT_PLOT = 15;
    public static final int SLOT_BACK = 18;
    public static final int SLOT_CLOSE = 26;

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final CityLevelService cityLevelService;
    private final PluginConfigUtils configUtils;
    private final ClaimRulesDialogService claimRulesDialogService;

    public CityClaimSettingsMenuService(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            CityLevelService cityLevelService,
            ClaimRulesDialogService claimRulesDialogService
    ) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.cityLevelService = cityLevelService;
        this.configUtils = new PluginConfigUtils(plugin);
        this.claimRulesDialogService = claimRulesDialogService;
    }

    public void clearViewerSession(UUID viewerId) {
        claimRulesDialogService.clearViewerSession(viewerId);
    }

    public void clearAllSessions() {
        claimRulesDialogService.clearAllSessions();
    }

    public boolean isClaimSettingsHolder(InventoryHolder holder) {
        return holder instanceof ClaimSettingsHolder;
    }

    public boolean isSessionHolder(InventoryHolder holder, UUID sessionId) {
        return holder instanceof ClaimSettingsHolder claimSettingsHolder
                && claimSettingsHolder.sessionId().equals(sessionId);
    }

    public void open(Player player, CityPlayersSession session) {
        if (session.staffView()) {
            player.sendMessage(configUtils.msg("city.players.gui.claim_settings.errors.staff_forbidden",
                    "<red>Questa azione non e disponibile in modalita staff.</red>"));
            return;
        }

        if (!cityLevelService.canToggleSpawnPrivacy(session.townId())) {
            cityLevelService.enforceSpawnPrivacyPolicy(session.townId());
        }

        ClaimSettingsHolder holder = new ClaimSettingsHolder(session.sessionId());
        Inventory inventory = Bukkit.createInventory(
                holder,
                GUI_SIZE,
                configUtils.msg("city.players.gui.claim_settings.title",
                        "<gold>Impostazioni claim</gold> <gray>{town}</gray>",
                        Placeholder.unparsed("town", session.townName()))
        );
        holder.bind(inventory);

        GuiLayoutUtils.fillAllExcept(
                inventory,
                GuiItemFactory.fillerPane(),
                SLOT_PRIVACY, SLOT_CLAIM, SLOT_FARM, SLOT_PLOT, SLOT_BACK, SLOT_CLOSE
        );

        inventory.setItem(SLOT_PRIVACY, privacyActionItem(session));
        inventory.setItem(SLOT_CLAIM, actionHead(
                GuiHeadTextures.CLAIM_SETTINGS,
                "city.players.gui.claim_settings.claim",
                "<white>Impostazioni CLAIM</white>"
        ));
        inventory.setItem(SLOT_FARM, actionHead(
                GuiHeadTextures.FARM_SETTINGS,
                "city.players.gui.claim_settings.farm",
                "<green>Impostazioni FARM</green>"
        ));
        inventory.setItem(SLOT_PLOT, actionHead(
                GuiHeadTextures.PLOT_SETTINGS,
                "city.players.gui.claim_settings.plot",
                "<aqua>Impostazioni PLOT</aqua>"
        ));
        inventory.setItem(SLOT_BACK, actionHead(
                GuiHeadTextures.ACTIONS,
                "city.players.gui.claim_settings.back",
                "<yellow>Indietro</yellow>"
        ));
        inventory.setItem(SLOT_CLOSE, actionHead(
                GuiHeadTextures.CLOSE,
                "city.players.gui.claim_settings.close",
                "<red>Chiudi</red>"
        ));

        player.openInventory(inventory);
    }

    public void handleClick(Player player, CityPlayersSession session, int slot, Runnable openActionsView) {
        if (slot == SLOT_BACK) {
            openActionsView.run();
            return;
        }
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_PRIVACY) {
            togglePrivacy(player, session);
            return;
        }
        if (slot == SLOT_CLAIM) {
            openClaimSettingsDialog(player, session, Claim.Type.CLAIM);
            return;
        }
        if (slot == SLOT_FARM) {
            openClaimSettingsDialog(player, session, Claim.Type.FARM);
            return;
        }
        if (slot == SLOT_PLOT) {
            openClaimSettingsDialog(player, session, Claim.Type.PLOT);
        }
    }

    private void togglePrivacy(Player player, CityPlayersSession session) {
        CityLevelService.SpawnPrivacyToggleResult result = cityLevelService.toggleSpawnPrivacy(player);
        if (!result.success()) {
            String mappedReason = ReasonMessageMapper.text(configUtils, "city.players.gui.privacy.reasons.", result.reason());
            player.sendMessage(configUtils.msg(
                    "city.players.gui.privacy.errors.failed",
                    "<red>Aggiornamento privacy non riuscito:</red> <gray>{reason}</gray>",
                    Placeholder.unparsed("reason", mappedReason)
            ));
            open(player, session);
            return;
        }
        player.sendMessage(configUtils.msg(
                "city.players.gui.privacy.toggle_success",
                "<green>Privacy warp aggiornata:</green> <white>{value}</white>",
                Placeholder.unparsed("value", Boolean.TRUE.equals(result.isPublic()) ? "public" : "private")
        ));
        open(player, session);
    }

    private void openClaimSettingsDialog(Player player, CityPlayersSession session, Claim.Type claimType) {
        claimRulesDialogService.open(player, session, claimType, () -> open(player, session));
    }

    private ItemStack privacyActionItem(CityPlayersSession session) {
        Optional<Town> town = huskTownsApiHook.getTownById(session.townId());
        TownStage stage = cityLevelService.statusForTown(session.townId(), false).stage();
        if (town.isEmpty() || town.get().getSpawn().isEmpty()) {
            return actionItem(
                    Material.GRAY_DYE,
                    "city.players.gui.claim_settings.privacy_no_spawn",
                    "<gray>Privacy warp (spawn non impostato)</gray>"
            );
        }

        boolean isPublic = town.get().getSpawn().get().isPublic();
        boolean canToggle = cityLevelService.canToggleSpawnPrivacy(session.townId());
        if (canToggle) {
            return actionItem(
                    isPublic ? Material.LIME_DYE : Material.ORANGE_DYE,
                    isPublic
                            ? "city.players.gui.claim_settings.privacy_public"
                            : "city.players.gui.claim_settings.privacy_private",
                    isPublic
                            ? "<green>Privacy warp: public</green>"
                            : "<gold>Privacy warp: private</gold>"
            );
        }

        return actionItem(
                Material.PAPER,
                "city.players.gui.claim_settings.privacy_locked",
                "<gray>Privacy warp bloccata su public da</gray> <yellow>{stage}</yellow>",
                Placeholder.unparsed("stage", stage.displayName())
        );
    }

    private ItemStack actionItem(
            Material material,
            String path,
            String fallback,
            net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... tags
    ) {
        return GuiItemFactory.item(material, configUtils.msg(path, fallback, tags));
    }

    private ItemStack actionHead(
            String texture,
            String path,
            String fallback,
            net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... tags
    ) {
        return GuiItemFactory.customHead(texture, configUtils.msg(path, fallback, tags));
    }

    public static final class ClaimSettingsHolder implements InventoryHolder {
        private final UUID sessionId;
        private Inventory inventory;

        public ClaimSettingsHolder(UUID sessionId) {
            this.sessionId = sessionId;
        }

        public UUID sessionId() {
            return sessionId;
        }

        void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
