package it.patric.cittaexp.createtown;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.utils.DialogInputUtils;
import it.patric.cittaexp.utils.DialogViewUtils;
import it.patric.cittaexp.utils.PluginConfigUtils;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class CityCreateDialogFlow {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9]+$");
    private static final Pattern TAG_PATTERN = Pattern.compile("^[A-Za-z]{3}$");
    private static final ClickCallback.Options CLICK_OPTIONS = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(2))
            .build();

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final CityCreationTraceStore cityCreationTraceStore;
    private final PluginConfigUtils configUtils;

    public CityCreateDialogFlow(Plugin plugin, HuskTownsApiHook huskTownsApiHook) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.cityCreationTraceStore = new CityCreationTraceStore(plugin);
        this.configUtils = new PluginConfigUtils(plugin);
    }

    public void openIntro(Player player) {
        DialogViewUtils.showDialog(plugin, player, buildIntroDialog(), configUtils.msg("city.create.dialog.errors.unavailable", "<red>Dialog non disponibile su questo server.</red>"), "city-create");
    }

    private Dialog buildIntroDialog() {
        DialogBase base = DialogBase
                .builder(configUtils.msg("city.create.dialog.intro.title", "<gold>Creazione Citta</gold>"))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(
                        configUtils.msg(
                                "city.create.dialog.intro.body",
                                "<gray>Benvenuto nella creazione citta.<newline></newline>Clicca <green>Prossimo</green> per inserire nome e tag.</gray>"))))
                .inputs(List.of())
                .build();

        return Dialog.create(factory -> {
            ActionButton nextButton = ActionButton.create(
                    configUtils.msg("city.create.dialog.intro.buttons.next", "<green>Prossimo"),
                    null,
                    150,
                    DialogAction.customClick((response, audience) -> DialogViewUtils.showDialog(plugin, audience, buildFormDialog(), configUtils.msg("city.create.dialog.errors.unavailable", "<red>Dialog non disponibile su questo server.</red>"), "city-create"),
                            CLICK_OPTIONS));
            ActionButton cancelButton = ActionButton.create(
                    configUtils.msg("city.create.dialog.intro.buttons.cancel", "<red>Annulla"),
                    null,
                    150,
                    null);
            factory.empty()
                    .base(base)
                    .type(DialogType.multiAction(List.of(nextButton), cancelButton, 2));
        });
    }

    private Dialog buildFormDialog() {
        DialogBase base = DialogBase
                .builder(configUtils.msg("city.create.dialog.form.title", "<gold>Dati Citta</gold>"))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(
                        configUtils.msg(
                                "city.create.dialog.form.body",
                                "<gray>Inserisci il nome della citta e un tag (max 3 lettere).</gray>"))))
                .inputs(List.of(
                        DialogInputUtils.text(
                                "city_name",
                                320,
                                configUtils.msg("city.create.dialog.form.inputs.name",
                                        "<yellow>Nome citta</yellow>"),
                                true,
                                "",
                                32),
                        DialogInputUtils.text(
                                "city_tag",
                                140,
                                configUtils.msg("city.create.dialog.form.inputs.tag",
                                        "<yellow>Tag (max 3 lettere)</yellow>"),
                                true,
                                "",
                                3)))
                .build();

        return Dialog.create(factory -> {
            ActionButton nextButton = ActionButton.create(
                    configUtils.msg("city.create.dialog.form.buttons.next", "<green>Prossimo"),
                    null,
                    150,
                    DialogAction.customClick(this::handleFormSubmit, CLICK_OPTIONS));
            ActionButton cancelButton = ActionButton.create(
                    configUtils.msg("city.create.dialog.form.buttons.cancel", "<red>Annulla"),
                    null,
                    150,
                    null);
            factory.empty()
                    .base(base)
                    .type(DialogType.multiAction(List.of(nextButton), cancelButton, 2));
        });
    }

    private void handleFormSubmit(DialogResponseView response, Audience audience) {
        String cityName = DialogInputUtils.normalized(response, "city_name", configUtils);
        String cityTag = DialogInputUtils.normalizedUpper(response, "city_tag", configUtils);

        if (cityName.isBlank() || !NAME_PATTERN.matcher(cityName).matches()) {
            audience.sendMessage(configUtils.msg(
                    "city.create.dialog.form.errors.name_required",
                    "<red>Il nome citta deve contenere solo caratteri alfanumerici (senza spazi).</red>"));
            DialogViewUtils.showDialog(plugin, audience, buildFormDialog(), configUtils.msg("city.create.dialog.errors.unavailable", "<red>Dialog non disponibile su questo server.</red>"), "city-create");
            return;
        }

        if (!TAG_PATTERN.matcher(cityTag).matches()) {
            audience.sendMessage(configUtils.msg(
                    "city.create.dialog.form.errors.tag_invalid",
                    "<red>Il tag deve contenere esattamente 3 lettere (A-Z).</red>"));
            DialogViewUtils.showDialog(plugin, audience, buildFormDialog(), configUtils.msg("city.create.dialog.errors.unavailable", "<red>Dialog non disponibile su questo server.</red>"), "city-create");
            return;
        }

        if (!(audience instanceof Player player)) {
            audience.sendMessage(configUtils.msg(
                    "city.create.dialog.errors.unavailable",
                    "<red>Dialog non disponibile su questo server.</red>"));
            return;
        }

        player.sendMessage(configUtils.msg(
                "city.create.dialog.form.create_in_progress",
                "<gray>Creazione citta in corso...</gray>"));

        huskTownsApiHook.api().createTown(player, cityName)
                .thenAccept(town -> {
                    try {
                        cityCreationTraceStore.saveTownCreation(
                                town,
                                player.getUniqueId(),
                                player.getName(),
                                cityTag);
                    } catch (Exception exception) {
                        plugin.getLogger().warning("Impossibile salvare city-creations.yml per townId=" + town.getId()
                                + " reason=" + exception.getMessage());
                    }
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.sendMessage(configUtils.msg(
                                "city.create.dialog.form.create_success",
                                "<green>Citta creata:</green> <yellow>{name}</yellow> <gray>(id: {id})</gray>",
                                Placeholder.unparsed("name", town.getName()),
                                Placeholder.unparsed("id", Integer.toString(town.getId()))));
                        player.sendMessage(configUtils.msg(
                                "city.create.dialog.form.success",
                                "<green>Dati validi:</green> <yellow>{name}</yellow> <gray>[</gray><gold>{tag}</gold><gray>]</gray>",
                                Placeholder.unparsed("name", cityName),
                                Placeholder.unparsed("tag", cityTag)));
                    });
                    plugin.getLogger().info(
                            "City created via dialog name=" + town.getName() + " id=" + town.getId()
                                    + " by=" + player.getUniqueId() + " tag=" + cityTag);
                })
                .exceptionally(throwable -> {
                    Throwable root = unwrap(throwable);
                    plugin.getLogger().warning("Town creation failed player=" + player.getUniqueId()
                            + " name=" + cityName + " reason=" + root.getMessage());
                    plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage(configUtils.msg(
                            "city.create.dialog.form.create_failed",
                            "<red>Creazione citta fallita:</red> <gray>{error}</gray>",
                            Placeholder.unparsed("error",
                                    root.getMessage() == null ? "errore sconosciuto" : root.getMessage()))));
                    return null;
                });
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }
}
