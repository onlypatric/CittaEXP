package it.patric.cittaexp.challenges;

import org.bukkit.Material;

public final class ChallengeObjectivePresentation {

    private ChallengeObjectivePresentation() {
    }

    public static Material icon(ChallengeObjectiveType type) {
        if (type == null) {
            return Material.BOOK;
        }
        return switch (type) {
            case RESOURCE_CONTRIBUTION -> Material.CHEST;
            case VAULT_DELIVERY -> Material.BARREL;
            case MOB_KILL -> Material.IRON_SWORD;
            case RARE_MOB_KILL -> Material.DIAMOND_SWORD;
            case BOSS_KILL -> Material.NETHER_STAR;
            case BLOCK_MINE -> Material.IRON_PICKAXE;
            case CROP_HARVEST -> Material.WHEAT;
            case FARMING_ECOSYSTEM -> Material.HAY_BLOCK;
            case CONSTRUCTION -> Material.BRICKS;
            case REDSTONE_AUTOMATION -> Material.REDSTONE;
            case FISH_CATCH -> Material.FISHING_ROD;
            case ANIMAL_INTERACTION -> Material.LEAD;
            case STRUCTURE_DISCOVERY -> Material.COMPASS;
            case ARCHAEOLOGY_BRUSH -> Material.BRUSH;
            case STRUCTURE_LOOT -> Material.CHEST_MINECART;
            case TRIAL_VAULT_OPEN -> Material.VAULT;
            case RAID_WIN -> Material.TOTEM_OF_UNDYING;
            case FOOD_CRAFT -> Material.BREAD;
            case BREW_POTION -> Material.BREWING_STAND;
            case ITEM_CRAFT -> Material.CRAFTING_TABLE;
            case NETHER_ACTIVITY -> Material.ANCIENT_DEBRIS;
            case OCEAN_ACTIVITY -> Material.PRISMARINE;
            case PLACE_BLOCK -> Material.STONE_BRICKS;
            case TEAM_PHASE_QUEST -> Material.DRAGON_HEAD;
            case SECRET_QUEST -> Material.ENDER_EYE;
            case TRANSPORT_DISTANCE -> Material.MINECART;
            case DIMENSION_TRAVEL -> Material.ENDER_PEARL;
            case VILLAGER_TRADE -> Material.EMERALD;
            case ECONOMY_ADVANCED -> Material.GOLD_INGOT;
            case PLAYTIME_MINUTES -> Material.CLOCK;
            case XP_PICKUP -> Material.EXPERIENCE_BOTTLE;
        };
    }

    public static String objectiveLabel(ChallengeTextCatalog textCatalog, ChallengeObjectiveType type) {
        if (textCatalog == null) {
            return defaultName(type);
        }
        return textCatalog.objectiveLabel(type);
    }

    public static String objectiveDescription(
            ChallengeTextCatalog textCatalog,
            String challengeId,
            ChallengeObjectiveType type,
            int target,
            String challengeName,
            String focusLabel,
            String variantLabel
    ) {
        if (textCatalog == null) {
            return defaultDescription(type);
        }
        return textCatalog.objectiveDescription(challengeId, type, target, challengeName, focusLabel, variantLabel);
    }

    static String defaultName(ChallengeObjectiveType type) {
        if (type == null) {
            return "Obiettivo";
        }
        return switch (type) {
            case RESOURCE_CONTRIBUTION -> "Contributo risorse";
            case VAULT_DELIVERY -> "Consegna al vault";
            case MOB_KILL -> "Uccisioni mob";
            case RARE_MOB_KILL -> "Uccisioni mob rari";
            case BOSS_KILL -> "Uccisioni boss";
            case BLOCK_MINE -> "Mining blocchi";
            case CROP_HARVEST -> "Raccolta campi";
            case FARMING_ECOSYSTEM -> "Farming ecosistema";
            case CONSTRUCTION -> "Costruzione";
            case REDSTONE_AUTOMATION -> "Automazione redstone";
            case FISH_CATCH -> "Pesca";
            case ANIMAL_INTERACTION -> "Interazione animali";
            case STRUCTURE_DISCOVERY -> "Scoperta strutture";
            case ARCHAEOLOGY_BRUSH -> "Archeologia";
            case STRUCTURE_LOOT -> "Loot strutture";
            case TRIAL_VAULT_OPEN -> "Volta di Prova";
            case RAID_WIN -> "Raid completato";
            case FOOD_CRAFT -> "Produzione cibo";
            case BREW_POTION -> "Produzione pozioni";
            case ITEM_CRAFT -> "Craft avanzato";
            case NETHER_ACTIVITY -> "Spedizione Nether";
            case OCEAN_ACTIVITY -> "Operazione Oceano";
            case PLACE_BLOCK -> "Monumento Cittadino";
            case TEAM_PHASE_QUEST -> "Quest di Team (3 Fasi)";
            case SECRET_QUEST -> "Quest Segreta";
            case TRANSPORT_DISTANCE -> "Distanza trasporto";
            case DIMENSION_TRAVEL -> "Viaggi dimensioni";
            case VILLAGER_TRADE -> "Trade villager";
            case ECONOMY_ADVANCED -> "Economia avanzata";
            case PLAYTIME_MINUTES -> "Tempo online";
            case XP_PICKUP -> "Raccolta XP";
        };
    }

    static String defaultDescription(ChallengeObjectiveType type) {
        if (type == null) {
            return "Completa l'obiettivo indicato.";
        }
        return switch (type) {
            case RESOURCE_CONTRIBUTION -> "Deposita risorse nell'Item Vault:\n- materiali comuni valgono 1 punto\n- materiali preziosi valgono di piu";
            case VAULT_DELIVERY -> "Deposita il materiale richiesto nell'Item Vault.\nQuando la sfida si chiude, questi oggetti vengono presi dal vault.";
            case MOB_KILL -> "Uccidi mob ostili mentre fai attivita normali.";
            case RARE_MOB_KILL -> "Uccidi i mob rari validi per il contesto della sfida.";
            case BOSS_KILL -> "Sconfiggi i boss validi per questa sfida.";
            case BLOCK_MINE -> "Rompi blocchi minando nel mondo.";
            case CROP_HARVEST -> "Raccogli colture mature della tua farm.";
            case FARMING_ECOSYSTEM -> "Completa attivita agricole varie (raccolta/allevamento).";
            case CONSTRUCTION -> "Piazza blocchi costruendo strutture utili.";
            case REDSTONE_AUTOMATION -> "Sviluppa automazioni e circuiti redstone.";
            case FISH_CATCH -> "Pesca oggetti o pesci con canna da pesca.";
            case ANIMAL_INTERACTION -> "Interagisci con animali (nutri, alleva, gestisci).";
            case STRUCTURE_DISCOVERY -> "Scopri strutture del mondo e visitale.";
            case ARCHAEOLOGY_BRUSH -> "Usa il pennello sui blocchi archeologici.";
            case STRUCTURE_LOOT -> "Apri loot di strutture naturali.";
            case TRIAL_VAULT_OPEN -> "Apri le volte nelle Camere della Prova.";
            case RAID_WIN -> "Completa raid difendendo il villaggio fino alla vittoria.";
            case FOOD_CRAFT -> "Produci cibo utile alla citta (es. bistecche, pane, carote dorate).";
            case BREW_POTION -> "Produci pozioni nel brewing stand (normali, splash o livello II).";
            case ITEM_CRAFT -> "Crafta unita di progresso tramite:\n- Beacon\n- Armature in diamante o netherite\n- Macchinari di redstone";
            case NETHER_ACTIVITY -> "Nel Nether completa azioni valide, per esempio combattimento o raccolta di materiali rari.";
            case OCEAN_ACTIVITY -> "Nell'oceano completa azioni valide, per esempio combattimento o raccolta di blocchi marini.";
            case PLACE_BLOCK -> "Costruzione cooperativa: piazza molti blocchi per il monumento della citta.";
            case TEAM_PHASE_QUEST -> "Quest stagionale in 3 fasi: raccogli risorse, costruisci la struttura, poi sconfiggi il boss finale.";
            case SECRET_QUEST -> "Una missione nascosta con indizi sparsi nel mondo. Decifra i trigger per sbloccarla.";
            case TRANSPORT_DISTANCE -> "Percorri distanza usando movimenti/mezzi.";
            case DIMENSION_TRAVEL -> "Viaggia tra Overworld, Nether ed End.";
            case VILLAGER_TRADE -> "Fai scambi con i villager.";
            case ECONOMY_ADVANCED -> "Accumula progress economico con attivita avanzate.";
            case PLAYTIME_MINUTES -> "Rimani attivo online nel ciclo corrente.";
            case XP_PICKUP -> "Raccogli sfere XP dalle tue attivita.";
        };
    }
}
