package it.patric.cittaexp.challenges;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class StaffEventPresetCatalog {

    public record StaffEventPreset(
            String key,
            StaffEventKind kind,
            String title,
            String subtitle,
            String description,
            ChallengeMode templateMode,
            String templateChallengeId,
            Map<String, String> payloadDefaults
    ) {
    }

    private static final List<StaffEventPreset> PRESETS = List.of(
            autoRace(
                    "bonifica_gallerie",
                    "Bonifica delle Gallerie",
                    "Ripulite le miniere e spingetevi piu a fondo.",
                    "Evento competitivo centrato sul mining cittadino. Vince la citta che regge meglio il ritmo in miniera.",
                    ChallengeMode.WEEKLY_CLASH,
                    "proc_mining",
                    Map.of("event_theme", "underground")
            ),
            autoRace(
                    "raccolto_stagione",
                    "Raccolto di Stagione",
                    "Fate girare campi, raccolti e produzione agricola.",
                    "Evento competitivo dedicato alla produzione agricola della citta. Funziona bene anche come evento accessibile alle citta piu giovani.",
                    ChallengeMode.WEEKLY_CLASH,
                    "proc_crop",
                    Map.of("event_theme", "harvest")
            ),
            autoRace(
                    "spedizione_oceanica",
                    "Spedizione Oceanica",
                    "Portate a casa il bottino del mare.",
                    "Versione iniziale dell'evento oceanico basata sulla pesca, gia compatibile col motore competitivo esistente.",
                    ChallengeMode.WEEKLY_CLASH,
                    "proc_fishing",
                    Map.of("event_theme", "coastal")
            ),
            autoRace(
                    "difesa_civica",
                    "Difesa Civica",
                    "Respinte le minacce e difendete il territorio.",
                    "Evento competitivo incentrato sui raid vittoriosi. E' la base giusta per una futura versione PvE piu ricca.",
                    ChallengeMode.WEEKLY_CLASH,
                    "proc_raid_win",
                    Map.of("event_theme", "defense")
            ),
            judgedBuild(
                    "porta_citta",
                    "Porta della Citta",
                    "Costruite l'ingresso simbolo del vostro centro urbano.",
                    "Contest build judged dedicato a porte, accessi monumentali e gatehouse nel territorio reale della citta.",
                    Map.of("event_theme", "gatehouse", "rubric", "landmark")
            ),
            judgedBuild(
                    "mercato_cittadino",
                    "Mercato Cittadino",
                    "Create una piazza commerciale viva e leggibile.",
                    "Contest build judged per mercati, piazze di scambio e zone commerciali costruite nel survival cittadino.",
                    Map.of("event_theme", "market", "rubric", "urban")
            ),
            judgedBuild(
                    "quartiere_agricolo_modello",
                    "Quartiere Agricolo Modello",
                    "Mostrate una zona produttiva bella e funzionale.",
                    "Contest build judged per distretti agricoli reali, leggibili e ben integrati nel territorio della citta.",
                    Map.of("event_theme", "agriculture", "rubric", "functional")
            ),
            judgedBuild(
                    "distretto_industriale_ordinato",
                    "Distretto Industriale Ordinato",
                    "Mostrate industria, ordine e funzionalita.",
                    "Contest build judged per smeltery, storage e automazioni ben organizzate nel survival della citta.",
                    Map.of("event_theme", "industry", "rubric", "functional")
            )
    );

    private StaffEventPresetCatalog() {
    }

    public static List<StaffEventPreset> all() {
        return PRESETS;
    }

    public static List<StaffEventPreset> autoRacePresets() {
        return PRESETS.stream()
                .filter(preset -> preset.kind() == StaffEventKind.AUTO_RACE)
                .toList();
    }

    public static List<StaffEventPreset> judgedBuildPresets() {
        return PRESETS.stream()
                .filter(preset -> preset.kind() == StaffEventKind.JUDGED_BUILD)
                .toList();
    }

    public static Optional<StaffEventPreset> find(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalizeKey(rawKey);
        return PRESETS.stream()
                .filter(preset -> preset.key().equalsIgnoreCase(normalized))
                .findFirst();
    }

    public static Optional<StaffEventPreset> findAutoRace(String rawKey) {
        return find(rawKey).filter(preset -> preset.kind() == StaffEventKind.AUTO_RACE);
    }

    public static Optional<StaffEventPreset> findJudgedBuild(String rawKey) {
        return find(rawKey).filter(preset -> preset.kind() == StaffEventKind.JUDGED_BUILD);
    }

    public static String autoRaceKeysLabel() {
        return joinKeys(autoRacePresets());
    }

    public static String judgedBuildKeysLabel() {
        return joinKeys(judgedBuildPresets());
    }

    private static StaffEventPreset autoRace(
            String key,
            String title,
            String subtitle,
            String description,
            ChallengeMode mode,
            String challengeId,
            Map<String, String> payloadDefaults
    ) {
        return new StaffEventPreset(
                key,
                StaffEventKind.AUTO_RACE,
                title,
                subtitle,
                description,
                mode,
                challengeId,
                Map.copyOf(new LinkedHashMap<>(payloadDefaults))
        );
    }

    private static StaffEventPreset judgedBuild(
            String key,
            String title,
            String subtitle,
            String description,
            Map<String, String> payloadDefaults
    ) {
        return new StaffEventPreset(
                key,
                StaffEventKind.JUDGED_BUILD,
                title,
                subtitle,
                description,
                null,
                null,
                Map.copyOf(new LinkedHashMap<>(payloadDefaults))
        );
    }

    private static String joinKeys(List<StaffEventPreset> presets) {
        return presets.stream()
                .map(StaffEventPreset::key)
                .toList()
                .toString();
    }

    private static String normalizeKey(String rawKey) {
        return rawKey.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }
}
