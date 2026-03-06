package it.patric.cittaexp.ui.theme;

import dev.patric.commonlib.api.gui.GuiItemView;
import dev.patric.commonlib.api.itemsadder.ItemsAdderService;
import it.patric.cittaexp.preview.ThemeMode;
import it.patric.cittaexp.ui.contract.UiAssetKey;
import it.patric.cittaexp.ui.contract.UiAssetResolver;
import it.patric.cittaexp.ui.contract.UiViewState;
import java.util.Objects;

public final class DefaultUiAssetResolver implements UiAssetResolver {

    private final UiAssetResolver vanilla;
    private final UiAssetResolver itemsAdder;

    public DefaultUiAssetResolver(ItemsAdderService itemsAdderService) {
        this.vanilla = new VanillaUiAssetResolver();
        this.itemsAdder = new ItemsAdderUiAssetResolver(itemsAdderService, vanilla);
    }

    public DefaultUiAssetResolver(UiAssetResolver vanilla, UiAssetResolver itemsAdder) {
        this.vanilla = Objects.requireNonNull(vanilla, "vanilla");
        this.itemsAdder = Objects.requireNonNull(itemsAdder, "itemsAdder");
    }

    @Override
    public GuiItemView resolve(UiAssetKey key, UiViewState state) {
        if (state.themeMode() == ThemeMode.VANILLA) {
            return vanilla.resolve(key, state);
        }
        if (state.themeMode() == ThemeMode.ITEMSADDER) {
            return itemsAdder.resolve(key, state);
        }
        if (state.itemsAdderAvailable()) {
            return itemsAdder.resolve(key, state);
        }
        return vanilla.resolve(key, state);
    }
}
