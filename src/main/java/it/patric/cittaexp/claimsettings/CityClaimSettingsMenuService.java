package it.patric.cittaexp.claimsettings;

import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.playersgui.CityPlayersSession;
import it.patric.cittaexp.text.UiLoreStyle;
import it.patric.cittaexp.utils.PluginConfigUtils;
import it.patric.cittaexp.utils.TexturedGuiSupport;
import it.patric.cittaexp.wiki.CityWikiBackTarget;
import it.patric.cittaexp.wiki.CityWikiDialogService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.william278.husktowns.claim.Claim;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CityClaimSettingsMenuService {

    public static final int GUI_SIZE = 36;
    private static final int[] CLAIM_SLOTS = {0, 1, 2, 9, 10, 11, 18, 19, 20};
    private static final int[] FARM_SLOTS = {3, 4, 5, 12, 13, 14, 21, 22, 23};
    private static final int[] PLOT_SLOTS = {6, 7, 8, 15, 16, 17, 24, 25, 26};
    public static final int SLOT_BACK_START = 28;
    public static final int SLOT_BACK_END = 30;
    public static final int SLOT_HELP_START = 32;
    public static final int SLOT_HELP_END = 34;
    private static final String CLAIM_RULES_FONT_IMAGE_ID = "cittaexp_gui:claim_rules_bg";
    private static final String TRANSPARENT_BUTTON_ID = "cittaexp_gui:transparent_button";

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final CityLevelService cityLevelService;
    private final PluginConfigUtils configUtils;
    private final ClaimRulesDialogService claimRulesDialogService;
    private final CityWikiDialogService cityWikiDialogService;

    public CityClaimSettingsMenuService(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            CityLevelService cityLevelService,
            ClaimRulesDialogService claimRulesDialogService,
            CityWikiDialogService cityWikiDialogService
    ) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.cityLevelService = cityLevelService;
        this.configUtils = new PluginConfigUtils(plugin);
        this.claimRulesDialogService = claimRulesDialogService;
        this.cityWikiDialogService = cityWikiDialogService;
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

        ClaimSettingsHolder holder = new ClaimSettingsHolder(session.sessionId());
        ClaimSettingsOpenResult openResult = createInventory(holder, session);
        Inventory inventory = openResult.inventory();

        ItemStack transparentBase = transparentButtonBase();
        ItemStack claimButton = overlayButton(
                transparentBase,
                "city.players.gui.claim_settings.claim",
                "<white>Impostazioni CLAIM</white>",
                sectionLore(
                        "<dark_gray>╭────────────╮</dark_gray>",
                        "<white>✦</white> <white>Regole CLAIM</white>",
                        "<gray>Qui imposti le regole dei chunk standard della citta.</gray>",
                        "<gray>Usa questo blocco per protezione, accessi e uso base del territorio.</gray>",
                        "<green>➜ Clicca per aprire le regole CLAIM.</green>",
                        "<dark_gray>╰────────────╯</dark_gray>"
                )
        );
        ItemStack farmButton = overlayButton(
                transparentBase,
                "city.players.gui.claim_settings.farm",
                "<green>Impostazioni FARM</green>",
                sectionLore(
                        "<dark_gray>╭────────────╮</dark_gray>",
                        "<green>✦</green> <white>Regole FARM</white>",
                        "<gray>Qui imposti le regole delle zone agricole e di produzione.</gray>",
                        "<gray>Usa questo blocco per raccolti, animali e lavoro condiviso.</gray>",
                        "<green>➜ Clicca per aprire le regole FARM.</green>",
                        "<dark_gray>╰────────────╯</dark_gray>"
                )
        );
        ItemStack plotButton = overlayButton(
                transparentBase,
                "city.players.gui.claim_settings.plot",
                "<aqua>Impostazioni PLOT</aqua>",
                sectionLore(
                        "<dark_gray>╭────────────╮</dark_gray>",
                        "<aqua>✦</aqua> <white>Regole PLOT</white>",
                        "<gray>Qui imposti le regole dei lotti speciali della citta.</gray>",
                        "<gray>Usa questo blocco per case, negozi e aree con accessi dedicati.</gray>",
                        "<green>➜ Clicca per aprire le regole PLOT.</green>",
                        "<dark_gray>╰────────────╯</dark_gray>"
                )
        );
        ItemStack backButton = overlayButton(
                transparentBase,
                "city.players.gui.claim_settings.back",
                "<yellow>Indietro</yellow>",
                sectionLore(
                        "<dark_gray>╭────────────╮</dark_gray>",
                        "<yellow>✦</yellow> <white>Ritorno al comando</white>",
                        "<gray>Lascia il pannello regole claim e torna alle azioni della citta.</gray>",
                        "<green>➜ Clicca per tornare indietro.</green>",
                        "<dark_gray>╰────────────╯</dark_gray>"
                )
        );
        ItemStack helpButton = overlayButton(
                transparentBase,
                "city.players.gui.claim_settings.help",
                "<gold>Aiuto</gold>",
                sectionLore(
                        "<dark_gray>╭────────────╮</dark_gray>",
                        "<gold>✦</gold> <white>Guida ai territori</white>",
                        "<gray>Apri un dialog che spiega il ruolo di CLAIM, FARM e PLOT e come usare questa pagina.</gray>",
                        "<green>➜ Clicca per leggere la guida.</green>",
                        "<dark_gray>╰────────────╯</dark_gray>"
                )
        );

        fillSlots(inventory, CLAIM_SLOTS, claimButton);
        fillSlots(inventory, FARM_SLOTS, farmButton);
        fillSlots(inventory, PLOT_SLOTS, plotButton);
        for (int slot = SLOT_BACK_START; slot <= SLOT_BACK_END; slot++) {
            inventory.setItem(slot, backButton.clone());
        }
        for (int slot = SLOT_HELP_START; slot <= SLOT_HELP_END; slot++) {
            inventory.setItem(slot, helpButton.clone());
        }

        if (openResult.wrapper() != null) {
            openResult.wrapper().showInventory(player);
        } else {
            player.openInventory(inventory);
        }
    }

    public void handleClick(Player player, CityPlayersSession session, int slot, Runnable openActionsView) {
        if (slot >= SLOT_BACK_START && slot <= SLOT_BACK_END) {
            openActionsView.run();
            return;
        }
        if (slot >= SLOT_HELP_START && slot <= SLOT_HELP_END) {
            openHelpDialog(player);
            return;
        }
        if (containsSlot(CLAIM_SLOTS, slot)) {
            openClaimSettingsDialog(player, session, Claim.Type.CLAIM);
            return;
        }
        if (containsSlot(FARM_SLOTS, slot)) {
            openClaimSettingsDialog(player, session, Claim.Type.FARM);
            return;
        }
        if (containsSlot(PLOT_SLOTS, slot)) {
            openClaimSettingsDialog(player, session, Claim.Type.PLOT);
        }
    }

    private void openClaimSettingsDialog(Player player, CityPlayersSession session, Claim.Type claimType) {
        claimRulesDialogService.open(player, session, claimType, () -> open(player, session));
    }

    private ClaimSettingsOpenResult createInventory(ClaimSettingsHolder holder, CityPlayersSession session) {
        TexturedGuiSupport.OpenResult result = TexturedGuiSupport.createInventory(
                plugin,
                holder,
                holder::bind,
                GUI_SIZE,
                configUtils.msg("city.players.gui.claim_settings.title",
                        "<gold>Impostazioni claim</gold> <gray>{town}</gray>",
                        Placeholder.unparsed("town", session.townName())),
                configUtils.missingKeyFallback("city.players.gui.claim_settings.title_plain", "Regole claim"),
                configUtils.cfgInt("city.hub.itemsadder.title_offset", 0),
                configUtils.cfgInt("city.hub.itemsadder.texture_offset", -8),
                CLAIM_RULES_FONT_IMAGE_ID,
                "claim-settings"
        );
        return new ClaimSettingsOpenResult(result.inventory(), result.texturedWrapper());
    }

    private ItemStack overlayButton(ItemStack transparentBase, String path, String fallback, List<Component> lore) {
        return TexturedGuiSupport.overlayButton(
                transparentBase,
                noItalic(configUtils.msg(path, fallback)),
                lore == null ? List.of() : lore.stream().map(this::noItalic).toList(),
                new ItemStack(Material.PAPER)
        );
    }

    private ItemStack transparentButtonBase() {
        return TexturedGuiSupport.transparentButtonBase(plugin, TRANSPARENT_BUTTON_ID);
    }

    private void openHelpDialog(Player player) {
        cityWikiDialogService.openPage(player, "claim_territory", CityWikiBackTarget.CLAIM_SETTINGS);
    }

    private List<Component> sectionLore(String... lines) {
        return UiLoreStyle.renderLegacyComponents(lines == null ? List.of() : List.of(lines)).stream()
                .map(this::noItalic)
                .toList();
    }

    private void fillSlots(Inventory inventory, int[] slots, ItemStack item) {
        if (inventory == null || slots == null || item == null) {
            return;
        }
        for (int slot : slots) {
            inventory.setItem(slot, item.clone());
        }
    }

    private static boolean containsSlot(int[] slots, int slot) {
        if (slots == null) {
            return false;
        }
        for (int value : slots) {
            if (value == slot) {
                return true;
            }
        }
        return false;
    }

    private Component noItalic(Component component) {
        return component == null ? Component.empty() : component.decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
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

    private record ClaimSettingsOpenResult(
            Inventory inventory,
            dev.lone.itemsadder.api.FontImages.TexturedInventoryWrapper wrapper
    ) {
    }
}
