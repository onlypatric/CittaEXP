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
import java.util.Locale;
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
        showDialog(player, buildDialog(player, claimType, member.get().town().getName(), onReturn), configUtils.msg(
                "city.players.dialog.claim_settings.errors.unavailable",
                "<red>Dialog non disponibile su questo server.</red>"
        ));
    }

    private Dialog buildDialog(Player viewer, Claim.Type claimType, String townName, Runnable onReturn) {
        ClaimRulesDialogSession session = sessionsByViewer.get(viewer.getUniqueId());
        if (session == null) {
            return Dialog.create(factory -> factory.empty());
        }

        String typeName = claimTypeLabel(claimType);
        long enabledCount = session.flagStates().values().stream().filter(Boolean.TRUE::equals).count();
        long disabledCount = session.flagStates().size() - enabledCount;
        DialogBase base = DialogBase.builder(configUtils.msg(
                        "city.players.dialog.claim_settings.mmo.title",
                        "<gradient:#f0d38a:#c98932>✦ Regole {type} ✦</gradient> <gray>{town}</gray>",
                        Placeholder.unparsed("type", typeName),
                        Placeholder.unparsed("town", townName)
                ))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(
                        DialogBody.plainMessage(configUtils.msg(
                                "city.players.dialog.claim_settings.mmo.body_intro",
                                "<gray>Qui la citta decide il tenore del territorio</gray> <white>{type}</white><gray>. " +
                                        "Ogni sigillo cambia cio che alleati, ospiti e stranieri possono fare entro questi confini.</gray>",
                                Placeholder.unparsed("type", typeName)
                        )),
                        DialogBody.plainMessage(configUtils.msg(
                                "city.players.dialog.claim_settings.mmo.body_role",
                                "<gold>Ruolo del dominio:</gold> <white>{role}</white>",
                                Placeholder.unparsed("role", typeRole(claimType))
                        )),
                        DialogBody.plainMessage(configUtils.msg(
                                "city.players.dialog.claim_settings.mmo.body_state",
                                "<green>Attivi:</green> <white>{enabled}</white> <gray>|</gray> <red>Bloccati:</red> <white>{disabled}</white>",
                                Placeholder.unparsed("enabled", Long.toString(enabledCount)),
                                Placeholder.unparsed("disabled", Long.toString(disabledCount))
                        ))
                ))
                .build();

        return Dialog.create(factory -> {
            List<ActionButton> buttons = new ArrayList<>();
            session.flagStates().entrySet().stream()
                    .sorted(Comparator.comparing(entry -> prettifyFlag(entry.getKey()), String.CASE_INSENSITIVE_ORDER))
                    .forEach(entry -> buttons.add(ActionButton.create(
                            configUtils.msg(entry.getValue()
                                            ? "city.players.dialog.claim_settings.mmo.buttons.toggle_on"
                                            : "city.players.dialog.claim_settings.mmo.buttons.toggle_off",
                                    entry.getValue()
                                            ? "<green>✦ {flag} <gray>•</gray> Attivo</green>"
                                            : "<red>✦ {flag} <gray>•</gray> Bloccato</red>",
                                    Placeholder.unparsed("flag", prettifyFlag(entry.getKey()))
                            ),
                            null,
                            160,
                            DialogAction.customClick((response, audience) -> toggleFlag(audience, session, entry.getKey(), townName, onReturn), CLICK_OPTIONS)
                    )));

            buttons.add(ActionButton.create(
                    configUtils.msg("city.players.dialog.claim_settings.mmo.buttons.save", "<green>✦ Sigilla l'assetto</green>"),
                    null,
                    140,
                    DialogAction.customClick((response, audience) -> saveRules(response, audience, session, onReturn), CLICK_OPTIONS)
            ));

            ActionButton cancelButton = ActionButton.create(
                    configUtils.msg("city.players.dialog.claim_settings.mmo.buttons.cancel", "<gray>Ritorna al presidio</gray>"),
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

    private void toggleFlag(Audience audience, ClaimRulesDialogSession session, String flagId, String townName, Runnable onReturn) {
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
        showDialog(player, buildDialog(player, current.claimType(), townName, onReturn), configUtils.msg(
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

    private String claimTypeLabel(Claim.Type claimType) {
        return switch (claimType) {
            case CLAIM -> "CLAIM";
            case FARM -> "FARM";
            case PLOT -> "PLOT";
        };
    }

    private String typeRole(Claim.Type claimType) {
        return switch (claimType) {
            case CLAIM -> "i confini principali, la sicurezza ordinaria e il respiro politico della citta";
            case FARM -> "i campi, le filiere e le zone in cui la produzione deve restare disciplinata";
            case PLOT -> "i lotti sensibili, gli spazi speciali e le aree che richiedono permessi piu stretti";
        };
    }

    private String prettifyFlag(String flagId) {
        if (flagId == null || flagId.isBlank()) {
            return "Flag sconosciuto";
        }
        String normalized = flagId.replace('_', ' ').replace('-', ' ').trim().toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(normalized.length());
        boolean capitalize = true;
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (Character.isWhitespace(current)) {
                capitalize = true;
                builder.append(current);
                continue;
            }
            builder.append(capitalize ? Character.toUpperCase(current) : current);
            capitalize = false;
        }
        return builder.toString();
    }

    private void showDialog(Audience audience, Dialog dialog, Component unavailableMessage) {
        DialogViewUtils.showDialog(plugin, audience, dialog, unavailableMessage, "claim-settings-dialog");
    }

}
