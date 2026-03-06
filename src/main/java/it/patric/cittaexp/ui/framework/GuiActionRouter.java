package it.patric.cittaexp.ui.framework;

import dev.patric.commonlib.api.gui.BackMenuAction;
import dev.patric.commonlib.api.gui.CloseGuiAction;
import dev.patric.commonlib.api.gui.GuiAction;
import dev.patric.commonlib.api.gui.GuiCloseReason;
import dev.patric.commonlib.api.gui.OpenSubMenuAction;
import it.patric.cittaexp.ui.contract.UiActionKey;
import it.patric.cittaexp.ui.contract.UiScreenKey;
import java.util.List;

public final class GuiActionRouter {

    public List<GuiAction> actions(UiActionKey actionKey) {
        return switch (actionKey) {
            case OPEN_MEMBERS -> List.of(new OpenSubMenuAction(UiScreenKey.MEMBERS.key(), true, true));
            case OPEN_ROLES -> List.of(new OpenSubMenuAction(UiScreenKey.ROLES.key(), true, true));
            case OPEN_TAXES -> List.of(new OpenSubMenuAction(UiScreenKey.TAXES.key(), true, true));
            case OPEN_WIZARD -> List.of(new OpenSubMenuAction(UiScreenKey.WIZARD_START.key(), true, true));
            case OPEN_DASHBOARD -> List.of(new OpenSubMenuAction(UiScreenKey.DASHBOARD.key(), true, true));
            case OPEN_WIZARD_BANNER -> List.of(new OpenSubMenuAction(UiScreenKey.WIZARD_BANNER_ARMOR.key(), true, true));
            case OPEN_WIZARD_CONFIRM -> List.of(new OpenSubMenuAction(UiScreenKey.WIZARD_CONFIRM.key(), true, true));
            case BACK -> List.of(new BackMenuAction());
            case CLOSE -> List.of(new CloseGuiAction(GuiCloseReason.USER_CLOSE));
        };
    }
}
