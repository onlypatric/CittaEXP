package it.patric.cittaexp.edittown;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.renametown.TownRenameDialogFlow;
import it.patric.cittaexp.utils.DialogInputUtils;
import it.patric.cittaexp.utils.DialogViewUtils;
import it.patric.cittaexp.utils.ExceptionUtils;
import it.patric.cittaexp.utils.PluginConfigUtils;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.william278.husktowns.town.Member;
import net.william278.husktowns.town.Privilege;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class TownEditDialogFlow {

    private static final ClickCallback.Options CLICK_OPTIONS = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(2))
            .build();

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final TownRenameDialogFlow townRenameDialogFlow;
    private final PluginConfigUtils configUtils;

    public TownEditDialogFlow(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            TownRenameDialogFlow townRenameDialogFlow
    ) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.townRenameDialogFlow = townRenameDialogFlow;
        this.configUtils = new PluginConfigUtils(plugin);
    }

    public void openRoot(Player player) {
        DialogViewUtils.showDialog(plugin, player, buildRootDialog(player), configUtils.msg("city.edit.dialog.errors.unavailable", "<red>Dialog non disponibile su questo server.</red>"), "city-edit");
    }

    private Dialog buildRootDialog(Player viewer) {
        DialogBase base = DialogBase.builder(configUtils.msg(
                        "city.edit.dialog.root.title",
                        "<gold>Modifica Citta</gold>"))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(configUtils.msg(
                        "city.edit.dialog.root.body",
                        "<gray>Seleziona quale campo vuoi modificare.</gray>"))))
                .inputs(List.of())
                .build();

        return Dialog.create(factory -> {
            ActionButton bioButton = ActionButton.create(
                    configUtils.msg("city.edit.dialog.root.buttons.bio", "<yellow>Bio</yellow>"),
                    null,
                    140,
                    DialogAction.customClick((response, audience) -> openTextDialog(EditField.BIO, audience), CLICK_OPTIONS)
            );
            ActionButton greetingButton = ActionButton.create(
                    configUtils.msg("city.edit.dialog.root.buttons.greeting", "<yellow>Greeting</yellow>"),
                    null,
                    140,
                    DialogAction.customClick((response, audience) -> openTextDialog(EditField.GREETING, audience), CLICK_OPTIONS)
            );
            ActionButton farewellButton = ActionButton.create(
                    configUtils.msg("city.edit.dialog.root.buttons.farewell", "<yellow>Farewell</yellow>"),
                    null,
                    140,
                    DialogAction.customClick((response, audience) -> openTextDialog(EditField.FAREWELL, audience), CLICK_OPTIONS)
            );
            ActionButton colorButton = ActionButton.create(
                    configUtils.msg("city.edit.dialog.root.buttons.color", "<yellow>Colore</yellow>"),
                    null,
                    140,
                    DialogAction.customClick((response, audience) -> openColorDialog(audience), CLICK_OPTIONS)
            );
            ActionButton renameButton = ActionButton.create(
                    configUtils.msg("city.edit.dialog.root.buttons.rename", "<yellow>Rinomina</yellow>"),
                    null,
                    140,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player player) {
                            townRenameDialogFlow.open(player);
                        }
                    }, CLICK_OPTIONS)
            );
            ActionButton cancelButton = ActionButton.create(
                    configUtils.msg("city.edit.dialog.root.buttons.cancel", "<red>Annulla</red>"),
                    null,
                    140,
                    null
            );

            List<ActionButton> buttons = new ArrayList<>(List.of(bioButton, greetingButton, farewellButton, colorButton));
            if (hasTownPrivilege(viewer, Privilege.RENAME)) {
                buttons.add(renameButton);
            }

            factory.empty()
                    .base(base)
                    .type(DialogType.multiAction(buttons, cancelButton, 2));
        });
    }

    private void openTextDialog(EditField field, Audience audience) {
        if (!(audience instanceof Player player)) {
            audience.sendMessage(configUtils.msg(
                    "city.edit.dialog.errors.unavailable",
                    "<red>Dialog non disponibile su questo server.</red>"));
            return;
        }
        Optional<Member> member = requireMemberWithPrivilege(player, field.requiredPrivilege);
        if (member.isEmpty()) {
            return;
        }
        DialogViewUtils.showDialog(plugin, audience, buildTextDialog(field, field.currentValue(member.get())), configUtils.msg("city.edit.dialog.errors.unavailable", "<red>Dialog non disponibile su questo server.</red>"), "city-edit");
    }

    private Dialog buildTextDialog(EditField field, String initialValue) {
        DialogBase base = DialogBase.builder(configUtils.msg(field.titlePath, field.titleFallback))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(configUtils.msg(field.bodyPath, field.bodyFallback))))
                .inputs(List.of(DialogInputUtils.text(
                        "value",
                        configUtils.cfgInt("city.edit.dialog.text.width", 340),
                        configUtils.msg(field.inputLabelPath, field.inputLabelFallback),
                        true,
                        initialValue,
                        configUtils.cfgInt("city.edit.dialog.text.maxLength", 256)
                )))
                .build();

        return Dialog.create(factory -> {
            ActionButton saveButton = ActionButton.create(
                    configUtils.msg("city.edit.dialog.text.buttons.save", "<green>Salva</green>"),
                    null,
                    140,
                    DialogAction.customClick((response, audience) -> handleTextSubmit(field, response, audience), CLICK_OPTIONS)
            );
            ActionButton cancelButton = ActionButton.create(
                    configUtils.msg("city.edit.dialog.text.buttons.cancel", "<red>Annulla</red>"),
                    null,
                    140,
                    DialogAction.customClick((response, audience) -> openRootFromAudience(audience), CLICK_OPTIONS)
            );
            factory.empty()
                    .base(base)
                    .type(DialogType.multiAction(List.of(saveButton), cancelButton, 2));
        });
    }

    private void openColorDialog(Audience audience) {
        if (!(audience instanceof Player player)) {
            audience.sendMessage(configUtils.msg(
                    "city.edit.dialog.errors.unavailable",
                    "<red>Dialog non disponibile su questo server.</red>"));
            return;
        }
        Optional<Member> member = requireMemberWithPrivilege(player, Privilege.SET_COLOR);
        if (member.isEmpty()) {
            return;
        }
        RgbValue current = parseHexColor(member.get().town().getColorRgb());
        DialogViewUtils.showDialog(plugin, audience, buildColorDialog(member.get().town().getColorRgb(), current, current), configUtils.msg("city.edit.dialog.errors.unavailable", "<red>Dialog non disponibile su questo server.</red>"), "city-edit");
    }

    private Dialog buildColorDialog(String currentColorHex, RgbValue input, RgbValue preview) {
        DialogBase base = DialogBase.builder(configUtils.msg(
                        "city.edit.dialog.color.title",
                        "<gold>Scegli Colore Citta (RGB)</gold>"))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(configUtils.msg(
                        "city.edit.dialog.color.body",
                        "<gray>Imposta RGB (0-255), poi usa Anteprima o Salva.<newline></newline>Attuale: <white>{current}</white><newline>Anteprima: <white>{preview}</white></gray>",
                        Placeholder.unparsed("current", currentColorHex),
                        Placeholder.unparsed("preview", preview.toHex())))))
                .inputs(List.of(
                        DialogInput.numberRange("red", configUtils.msg("city.edit.dialog.color.inputs.red", "<red>Rosso</red>"), 0f, 255f)
                                .width(configUtils.cfgInt("city.edit.dialog.color.width", 320))
                                .initial((float) input.red())
                                .step(1f)
                                .build(),
                        DialogInput.numberRange("green", configUtils.msg("city.edit.dialog.color.inputs.green", "<green>Verde</green>"), 0f, 255f)
                                .width(configUtils.cfgInt("city.edit.dialog.color.width", 320))
                                .initial((float) input.green())
                                .step(1f)
                                .build(),
                        DialogInput.numberRange("blue", configUtils.msg("city.edit.dialog.color.inputs.blue", "<blue>Blu</blue>"), 0f, 255f)
                                .width(configUtils.cfgInt("city.edit.dialog.color.width", 320))
                                .initial((float) input.blue())
                                .step(1f)
                                .build()
                ))
                .build();

        return Dialog.create(factory -> {
            ActionButton previewButton = ActionButton.create(
                    configUtils.msg("city.edit.dialog.color.buttons.preview", "<yellow>Anteprima</yellow>"),
                    null,
                    120,
                    DialogAction.customClick((response, audience) -> handleColorPreview(currentColorHex, response, audience), CLICK_OPTIONS)
            );
            ActionButton saveButton = ActionButton.create(
                    configUtils.msg("city.edit.dialog.color.buttons.save", "<green>Salva</green>"),
                    null,
                    120,
                    DialogAction.customClick((response, audience) -> handleColorSubmit(currentColorHex, response, audience), CLICK_OPTIONS)
            );

            ActionButton cancelButton = ActionButton.create(
                    configUtils.msg("city.edit.dialog.color.buttons.cancel", "<red>Indietro</red>"),
                    null,
                    120,
                    DialogAction.customClick((response, audience) -> openRootFromAudience(audience), CLICK_OPTIONS)
            );

            factory.empty()
                    .base(base)
                    .type(DialogType.multiAction(List.of(previewButton, saveButton), cancelButton, 2));
        });
    }

    private void handleTextSubmit(EditField field, DialogResponseView response, Audience audience) {
        String value = DialogInputUtils.normalized(response, "value", configUtils);
        if (value.isBlank()) {
            audience.sendMessage(configUtils.msg(
                    "city.edit.dialog.text.errors.empty",
                    "<red>Il valore non puo essere vuoto.</red>"));
            DialogViewUtils.showDialog(plugin, audience, buildTextDialog(field, value), configUtils.msg("city.edit.dialog.errors.unavailable", "<red>Dialog non disponibile su questo server.</red>"), "city-edit");
            return;
        }
        if (!(audience instanceof Player player)) {
            audience.sendMessage(configUtils.msg(
                    "city.edit.dialog.errors.unavailable",
                    "<red>Dialog non disponibile su questo server.</red>"));
            return;
        }

        Optional<Member> member = requireMemberWithPrivilege(player, field.requiredPrivilege);
        if (member.isEmpty()) {
            return;
        }

        try {
            huskTownsApiHook.editTown(player, member.get().town().getId(), town -> field.apply(town, value));
            player.sendMessage(configUtils.msg(
                    "city.edit.dialog.text.success",
                    "<green>{field} aggiornato con successo.</green>",
                    Placeholder.unparsed("field", field.successLabel)
            ));
        } catch (RuntimeException exception) {
            player.sendMessage(configUtils.msg(
                    "city.edit.dialog.text.failed",
                    "<red>Modifica fallita:</red> <gray>{error}</gray>",
                    Placeholder.unparsed("error", ExceptionUtils.safeMessage(exception, "errore sconosciuto"))
            ));
            plugin.getLogger().warning("Town edit failed player=" + player.getUniqueId()
                    + " field=" + field.successLabel
                    + " reason=" + exception.getMessage());
        }
    }

    private void handleColorPreview(String currentColorHex, DialogResponseView response, Audience audience) {
        if (!(audience instanceof Player player)) {
            audience.sendMessage(configUtils.msg(
                    "city.edit.dialog.errors.unavailable",
                    "<red>Dialog non disponibile su questo server.</red>"));
            return;
        }
        Optional<Member> member = requireMemberWithPrivilege(player, Privilege.SET_COLOR);
        if (member.isEmpty()) {
            return;
        }
        RgbValue fallback = parseHexColor(member.get().town().getColorRgb());
        RgbValue rgb = readRgb(response, fallback);
        DialogViewUtils.showDialog(plugin, audience, buildColorDialog(currentColorHex, rgb, rgb), configUtils.msg("city.edit.dialog.errors.unavailable", "<red>Dialog non disponibile su questo server.</red>"), "city-edit");
    }

    private void handleColorSubmit(String currentColorHex, DialogResponseView response, Audience audience) {
        if (!(audience instanceof Player player)) {
            audience.sendMessage(configUtils.msg(
                    "city.edit.dialog.errors.unavailable",
                    "<red>Dialog non disponibile su questo server.</red>"));
            return;
        }
        Optional<Member> member = requireMemberWithPrivilege(player, Privilege.SET_COLOR);
        if (member.isEmpty()) {
            return;
        }

        RgbValue fallback = parseHexColor(member.get().town().getColorRgb());
        RgbValue rgb = readRgb(response, fallback);
        String rawHex = rgb.toHex();
        TextColor color = TextColor.color(rgb.red(), rgb.green(), rgb.blue());

        try {
            huskTownsApiHook.editTown(player, member.get().town().getId(), town -> town.setTextColor(color));
            player.sendMessage(configUtils.msg(
                    "city.edit.dialog.color.success",
                    "<green>Colore citta aggiornato a</green> <white>{hex}</white>.",
                    Placeholder.unparsed("hex", rawHex)
            ));
        } catch (RuntimeException exception) {
            player.sendMessage(configUtils.msg(
                    "city.edit.dialog.color.failed",
                    "<red>Aggiornamento colore fallito:</red> <gray>{error}</gray>",
                    Placeholder.unparsed("error", ExceptionUtils.safeMessage(exception, "errore sconosciuto"))
            ));
            plugin.getLogger().warning("Town color edit failed player=" + player.getUniqueId()
                    + " hex=" + rawHex
                    + " reason=" + exception.getMessage());
            DialogViewUtils.showDialog(plugin, audience, buildColorDialog(currentColorHex, rgb, rgb), configUtils.msg("city.edit.dialog.errors.unavailable", "<red>Dialog non disponibile su questo server.</red>"), "city-edit");
        }
    }

    private RgbValue readRgb(DialogResponseView response, RgbValue fallback) {
        int red = clampColor(response.getFloat("red"), fallback.red());
        int green = clampColor(response.getFloat("green"), fallback.green());
        int blue = clampColor(response.getFloat("blue"), fallback.blue());
        return new RgbValue(red, green, blue);
    }

    private static int clampColor(Float value, int fallback) {
        if (value == null) {
            return fallback;
        }
        int rounded = Math.round(value);
        return Math.max(0, Math.min(255, rounded));
    }

    private static RgbValue parseHexColor(String rawHex) {
        TextColor parsed = TextColor.fromHexString(rawHex);
        if (parsed == null) {
            return new RgbValue(255, 255, 255);
        }
        return new RgbValue(parsed.red(), parsed.green(), parsed.blue());
    }

    private Optional<Member> requireMemberWithPrivilege(Player player, Privilege privilege) {
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty()) {
            player.sendMessage(configUtils.msg(
                    "city.edit.dialog.errors.no_town",
                    "<red>Devi essere membro di una citta per modificarla.</red>"));
            return Optional.empty();
        }
        if (!huskTownsApiHook.hasPrivilege(player, privilege)) {
            player.sendMessage(configUtils.msg(
                    "city.edit.dialog.errors.no_privilege",
                    "<red>Permessi insufficienti per questa modifica.</red>"));
            return Optional.empty();
        }
        return member;
    }

    private boolean hasTownPrivilege(Player player, Privilege privilege) {
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        return member.isPresent() && huskTownsApiHook.hasPrivilege(player, privilege);
    }

    private void openRootFromAudience(Audience audience) {
        if (audience instanceof Player player) {
            DialogViewUtils.showDialog(plugin, player, buildRootDialog(player), configUtils.msg("city.edit.dialog.errors.unavailable", "<red>Dialog non disponibile su questo server.</red>"), "city-edit");
            return;
        }
        audience.sendMessage(configUtils.msg(
                "city.edit.dialog.errors.unavailable",
                "<red>Dialog non disponibile su questo server.</red>"));
    }

    private record RgbValue(int red, int green, int blue) {
        private String toHex() {
            return String.format("#%02x%02x%02x", red, green, blue);
        }
    }

    private enum EditField {
        BIO(
                Privilege.SET_BIO,
                "Bio",
                "city.edit.dialog.text.bio.title",
                "<gold>Modifica Bio</gold>",
                "city.edit.dialog.text.bio.body",
                "<gray>Inserisci la nuova bio della citta.</gray>",
                "city.edit.dialog.text.bio.label",
                "<yellow>Bio</yellow>"
        ),
        GREETING(
                Privilege.SET_GREETING,
                "Greeting",
                "city.edit.dialog.text.greeting.title",
                "<gold>Modifica Greeting</gold>",
                "city.edit.dialog.text.greeting.body",
                "<gray>Inserisci il messaggio di benvenuto.</gray>",
                "city.edit.dialog.text.greeting.label",
                "<yellow>Greeting</yellow>"
        ),
        FAREWELL(
                Privilege.SET_FAREWELL,
                "Farewell",
                "city.edit.dialog.text.farewell.title",
                "<gold>Modifica Farewell</gold>",
                "city.edit.dialog.text.farewell.body",
                "<gray>Inserisci il messaggio di uscita.</gray>",
                "city.edit.dialog.text.farewell.label",
                "<yellow>Farewell</yellow>"
        );

        private final Privilege requiredPrivilege;
        private final String successLabel;
        private final String titlePath;
        private final String titleFallback;
        private final String bodyPath;
        private final String bodyFallback;
        private final String inputLabelPath;
        private final String inputLabelFallback;

        EditField(
                Privilege requiredPrivilege,
                String successLabel,
                String titlePath,
                String titleFallback,
                String bodyPath,
                String bodyFallback,
                String inputLabelPath,
                String inputLabelFallback
        ) {
            this.requiredPrivilege = requiredPrivilege;
            this.successLabel = successLabel;
            this.titlePath = titlePath;
            this.titleFallback = titleFallback;
            this.bodyPath = bodyPath;
            this.bodyFallback = bodyFallback;
            this.inputLabelPath = inputLabelPath;
            this.inputLabelFallback = inputLabelFallback;
        }

        private void apply(net.william278.husktowns.town.Town town, String value) {
            switch (this) {
                case BIO -> town.setBio(value);
                case GREETING -> town.setGreeting(value);
                case FAREWELL -> town.setFarewell(value);
            }
        }

        private String currentValue(Member member) {
            return switch (this) {
                case BIO -> member.town().getBio().orElse("");
                case GREETING -> member.town().getGreeting().orElse("");
                case FAREWELL -> member.town().getFarewell().orElse("");
            };
        }
    }
}
