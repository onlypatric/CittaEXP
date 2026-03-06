package it.patric.cittaexp.ui.theme;

import dev.patric.commonlib.api.gui.GuiItemView;
import it.patric.cittaexp.ui.contract.UiAssetKey;
import it.patric.cittaexp.ui.contract.UiAssetResolver;
import it.patric.cittaexp.ui.contract.UiViewState;
import java.util.List;

public final class VanillaUiAssetResolver implements UiAssetResolver {

    @Override
    public GuiItemView resolve(UiAssetKey key, UiViewState state) {
        return switch (key) {
            case ICON_CITY -> item("NETHER_STAR", "<gold>City Dashboard", List.of("<gray>Overview city status"));
            case ICON_MEMBERS -> item("PLAYER_HEAD", "<aqua>Members", List.of("<gray>Open member roster"));
            case ICON_ROLES -> item("BOOK", "<yellow>Roles", List.of("<gray>Manage role flags"));
            case ICON_TAXES -> item("GOLD_INGOT", "<gold>Taxes", List.of("<gray>Monthly costs and due date"));
            case ICON_WIZARD -> item("CRAFTING_TABLE", "<light_purple>Creation Wizard", List.of("<gray>Name, tag and setup flow"));
            case STATE_FREEZE_ON -> item("REDSTONE_BLOCK", "<red>FREEZE ACTIVE", List.of("<gray>Restricted actions are blocked"));
            case STATE_FREEZE_OFF -> item("LIME_CONCRETE", "<green>Freeze: Off", List.of("<gray>City actions are available"));
            case STATE_CAPITAL_ON -> item("BEACON", "<gold>Capital Status: ON", List.of("<gray>Tax bonus active"));
            case STATE_CAPITAL_OFF -> item("COPPER_BLOCK", "<gray>Capital Status: Off", List.of("<gray>No capital privileges"));
            case CTA_CONFIRM -> item("LIME_DYE", "<green>Confirm", List.of());
            case CTA_CANCEL -> item("BARRIER", "<red>Cancel", List.of());
            case CTA_BACK -> item("ARROW", "<gray>Back", List.of());
            case CTA_NEXT -> item("SPECTRAL_ARROW", "<aqua>Next", List.of());
            case CTA_DIALOG_NAME -> item("NAME_TAG", "<yellow>Set City Name", List.of("<gray>Opens dialog input"));
            case CTA_DIALOG_TAG -> item("PAPER", "<yellow>Set City Tag", List.of("<gray>Opens dialog input"));
            case CTA_BANNER -> item("WHITE_BANNER", "<gold>Choose Banner", List.of("<gray>Template visual selector"));
            case CTA_ARMOR -> item("NETHERITE_CHESTPLATE", "<gold>Choose Armor", List.of("<gray>Template visual selector"));
        };
    }

    private static GuiItemView item(String material, String displayName, List<String> lore) {
        return new GuiItemView(material, displayName, lore);
    }
}
