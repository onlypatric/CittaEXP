package it.patric.cittaexp.discord;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.text.MiniMessageHelper;
import java.lang.reflect.Method;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.william278.husktowns.town.Town;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

public final class DiscordCommandService implements Listener {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final ClickCallback.Options CLICK_OPTIONS = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(2))
            .build();
    private static final String VERIFY_COMMAND_PREFIX = "/link codice:";

    private final Plugin plugin;
    private final DiscordBridgeSettings settings;
    private final DiscordIdentityLinkService linkService;
    private final DiscordTownSyncService townSyncService;
    private final HuskTownsApiHook huskTownsApiHook;
    private final DiscordBotService botService;
    private final DiscordLogHelper log;

    public DiscordCommandService(
            Plugin plugin,
            DiscordBridgeSettings settings,
            DiscordIdentityLinkService linkService,
            DiscordTownSyncService townSyncService,
            HuskTownsApiHook huskTownsApiHook,
            DiscordBotService botService
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.linkService = linkService;
        this.townSyncService = townSyncService;
        this.huskTownsApiHook = huskTownsApiHook;
        this.botService = botService;
        this.log = new DiscordLogHelper(plugin, settings.verboseLogging());
    }

    public void openLink(Player player) {
        if (!settings.enabled()) {
            log.debug("openLink denied bridge disabled player=" + player.getUniqueId());
            player.sendMessage("[CittaEXP] Ponte Discord disabilitato.");
            return;
        }
        log.info("link command invoked player=" + player.getUniqueId() + " name=" + player.getName());
        var code = linkService.issueLinkCode(player.getUniqueId());
        String expires = TIME_FORMAT.format(code.expiresAt());
        if (!showVerifyDialog(player, code.code(), expires)) {
            sendLinkCard(player, code.code(), expires);
        }
    }

    public void openVerify(Player player) {
        if (!settings.enabled()) {
            log.debug("openVerify denied bridge disabled player=" + player.getUniqueId());
            player.sendMessage("[CittaEXP] Ponte Discord disabilitato.");
            return;
        }
        if (linkService.findLinkByMinecraft(player.getUniqueId()).isPresent()) {
            player.sendMessage("[CittaEXP] Il tuo account Discord e gia collegato.");
            return;
        }
        if (!showDialog(player, buildVerifyEntryDialog())) {
            player.sendMessage(MiniMessageHelper.parse(
                    "<dark_gray>[</dark_gray><gold>Discord</gold><dark_gray>]</dark_gray> "
                            + "<yellow>Usa</yellow> <gold>/discord</gold> <yellow>per entrare nel server e poi</yellow> <gold>/verify</gold><yellow>.</yellow>"));
        }
    }

    public boolean requireVerified(Player player, String actionLabel) {
        if (linkService.findLinkByMinecraft(player.getUniqueId()).isPresent()) {
            return true;
        }
        String resolvedAction = actionLabel == null || actionLabel.isBlank() ? "questa azione" : actionLabel;
        player.sendMessage(MiniMessageHelper.parse(
                "<dark_gray>[</dark_gray><gold>Discord</gold><dark_gray>]</dark_gray> "
                        + "<red>Devi verificarti su Discord prima di usare</red> <yellow>{action}</yellow><red>.</red> "
                        + "<yellow>Usa</yellow> <gold>/verify</gold><yellow>.</yellow>",
                Placeholder.unparsed("action", resolvedAction)
        ));
        openVerify(player);
        return false;
    }

    public void showStatus(Player player) {
        log.debug("status command invoked player=" + player.getUniqueId());
        Optional<DiscordLinkRepository.PlayerDiscordLink> linkOptional = linkService.findLinkByMinecraft(player.getUniqueId());
        if (linkOptional.isEmpty()) {
            player.sendMessage("[CittaEXP] Nessun account Discord collegato.");
            return;
        }
        DiscordLinkRepository.PlayerDiscordLink link = linkOptional.get();
        player.sendMessage("[CittaEXP] Discord collegato: " + safeName(link));
        player.sendMessage("[CittaEXP] User ID: " + Long.toUnsignedString(link.discordUserId()));
        player.sendMessage("[CittaEXP] Collegato il: " + TIME_FORMAT.format(link.linkedAt()));
    }

    public void unlink(Player player) {
        log.info("unlink command invoked player=" + player.getUniqueId() + " name=" + player.getName());
        if (!linkService.unlink(player.getUniqueId())) {
            player.sendMessage("[CittaEXP] Nessun collegamento Discord attivo da rimuovere.");
            return;
        }
        player.sendMessage("[CittaEXP] Collegamento Discord rimosso.");
    }

    public void resyncAll(org.bukkit.command.CommandSender sender) {
        log.info("staff requested global discord resync sender=" + sender.getName());
        townSyncService.reconcileAll();
        sender.sendMessage("[CittaEXP] Discord resync completo avviato.");
    }

    public void resyncTown(org.bukkit.command.CommandSender sender, String rawTownName) {
        log.info("staff requested town discord resync sender=" + sender.getName() + " town=" + rawTownName);
        Optional<Town> town = huskTownsApiHook.getTownByName(rawTownName);
        if (town.isEmpty()) {
            sender.sendMessage("[CittaEXP] Citta non trovata: " + rawTownName);
            return;
        }
        townSyncService.syncTown(town.get());
        sender.sendMessage("[CittaEXP] Discord resync avviato per " + town.get().getName() + ".");
    }

    public void lookup(org.bukkit.command.CommandSender sender, String rawPlayerName) {
        log.debug("staff lookup requested sender=" + sender.getName() + " player=" + rawPlayerName);
        OfflinePlayer offlinePlayer = resolvePlayer(rawPlayerName);
        if (offlinePlayer == null) {
            sender.sendMessage("[CittaEXP] Player non trovato: " + rawPlayerName);
            return;
        }
        Optional<DiscordLinkRepository.PlayerDiscordLink> linkOptional = linkService.findLinkByMinecraft(offlinePlayer.getUniqueId());
        if (linkOptional.isEmpty()) {
            sender.sendMessage("[CittaEXP] Nessun link Discord per " + offlinePlayer.getName() + ".");
            return;
        }
        DiscordLinkRepository.PlayerDiscordLink link = linkOptional.get();
        sender.sendMessage("[CittaEXP] " + offlinePlayer.getName() + " -> " + safeName(link)
                + " (" + Long.toUnsignedString(link.discordUserId()) + ")");
    }

    public void staffUnlink(org.bukkit.command.CommandSender sender, String rawPlayerName) {
        log.info("staff unlink requested sender=" + sender.getName() + " player=" + rawPlayerName);
        OfflinePlayer offlinePlayer = resolvePlayer(rawPlayerName);
        if (offlinePlayer == null) {
            sender.sendMessage("[CittaEXP] Player non trovato: " + rawPlayerName);
            return;
        }
        boolean removed = linkService.unlink(offlinePlayer.getUniqueId());
        if (!removed) {
            sender.sendMessage("[CittaEXP] Nessun link Discord da rimuovere per " + offlinePlayer.getName() + ".");
            return;
        }
        sender.sendMessage("[CittaEXP] Link Discord rimosso per " + offlinePlayer.getName() + ".");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!settings.enabled()) {
            return;
        }
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (linkService.findLinkByMinecraft(player.getUniqueId()).isPresent()) {
                return;
            }
            player.sendMessage(MiniMessageHelper.parse(
                    "<dark_gray>[</dark_gray><gold>Discord</gold><dark_gray>]</dark_gray> "
                            + "<gray>Il tuo account non e ancora collegato.</gray> "
                            + "<yellow>Usa</yellow> <gold>/verify</gold> <yellow>e poi</yellow> <gold>/link</gold> <yellow>nel canale Discord di verifica.</yellow>"));
        }, 60L);
    }

    private OfflinePlayer resolvePlayer(String rawPlayerName) {
        if (rawPlayerName == null || rawPlayerName.isBlank()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(rawPlayerName.trim());
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayer(rawPlayerName.trim());
    }

    private String safeName(DiscordLinkRepository.PlayerDiscordLink link) {
        if (link.discordGlobalName() != null && !link.discordGlobalName().isBlank()) {
            return link.discordGlobalName() + " (@" + link.discordUsername() + ")";
        }
        return "@" + link.discordUsername();
    }

    private Dialog buildVerifyDialog(String code, String expires) {
        DialogBase base = DialogBase.builder(MiniMessageHelper.parse("<gold>Verifica Discord</gold>"))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(List.of(
                        DialogBody.plainMessage(MiniMessageHelper.parse(
                                "<gray>Il tuo sigillo di verifica e pronto.</gray>")),
                        DialogBody.plainMessage(MiniMessageHelper.parse(
                                "<gray>Codice:</gray> <yellow><bold>{code}</bold></yellow>",
                                Placeholder.unparsed("code", code))),
                        DialogBody.plainMessage(MiniMessageHelper.parse(
                                "<gray>Scadenza:</gray> <white>{expires}</white>",
                                Placeholder.unparsed("expires", expires))),
                        DialogBody.plainMessage(MiniMessageHelper.parse(
                                "<gray>Copia il codice e poi usa</gray> <gold>/link</gold> <gray>nel canale Discord di verifica.</gray>"))))
                .build();

        return Dialog.create(factory -> {
            ActionButton copyButton = ActionButton.create(
                    MiniMessageHelper.parse("<yellow>Copia codice</yellow>"),
                    MiniMessageHelper.parse("<gray>Copia il comando completo di verifica</gray>"),
                    160,
                    DialogAction.staticAction(ClickEvent.copyToClipboard(verificationCommand(code)))
            );
            ActionButton nextButton = ActionButton.create(
                    MiniMessageHelper.parse("<gold>Ho copiato il codice</gold>"),
                    MiniMessageHelper.parse("<gray>Procedi alla schermata del canale verifica</gray>"),
                    220,
                    DialogAction.customClick((response, audience) -> openVerifyOpenDiscordStep(audience, code, expires), CLICK_OPTIONS)
            );
            ActionButton closeButton = ActionButton.create(
                    MiniMessageHelper.parse("<gray>Chiudi</gray>"),
                    null,
                    120,
                    null
            );
            factory.empty()
                    .base(base)
                    .type(DialogType.multiAction(List.of(copyButton, nextButton), closeButton, 2));
        });
    }

    private Dialog buildVerifyEntryDialog() {
        DialogBase base = DialogBase.builder(MiniMessageHelper.parse("<gold>Verifica Discord</gold>"))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(
                        DialogBody.plainMessage(MiniMessageHelper.parse(
                                "<gray>Confermi di essere sul server di mcexp?</gray>"))))
                .build();

        return Dialog.create(factory -> {
            ActionButton yesButton = ActionButton.create(
                    MiniMessageHelper.parse("<gold>Si</gold>"),
                    MiniMessageHelper.parse("<gray>Continua con la verifica</gray>"),
                    180,
                    DialogAction.customClick((response, audience) -> openVerifyCodeFlow(audience), CLICK_OPTIONS)
            );
            ActionButton noButton = ActionButton.create(
                    MiniMessageHelper.parse("<red>No</red>"),
                    MiniMessageHelper.parse("<gray>Apri il comando Discord del server</gray>"),
                    140,
                    DialogAction.customClick((response, audience) -> openDiscordCommand(audience), CLICK_OPTIONS)
            );
            ActionButton closeButton = ActionButton.create(
                    MiniMessageHelper.parse("<gray>Chiudi</gray>"),
                    null,
                    120,
                    null
            );
            factory.empty()
                    .base(base)
                    .type(DialogType.multiAction(List.of(yesButton, noButton), closeButton, 2));
        });
    }

    private Dialog buildVerifyOpenDiscordDialog(String code, String expires) {
        DialogBase base = DialogBase.builder(MiniMessageHelper.parse("<gold>Apri Discord</gold>"))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(
                        DialogBody.plainMessage(MiniMessageHelper.parse(
                                "<gray>Il codice e stato copiato o e visibile qui sotto.</gray>")),
                        DialogBody.plainMessage(MiniMessageHelper.parse(
                                "<gray>Codice:</gray> <yellow><bold>{code}</bold></yellow>",
                                Placeholder.unparsed("code", code))),
                        DialogBody.plainMessage(MiniMessageHelper.parse(
                                "<gray>Scadenza:</gray> <white>{expires}</white>",
                                Placeholder.unparsed("expires", expires))),
                        DialogBody.plainMessage(MiniMessageHelper.parse(
                                "<gray>Apri il canale Discord di verifica e usa</gray> <gold>/link</gold> <gray>incollando il codice.</gray>"))))
                .build();

        return Dialog.create(factory -> {
            ActionButton openDiscordButton = ActionButton.create(
                    MiniMessageHelper.parse("<gold>Apri canale verifica</gold>"),
                    MiniMessageHelper.parse("<gray>Apri direttamente il canale Discord di verifica</gray>"),
                    200,
                    DialogAction.staticAction(ClickEvent.openUrl(verificationChannelUrl()))
            );
            ActionButton backButton = ActionButton.create(
                    MiniMessageHelper.parse("<yellow>Indietro</yellow>"),
                    MiniMessageHelper.parse("<gray>Torna al dialog del codice</gray>"),
                    140,
                    DialogAction.customClick((response, audience) -> openVerifyPrimaryStep(audience, code, expires), CLICK_OPTIONS)
            );
            ActionButton closeButton = ActionButton.create(
                    MiniMessageHelper.parse("<gray>Chiudi</gray>"),
                    null,
                    120,
                    null
            );
            factory.empty()
                    .base(base)
                    .type(DialogType.multiAction(List.of(openDiscordButton, backButton), closeButton, 2));
        });
    }

    private void openVerifyOpenDiscordStep(net.kyori.adventure.audience.Audience audience, String code, String expires) {
        if (!(audience instanceof Player player)) {
            return;
        }
        if (!showDialog(player, buildVerifyOpenDiscordDialog(code, expires))) {
            sendLinkCard(player, code, expires);
        }
    }

    private void openVerifyCodeFlow(net.kyori.adventure.audience.Audience audience) {
        if (!(audience instanceof Player player)) {
            return;
        }
        if (!player.isOnline()) {
            return;
        }
        var code = linkService.issueLinkCode(player.getUniqueId());
        String expires = TIME_FORMAT.format(code.expiresAt());
        if (!showVerifyDialog(player, code.code(), expires)) {
            sendLinkCard(player, code.code(), expires);
        }
    }

    private void openDiscordCommand(net.kyori.adventure.audience.Audience audience) {
        if (!(audience instanceof Player player)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> player.performCommand("discord"));
    }

    private void openVerifyPrimaryStep(net.kyori.adventure.audience.Audience audience, String code, String expires) {
        if (!(audience instanceof Player player)) {
            return;
        }
        if (!showDialog(player, buildVerifyDialog(code, expires))) {
            sendLinkCard(player, code, expires);
        }
    }

    private boolean showVerifyDialog(Player player, String code, String expires) {
        return showDialog(player, buildVerifyDialog(code, expires));
    }

    private boolean showDialog(Player player, Dialog dialog) {
        try {
            for (Method method : player.getClass().getMethods()) {
                if (!method.getName().equals("showDialog") || method.getParameterCount() != 1) {
                    continue;
                }
                method.invoke(player, dialog);
                return true;
            }
        } catch (ReflectiveOperationException exception) {
            log.warn("verify dialog open failed player=" + player.getUniqueId(), exception);
        }
        player.sendMessage(MiniMessageHelper.parse("<red>Dialog non disponibile su questo server.</red>"));
        return false;
    }

    private void sendLinkCard(Player player, String code, String expires) {
        player.sendMessage(MiniMessageHelper.parse(
                "<dark_gray>┌────────────────────────────────────────────┐</dark_gray>"));
        player.sendMessage(MiniMessageHelper.parse(
                "<gold>Discord Verifica</gold> <gray>• usa il codice nel canale Discord di verifica</gray>"));
        player.sendMessage(MiniMessageHelper.parse(
                "<gray>Codice:</gray> <yellow><bold>{code}</bold></yellow> <dark_gray>|</dark_gray> <gray>Scade:</gray> <white>{expires}</white>",
                Placeholder.unparsed("code", code),
                Placeholder.unparsed("expires", expires)
        ));
        player.sendMessage(MiniMessageHelper.parse(
                "<gray>Su Discord usa:</gray> <gold>/link</gold> <gray>nel canale di verifica.</gray>"));
        player.sendMessage(buildActionRow(code));
        player.sendMessage(MiniMessageHelper.parse(
                "<dark_gray>└────────────────────────────────────────────┘</dark_gray>"));
    }

    private Component buildActionRow(String code) {
        Component copyButton = MiniMessageHelper.parse(
                "<click:suggest_command:'{command}'><hover:show_text:'<gray>Copia il comando completo /link</gray>'>"
                        + "<yellow><bold>[ Copia codice ]</bold></yellow></hover></click>",
                Placeholder.unparsed("command", verificationCommand(code))
        );
        Component openButton = MiniMessageHelper.parse("<gold><bold>[ Apri canale verifica ]</bold></gold>")
                .clickEvent(ClickEvent.openUrl(verificationChannelUrl()))
                .hoverEvent(HoverEvent.showText(MiniMessageHelper.parse("<gray>Apri il canale Discord di verifica</gray>")));
        return copyButton.append(MiniMessageHelper.parse(" <dark_gray>•</dark_gray> ")).append(openButton);
    }

    private String verificationChannelUrl() {
        if (!settings.verificationUrl().isBlank()) {
            return settings.verificationUrl();
        }
        if (settings.verificationChannelConfigured()) {
            return "https://discord.com/channels/"
                    + Long.toUnsignedString(settings.guildId())
                    + "/"
                    + Long.toUnsignedString(settings.verificationChannelId());
        }
        return "https://discord.com/channels/" + Long.toUnsignedString(settings.guildId());
    }

    private String verificationCommand(String code) {
        return VERIFY_COMMAND_PREFIX + code;
    }
}
