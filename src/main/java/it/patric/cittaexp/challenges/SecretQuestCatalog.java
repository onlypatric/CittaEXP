package it.patric.cittaexp.challenges;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SecretQuestCatalog {

    public record Stage(
            String triggerKey,
            List<String> hintLines
    ) {
    }

    public record Script(
            String id,
            List<Stage> stages
    ) {
    }

    private static final Map<String, Script> SCRIPTS = createScripts();

    private SecretQuestCatalog() {
    }

    public static Script byId(String challengeId) {
        if (challengeId == null || challengeId.isBlank()) {
            return null;
        }
        return SCRIPTS.get(challengeId.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public static boolean hasScript(String challengeId) {
        return byId(challengeId) != null;
    }

    public static int stageCount(String challengeId) {
        Script script = byId(challengeId);
        if (script == null || script.stages() == null || script.stages().isEmpty()) {
            return 0;
        }
        return script.stages().size();
    }

    public static Stage currentStage(String challengeId, int progress) {
        Script script = byId(challengeId);
        if (script == null || script.stages() == null || script.stages().isEmpty()) {
            return null;
        }
        int index = Math.max(0, Math.min(Math.max(0, progress), script.stages().size() - 1));
        return script.stages().get(index);
    }

    public static List<String> currentHints(String challengeId, int progress) {
        Stage stage = currentStage(challengeId, progress);
        if (stage == null || stage.hintLines() == null || stage.hintLines().isEmpty()) {
            return List.of(
                    "Le tracce si rivelano solo a chi insiste oltre il percorso normale.",
                    "Il mandato non si apre con un solo gesto: serve continuita di squadra.",
                    "Quando il mondo sembra muto, il prossimo indizio e vicino."
            );
        }
        return stage.hintLines();
    }

    public static boolean matchesCurrentStage(String challengeId, int progress, String triggerKey) {
        Stage stage = currentStage(challengeId, progress);
        if (stage == null || triggerKey == null || triggerKey.isBlank()) {
            return false;
        }
        return stage.triggerKey() != null && stage.triggerKey().equalsIgnoreCase(triggerKey.trim());
    }

    private static Map<String, Script> createScripts() {
        Map<String, Script> result = new LinkedHashMap<>();
        result.put("proc_secret_quest", new Script(
                "proc_secret_quest",
                List.of(
                        new Stage(
                                CityChallengeService.SECRET_TRIGGER_MINE_BLOCK,
                                List.of(
                                        "La prima traccia dorme nel calore e sotto molta pressione.",
                                        "Le vene che contano non affiorano mai lungo i percorsi sicuri.",
                                        "Scava dove il fuoco antico ha lasciato il suo peso nel mondo."
                                )
                        ),
                        new Stage(
                                CityChallengeService.SECRET_TRIGGER_VISIT_LOCATION,
                                List.of(
                                        "Il secondo sigillo si spezza solo davanti a una fortezza gia perduta.",
                                        "Le mura che cerchi non stanno nel mondo quieto della superficie.",
                                        "Quando le torce si accendono tra pietra nera e oro vecchio, sei sulla pista."
                                )
                        ),
                        new Stage(
                                CityChallengeService.SECRET_TRIGGER_KILL_MOB,
                                List.of(
                                        "L'ultimo custode non cade davanti a una ronda casuale.",
                                        "La guardia giusta difende le sale piu dure della frontiera cremisi.",
                                        "Senza una squadra pronta, il mandato resta chiuso."
                                )
                        )
                )
        ));
        result.put("secret_hidden_mandate", new Script(
                "secret_hidden_mandate",
                List.of(
                        new Stage(
                                CityChallengeService.SECRET_TRIGGER_ARCHAEOLOGY,
                                List.of(
                                        "Il mandato si apre solo dove il passato e ancora sepolto.",
                                        "Non tutta la sabbia tace allo stesso modo.",
                                        "Cerca i segni fragili che si rompono solo con pazienza."
                                )
                        ),
                        new Stage(
                                CityChallengeService.SECRET_TRIGGER_TRIAL_VAULT,
                                List.of(
                                        "La seconda porta richiede una prova vera, non semplice bottino.",
                                        "Le camere antiche si aprono solo a chi completa il rito.",
                                        "Il sigillo non premia chi si ferma all'ingresso."
                                )
                        ),
                        new Stage(
                                CityChallengeService.SECRET_TRIGGER_RAID,
                                List.of(
                                        "La sentenza finale cade solo dopo una difesa totale.",
                                        "Quando il villaggio resiste fino all'ultima ondata, il mandato risponde.",
                                        "L'ordine nascosto riconosce solo chi regge l'assedio fino in fondo."
                                )
                        )
                )
        ));
        result.put("secret_gate_of_the_undertow", new Script(
                "secret_gate_of_the_undertow",
                List.of(
                        new Stage(
                                CityChallengeService.SECRET_TRIGGER_DIMENSION_TRAVEL,
                                List.of(
                                        "La prima soglia non si trova camminando in un solo mondo.",
                                        "Le rotte proibite si aprono a chi continua a varcare i confini.",
                                        "Ogni passaggio lascia una cicatrice che il mandato sa leggere."
                                )
                        ),
                        new Stage(
                                CityChallengeService.SECRET_TRIGGER_STRUCTURE_DISCOVERY,
                                List.of(
                                        "La seconda mappa non si compra: si costruisce con molte scoperte.",
                                        "Le strutture comuni non bastano; conta la costanza della ricerca.",
                                        "Allarga la rete, chiudi i vuoti, torna con nuove coordinate."
                                )
                        ),
                        new Stage(
                                CityChallengeService.SECRET_TRIGGER_TRIAL_VAULT,
                                List.of(
                                        "La chiave finale sta oltre una prova chiusa e custodita.",
                                        "Nessuna scorciatoia apre la terza serratura.",
                                        "Serve una squadra capace di attraversare e chiudere la camera."
                                )
                        )
                )
        ));
        result.put("secret_court_of_ashes", new Script(
                "secret_court_of_ashes",
                List.of(
                        new Stage(
                                CityChallengeService.SECRET_TRIGGER_VISIT_LOCATION,
                                List.of(
                                        "Le prime parole del consiglio bruciato sono scritte dentro rovine ostili.",
                                        "Segui le fortezze che il Nether ha tenuto in piedi piu a lungo.",
                                        "Il mandato non si muove finche qualcuno non entra davvero nel luogo giusto."
                                )
                        ),
                        new Stage(
                                CityChallengeService.SECRET_TRIGGER_MINE_BLOCK,
                                List.of(
                                        "Il secondo voto richiede materia che non appartiene al mondo tranquillo.",
                                        "Le vene che servono non si trovano vicino al ritorno.",
                                        "Quando il piccone incontra la profondita giusta, il sigillo cede."
                                )
                        ),
                        new Stage(
                                CityChallengeService.SECRET_TRIGGER_DIMENSION_TRAVEL,
                                List.of(
                                        "L'ultima sentenza si compie solo dopo molti attraversamenti.",
                                        "Il percorso conta piu del portale singolo.",
                                        "Continua a legare mondi diversi finche la corte non risponde."
                                )
                        )
                )
        ));
        registerGeneratedScripts(result);
        return Map.copyOf(result);
    }

    private static void registerGeneratedScripts(Map<String, Script> result) {
        register(result, "secret_bell_of_deepwater", "campana degli abissi", CityChallengeService.SECRET_TRIGGER_DIMENSION_TRAVEL, CityChallengeService.SECRET_TRIGGER_STRUCTURE_DISCOVERY, CityChallengeService.SECRET_TRIGGER_TRIAL_VAULT);
        register(result, "secret_embers_of_the_seal", "sigillo di braci", CityChallengeService.SECRET_TRIGGER_MINE_BLOCK, CityChallengeService.SECRET_TRIGGER_VISIT_LOCATION, CityChallengeService.SECRET_TRIGGER_KILL_MOB);
        register(result, "secret_road_of_tithe", "strada della decima", CityChallengeService.SECRET_TRIGGER_VISIT_LOCATION, CityChallengeService.SECRET_TRIGGER_DIMENSION_TRAVEL, CityChallengeService.SECRET_TRIGGER_RAID);
        register(result, "secret_threefold_sigil", "triplice sigillo", CityChallengeService.SECRET_TRIGGER_ARCHAEOLOGY, CityChallengeService.SECRET_TRIGGER_STRUCTURE_DISCOVERY, CityChallengeService.SECRET_TRIGGER_TRIAL_VAULT);
        register(result, "secret_sunken_court", "corte sommersa", CityChallengeService.SECRET_TRIGGER_DIMENSION_TRAVEL, CityChallengeService.SECRET_TRIGGER_STRUCTURE_DISCOVERY, CityChallengeService.SECRET_TRIGGER_RAID);
        register(result, "secret_furnace_of_echoes", "fornace degli echi", CityChallengeService.SECRET_TRIGGER_MINE_BLOCK, CityChallengeService.SECRET_TRIGGER_ARCHAEOLOGY, CityChallengeService.SECRET_TRIGGER_TRIAL_VAULT);
        register(result, "secret_hush_of_monoliths", "silenzio dei monoliti", CityChallengeService.SECRET_TRIGGER_STRUCTURE_DISCOVERY, CityChallengeService.SECRET_TRIGGER_DIMENSION_TRAVEL, CityChallengeService.SECRET_TRIGGER_RAID);
        register(result, "secret_watchers_tithe", "decima dei guardiani", CityChallengeService.SECRET_TRIGGER_KILL_MOB, CityChallengeService.SECRET_TRIGGER_VISIT_LOCATION, CityChallengeService.SECRET_TRIGGER_RAID);
        register(result, "secret_lanterns_of_the_fault", "lanterne della faglia", CityChallengeService.SECRET_TRIGGER_ARCHAEOLOGY, CityChallengeService.SECRET_TRIGGER_MINE_BLOCK, CityChallengeService.SECRET_TRIGGER_DIMENSION_TRAVEL);
        register(result, "secret_red_bastion_pact", "patto del bastione rosso", CityChallengeService.SECRET_TRIGGER_VISIT_LOCATION, CityChallengeService.SECRET_TRIGGER_KILL_MOB, CityChallengeService.SECRET_TRIGGER_DIMENSION_TRAVEL);
        register(result, "secret_archive_of_salt", "archivio del sale", CityChallengeService.SECRET_TRIGGER_STRUCTURE_DISCOVERY, CityChallengeService.SECRET_TRIGGER_ARCHAEOLOGY, CityChallengeService.SECRET_TRIGGER_TRIAL_VAULT);
        register(result, "secret_thorn_market", "mercato delle spine", CityChallengeService.SECRET_TRIGGER_DIMENSION_TRAVEL, CityChallengeService.SECRET_TRIGGER_RAID, CityChallengeService.SECRET_TRIGGER_VISIT_LOCATION);
        register(result, "secret_black_current", "corrente nera", CityChallengeService.SECRET_TRIGGER_DIMENSION_TRAVEL, CityChallengeService.SECRET_TRIGGER_TRIAL_VAULT, CityChallengeService.SECRET_TRIGGER_KILL_MOB);
        register(result, "secret_stone_oath", "giuramento di pietra", CityChallengeService.SECRET_TRIGGER_MINE_BLOCK, CityChallengeService.SECRET_TRIGGER_STRUCTURE_DISCOVERY, CityChallengeService.SECRET_TRIGGER_RAID);
        register(result, "secret_hall_of_veins", "sala delle vene", CityChallengeService.SECRET_TRIGGER_MINE_BLOCK, CityChallengeService.SECRET_TRIGGER_TRIAL_VAULT, CityChallengeService.SECRET_TRIGGER_DIMENSION_TRAVEL);
        register(result, "secret_ashen_compass", "bussola di cenere", CityChallengeService.SECRET_TRIGGER_VISIT_LOCATION, CityChallengeService.SECRET_TRIGGER_DIMENSION_TRAVEL, CityChallengeService.SECRET_TRIGGER_STRUCTURE_DISCOVERY);
        register(result, "secret_cinder_route", "rotta di cenere", CityChallengeService.SECRET_TRIGGER_DIMENSION_TRAVEL, CityChallengeService.SECRET_TRIGGER_VISIT_LOCATION, CityChallengeService.SECRET_TRIGGER_TRIAL_VAULT);
        register(result, "secret_shrouded_ledger", "registro velato", CityChallengeService.SECRET_TRIGGER_RAID, CityChallengeService.SECRET_TRIGGER_STRUCTURE_DISCOVERY, CityChallengeService.SECRET_TRIGGER_DIMENSION_TRAVEL);
        register(result, "secret_deep_court", "corte profonda", CityChallengeService.SECRET_TRIGGER_ARCHAEOLOGY, CityChallengeService.SECRET_TRIGGER_VISIT_LOCATION, CityChallengeService.SECRET_TRIGGER_RAID);
        register(result, "secret_last_toll", "ultimo rintocco", CityChallengeService.SECRET_TRIGGER_KILL_MOB, CityChallengeService.SECRET_TRIGGER_DIMENSION_TRAVEL, CityChallengeService.SECRET_TRIGGER_TRIAL_VAULT);
        register(result, "secret_harrowed_passage", "passaggio segnato", CityChallengeService.SECRET_TRIGGER_STRUCTURE_DISCOVERY, CityChallengeService.SECRET_TRIGGER_DIMENSION_TRAVEL, CityChallengeService.SECRET_TRIGGER_KILL_MOB);
        register(result, "secret_iron_vigil", "veglia di ferro", CityChallengeService.SECRET_TRIGGER_MINE_BLOCK, CityChallengeService.SECRET_TRIGGER_RAID, CityChallengeService.SECRET_TRIGGER_VISIT_LOCATION);
        register(result, "secret_drowned_circuit", "circuito sommerso", CityChallengeService.SECRET_TRIGGER_ARCHAEOLOGY, CityChallengeService.SECRET_TRIGGER_DIMENSION_TRAVEL, CityChallengeService.SECRET_TRIGGER_STRUCTURE_DISCOVERY);
        register(result, "secret_sealed_wayfinder", "wayfinder sigillato", CityChallengeService.SECRET_TRIGGER_STRUCTURE_DISCOVERY, CityChallengeService.SECRET_TRIGGER_TRIAL_VAULT, CityChallengeService.SECRET_TRIGGER_DIMENSION_TRAVEL);
        register(result, "secret_low_flame_accord", "accordo di brace bassa", CityChallengeService.SECRET_TRIGGER_VISIT_LOCATION, CityChallengeService.SECRET_TRIGGER_RAID, CityChallengeService.SECRET_TRIGGER_MINE_BLOCK);
        register(result, "secret_void_meridian", "meridiano del vuoto", CityChallengeService.SECRET_TRIGGER_DIMENSION_TRAVEL, CityChallengeService.SECRET_TRIGGER_TRIAL_VAULT, CityChallengeService.SECRET_TRIGGER_STRUCTURE_DISCOVERY);
        register(result, "secret_worn_throne", "trono consunto", CityChallengeService.SECRET_TRIGGER_VISIT_LOCATION, CityChallengeService.SECRET_TRIGGER_KILL_MOB, CityChallengeService.SECRET_TRIGGER_RAID);
        register(result, "secret_tide_bell", "campana di marea", CityChallengeService.SECRET_TRIGGER_DIMENSION_TRAVEL, CityChallengeService.SECRET_TRIGGER_ARCHAEOLOGY, CityChallengeService.SECRET_TRIGGER_TRIAL_VAULT);
        register(result, "secret_fallow_sigil", "sigillo di maggese", CityChallengeService.SECRET_TRIGGER_STRUCTURE_DISCOVERY, CityChallengeService.SECRET_TRIGGER_ARCHAEOLOGY, CityChallengeService.SECRET_TRIGGER_DIMENSION_TRAVEL);
        register(result, "secret_buried_concord", "concordato sepolto", CityChallengeService.SECRET_TRIGGER_ARCHAEOLOGY, CityChallengeService.SECRET_TRIGGER_VISIT_LOCATION, CityChallengeService.SECRET_TRIGGER_RAID);
    }

    private static void register(
            Map<String, Script> result,
            String id,
            String theme,
            String trigger1,
            String trigger2,
            String trigger3
    ) {
        result.put(id, new Script(
                id,
                List.of(
                        new Stage(trigger1, hintLines(theme, 1, trigger1)),
                        new Stage(trigger2, hintLines(theme, 2, trigger2)),
                        new Stage(trigger3, hintLines(theme, 3, trigger3))
                )
        ));
    }

    private static List<String> hintLines(String theme, int stage, String triggerKey) {
        String motif = theme == null || theme.isBlank() ? "mandato" : theme;
        String first = switch (stage) {
            case 1 -> "La prima voce del " + motif + " non si lascia leggere in chiaro.";
            case 2 -> "Il secondo sigillo del " + motif + " chiede una prova diversa dalla prima.";
            default -> "L'ultima soglia del " + motif + " si apre solo a una citta gia preparata.";
        };
        String second = switch (triggerKey == null ? "" : triggerKey) {
            case CityChallengeService.SECRET_TRIGGER_MINE_BLOCK ->
                    "Scava dove la ricchezza vera non compare mai senza rischio e profondita.";
            case CityChallengeService.SECRET_TRIGGER_VISIT_LOCATION ->
                    "Entra in un luogo che il mondo non regala a chi segue la rotta piu breve.";
            case CityChallengeService.SECRET_TRIGGER_KILL_MOB ->
                    "Il custode giusto non cade durante una ronda casuale.";
            case CityChallengeService.SECRET_TRIGGER_ARCHAEOLOGY ->
                    "Le tracce piu vecchie cedono solo sotto mani pazienti e coordinate.";
            case CityChallengeService.SECRET_TRIGGER_TRIAL_VAULT ->
                    "La camera deve essere chiusa davvero prima che il sigillo risponda.";
            case CityChallengeService.SECRET_TRIGGER_RAID ->
                    "Conta solo una difesa completa, non una scaramuccia interrotta.";
            case CityChallengeService.SECRET_TRIGGER_DIMENSION_TRAVEL ->
                    "Il confine va varcato piu volte: una sola soglia non basta.";
            case CityChallengeService.SECRET_TRIGGER_STRUCTURE_DISCOVERY ->
                    "La mappa si compone solo ampliando davvero il numero delle scoperte.";
            default -> "Il mondo nasconde ancora un passo che la citta non ha compiuto.";
        };
        String third = switch (triggerKey == null ? "" : triggerKey) {
            case CityChallengeService.SECRET_TRIGGER_MINE_BLOCK ->
                    "Quando il piccone trova la vena giusta, il diario smette di mentire.";
            case CityChallengeService.SECRET_TRIGGER_VISIT_LOCATION ->
                    "Solo la presenza nel posto giusto fa avanzare il mandato.";
            case CityChallengeService.SECRET_TRIGGER_KILL_MOB ->
                    "Serve disciplina di squadra, non fortuna.";
            case CityChallengeService.SECRET_TRIGGER_ARCHAEOLOGY ->
                    "Chi spazza troppo in fretta perde il segno decisivo.";
            case CityChallengeService.SECRET_TRIGGER_TRIAL_VAULT ->
                    "La reliquia compare solo dopo la prova, non prima.";
            case CityChallengeService.SECRET_TRIGGER_RAID ->
                    "L'ordine nascosto riconosce chi regge l'assedio fino all'ultima ondata.";
            case CityChallengeService.SECRET_TRIGGER_DIMENSION_TRAVEL ->
                    "Continua a collegare mondi diversi finche la pista non si chiude.";
            case CityChallengeService.SECRET_TRIGGER_STRUCTURE_DISCOVERY ->
                    "Una sola scoperta non conta: serve una campagna vera di ricognizione.";
            default -> "Il mandato premia soltanto chi insiste oltre il percorso normale.";
        };
        return List.of(first, second, third);
    }
}
