package it.patric.cittaexp.ui.framework;

import dev.patric.commonlib.api.gui.GuiDefinition;
import it.patric.cittaexp.ui.contract.UiScreenKey;
import it.patric.cittaexp.ui.contract.UiViewState;
import java.util.Map;

public interface GuiCatalog {

    Map<UiScreenKey, GuiDefinition> build(UiViewState state);
}
