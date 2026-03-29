package it.patric.cittaexp.renametown;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import it.patric.cittaexp.discord.DiscordTownSyncService;
import it.patric.cittaexp.economy.EconomyBalanceCategory;
import it.patric.cittaexp.economy.EconomyValueService;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.utils.DialogInputUtils;
import it.patric.cittaexp.utils.DialogViewUtils;
import it.patric.cittaexp.utils.ExceptionUtils;
import it.patric.cittaexp.utils.PluginConfigUtils;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.william278.husktowns.town.Member;
import net.william278.husktowns.town.Privilege;
import net.william278.husktowns.town.Town;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class TownRenameDialogFlow {

    private static final String FIELD_NAME = "town_name";
    private static final String FIELD_TAG = "town_tag";
    private static final Pattern TAG_PATTERN = Pattern.compile("^[A-Za-z0-9]{3}$");
    private static final Key TOWN_TAG_KEY = Key.key("cittaexp", "tag");
    private static final ClickCallback.Options CLICK_OPTIONS = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(2))
            .build();

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final DiscordTownSyncService discordTownSyncService;
    private final EconomyValueService economyValueService;
    private final PluginConfigUtils configUtils;

    public TownRenameDialogFlow(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            DiscordTownSyncService discordTownSyncService,
            EconomyValueService economyValueService
    ) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.discordTownSyncService = discordTownSyncService;
        this.economyValueService = economyValueService;
        this.configUtils = new PluginConfigUtils(plugin);
    }

    public void open(Player player) {
        Optional<Member> member = requireRenamePrivilege(player);
        if (member.isEmpty()) {
            return;
        }
        String currentName = member.get().town().getName();
        String currentTag = member.get().town().getMetadataTag(TOWN_TAG_KEY).orElse("");
        DialogViewUtils.showDialog(plugin, player, buildDialog(currentName, currentTag), configUtils.msg("city.rename.dialog.errors.unavailable", "<red>Dialog non disponibile su questo server.</red>"), "city-rename");
    }

    private Dialog buildDialog(String currentName, String currentTag) {
        DialogBase base = DialogBase.builder(configUtils.msg(
                        "city.rename.dialog.title",
                        "<gold>Rinomina Citta</gold>"))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(configUtils.msg(
                        "city.rename.dialog.body",
                        "<gray>Imposta nuovo nome e tag citta.</gray> <gray>Costo araldico:</gray> <gold>{cost}</gold>",
                        Placeholder.unparsed("cost", formattedRenameCost())))))
                .inputs(List.of(
                        DialogInputUtils.text(
                                FIELD_NAME,
                                configUtils.cfgInt("city.rename.dialog.inputs.name.width", 320),
                                configUtils.msg("city.rename.dialog.inputs.name.label", "<yellow>Nuovo nome</yellow>"),
                                true,
                                currentName,
                                configUtils.cfgInt("city.rename.dialog.inputs.name.maxLength", 32)
                        ),
                        DialogInputUtils.text(
                                FIELD_TAG,
                                configUtils.cfgInt("city.rename.dialog.inputs.tag.width", 140),
                                configUtils.msg("city.rename.dialog.inputs.tag.label", "<yellow>Tag (3 caratteri)</yellow>"),
                                true,
                                currentTag,
                                configUtils.cfgInt("city.rename.dialog.inputs.tag.maxLength", 3)
                        )))
                .build();

        return Dialog.create(factory -> {
            ActionButton confirmButton = ActionButton.create(
                    configUtils.msg("city.rename.dialog.buttons.confirm", "<green>CONFERMA</green>"),
                    null,
                    170,
                    DialogAction.customClick(this::handleSubmit, CLICK_OPTIONS)
            );
            ActionButton cancelButton = ActionButton.create(
                    configUtils.msg("city.rename.dialog.buttons.cancel", "<red>ANNULLA</red>"),
                    null,
                    170,
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
                    "city.rename.dialog.errors.unavailable",
                    "<red>Dialog non disponibile su questo server.</red>"));
            return;
        }
        Optional<Member> member = requireRenamePrivilege(player);
        if (member.isEmpty()) {
            return;
        }
        Town town = member.get().town();

        String newName = DialogInputUtils.normalized(response, FIELD_NAME, configUtils);
        String newTag = DialogInputUtils.normalizedUpper(response, FIELD_TAG, configUtils);
        if (newName.isBlank()) {
            player.sendMessage(configUtils.msg(
                    "city.rename.dialog.errors.name_required",
                    "<red>Il nome citta e obbligatorio.</red>"));
            DialogViewUtils.showDialog(plugin, player, buildDialog(newName, newTag), configUtils.msg("city.rename.dialog.errors.unavailable", "<red>Dialog non disponibile su questo server.</red>"), "city-rename");
            return;
        }
        if (!TAG_PATTERN.matcher(newTag).matches()) {
            player.sendMessage(configUtils.msg(
                    "city.rename.dialog.errors.tag_invalid",
                    "<red>Il tag deve avere esattamente 3 caratteri alfanumerici.</red>"));
            DialogViewUtils.showDialog(plugin, player, buildDialog(newName, newTag), configUtils.msg("city.rename.dialog.errors.unavailable", "<red>Dialog non disponibile su questo server.</red>"), "city-rename");
            return;
        }
        if (town.getName().equalsIgnoreCase(newName) && town.getMetadataTag(TOWN_TAG_KEY).orElse("").equalsIgnoreCase(newTag)) {
            player.sendMessage(configUtils.msg(
                    "city.rename.dialog.errors.no_changes",
                    "<red>Nessuna modifica rilevata su nome/tag.</red>"));
            return;
        }

        Optional<Town> existingTown = huskTownsApiHook.getTownByName(newName);
        if (existingTown.isPresent() && existingTown.get().getId() != town.getId()) {
            player.sendMessage(configUtils.msg(
                    "city.rename.dialog.errors.name_taken",
                    "<red>Esiste gia una citta con questo nome.</red>"));
            return;
        }

        BigDecimal renameCost = renameCost();
        try {
            huskTownsApiHook.editTown(player, town.getId(), mutableTown -> {
                if (renameCost.signum() > 0 && mutableTown.getMoney().compareTo(renameCost) < 0) {
                    throw new IllegalStateException("insufficient-town-money");
                }
                if (renameCost.signum() > 0) {
                    mutableTown.setMoney(mutableTown.getMoney().subtract(renameCost));
                }
                mutableTown.setName(newName);
                mutableTown.setMetadataTag(TOWN_TAG_KEY, newTag);
            });
            discordTownSyncService.registerTownMetadataEdit(town.getId(), player.getUniqueId());

            player.sendMessage(configUtils.msg(
                    "city.rename.dialog.success",
                    "<green>Citta rinominata:</green> <yellow>{name}</yellow> <gray>[</gray><gold>{tag}</gold><gray>]</gray> <gray>(costo: {cost})</gray>",
                    Placeholder.unparsed("name", newName),
                    Placeholder.unparsed("tag", newTag),
                    Placeholder.unparsed("cost", formattedRenameCost())
            ));
        } catch (RuntimeException exception) {
            String reason = ExceptionUtils.safeMessage(exception, "errore sconosciuto");
            if ("insufficient-town-money".equalsIgnoreCase(reason)) {
                player.sendMessage(configUtils.msg(
                        "city.rename.dialog.errors.insufficient_funds",
                        "<red>Fondi banca citta insufficienti per rinominare.</red>"));
                return;
            }
            player.sendMessage(configUtils.msg(
                    "city.rename.dialog.errors.failed",
                    "<red>Rinomina fallita:</red> <gray>{reason}</gray>",
                    Placeholder.unparsed("reason", reason)
            ));
            plugin.getLogger().warning("Town rename failed player=" + player.getUniqueId()
                    + " townId=" + town.getId()
                    + " reason=" + reason);
        }
    }

    private Optional<Member> requireRenamePrivilege(Player player) {
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty()) {
            player.sendMessage(configUtils.msg(
                    "city.rename.dialog.errors.no_town",
                    "<red>Devi essere membro attivo di una citta.</red>"));
            return Optional.empty();
        }
        if (!huskTownsApiHook.hasPrivilege(player, Privilege.RENAME)) {
            player.sendMessage(configUtils.msg(
                    "city.rename.dialog.errors.no_privilege",
                    "<red>Non hai i permessi per rinominare la citta.</red>"));
            return Optional.empty();
        }
        return member;
    }

    private BigDecimal renameCost() {
        double nominal = Math.max(0.0D, plugin.getConfig().getDouble("city.rename.cost.amount", 250.0D));
        return economyValueService.effectiveMoney(EconomyBalanceCategory.TOWN_RENAME, BigDecimal.valueOf(nominal));
    }

    private String formattedRenameCost() {
        BigDecimal renameCost = renameCost();
        return renameCost.stripTrailingZeros().toPlainString();
    }

}
