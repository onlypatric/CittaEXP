package it.patric.cittaexp.claimsettings;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.playersgui.CityPlayersSession;
import it.patric.cittaexp.utils.DialogViewUtils;
import it.patric.cittaexp.utils.PluginConfigUtils;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.william278.husktowns.claim.Claim;
import net.william278.husktowns.claim.Flag;
import net.william278.husktowns.town.Member;
import net.william278.husktowns.town.Privilege;
import net.william278.husktowns.town.Town;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class ClaimRulesDialogService {

    private static final ClickCallback.Options CLICK_OPTIONS = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(2))
            .build();

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final PluginConfigUtils configUtils;
    private final ConcurrentMap<UUID, ClaimRulesDialogSession> sessionsByViewer = new ConcurrentHashMap<>();

    public ClaimRulesDialogService(Plugin plugin, HuskTownsApiHook huskTownsApiHook) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.configUtils = new PluginConfigUtils(plugin);
    }

    public void clearViewerSession(UUID viewerId) {
        sessionsByViewer.remove(viewerId);
    }

    public void clearAllSessions() {
        sessionsByViewer.clear();
    }

    public void open(Player player, CityPlayersSession citySession, Claim.Type claimType, Runnable onReturn) {
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty()) {
            player.sendMessage(configUtils.msg("city.players.gui.claim_settings.errors.no_town",
                    "<red>Devi essere membro attivo di una citta.</red>"));
            return;
        }
        if (member.get().town().getId() != citySession.townId()) {
            player.sendMessage(configUtils.msg("city.players.gui.errors.session_expired",
                    "<red>Sessione GUI scaduta. Riapri /city.</red>"));
            return;
        }
        if (!huskTownsApiHook.hasPrivilege(player, Privilege.SET_RULES)) {
            player.sendMessage(configUtils.msg("city.players.gui.claim_settings.errors.no_permission",
                    "<red>Non hai i permessi per modificare le regole claim.</red>"));
            return;
        }

        Optional<Town> town = huskTownsApiHook.getTownById(citySession.townId());
        if (town.isEmpty()) {
            player.sendMessage(configUtils.msg("city.players.gui.errors.town_not_found_runtime",
                    "<red>La citta non e piu disponibile.</red>"));
            return;
        }

        LinkedHashMap<String, Boolean> state = new LinkedHashMap<>();
        Map<String, Boolean> currentRules = huskTownsApiHook.getTownClaimRules(citySession.townId(), claimType);
        huskTownsApiHook.api().getFlagSet().stream()
                .map(Flag::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(flagId -> state.put(flagId, currentRules.getOrDefault(flagId, false)));

        sessionsByViewer.put(player.getUniqueId(), new ClaimRulesDialogSession(
                citySession.sessionId(),
                citySession.townId(),
                claimType,
                state
        ));
        showDialog(player, buildDialog(player, claimType, onReturn), configUtils.msg(
                "city.players.dialog.claim_settings.errors.unavailable",
                "<red>Dialog non disponibile su questo server.</red>"
        ));
    }

    private Dialog buildDialog(Player viewer, Claim.Type claimType, Runnable onReturn) {
        ClaimRulesDialogSession session = sessionsByViewer.get(viewer.getUniqueId());
        if (session == null) {
            return Dialog.create(factory -> factory.empty());
        }

        String typeName = claimType.name();
        DialogBase base = DialogBase.builder(configUtils.msg(
                        "city.players.dialog.claim_settings.title",
                        "<gold>Impostazioni claim</gold> <gray>{type}</gray>",
                        Placeholder.unparsed("type", typeName)
                ))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(configUtils.msg(
                        "city.players.dialog.claim_settings.body",
                        "<gray>Configura i flag per il tipo</gray> <white>{type}</white><gray>. " +
                                "Usa i pulsanti per ON/OFF e salva quando hai finito.</gray>",
                        Placeholder.unparsed("type", typeName)
                ))))
                .build();

        return Dialog.create(factory -> {
            List<ActionButton> buttons = new ArrayList<>();
            session.flagStates().entrySet().stream()
                    .sorted(Comparator.comparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER))
                    .forEach(entry -> buttons.add(ActionButton.create(
                            configUtils.msg(entry.getValue()
                                            ? "city.players.dialog.claim_settings.buttons.toggle_on"
                                            : "city.players.dialog.claim_settings.buttons.toggle_off",
                                    entry.getValue()
                                            ? "<green>{flag}: ON</green>"
                                            : "<red>{flag}: OFF</red>",
                                    Placeholder.unparsed("flag", entry.getKey())
                            ),
                            null,
                            120,
                            DialogAction.customClick((response, audience) -> toggleFlag(audience, session, entry.getKey(), onReturn), CLICK_OPTIONS)
                    )));

            buttons.add(ActionButton.create(
                    configUtils.msg("city.players.dialog.claim_settings.buttons.save", "<green>SALVA</green>"),
                    null,
                    140,
                    DialogAction.customClick((response, audience) -> saveRules(response, audience, session, onReturn), CLICK_OPTIONS)
            ));

            ActionButton cancelButton = ActionButton.create(
                    configUtils.msg("city.players.dialog.claim_settings.buttons.cancel", "<gray>ANNULLA</gray>"),
                    null,
                    140,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player player) {
                            sessionsByViewer.remove(player.getUniqueId());
                            plugin.getServer().getScheduler().runTask(plugin, onReturn);
                        }
                    }, CLICK_OPTIONS)
            );
            factory.empty()
                    .base(base)
                    .type(DialogType.multiAction(buttons, cancelButton, 2));
        });
    }

    private void toggleFlag(Audience audience, ClaimRulesDialogSession session, String flagId, Runnable onReturn) {
        if (!(audience instanceof Player player)) {
            return;
        }
        ClaimRulesDialogSession current = sessionsByViewer.get(player.getUniqueId());
        if (!isValidSession(current, session)) {
            player.sendMessage(configUtils.msg("city.players.gui.errors.session_expired",
                    "<red>Sessione GUI scaduta. Riapri /city.</red>"));
            return;
        }
        current.toggle(flagId);
        showDialog(player, buildDialog(player, current.claimType(), onReturn), configUtils.msg(
                "city.players.dialog.claim_settings.errors.unavailable",
                "<red>Dialog non disponibile su questo server.</red>"
        ));
    }

    private void saveRules(
            DialogResponseView response,
            Audience audience,
            ClaimRulesDialogSession session,
            Runnable onReturn
    ) {
        if (!(audience instanceof Player player)) {
            return;
        }
        ClaimRulesDialogSession current = sessionsByViewer.get(player.getUniqueId());
        if (!isValidSession(current, session)) {
            player.sendMessage(configUtils.msg("city.players.gui.errors.session_expired",
                    "<red>Sessione GUI scaduta. Riapri /city.</red>"));
            return;
        }
        if (!huskTownsApiHook.hasPrivilege(player, Privilege.SET_RULES)) {
            player.sendMessage(configUtils.msg("city.players.gui.claim_settings.errors.no_permission",
                    "<red>Non hai i permessi per modificare le regole claim.</red>"));
            sessionsByViewer.remove(player.getUniqueId());
            plugin.getServer().getScheduler().runTask(plugin, onReturn);
            return;
        }

        boolean applied = huskTownsApiHook.applyTownClaimRules(
                player,
                current.townId(),
                current.claimType(),
                current.flagStates()
        );
        if (applied) {
            player.sendMessage(configUtils.msg(
                    "city.players.gui.claim_settings.actions.save_success",
                    "<green>Regole claim salvate per</green> <yellow>{type}</yellow>.",
                    Placeholder.unparsed("type", current.claimType().name())
            ));
        } else {
            player.sendMessage(configUtils.msg(
                    "city.players.gui.claim_settings.actions.save_failed",
                    "<red>Salvataggio regole claim fallito.</red>"
            ));
        }

        sessionsByViewer.remove(player.getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin, onReturn);
    }

    private boolean isValidSession(ClaimRulesDialogSession current, ClaimRulesDialogSession expected) {
        return current != null
                && current.citySessionId().equals(expected.citySessionId())
                && current.townId() == expected.townId()
                && current.claimType() == expected.claimType();
    }

    private void showDialog(Audience audience, Dialog dialog, Component unavailableMessage) {
        DialogViewUtils.showDialog(plugin, audience, dialog, unavailableMessage, "claim-settings-dialog");
    }

}
