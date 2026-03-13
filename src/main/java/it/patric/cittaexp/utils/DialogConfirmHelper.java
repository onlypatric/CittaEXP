package it.patric.cittaexp.utils;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class DialogConfirmHelper {

    private static final ClickCallback.Options CLICK_OPTIONS = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(2))
            .build();

    private DialogConfirmHelper() {
    }

    public static void open(
            Plugin plugin,
            Audience audience,
            Component title,
            Component body,
            Component confirmLabel,
            Component cancelLabel,
            Consumer<Player> onConfirm,
            Component unavailableMessage
    ) {
        DialogBase base = DialogBase.builder(title)
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(body)))
                .inputs(List.of())
                .build();

        Dialog dialog = Dialog.create(factory -> {
            ActionButton confirmButton = ActionButton.create(
                    confirmLabel,
                    null,
                    150,
                    DialogAction.customClick((response, targetAudience) -> {
                        if (targetAudience instanceof Player player) {
                            onConfirm.accept(player);
                        }
                    }, CLICK_OPTIONS)
            );
            ActionButton cancelButton = ActionButton.create(
                    cancelLabel,
                    null,
                    150,
                    null
            );
            factory.empty()
                    .base(base)
                    .type(DialogType.multiAction(List.of(confirmButton), cancelButton, 2));
        });

        show(plugin, audience, dialog, unavailableMessage);
    }

    private static void show(Plugin plugin, Audience audience, Dialog dialog, Component unavailableMessage) {
        DialogViewUtils.showDialog(plugin, audience, dialog, unavailableMessage, "dialog-confirm");
    }
}
