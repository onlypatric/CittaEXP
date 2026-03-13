package it.patric.cittaexp.disbandtown;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.utils.CommandBridgeUtils;
import it.patric.cittaexp.utils.DialogViewUtils;
import it.patric.cittaexp.utils.PluginConfigUtils;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.event.ClickCallback;
import net.william278.husktowns.town.Member;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class TownDisbandDialogFlow {

    private static final ClickCallback.Options CLICK_OPTIONS = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(2))
            .build();

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final PluginConfigUtils configUtils;

    public TownDisbandDialogFlow(Plugin plugin, HuskTownsApiHook huskTownsApiHook) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.configUtils = new PluginConfigUtils(plugin);
    }

    public void openConfirm(Player player) {
        if (requireMayor(player).isEmpty()) {
            return;
        }
        DialogViewUtils.showDialog(plugin, player, buildDialog(), configUtils.msg("city.disband.dialog.errors.unavailable", "<red>Dialog non disponibile su questo server.</red>"), "town-disband-dialog");
    }

    private Dialog buildDialog() {
        DialogBase base = DialogBase.builder(configUtils.msg(
                        "city.disband.dialog.title",
                        "<red>Elimina Citta</red>"))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(configUtils.msg(
                        "city.disband.dialog.body",
                        "<gray>Conferma di voler eliminare la tua citta. Questa azione e irreversibile.</gray>"))))
                .inputs(List.of(DialogInput.bool(
                        "confirm_disband",
                        configUtils.msg(
                                "city.disband.dialog.inputs.confirm",
                                "<yellow>Confermo di voler eliminare definitivamente la mia citta</yellow>"),
                        false,
                        "true",
                        "false"
                )))
                .build();

        return Dialog.create(factory -> {
            ActionButton confirmButton = ActionButton.create(
                    configUtils.msg("city.disband.dialog.buttons.confirm", "<red>CONFERMA</red>"),
                    null,
                    160,
                    DialogAction.customClick(this::handleSubmit, CLICK_OPTIONS)
            );
            ActionButton cancelButton = ActionButton.create(
                    configUtils.msg("city.disband.dialog.buttons.cancel", "<gray>ANNULLA</gray>"),
                    null,
                    160,
                    null
            );
            factory.empty()
                    .base(base)
                    .type(DialogType.multiAction(List.of(confirmButton), cancelButton, 2));
        });
    }

    private void handleSubmit(DialogResponseView response, Audience audience) {
        if (!(audience instanceof Player player)) {
            audience.sendMessage(configUtils.msg(
                    "city.disband.dialog.errors.unavailable",
                    "<red>Dialog non disponibile su questo server.</red>"));
            return;
        }

        if (requireMayor(player).isEmpty()) {
            return;
        }

        if (!Boolean.TRUE.equals(response.getBoolean("confirm_disband"))) {
            player.sendMessage(configUtils.msg(
                    "city.disband.dialog.errors.checkbox_required",
                    "<red>Devi spuntare la conferma prima di procedere.</red>"));
            DialogViewUtils.showDialog(plugin, player, buildDialog(), configUtils.msg("city.disband.dialog.errors.unavailable", "<red>Dialog non disponibile su questo server.</red>"), "town-disband-dialog");
            return;
        }

        player.sendMessage(configUtils.msg(
                "city.disband.dialog.in_progress",
                "<gray>Richiesta eliminazione citta in corso...</gray>"));

        boolean dispatched = CommandBridgeUtils.dispatchTown(plugin, player, "disband", "confirm");
        if (!dispatched) {
            player.sendMessage(configUtils.msg(
                    "city.disband.dialog.dispatch_failed",
                    "<red>Impossibile eseguire la richiesta di eliminazione adesso.</red>"));
        }
    }

    private Optional<Member> requireMayor(Player player) {
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty()) {
            player.sendMessage(configUtils.msg(
                    "city.disband.dialog.errors.no_town",
                    "<red>Devi essere membro attivo di una citta.</red>"));
            return Optional.empty();
        }
        if (!member.get().town().getMayor().equals(player.getUniqueId())) {
            player.sendMessage(configUtils.msg(
                    "city.disband.dialog.errors.no_privilege",
                    "<red>Solo il capo citta puo eliminare la citta.</red>"));
            return Optional.empty();
        }
        return member;
    }

}
