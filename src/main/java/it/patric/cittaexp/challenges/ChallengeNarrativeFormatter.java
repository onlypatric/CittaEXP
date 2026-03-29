package it.patric.cittaexp.challenges;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ChallengeNarrativeFormatter {

    private ChallengeNarrativeFormatter() {
    }

    public static String objectiveDescription(
            ChallengeTextCatalog textCatalog,
            ChallengeSnapshot snapshot
    ) {
        if (snapshot == null) {
            return "Completa l'obiettivo della sfida.";
        }
        String secretId = snapshot.challengeId() == null ? "" : snapshot.challengeId().toLowerCase(Locale.ROOT);
        if (snapshot.mode() == ChallengeMode.MONTHLY_LEDGER_MYSTERY
                || snapshot.mode() == ChallengeMode.SEASON_CODEX_HIDDEN_RELIC
                || "proc_secret_quest".equalsIgnoreCase(snapshot.challengeId())
                || secretId.startsWith("secret_")) {
            if (SecretQuestCatalog.hasScript(snapshot.challengeId())) {
                int total = Math.max(1, SecretQuestCatalog.stageCount(snapshot.challengeId()));
                int current = Math.min(total, Math.max(1, snapshot.townProgress() + 1));
                return "Missione segreta: completa l'indizio " + current + "/" + total
                        + " seguendo le tracce trovate nel mondo.";
            }
            return "Missione segreta: la prova non e scritta chiaramente. Segui gli indizi e scopri da solo il percorso giusto.";
        }
        if (snapshot.objectiveType() == ChallengeObjectiveType.BOSS_KILL) {
            return "Sconfiggi " + snapshot.target() + " boss validi per questa sfida.";
        }
        if (snapshot.objectiveType() == ChallengeObjectiveType.ECONOMY_ADVANCED) {
            return "Completa " + snapshot.target() + " scambi conclusi con i villager usando la finestra di trade.";
        }
        if (snapshot.objectiveType() == ChallengeObjectiveType.TEAM_PHASE_QUEST) {
            TeamPhaseQuestCatalog.Quest quest = TeamPhaseQuestCatalog.byId(snapshot.challengeId());
            if (quest == null) {
                return "Quest stagionale in 3 fasi: raccogli risorse, costruisci la struttura e sconfiggi il boss finale.";
            }
            int gatherCap = quest.gatherTarget();
            int buildCap = quest.gatherTarget() + quest.buildTarget();
            int progress = Math.max(0, snapshot.townProgress());
            if (progress < gatherCap) {
                return "Fase 1/3: raccogli " + quest.gatherTarget() + " risorse (mining o contributi al vault).";
            }
            if (progress < buildCap) {
                return "Fase 2/3: piazza " + quest.buildTarget() + " blocchi per la struttura stagionale.";
            }
            return "Fase 3/3: sconfiggi " + quest.bossTarget()
                    + " boss maggiori (Ender Dragon, Wither o Warden).";
        }
        String focus = resolveFocusLabel(snapshot);
        String biome = null;
        String dimension = ChallengeContextResolver.dimensionLabel(snapshot.dimensionKey());
        String template = textCatalog == null ? null : textCatalog.narrativeTemplate(snapshot.objectiveType());
        if (template != null && !template.isBlank()) {
            return render(template, snapshot.objectiveType(), snapshot.target(), focus, biome, dimension);
        }
        return switch (snapshot.objectiveType()) {
            case MOB_KILL -> "Uccidi " + snapshot.target() + " " + pluralizeEntity(focus, "mob ostili")
                    + locationSuffix(biome, dimension) + ".";
            case RARE_MOB_KILL -> "Uccidi " + snapshot.target() + " " + pluralizeEntity(focus, "mob rari")
                    + locationSuffix(biome, dimension) + ".";
            case BLOCK_MINE -> "Rompi " + snapshot.target() + " " + pluralizeEntity(focus, "blocchi in miniera")
                    + locationSuffix(biome, dimension) + ".";
            case CROP_HARVEST -> "Raccogli " + snapshot.target() + " " + pluralizeEntity(focus, "colture mature")
                    + locationSuffix(biome, dimension) + ".";
            case FISH_CATCH -> "Pesca " + snapshot.target() + " catture" + locationSuffix(biome, dimension) + ".";
            case ANIMAL_INTERACTION -> "Completa " + snapshot.target() + " interazioni con " + pluralizeEntity(focus, "animali")
                    + locationSuffix(biome, dimension) + ".";
            case CONSTRUCTION -> "Piazza " + snapshot.target() + " " + pluralizeEntity(focus, "blocchi")
                    + locationSuffix(biome, dimension) + ".";
            case REDSTONE_AUTOMATION -> "Completa " + snapshot.target() + " azioni redstone con "
                    + pluralizeEntity(focus, "componenti") + locationSuffix(biome, dimension) + ".";
            case STRUCTURE_DISCOVERY -> "Scopri " + snapshot.target() + " strutture naturali"
                    + locationSuffix(biome, dimension) + ".";
            case STRUCTURE_LOOT -> "Apri " + snapshot.target() + " loot di strutture"
                    + locationSuffix(biome, dimension) + ".";
            case TRIAL_VAULT_OPEN -> "Apri " + snapshot.target() + " volte nelle Camere della Prova.";
            case RAID_WIN -> "Completa con successo " + snapshot.target()
                    + " raid (difesa del villaggio fino a \"Hero of the Village\").";
            case FOOD_CRAFT -> "Produci " + snapshot.target() + " porzioni di "
                    + pluralizeEntity(focus, "cibo") + ".";
            case BREW_POTION -> "Prepara " + snapshot.target() + " pozioni: "
                    + pluralizeEntity(focus, "normali, splash o livello II") + ".";
            case ITEM_CRAFT -> "Crafta " + snapshot.target() + " unita di progresso tramite:\n"
                    + "- Beacon\n"
                    + "- Armature in diamante o netherite\n"
                    + "- Macchinari di redstone";
            case NETHER_ACTIVITY -> "Nel Nether completa " + snapshot.target()
                    + " azioni valide, per esempio combattimento o raccolta di materiali rari.";
            case OCEAN_ACTIVITY -> "Nell'oceano completa " + snapshot.target()
                    + " azioni valide, per esempio combattimento o raccolta di blocchi marini.";
            case PLACE_BLOCK -> "Costruisci il monumento cittadino: piazza " + snapshot.target() + " blocchi in cooperativa.";
            case TEAM_PHASE_QUEST -> "Missione stagionale in 3 fasi: raccogli risorse, costruisci e completa il boss finale.";
            case SECRET_QUEST -> "Missione segreta: segui gli indizi e completa i 3 trigger nascosti del mese.";
            case TRANSPORT_DISTANCE -> "Raggiungi una distanza totale di " + snapshot.target() + " blocchi.";
            case PLAYTIME_MINUTES -> "Rimani attivo per " + snapshot.target() + " minuti.";
            case XP_PICKUP -> "Raccogli " + snapshot.target() + " punti esperienza.";
            case VILLAGER_TRADE -> "Esegui " + snapshot.target() + " scambi con i villager.";
            case RESOURCE_CONTRIBUTION -> "Deposita " + snapshot.target() + " risorse nell'Item Vault:\n"
                    + "- materiali comuni valgono 1 punto\n"
                    + "- materiali preziosi valgono di piu";
            case VAULT_DELIVERY -> "Deposita " + snapshot.target() + " " + pluralizeEntity(focus, "materiali")
                    + " nell'Item Vault.\nQuando completi la missione, questi materiali vengono presi dal vault.";
            case FARMING_ECOSYSTEM -> "Completa " + snapshot.target() + " azioni agricole coordinate.";
            case DIMENSION_TRAVEL -> "Completa " + snapshot.target() + " viaggi validi tra le dimensioni.";
            case BOSS_KILL -> "Sconfiggi " + snapshot.target() + " boss validi per questa sfida.";
            case ECONOMY_ADVANCED -> "Completa " + snapshot.target() + " scambi conclusi con i villager usando la finestra di trade.";
            case ARCHAEOLOGY_BRUSH -> "Usa il pennello su " + snapshot.target() + " blocchi archeologici.";
        };
    }

    public static List<String> objectiveDescriptionLines(
            ChallengeTextCatalog textCatalog,
            ChallengeSnapshot snapshot
    ) {
        String description = objectiveDescription(textCatalog, snapshot);
        if (description == null || description.isBlank()) {
            return List.of("Completa l'obiettivo della sfida.");
        }
        String normalized = description
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("<br>", "\n");
        String[] rawLines = normalized.split("\n");
        List<String> lines = new ArrayList<>(rawLines.length);
        for (String raw : rawLines) {
            if (raw == null) {
                continue;
            }
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        if (lines.isEmpty()) {
            lines.add(description.trim());
        }
        return List.copyOf(lines);
    }

    private static String render(
            String template,
            ChallengeObjectiveType objectiveType,
            int target,
            String focus,
            String biome,
            String dimension
    ) {
        if (focus == null || focus.isBlank()) {
            String generic = renderGenericNarrative(objectiveType, target, biome, dimension);
            if (generic != null && !generic.isBlank()) {
                return generic;
            }
        }
        String rendered = template
                .replace("{target}", Integer.toString(Math.max(0, target)))
                .replace("{focus}", focus == null ? genericFocusFallback(objectiveType) : focus)
                .replace("{biome}", biome == null ? "-" : biome)
                .replace("{dimension}", dimension == null ? "-" : dimension);
        rendered = rendered
                .replace(" nel bioma -", "")
                .replace(" in -", "")
                .replace("  ", " ")
                .trim();
        if (!rendered.endsWith(".")) {
            rendered = rendered + ".";
        }
        return rendered;
    }

    private static String renderGenericNarrative(
            ChallengeObjectiveType objectiveType,
            int target,
            String biome,
            String dimension
    ) {
        if (objectiveType == null) {
            return null;
        }
        return switch (objectiveType) {
            case CONSTRUCTION -> "Piazza " + target + " blocchi di costruzione" + locationSuffix(biome, dimension) + ".";
            case REDSTONE_AUTOMATION -> "Completa " + target + " azioni di automazione redstone" + locationSuffix(biome, dimension) + ".";
            case ANIMAL_INTERACTION -> "Completa " + target + " interazioni con animali" + locationSuffix(biome, dimension) + ".";
            case FOOD_CRAFT -> "Produci " + target + " porzioni di cibo.";
            case BREW_POTION -> "Produci " + target + " pozioni utili alla citta.";
            case ITEM_CRAFT -> "Crafta " + target + " unita di progresso tramite:\n"
                    + "- Beacon\n"
                    + "- Armature in diamante o netherite\n"
                    + "- Macchinari di redstone";
            case BLOCK_MINE -> "Rompi " + target + " blocchi in miniera" + locationSuffix(biome, dimension) + ".";
            case CROP_HARVEST -> "Raccogli " + target + " unita di colture mature.";
            case MOB_KILL -> "Uccidi " + target + " mob ostili" + locationSuffix(biome, dimension) + ".";
            case RARE_MOB_KILL -> "Sconfiggi " + target + " mob rari" + locationSuffix(biome, dimension) + ".";
            default -> null;
        };
    }

    private static String genericFocusFallback(ChallengeObjectiveType objectiveType) {
        if (objectiveType == null) {
            return "attivita";
        }
        return switch (objectiveType) {
            case MOB_KILL -> "mob ostili";
            case RARE_MOB_KILL -> "mob rari";
            case BLOCK_MINE -> "blocchi in miniera";
            case CROP_HARVEST -> "colture mature";
            case CONSTRUCTION -> "costruzione";
            case REDSTONE_AUTOMATION -> "componenti redstone";
            case ANIMAL_INTERACTION -> "animali";
            case FOOD_CRAFT -> "cibo";
            case BREW_POTION -> "pozioni utili";
            case ITEM_CRAFT -> "item avanzati";
            default -> "attivita";
        };
    }

    private static String resolveFocusLabel(ChallengeSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        Material material = materialBackedFocus(snapshot.objectiveType(), snapshot.focusKey());
        if (material != null) {
            return ChallengeLoreFormatter.materialLabel(material);
        }
        String sanitized = normalizeLabel(snapshot.focusLabel());
        if (sanitized != null) {
            return sanitized;
        }
        return normalizeKey(snapshot.focusKey());
    }

    private static Material materialBackedFocus(ChallengeObjectiveType objectiveType, String focusKey) {
        if (objectiveType != ChallengeObjectiveType.VAULT_DELIVERY || focusKey == null || focusKey.isBlank()) {
            return null;
        }
        return Material.matchMaterial(focusKey.trim().toUpperCase(Locale.ROOT));
    }

    private static String normalizeLabel(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isBlank()
                || "-".equals(trimmed)
                || trimmed.contains("<focus_label>")
                || trimmed.contains("{focus_label}")) {
            return null;
        }
        return trimmed;
    }

    private static String normalizeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return ChallengeLoreFormatter.keyLabel(raw);
    }

    private static String pluralizeEntity(String focus, String fallback) {
        if (focus == null || focus.isBlank()) {
            return fallback;
        }
        return focus;
    }

    private static String locationSuffix(String biome, String dimension) {
        if (biome != null && !biome.isBlank() && dimension != null && !dimension.isBlank()) {
            return " nel bioma " + biome + " in " + dimension;
        }
        if (biome != null && !biome.isBlank()) {
            return " nel bioma " + biome;
        }
        if (dimension != null && !dimension.isBlank()) {
            return " in " + dimension;
        }
        return "";
    }
}
