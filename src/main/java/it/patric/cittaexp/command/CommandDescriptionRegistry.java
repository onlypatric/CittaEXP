package it.patric.cittaexp.command;

import it.patric.cittaexp.utils.PluginConfigUtils;
import java.util.List;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

public final class CommandDescriptionRegistry {

    public enum Scope {
        CITY,
        CITTAEXP
    }

    public record CommandDescription(String key, String syntax, String description, String example) {
    }

    private static final List<CommandDescription> CITY = List.of(
            new CommandDescription("root", "/city", "Apri il City Hub principale della tua citta.", ""),
            new CommandDescription("help", "/city help", "Mostra questa guida rapida dei comandi /city.", ""),
            new CommandDescription("create", "/city create", "Avvia la creazione di una nuova citta.", "/city create"),
            new CommandDescription("list", "/city list", "Apri la lista citta con ordinamenti e warp.", "/city list"),
            new CommandDescription("invite", "/city invite <player>", "Invita un giocatore nella tua citta.", "/city invite Notch"),
            new CommandDescription("invite_accept", "/city invite accept [player]", "Accetta un invito ricevuto.", "/city invite accept"),
            new CommandDescription("invite_decline", "/city invite decline [player]", "Rifiuta un invito ricevuto.", "/city invite decline"),
            new CommandDescription("redeem", "/city redeem", "Riscatta nel tuo inventario le reward citta rimaste fuori dal vault pieno.", "/city redeem"),
            new CommandDescription("section", "/city section", "Apri la mappa territori GUI con modalita Claim/Tipo/Info.", "/city section"),
            new CommandDescription("warp", "/city warp [town]", "Teletrasporto al warp della tua citta o di una citta pubblica.", "/city warp"),
            new CommandDescription("setwarp", "/city setwarp", "Imposta lo spawn/warp della tua citta nella posizione attuale.", "/city setwarp"),
            new CommandDescription("chat", "/city chat <messaggio>", "Invia un messaggio nella chat citta.", "/city chat Ciao team"),
            new CommandDescription("discord_link", "/city discord link", "Genera un codice per collegare il tuo account Discord via DM al bot.", "/city discord link"),
            new CommandDescription("discord_status", "/city discord status", "Mostra lo stato del tuo collegamento Discord.", "/city discord status"),
            new CommandDescription("discord_unlink", "/city discord unlink", "Rimuove il collegamento tra Minecraft e Discord.", "/city discord unlink")
    );

    private static final List<CommandDescription> CITTAEXP = List.of(
            new CommandDescription("root", "/cittaexp", "Comando root staff/diagnostica CittaEXP.", ""),
            new CommandDescription("help", "/cittaexp help", "Mostra questa guida rapida dei comandi staff.", ""),
            new CommandDescription("reload", "/cittaexp reload", "Ricarica config.yml e tutti gli YAML di CittaEXP riavviando il plugin.", "/cittaexp reload"),
            new CommandDescription("who", "/cittaexp who <player>", "Mostra il Discord collegato a un account Minecraft.", "/cittaexp who Notch"),
            new CommandDescription("probe", "/cittaexp probe", "Mostra diagnostica integrazioni/runtime/challenges.", "/cittaexp probe"),
            new CommandDescription("admin_challenges_regenerate", "/cittaexp admin challenges regenerate", "Rigenera tutte le sfide attive del ciclo corrente (reset runtime).", "/cittaexp admin challenges regenerate"),
            new CommandDescription("admin_events_list", "/cittaexp admin challenges events list", "Mostra il riepilogo degli staff events attivi e storici.", "/cittaexp admin challenges events list"),
            new CommandDescription("admin_events_create_auto", "/cittaexp admin challenges events create auto", "Apre il dialog per creare una bozza AUTO_RACE.", "/cittaexp admin challenges events create auto"),
            new CommandDescription("admin_events_create_judged", "/cittaexp admin challenges events create judged", "Apre il dialog per creare una bozza JUDGED_BUILD.", "/cittaexp admin challenges events create judged"),
            new CommandDescription("admin_events_publish", "/cittaexp admin challenges events publish <eventId>", "Pubblica uno staff event e lo rende visibile ai player.", "/cittaexp admin challenges events publish auto-build-123"),
            new CommandDescription("admin_events_review", "/cittaexp admin challenges events review <eventId> <citta>", "Apre il dialog staff di review per una submission build.", "/cittaexp admin challenges events review build-mensile Roma"),
            new CommandDescription("admin_events_reward", "/cittaexp admin challenges events reward <eventId> <citta>", "Apre il dialog di reward manuale per una submission evento.", "/cittaexp admin challenges events reward build-mensile Roma"),
            new CommandDescription("admin_defense_force_start", "/cittaexp admin defense force-start <tier>", "Avvia una Mob invasion sulla tua citta bypassando livello, costo e cooldown.", "/cittaexp admin defense force-start l25"),
            new CommandDescription("admin_defense_live_sample", "/cittaexp admin defense live-sample", "Apre una preview della schermata live difesa con dati sample.", "/cittaexp admin defense live-sample"),
            new CommandDescription("admin_defense_force_stop", "/cittaexp admin defense force-stop", "Termina la Mob invasion attiva della tua citta senza reward e senza cooldown.", "/cittaexp admin defense force-stop"),
            new CommandDescription("admin_war_sample", "/cittaexp admin war sample <list|live|incoming|busy|unavailable|ally-warning|declare>", "Apre una preview staff delle GUI/dialog della sezione guerre.", "/cittaexp admin war sample live"),
            new CommandDescription("admin_relations_sample", "/cittaexp admin relations sample", "Apre una preview del registro diplomatico con citta sample.", "/cittaexp admin relations sample"),
            new CommandDescription("admin_discord_resync", "/cittaexp admin discord resync <all|town>", "Riallinea ruoli/canali Discord per tutte le citta o per una citta specifica.", "/cittaexp admin discord resync all"),
            new CommandDescription("admin_discord_lookup", "/cittaexp admin discord lookup <player>", "Mostra il collegamento Discord associato a un player.", "/cittaexp admin discord lookup Notch"),
            new CommandDescription("admin_discord_unlink", "/cittaexp admin discord unlink <player>", "Rimuove staff-side il collegamento Discord di un player.", "/cittaexp admin discord unlink Notch"),
            new CommandDescription("admin_claims_doctor", "/cittaexp admin claims doctor", "Diagnostica duplicati dei confini e mismatch tra database e runtime claim.", "/cittaexp admin claims doctor"),
            new CommandDescription("admin_claims_get", "/cittaexp admin claims get <citta>", "Mostra cap base, bonus admin, cap totale e claim usati di una citta.", "/cittaexp admin claims get Roma"),
            new CommandDescription("admin_claims_set", "/cittaexp admin claims set <citta> <amount>", "Imposta il bonus claim admin separato di una citta.", "/cittaexp admin claims set Roma 12"),
            new CommandDescription("admin_bank_get", "/cittaexp admin bank get <citta>", "Mostra il saldo attuale del tesoro di una citta.", "/cittaexp admin bank get Roma"),
            new CommandDescription("admin_bank_add", "/cittaexp admin bank add <citta> <amount>", "Aggiunge soldi al tesoro di una citta.", "/cittaexp admin bank add Roma 50000"),
            new CommandDescription("admin_bank_take", "/cittaexp admin bank take <citta> <amount>", "Rimuove soldi dal tesoro di una citta.", "/cittaexp admin bank take Roma 25000"),
            new CommandDescription("admin_vault_open", "/cittaexp admin vault open <citta>", "Apre l'Item Vault di una citta in modalita admin.", "/cittaexp admin vault open Roma"),
            new CommandDescription("admin_xp_get", "/cittaexp admin xp get <citta>", "Mostra XP, level e stage della citta.", "/cittaexp admin xp get Roma"),
            new CommandDescription("admin_xp_add", "/cittaexp admin xp add <citta> <amount>", "Aggiunge XP citta.", "/cittaexp admin xp add Roma 2500"),
            new CommandDescription("admin_xp_take", "/cittaexp admin xp take <citta> <amount>", "Rimuove XP citta.", "/cittaexp admin xp take Roma 500"),
            new CommandDescription("admin_xp_set", "/cittaexp admin xp set <citta> <amount>", "Imposta l'XP totale della citta.", "/cittaexp admin xp set Roma 10000"),
            new CommandDescription("admin_hopper_here", "/cittaexp admin hopper here", "Mostra quanti hopper ci sono nel chunk attuale e quanto manca al cap.", "/cittaexp admin hopper here"),
            new CommandDescription("admin_hopper_chunk", "/cittaexp admin hopper chunk <x> <z>", "Mostra il conteggio hopper per un chunk specifico nel mondo corrente.", "/cittaexp admin hopper chunk 12 -4"),
            new CommandDescription("admin_economy_probe", "/cittaexp admin economy probe", "Mostra snapshot inflazione, moltiplicatori e metriche economia citta.", "/cittaexp admin economy probe"),
            new CommandDescription("admin_economy_recalc", "/cittaexp admin economy recalc", "Ricalcola subito l'indice inflazione dal tesoro citta e dalle emissioni recenti.", "/cittaexp admin economy recalc"),
            new CommandDescription("admin_economy_freeze", "/cittaexp admin economy freeze", "Congela l'indice inflazione attuale.", "/cittaexp admin economy freeze"),
            new CommandDescription("admin_economy_unfreeze", "/cittaexp admin economy unfreeze", "Sblocca e ricalcola l'indice inflazione.", "/cittaexp admin economy unfreeze"),
            new CommandDescription("admin_economy_set_bias", "/cittaexp admin economy set-bias <percent>", "Applica un bias manuale percentuale all'inflazione.", "/cittaexp admin economy set-bias 12.5"),
            new CommandDescription("admin_economy_reset_bias", "/cittaexp admin economy reset-bias", "Rimuove il bias manuale dell'inflazione.", "/cittaexp admin economy reset-bias"),
            new CommandDescription("admin_level_list", "/cittaexp admin level list [status]", "Lista richieste upgrade livello citta.", "/cittaexp admin level list pending"),
            new CommandDescription("admin_level_approve", "/cittaexp admin level approve <id> [note]", "Approva una richiesta livello.", "/cittaexp admin level approve 12 ok"),
            new CommandDescription("admin_level_reject", "/cittaexp admin level reject <id> <reason>", "Rifiuta una richiesta livello.", "/cittaexp admin level reject 12 requisiti mancanti"),
            new CommandDescription("admin_town_info", "/cittaexp admin town info <citta>", "Mostra un riepilogo completo della citta.", "/cittaexp admin town info Roma"),
            new CommandDescription("admin_town_players", "/cittaexp admin town players <citta>", "Apri la GUI membri/gestione in modalita staff target.", "/cittaexp admin town players Roma"),
            new CommandDescription("admin_town_fulltakeover", "/cittaexp admin town fulltakeover <citta>", "Assumi la proprieta della citta come admin.", "/cittaexp admin town fulltakeover Roma"),
            new CommandDescription("admin_town_delete", "/cittaexp admin town delete <citta>", "Elimina una citta come admin.", "/cittaexp admin town delete Roma")
    );

    private final PluginConfigUtils cfg;

    public CommandDescriptionRegistry(Plugin plugin) {
        this.cfg = new PluginConfigUtils(plugin);
    }

    public void sendHelp(CommandSender sender, Scope scope) {
        String scopeKey = scope == Scope.CITY ? "city" : "cittaexp";
        sender.sendMessage(cfg.msg(
                "commands.help." + scopeKey + ".header",
                "<gold>Guida comandi</gold> <gray>(" + scopeKey + ")</gray>"
        ));
        for (CommandDescription entry : list(scope)) {
            String resolvedSyntax = cfg.cfgString("commands.descriptions." + scopeKey + "." + entry.key + ".syntax", entry.syntax);
            String resolvedDescription = cfg.cfgString("commands.descriptions." + scopeKey + "." + entry.key + ".description", entry.description);
            String resolvedExample = cfg.cfgString("commands.descriptions." + scopeKey + "." + entry.key + ".example", entry.example);

            sender.sendMessage(cfg.msg(
                    "commands.help." + scopeKey + ".row",
                    "<gray>{syntax}</gray> <dark_gray>-</dark_gray> <white>{description}</white>",
                    Placeholder.unparsed("syntax", resolvedSyntax),
                    Placeholder.unparsed("description", resolvedDescription)
            ));
            if (resolvedExample != null && !resolvedExample.isBlank()) {
                sender.sendMessage(cfg.msg(
                        "commands.help." + scopeKey + ".example",
                        "<dark_gray>esempio:</dark_gray> <gray>{example}</gray>",
                        Placeholder.unparsed("example", resolvedExample)
                ));
            }
        }
    }

    public String suggestionTooltip(String key, String fallback) {
        return cfg.cfgString("commands.suggestions." + key, fallback);
    }

    private static List<CommandDescription> list(Scope scope) {
        return scope == Scope.CITY ? CITY : CITTAEXP;
    }
}
