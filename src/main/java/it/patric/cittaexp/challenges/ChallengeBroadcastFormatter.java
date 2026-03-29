package it.patric.cittaexp.challenges;

import it.patric.cittaexp.levels.TownStage;
import it.patric.cittaexp.text.UiChatStyle;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;

public final class ChallengeBroadcastFormatter {
    private static final Set<String> REWARD_KEYS = Set.of(
            "challenge_complete_broadcast",
            "challenge_excellence_broadcast",
            "race_winner_broadcast",
            "race_top_2_broadcast",
            "race_top_3_broadcast",
            "race_threshold_broadcast",
            "race_pvp_legend_broadcast",
            "event_top_1_broadcast",
            "event_top_2_broadcast",
            "event_top_3_broadcast",
            "event_threshold_broadcast",
            "milestone_weekly_1",
            "milestone_weekly_2",
            "milestone_weekly_3",
            "milestone_season_final"
    );
    private static final Set<Material> PRESTIGE_VAULT_ITEMS = Set.of(
            Material.DIAMOND,
            Material.DIAMOND_BLOCK,
            Material.EMERALD,
            Material.EMERALD_BLOCK,
            Material.NETHERITE_INGOT,
            Material.NETHERITE_BLOCK,
            Material.ANCIENT_DEBRIS,
            Material.NETHERITE_SCRAP,
            Material.BEACON,
            Material.TOTEM_OF_UNDYING,
            Material.ENCHANTED_GOLDEN_APPLE,
            Material.END_CRYSTAL,
            Material.NETHER_STAR,
            Material.ECHO_SHARD
    );

    private ChallengeBroadcastFormatter() {
    }

    public static boolean handlesRewardKey(String commandKey) {
        if (commandKey == null || commandKey.isBlank()) {
            return false;
        }
        return REWARD_KEYS.contains(commandKey.trim().toLowerCase(Locale.ROOT));
    }

    public static String renderReward(String commandKey, ChallengeInstance instance, String townName, String cycleKey) {
        return renderReward(commandKey, instance, townName, cycleKey, ChallengeRewardSpec.EMPTY);
    }

    public static String renderReward(
            String commandKey,
            ChallengeInstance instance,
            String townName,
            String cycleKey,
            ChallengeRewardSpec rewardSpec
    ) {
        if (!handlesRewardKey(commandKey)) {
            return "";
        }
        String normalized = commandKey.trim().toLowerCase(Locale.ROOT);
        BroadcastView view = switch (normalized) {
            case "challenge_complete_broadcast" -> rewardView(
                    "SFIDA COMPLETATA",
                    instance,
                    townName,
                    cycleKey,
                    "Obiettivo completato",
                    rewardSpec,
                    fallbackTitle(instance, "Sfida cittadina")
            );
            case "challenge_excellence_broadcast" -> rewardView(
                    "ECCELLENZA COMPLETATA",
                    instance,
                    townName,
                    cycleKey,
                    "Eccellenza completata",
                    rewardSpec,
                    fallbackTitle(instance, "Sfida d'eccellenza")
            );
            case "race_winner_broadcast" -> rewardView(
                    "PRIMO POSTO DELLA GARA",
                    instance,
                    townName,
                    cycleKey,
                    "Primo posto nella corsa",
                    rewardSpec,
                    fallbackTitle(instance, "Corsa cittadina")
            );
            case "race_top_2_broadcast" -> rewardView(
                    "SECONDO POSTO DELLA GARA",
                    instance,
                    townName,
                    cycleKey,
                    "Secondo posto nella corsa",
                    rewardSpec,
                    fallbackTitle(instance, "Corsa cittadina")
            );
            case "race_top_3_broadcast" -> rewardView(
                    "TERZO POSTO DELLA GARA",
                    instance,
                    townName,
                    cycleKey,
                    "Terzo posto nella corsa",
                    rewardSpec,
                    fallbackTitle(instance, "Corsa cittadina")
            );
            case "race_threshold_broadcast" -> rewardView(
                    "TRAGUARDO DELLA GARA",
                    instance,
                    townName,
                    cycleKey,
                    "Traguardo competitivo raggiunto",
                    rewardSpec,
                    fallbackTitle(instance, "Corsa cittadina")
            );
            case "race_pvp_legend_broadcast" -> rewardView(
                    "VITTORIA PVP DEL GIORNO",
                    instance,
                    townName,
                    cycleKey,
                    "Primo posto nella gara PVP",
                    rewardSpec,
                    fallbackTitle(instance, "Gara PVP")
            );
            case "event_top_1_broadcast" -> rewardView(
                    "PRIMO POSTO DEL MESE",
                    instance,
                    townName,
                    cycleKey,
                    "Primo posto dell'evento",
                    rewardSpec,
                    fallbackTitle(instance, "Evento mensile")
            );
            case "event_top_2_broadcast" -> rewardView(
                    "SECONDO POSTO DEL MESE",
                    instance,
                    townName,
                    cycleKey,
                    "Secondo posto dell'evento",
                    rewardSpec,
                    fallbackTitle(instance, "Evento mensile")
            );
            case "event_top_3_broadcast" -> rewardView(
                    "TERZO POSTO DEL MESE",
                    instance,
                    townName,
                    cycleKey,
                    "Terzo posto dell'evento",
                    rewardSpec,
                    fallbackTitle(instance, "Evento mensile")
            );
            case "event_threshold_broadcast" -> rewardView(
                    "TRAGUARDO DEL MESE",
                    instance,
                    townName,
                    cycleKey,
                    "Traguardo evento raggiunto",
                    rewardSpec,
                    fallbackTitle(instance, "Evento mensile")
            );
            case "milestone_weekly_1" -> simpleRewardView(
                    "PRIMO TRAGUARDO SETTIMANALE",
                    townName,
                    cycleKey,
                    "Soglia settimanale I raggiunta",
                    rewardSpec,
                    "Riepilogo settimanale"
            );
            case "milestone_weekly_2" -> simpleRewardView(
                    "SECONDO TRAGUARDO SETTIMANALE",
                    townName,
                    cycleKey,
                    "Soglia settimanale II raggiunta",
                    rewardSpec,
                    "Riepilogo settimanale"
            );
            case "milestone_weekly_3" -> simpleRewardView(
                    "TERZO TRAGUARDO SETTIMANALE",
                    townName,
                    cycleKey,
                    "Soglia settimanale III raggiunta",
                    rewardSpec,
                    "Riepilogo settimanale"
            );
            case "milestone_season_final" -> simpleRewardView(
                    "TRAGUARDO FINALE DI STAGIONE",
                    townName,
                    cycleKey,
                    "Traguardo finale stagionale raggiunto",
                    rewardSpec,
                    "Riepilogo stagione"
            );
            default -> null;
        };
        return view == null ? "" : renderBox(view);
    }

    public static String renderRaceStarted(ChallengeInstance instance, ChallengeRewardPreview rewardPreview) {
        List<UiChatStyle.DetailLine> details = new ArrayList<>();
        details.addAll(contextDetails(null, instance == null ? null : instance.cycleKey()));
        details.addAll(rewardPreviewDetails(rewardPreview));
        String objective = objectiveLine(instance);
        if (!objective.isBlank()) {
            details.add(UiChatStyle.detail(UiChatStyle.DetailType.GOAL, "Obiettivo: " + objective));
        }
        details.add(UiChatStyle.detail(UiChatStyle.DetailType.NOTE,
                "La prima citta a completare la sfida prende il premio del ciclo."));
        return renderBox(new BroadcastView(
                "SFIDA GLOBALE CITTA'",
                fallbackTitle(instance, "Corsa cittadina"),
                "La competizione e ora aperta",
                List.copyOf(details)
        ));
    }

    public static String renderMonthlyEventStarted(ChallengeMode mode, String cycleKey) {
        String title = switch (mode) {
            case MONTHLY_CROWN -> "Corona del mese";
            case MONTHLY_EVENT_A -> "Evento mensile A";
            case MONTHLY_EVENT_B -> "Evento mensile B";
            default -> "Evento mensile";
        };
        return renderBox(new BroadcastView(
                "EVENTO DEL MESE",
                title,
                "La finestra mensile e stata aperta",
                List.of(
                        UiChatStyle.detail(UiChatStyle.DetailType.CONTEXT, "Ciclo: " + normalizeCycle(cycleKey)),
                        UiChatStyle.detail(UiChatStyle.DetailType.STATUS, "Il podio e ora aperto a tutte le citta."),
                        UiChatStyle.detail(UiChatStyle.DetailType.NOTE, "Chi chiude piu obiettivi nel ciclo sale in classifica.")
                )
        ));
    }

    public static String renderRaceCompleted(ChallengeInstance instance, String townName) {
        return renderBox(new BroadcastView(
                "GARA CONCLUSA",
                fallbackTitle(instance, "Corsa cittadina"),
                "La finestra competitiva si e chiusa",
                List.of(
                        UiChatStyle.detail(UiChatStyle.DetailType.CONTEXT, "Citta vincente: " + normalizeTownName(townName)),
                        UiChatStyle.detail(UiChatStyle.DetailType.CONTEXT, "Ciclo: " + normalizeCycle(instance == null ? null : instance.cycleKey())),
                        UiChatStyle.detail(UiChatStyle.DetailType.STATUS, "La classifica finale e stata registrata."),
                        UiChatStyle.detail(UiChatStyle.DetailType.NOTE, "I premi vengono distribuiti ai vincitori del ciclo.")
                )
        ));
    }

    public static String renderMonthlyEventCompleted(ChallengeInstance instance, String townName) {
        return renderBox(new BroadcastView(
                "MESE CONCLUSO",
                fallbackTitle(instance, "Evento mensile"),
                "La finestra del mese si e chiusa",
                List.of(
                        UiChatStyle.detail(UiChatStyle.DetailType.CONTEXT, "Citta in evidenza: " + normalizeTownName(townName)),
                        UiChatStyle.detail(UiChatStyle.DetailType.CONTEXT, "Ciclo: " + normalizeCycle(instance == null ? null : instance.cycleKey())),
                        UiChatStyle.detail(UiChatStyle.DetailType.STATUS, "Il podio mensile e stato definito."),
                        UiChatStyle.detail(UiChatStyle.DetailType.NOTE, "Controlla classifica e premi del mese.")
                )
        ));
    }

    public static String renderCodexProgress(String townName, ChallengeObjectiveType type, int points, int nextThreshold) {
        List<UiChatStyle.DetailLine> details = new ArrayList<>();
        details.add(UiChatStyle.detail(UiChatStyle.DetailType.CONTEXT, "Citta': " + normalizeTownName(townName)));
        details.add(UiChatStyle.detail(UiChatStyle.DetailType.STATUS, "+" + Math.max(0, points) + " punti stagionali registrati"));
        details.add(UiChatStyle.detail(
                nextThreshold > 0 ? UiChatStyle.DetailType.GOAL : UiChatStyle.DetailType.NOTE,
                nextThreshold > 0
                        ? "Prossimo traguardo: " + nextThreshold + " punti"
                        : "Tutte le soglie attuali sono gia state superate"
        ));
        return renderBox(new BroadcastView(
                "CODEX STAGIONALE",
                objectiveLabel(type),
                "Progressi stagionali aggiornati",
                List.copyOf(details)
        ));
    }

    public static String renderCodexMilestone(int threshold) {
        return renderBox(new BroadcastView(
                "TRAGUARDO STAGIONALE",
                "Soglia stagionale raggiunta",
                "Nuovo traguardo registrato",
                List.of(
                        UiChatStyle.detail(UiChatStyle.DetailType.CONTEXT, "Soglia: " + Math.max(0, threshold) + " punti"),
                        UiChatStyle.detail(UiChatStyle.DetailType.REWARD, "+XP citta per il traguardo stagionale")
                )
        ));
    }

    public static String renderCityLevelUp(String townName, int level) {
        return renderBox(new BroadcastView(
                "ASCESA CITTADINA",
                "Livello citta aumentato",
                "La citta ha raggiunto il livello " + Math.max(0, level),
                List.of(
                        UiChatStyle.detail(UiChatStyle.DetailType.CONTEXT, "Citta': " + normalizeTownName(townName)),
                        UiChatStyle.detail(UiChatStyle.DetailType.STATUS, "Il nuovo livello e ora attivo per tutta la citta.")
                )
        ));
    }

    public static String renderStageUpgrade(String townName, TownStage stage) {
        String stageName = stage == null ? "Nuovo grado" : safe(stage.displayName());
        return renderBox(new BroadcastView(
                "NUOVO GRADO CITTADINO",
                stageName,
                "Lo stage della citta e stato confermato",
                List.of(
                        UiChatStyle.detail(UiChatStyle.DetailType.CONTEXT, "Citta': " + normalizeTownName(townName)),
                        UiChatStyle.detail(UiChatStyle.DetailType.STATUS, "Nuovo stage attivo: " + stageName)
                )
        ));
    }

    private static BroadcastView rewardView(
            String header,
            ChallengeInstance instance,
            String townName,
            String cycleKey,
            String status,
            ChallengeRewardSpec rewardSpec,
            String title
    ) {
        List<UiChatStyle.DetailLine> details = new ArrayList<>();
        details.addAll(contextDetails(townName, cycleKey == null && instance != null ? instance.cycleKey() : cycleKey));
        String objective = objectiveLine(instance);
        if (!objective.isBlank()) {
            details.add(UiChatStyle.detail(UiChatStyle.DetailType.GOAL, "Obiettivo: " + objective));
        }
        details.addAll(rewardSpecDetails(rewardSpec));
        return new BroadcastView(header, title, status, List.copyOf(details));
    }

    private static BroadcastView simpleRewardView(
            String header,
            String townName,
            String cycleKey,
            String status,
            ChallengeRewardSpec rewardSpec,
            String title
    ) {
        List<UiChatStyle.DetailLine> details = new ArrayList<>(contextDetails(townName, cycleKey));
        details.addAll(rewardSpecDetails(rewardSpec));
        return new BroadcastView(header, title, status, List.copyOf(details));
    }

    private static String renderBox(BroadcastView view) {
        return UiChatStyle.render(
                safe(view.header()),
                safe(view.titleLine()),
                safe(view.statusLine()),
                view.details()
        );
    }

    private static List<UiChatStyle.DetailLine> contextDetails(String townName, String cycleKey) {
        List<UiChatStyle.DetailLine> details = new ArrayList<>();
        if (townName != null || cycleKey != null) {
            details.add(UiChatStyle.detail(UiChatStyle.DetailType.CONTEXT, "Citta': " + normalizeTownName(townName)));
            details.add(UiChatStyle.detail(UiChatStyle.DetailType.CONTEXT, "Stagione: " + normalizeCycle(cycleKey)));
        }
        return List.copyOf(details);
    }

    private static List<UiChatStyle.DetailLine> rewardSpecDetails(ChallengeRewardSpec rewardSpec) {
        if (rewardSpec == null || rewardSpec.empty()) {
            return List.of(UiChatStyle.detail(UiChatStyle.DetailType.NOTE, "Nessun premio extra registrato per questa sfida."));
        }
        List<UiChatStyle.DetailLine> details = new ArrayList<>();
        if (rewardSpec.xpCity() > 0.0D) {
            details.add(UiChatStyle.detail(UiChatStyle.DetailType.REWARD, "+" + formatNumber(rewardSpec.xpCity()) + " XP citta"));
        }
        if (rewardSpec.moneyCity() > 0.0D) {
            details.add(UiChatStyle.detail(UiChatStyle.DetailType.REWARD, "+" + formatNumber(rewardSpec.moneyCity()) + " Monete citta"));
        }
        if (rewardSpec.vaultItems() != null && !rewardSpec.vaultItems().isEmpty()) {
            rewardSpec.vaultItems().entrySet().stream()
                    .filter(entry -> entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0)
                    .sorted(Comparator.comparing(entry -> ChallengeLoreFormatter.materialLabel(entry.getKey()), String.CASE_INSENSITIVE_ORDER))
                    .forEach(entry -> details.add(UiChatStyle.detail(
                            PRESTIGE_VAULT_ITEMS.contains(entry.getKey()) ? UiChatStyle.DetailType.SPECIAL : UiChatStyle.DetailType.REWARD,
                            entry.getValue() + "x " + ChallengeLoreFormatter.materialLabel(entry.getKey())
                    )));
        }
        if (rewardSpec.hasPersonalRewards()) {
            details.add(UiChatStyle.detail(
                    UiChatStyle.DetailType.REWARD,
                    "+" + formatNumber(rewardSpec.personalXpCityBonus()) + "% bonus XP citta personale"
            ));
        }
        if ((rewardSpec.commandKeys() != null && !rewardSpec.commandKeys().isEmpty())
                || (rewardSpec.consoleCommands() != null && !rewardSpec.consoleCommands().isEmpty())) {
            int count = Math.max(
                    rewardSpec.commandKeys() == null ? 0 : rewardSpec.commandKeys().size(),
                    rewardSpec.consoleCommands() == null ? 0 : rewardSpec.consoleCommands().size()
            );
            details.add(UiChatStyle.detail(UiChatStyle.DetailType.SPECIAL,
                    "+" + count + " premio speciale del server"));
        }
        return List.copyOf(details);
    }

    private static List<UiChatStyle.DetailLine> rewardPreviewDetails(ChallengeRewardPreview preview) {
        if (preview == null || preview.equals(ChallengeRewardPreview.EMPTY)) {
            return List.of(UiChatStyle.detail(UiChatStyle.DetailType.NOTE, "Il premio del ciclo sara svelato alla chiusura della sfida."));
        }
        List<UiChatStyle.DetailLine> details = new ArrayList<>();
        if (preview.xpCity() > 0.0D) {
            details.add(UiChatStyle.detail(UiChatStyle.DetailType.REWARD, "+" + formatNumber(preview.xpCity()) + " XP citta"));
        }
        if (preview.moneyCity() > 0.0D) {
            details.add(UiChatStyle.detail(UiChatStyle.DetailType.REWARD, "+" + formatNumber(preview.moneyCity()) + " Monete citta"));
        }
        if (preview.vaultItemAmount() > 0) {
            details.add(UiChatStyle.detail(UiChatStyle.DetailType.REWARD,
                    "+" + preview.vaultItemAmount() + " materiali nel vault"));
            preview.rewardItems().stream()
                    .limit(4)
                    .forEach(item -> details.add(UiChatStyle.detail(
                            UiChatStyle.DetailType.REWARD,
                            item.amount() + "x " + ChallengeLoreFormatter.materialLabel(item.material())
                    )));
            if (preview.rewardItems().size() > 4) {
                details.add(UiChatStyle.detail(UiChatStyle.DetailType.NOTE,
                        "+" + (preview.rewardItems().size() - 4) + " altri tipi di materiale"));
            }
        }
        if (preview.personalXpCityBonus() > 0.0D) {
            details.add(UiChatStyle.detail(UiChatStyle.DetailType.REWARD,
                    "+" + formatNumber(preview.personalXpCityBonus()) + "% bonus XP citta personale"));
        }
        if (preview.commandCount() > 0) {
            details.add(UiChatStyle.detail(UiChatStyle.DetailType.SPECIAL,
                    "+" + preview.commandCount() + " premio speciale del server"));
        }
        return List.copyOf(details);
    }

    private static String objectiveLine(ChallengeInstance instance) {
        if (instance == null) {
            return "";
        }
        String focus = instance.focusLabel() == null || instance.focusLabel().isBlank()
                ? ""
                : instance.focusLabel().trim();
        return switch (instance.objectiveType()) {
            case RESOURCE_CONTRIBUTION -> "deposita " + instance.target() + " punti risorsa nel vault cittadino";
            case VAULT_DELIVERY -> "consegna " + instance.target() + " " + genericFocus(focus, "materiali") + " al vault cittadino";
            case MOB_KILL -> "uccidi " + instance.target() + " " + genericFocus(focus, "mob ostili");
            case RARE_MOB_KILL -> "uccidi " + instance.target() + " " + genericFocus(focus, "mob rari");
            case BOSS_KILL -> "sconfiggi " + instance.target() + " boss maggiori";
            case BLOCK_MINE -> "rompi " + instance.target() + " " + genericFocus(focus, "blocchi in miniera");
            case CROP_HARVEST -> "raccogli " + instance.target() + " " + genericFocus(focus, "colture mature");
            case FARMING_ECOSYSTEM -> "completa " + instance.target() + " azioni agricole";
            case CONSTRUCTION -> "piazza " + instance.target() + " blocchi utili alla costruzione";
            case REDSTONE_AUTOMATION -> "completa " + instance.target() + " azioni redstone";
            case FISH_CATCH -> "pesca " + instance.target() + " catture";
            case ANIMAL_INTERACTION -> "completa " + instance.target() + " interazioni con animali";
            case STRUCTURE_DISCOVERY -> "scopri " + instance.target() + " strutture naturali";
            case ARCHAEOLOGY_BRUSH -> "usa il pennello su " + instance.target() + " blocchi archeologici";
            case STRUCTURE_LOOT -> "apri " + instance.target() + " bottini di struttura";
            case TRIAL_VAULT_OPEN -> "apri " + instance.target() + " volte delle Camere della Prova";
            case RAID_WIN -> "vinci " + instance.target() + " raid";
            case FOOD_CRAFT -> "produci " + instance.target() + " unita di cibo";
            case BREW_POTION -> "prepara " + instance.target() + " pozioni";
            case ITEM_CRAFT -> "crafta " + instance.target() + " oggetti di progresso";
            case NETHER_ACTIVITY -> "completa " + instance.target() + " azioni valide nel Nether";
            case OCEAN_ACTIVITY -> "completa " + instance.target() + " azioni valide nell'oceano";
            case PLACE_BLOCK -> "piazza " + instance.target() + " blocchi per il monumento cittadino";
            case TEAM_PHASE_QUEST -> "completa la fase attiva della missione di squadra";
            case SECRET_QUEST -> "segui gli indizi e chiudi la missione segreta";
            case TRANSPORT_DISTANCE -> "copri una distanza totale di " + instance.target() + " blocchi";
            case DIMENSION_TRAVEL -> "completa " + instance.target() + " viaggi tra le dimensioni";
            case VILLAGER_TRADE -> "esegui " + instance.target() + " scambi con i villager";
            case ECONOMY_ADVANCED -> "completa " + instance.target() + " scambi economici avanzati";
            case PLAYTIME_MINUTES -> "rimani attivo per " + instance.target() + " minuti";
            case XP_PICKUP -> "raccogli " + instance.target() + " punti esperienza";
        };
    }

    private static String genericFocus(String focus, String fallback) {
        return focus == null || focus.isBlank() ? fallback : focus.toLowerCase(Locale.ROOT);
    }

    private static String objectiveLabel(ChallengeObjectiveType type) {
        if (type == null) {
            return "Sfida stagionale";
        }
        return switch (type) {
            case RESOURCE_CONTRIBUTION -> "Consegna risorse";
            case VAULT_DELIVERY -> "Deposito richiesto";
            case MOB_KILL -> "Elimina mob";
            case RARE_MOB_KILL -> "Elimina mob rari";
            case BOSS_KILL -> "Elimina boss";
            case BLOCK_MINE -> "Scava blocchi";
            case CROP_HARVEST -> "Raccogli colture";
            case FARMING_ECOSYSTEM -> "Attivita in fattoria";
            case CONSTRUCTION -> "Costruisci";
            case REDSTONE_AUTOMATION -> "Usa la redstone";
            case FISH_CATCH -> "Pesca";
            case ANIMAL_INTERACTION -> "Gestisci animali";
            case STRUCTURE_DISCOVERY -> "Scopri strutture";
            case ARCHAEOLOGY_BRUSH -> "Spedizione archeologica";
            case STRUCTURE_LOOT -> "Loot strutture";
            case TRIAL_VAULT_OPEN -> "Apri Trial Vault";
            case RAID_WIN -> "Vinci raid";
            case FOOD_CRAFT -> "Prepara cibo";
            case BREW_POTION -> "Preparazione di pozioni";
            case ITEM_CRAFT -> "Craft avanzato";
            case NETHER_ACTIVITY -> "Attivita nel Nether";
            case OCEAN_ACTIVITY -> "Attivita nell'oceano";
            case PLACE_BLOCK -> "Costruisci il monumento";
            case TEAM_PHASE_QUEST -> "Missione di squadra";
            case SECRET_QUEST -> "Missione segreta";
            case TRANSPORT_DISTANCE -> "Distanza percorsa";
            case DIMENSION_TRAVEL -> "Viaggi tra dimensioni";
            case VILLAGER_TRADE -> "Scambi coi villager";
            case ECONOMY_ADVANCED -> "Economia avanzata";
            case PLAYTIME_MINUTES -> "Tempo online";
            case XP_PICKUP -> "Raccogli XP";
        };
    }

    private static String fallbackTitle(ChallengeInstance instance, String fallback) {
        if (instance == null || instance.challengeName() == null || instance.challengeName().isBlank()) {
            return fallback;
        }
        return safe(instance.challengeName());
    }

    private static String normalizeTownName(String townName) {
        if (townName == null || townName.isBlank()) {
            return "Senza insegna";
        }
        return safe(townName);
    }

    private static String normalizeCycle(String cycleKey) {
        if (cycleKey == null || cycleKey.isBlank()) {
            return "Stagione in corso";
        }
        return safe(cycleKey.replace('_', ' '));
    }

    private static String formatNumber(double value) {
        return BigDecimal.valueOf(Math.max(0.0D, value))
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private static String safe(String raw) {
        if (raw == null || raw.isBlank()) {
            return "-";
        }
        return raw
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('<', '[')
                .replace('>', ']')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record BroadcastView(
            String header,
            String titleLine,
            String statusLine,
            List<UiChatStyle.DetailLine> details
    ) {
    }
}
