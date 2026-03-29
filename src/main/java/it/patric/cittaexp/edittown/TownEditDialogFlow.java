package it.patric.cittaexp.edittown;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import it.patric.cittaexp.discord.DiscordTownSyncService;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.renametown.TownRenameDialogFlow;
import it.patric.cittaexp.text.MiniMessageHelper;
import it.patric.cittaexp.utils.DialogBodyUtils;
import it.patric.cittaexp.utils.DialogInputUtils;
import it.patric.cittaexp.utils.DialogViewUtils;
import it.patric.cittaexp.utils.ExceptionUtils;
import it.patric.cittaexp.utils.PluginConfigUtils;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private static final List<PresetColor> PRESET_COLORS = List.of(
            new PresetColor("Rubino", "#e63946"),
            new PresetColor("Cremisi", "#c1121f"),
            new PresetColor("Corallo", "#ff6b6b"),
            new PresetColor("Ambra", "#f77f00"),
            new PresetColor("Oro", "#ffb703"),
            new PresetColor("Ocra", "#d4a373"),
            new PresetColor("Smeraldo", "#2a9d8f"),
            new PresetColor("Giada", "#52b788"),
            new PresetColor("Salvia", "#84a98c"),
            new PresetColor("Menta", "#80ed99"),
            new PresetColor("Lime", "#a7c957"),
            new PresetColor("Muschio", "#6a994e"),
            new PresetColor("Turchese", "#00b4d8"),
            new PresetColor("Azzurro", "#48cae4"),
            new PresetColor("Zaffiro", "#3a86ff"),
            new PresetColor("Cobalto", "#4361ee"),
            new PresetColor("Indaco", "#3f37c9"),
            new PresetColor("Notte", "#1d3557"),
            new PresetColor("Lavanda", "#9d4edd"),
            new PresetColor("Ametista", "#7b2cbf"),
            new PresetColor("Viola", "#c77dff"),
            new PresetColor("Orchidea", "#ff70a6"),
            new PresetColor("Rosa", "#ff99c8"),
            new PresetColor("Perla", "#f8edeb"),
            new PresetColor("Avorio", "#f3e9dc"),
            new PresetColor("Argento", "#adb5bd"),
            new PresetColor("Ardesia", "#6c757d"),
            new PresetColor("Ferro", "#495057"),
            new PresetColor("Bronzo", "#b08968"),
            new PresetColor("Carbone", "#212529")
    );

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final DiscordTownSyncService discordTownSyncService;
    private final TownRenameDialogFlow townRenameDialogFlow;
    private final PluginConfigUtils configUtils;

    public TownEditDialogFlow(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            DiscordTownSyncService discordTownSyncService,
            TownRenameDialogFlow townRenameDialogFlow
    ) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.discordTownSyncService = discordTownSyncService;
        this.townRenameDialogFlow = townRenameDialogFlow;
        this.configUtils = new PluginConfigUtils(plugin);
    }

    public void openRoot(Player player) {
        DialogViewUtils.showDialog(plugin, player, buildRootDialog(player), configUtils.msg("city.edit.dialog.errors.unavailable", "<red>Dialog non disponibile su questo server.</red>"), "city-edit");
    }

    public void openBio(Player player) {
        openTextDialog(EditField.BIO, player);
    }

    public void openGreeting(Player player) {
        openTextDialog(EditField.GREETING, player);
    }

    public void openFarewell(Player player) {
        openTextDialog(EditField.FAREWELL, player);
    }

    public void openColor(Player player) {
        openColorDialog(player);
    }

    public void openRename(Player player) {
        if (!hasTownPrivilege(player, Privilege.RENAME)) {
            player.sendMessage(configUtils.msg(
                    "city.edit.dialog.errors.no_privilege",
                    "<red>Permessi insufficienti per questa modifica.</red>"));
            return;
        }
        townRenameDialogFlow.open(player);
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
        DialogViewUtils.showDialog(
                plugin,
                audience,
                buildColorDialog(normalizeTownHex(member.get().town().getColorRgb())),
                configUtils.msg("city.edit.dialog.errors.unavailable", "<red>Dialog non disponibile su questo server.</red>"),
                "city-edit");
    }

    private Dialog buildColorDialog(String currentColorHex) {
        DialogBase base = DialogBase.builder(configUtils.msg(
                        "city.edit.dialog.color.title",
                        "<gold>Araldica Cromatica</gold>"))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(DialogBodyUtils.plainMessages(
                        configUtils,
                        "city.edit.dialog.color.body",
                        "<gray>Scegli uno dei sigilli cromatici della tua citta.<newline></newline>Colore attuale: <white>{current}</white><newline>Palette disponibili: <gold>{count}</gold></gray>",
                        Placeholder.unparsed("current", currentColorHex),
                        Placeholder.unparsed("count", Integer.toString(PRESET_COLORS.size()))))
                .build();

        return Dialog.create(factory -> {
            List<ActionButton> paletteButtons = PRESET_COLORS.stream()
                    .map(color -> ActionButton.create(
                            color.buttonLabel(),
                            color.hoverText(currentColorHex),
                            110,
                            DialogAction.customClick((response, audience) -> handleColorSubmit(color, audience), CLICK_OPTIONS)
                    ))
                    .toList();
            ActionButton cancelButton = ActionButton.create(
                    configUtils.msg("city.edit.dialog.color.buttons.cancel", "<red>Indietro</red>"),
                    null,
                    120,
                    DialogAction.customClick((response, audience) -> openRootFromAudience(audience), CLICK_OPTIONS)
            );

            factory.empty()
                    .base(base)
                    .type(DialogType.multiAction(paletteButtons, cancelButton, 3));
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
            int townId = member.get().town().getId();
            huskTownsApiHook.editTown(player, townId, town -> field.apply(town, value));
            discordTownSyncService.registerTownMetadataEdit(townId, player.getUniqueId());
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

    private void handleColorSubmit(PresetColor presetColor, Audience audience) {
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

        String rawHex = presetColor.hex();
        TextColor color = TextColor.fromHexString(rawHex);
        if (color == null) {
            player.sendMessage(configUtils.msg(
                    "city.edit.dialog.color.failed",
                    "<red>Aggiornamento colore fallito:</red> <gray>{error}</gray>",
                    Placeholder.unparsed("error", "preset colore non valido")
            ));
            return;
        }

        try {
            int townId = member.get().town().getId();
            huskTownsApiHook.editTown(player, townId, town -> town.setTextColor(color));
            discordTownSyncService.registerTownMetadataEdit(townId, player.getUniqueId());
            player.sendMessage(configUtils.msg(
                    "city.edit.dialog.color.success",
                    "<green>Colore citta consacrato:</green> <white>{hex}</white> <gray>({name})</gray>.",
                    Placeholder.unparsed("hex", rawHex),
                    Placeholder.unparsed("name", presetColor.name())
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
            DialogViewUtils.showDialog(
                    plugin,
                    audience,
                    buildColorDialog(normalizeTownHex(member.get().town().getColorRgb())),
                    configUtils.msg("city.edit.dialog.errors.unavailable", "<red>Dialog non disponibile su questo server.</red>"),
                    "city-edit");
        }
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

    private static String normalizeTownHex(String rawHex) {
        TextColor parsed = TextColor.fromHexString(rawHex);
        if (parsed == null) {
            return "#ffffff";
        }
        return String.format(Locale.ROOT, "#%02x%02x%02x", parsed.red(), parsed.green(), parsed.blue());
    }

    private record PresetColor(String name, String hex) {
        private net.kyori.adventure.text.Component buttonLabel() {
            return configLabel(name, hex);
        }

        private net.kyori.adventure.text.Component hoverText(String currentColorHex) {
            String suffix = hex.equalsIgnoreCase(currentColorHex)
                    ? "<gray>Colore attuale della citta</gray>"
                    : "<gray>Imposta questo sigillo cromatico</gray>";
            return MiniMessageHelper.parse(
                    "<white>" + name + "</white> <dark_gray>•</dark_gray> <white>" + hex + "</white><newline>" + suffix);
        }

        private static net.kyori.adventure.text.Component configLabel(String name, String hex) {
            return MiniMessageHelper.parse("<" + hex + ">" + name + "</" + hex + ">");
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
