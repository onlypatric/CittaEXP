package it.patric.cittaexp.ui.theme;

import dev.patric.commonlib.api.gui.GuiItemView;
import dev.patric.commonlib.api.itemsadder.ItemsAdderService;
import it.patric.cittaexp.ui.contract.UiAssetKey;
import it.patric.cittaexp.ui.contract.UiAssetResolver;
import it.patric.cittaexp.ui.contract.UiViewState;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ItemsAdderUiAssetResolver implements UiAssetResolver {

    private final ItemsAdderService itemsAdderService;
    private final UiAssetResolver fallback;
    private final Map<UiAssetKey, String> customItems = Map.ofEntries(
            Map.entry(UiAssetKey.ICON_CITY, "cittaexp:icon_city"),
            Map.entry(UiAssetKey.ICON_MEMBERS, "cittaexp:icon_members"),
            Map.entry(UiAssetKey.ICON_ROLES, "cittaexp:icon_roles"),
            Map.entry(UiAssetKey.ICON_TAXES, "cittaexp:icon_taxes"),
            Map.entry(UiAssetKey.ICON_WIZARD, "cittaexp:icon_wizard"),
            Map.entry(UiAssetKey.STATE_FREEZE_ON, "cittaexp:state_freeze_on"),
            Map.entry(UiAssetKey.STATE_FREEZE_OFF, "cittaexp:state_freeze_off"),
            Map.entry(UiAssetKey.STATE_CAPITAL_ON, "cittaexp:state_capital_on"),
            Map.entry(UiAssetKey.STATE_CAPITAL_OFF, "cittaexp:state_capital_off"),
            Map.entry(UiAssetKey.CTA_CONFIRM, "cittaexp:cta_confirm"),
            Map.entry(UiAssetKey.CTA_CANCEL, "cittaexp:cta_cancel"),
            Map.entry(UiAssetKey.CTA_BACK, "cittaexp:cta_back"),
            Map.entry(UiAssetKey.CTA_NEXT, "cittaexp:cta_next"),
            Map.entry(UiAssetKey.CTA_DIALOG_NAME, "cittaexp:cta_dialog_name"),
            Map.entry(UiAssetKey.CTA_DIALOG_TAG, "cittaexp:cta_dialog_tag"),
            Map.entry(UiAssetKey.CTA_BANNER, "cittaexp:cta_banner"),
            Map.entry(UiAssetKey.CTA_ARMOR, "cittaexp:cta_armor")
    );

    private final Map<UiAssetKey, String> fontImages = Map.of(
            UiAssetKey.ICON_CITY, "cittaexp:font_city",
            UiAssetKey.ICON_MEMBERS, "cittaexp:font_members",
            UiAssetKey.ICON_ROLES, "cittaexp:font_roles",
            UiAssetKey.ICON_TAXES, "cittaexp:font_taxes",
            UiAssetKey.ICON_WIZARD, "cittaexp:font_wizard"
    );

    public ItemsAdderUiAssetResolver(ItemsAdderService itemsAdderService, UiAssetResolver fallback) {
        this.itemsAdderService = Objects.requireNonNull(itemsAdderService, "itemsAdderService");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    @Override
    public GuiItemView resolve(UiAssetKey key, UiViewState state) {
        GuiItemView fallbackItem = fallback.resolve(key, state);
        String namespacedId = customItems.get(key);
        if (namespacedId == null) {
            return fallbackItem;
        }
        if (!state.itemsAdderAvailable() || !itemsAdderService.isAvailable()) {
            return fallbackItem;
        }
        if (!itemsAdderService.isCustomItem(namespacedId)) {
            return fallbackItem;
        }

        String display = fallbackItem.displayName();
        String fontId = fontImages.get(key);
        if (fontId != null) {
            Optional<String> token = itemsAdderService.fontImage(fontId);
            if (token.isPresent() && !token.get().isBlank()) {
                display = token.get() + " " + display;
            }
        }

        return new GuiItemView(namespacedId, display, fallbackItem.lore());
    }
}
