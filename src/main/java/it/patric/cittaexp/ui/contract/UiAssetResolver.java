package it.patric.cittaexp.ui.contract;

import dev.patric.commonlib.api.gui.GuiItemView;

public interface UiAssetResolver {

    GuiItemView resolve(UiAssetKey key, UiViewState state);
}
