package it.patric.cittaexp.createtown;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import it.patric.cittaexp.economy.EconomyBalanceCategory;
import it.patric.cittaexp.economy.EconomyValueService;
import it.patric.cittaexp.economy.PlayerEconomyService;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.utils.DialogBodyUtils;
import it.patric.cittaexp.utils.DialogInputUtils;
import it.patric.cittaexp.utils.DialogViewUtils;
import it.patric.cittaexp.utils.PluginConfigUtils;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class CityCreateDialogFlow {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9]+$");
    private static final Pattern TAG_PATTERN = Pattern.compile("^[A-Za-z]{3}$");
    private static final Key TOWN_TAG_KEY = Key.key("cittaexp", "tag");
    private static final ClickCallback.Options CLICK_OPTIONS = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(2))
            .build();

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final PlayerEconomyService playerEconomyService;
    private final EconomyValueService economyValueService;
    private final CityCreationTraceStore cityCreationTraceStore;
    private final PluginConfigUtils configUtils;

    public CityCreateDialogFlow(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            PlayerEconomyService playerEconomyService,
            EconomyValueService economyValueService
    ) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.playerEconomyService = playerEconomyService;
        this.economyValueService = economyValueService;
        this.cityCreationTraceStore = new CityCreationTraceStore(plugin);
        this.configUtils = new PluginConfigUtils(plugin);
    }

    public void openIntro(Player player) {
        DialogViewUtils.showDialog(plugin, player, buildIntroDialog(), configUtils.msg("city.create.dialog.errors.unavailable", "<red>Dialog non disponibile su questo server.</red>"), "city-create");
    }

    private Dialog buildIntroDialog() {
        String createCost = formattedCreateCost();
        DialogBase base = DialogBase
                .builder(configUtils.msg("city.create.dialog.intro.title", "<gold>Creazione Citta</gold>"))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(DialogBodyUtils.plainMessages(
                        configUtils,
                        "city.create.dialog.intro.body",
                        "<gray>Benvenuto nella creazione citta.<newline></newline>Fondare una citta costa <gold>{cost}</gold>.<newline></newline>Clicca <green>Prossimo</green> per inserire nome e tag.</gray>",
                        Placeholder.unparsed("cost", createCost)))
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
        String createCost = formattedCreateCost();
        DialogBase base = DialogBase
                .builder(configUtils.msg("city.create.dialog.form.title", "<gold>Dati Citta</gold>"))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(DialogBodyUtils.plainMessages(
                        configUtils,
                        "city.create.dialog.form.body",
                        "<gray>Inserisci il nome della citta e un tag (max 3 lettere).<newline></newline><gray>Costo fondazione:</gray> <gold>{cost}</gold></gray>",
                        Placeholder.unparsed("cost", createCost)))
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

        double createCost = createCost();
        String formattedCost = playerEconomyService == null
                ? fallbackFormat(createCost)
                : playerEconomyService.format(createCost);
        if (createCost > 0.0D) {
            if (playerEconomyService == null || !playerEconomyService.available()) {
                audience.sendMessage(configUtils.msg(
                        "city.create.dialog.form.errors.economy_unavailable",
                        "<red>Vault/Economy non disponibile. La creazione citta a pagamento e bloccata.</red>"));
                return;
            }
            if (!playerEconomyService.has(player, createCost)) {
                audience.sendMessage(configUtils.msg(
                        "city.create.dialog.form.errors.insufficient_funds",
                        "<red>Saldo insufficiente.</red> <gray>Servono {cost} per fondare una citta.</gray>",
                        Placeholder.unparsed("cost", formattedCost)));
                return;
            }
            PlayerEconomyService.TransactionResult withdraw = playerEconomyService.withdraw(player, createCost);
            if (!withdraw.success()) {
                audience.sendMessage(configUtils.msg(
                        "city.create.dialog.form.errors.withdraw_failed",
                        "<red>Impossibile addebitare {cost} dal tuo saldo.</red>",
                        Placeholder.unparsed("cost", formattedCost)));
                return;
            }
        }

        player.sendMessage(configUtils.msg(
                "city.create.dialog.form.create_in_progress",
                "<gray>Creazione citta in corso...</gray>"));

        try {
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
                        try {
                            huskTownsApiHook.editTown(player, town.getId(), mutableTown ->
                                    mutableTown.setMetadataTag(TOWN_TAG_KEY, cityTag));
                        } catch (RuntimeException exception) {
                            plugin.getLogger().warning("Impossibile salvare il tag citta per townId=" + town.getId()
                                    + " tag=" + cityTag
                                    + " reason=" + unwrap(exception).getMessage());
                        }
                        player.sendMessage(configUtils.msg(
                                "city.create.dialog.form.create_success",
                                "<green>Citta creata:</green> <yellow>{name}</yellow> <gray>(id: {id})</gray> <gray>|</gray> <gold>Costo pagato:</gold> <white>{cost}</white>",
                                Placeholder.unparsed("name", town.getName()),
                                Placeholder.unparsed("id", Integer.toString(town.getId())),
                                Placeholder.unparsed("cost", formattedCost)));
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
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        refundCreateCost(player, createCost, formattedCost);
                        player.sendMessage(configUtils.msg(
                                "city.create.dialog.form.create_failed",
                                "<red>Creazione citta fallita:</red> <gray>{error}</gray>",
                                Placeholder.unparsed("error",
                                        root.getMessage() == null ? "errore sconosciuto" : root.getMessage())));
                    });
                    return null;
                });
        } catch (RuntimeException exception) {
            refundCreateCost(player, createCost, formattedCost);
            Throwable root = unwrap(exception);
            plugin.getLogger().warning("Town creation dispatch failed player=" + player.getUniqueId()
                    + " name=" + cityName + " reason=" + root.getMessage());
            player.sendMessage(configUtils.msg(
                    "city.create.dialog.form.create_failed",
                    "<red>Creazione citta fallita:</red> <gray>{error}</gray>",
                    Placeholder.unparsed("error",
                            root.getMessage() == null ? "errore sconosciuto" : root.getMessage())));
        }
    }

    private void refundCreateCost(Player player, double createCost, String formattedCost) {
        if (createCost <= 0.0D || playerEconomyService == null || !playerEconomyService.available()) {
            return;
        }
        PlayerEconomyService.TransactionResult refund = playerEconomyService.deposit(player, createCost);
        if (!refund.success()) {
            player.sendMessage(configUtils.msg(
                    "city.create.dialog.form.errors.refund_failed",
                    "<red>Creazione fallita dopo l'addebito.</red> <gray>Refund automatico non riuscito per {cost}. Contatta lo staff.</gray>",
                    Placeholder.unparsed("cost", formattedCost)));
        }
    }

    private double createCost() {
        double nominal = Math.max(0.0D, plugin.getConfig().getDouble("city.create.cost", 2000.0D));
        return economyValueService.effectiveAmount(EconomyBalanceCategory.PLAYER_CITY_CREATE, nominal);
    }

    private String formattedCreateCost() {
        double createCost = createCost();
        return playerEconomyService == null ? fallbackFormat(createCost) : playerEconomyService.format(createCost);
    }

    private String fallbackFormat(double amount) {
        return "$" + String.format(Locale.US, "%.2f", Math.max(0.0D, amount));
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }
}
