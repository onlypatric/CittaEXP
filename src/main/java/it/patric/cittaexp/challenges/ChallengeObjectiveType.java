package it.patric.cittaexp.challenges;

public enum ChallengeObjectiveType {
    RESOURCE_CONTRIBUTION("resource_contribution"),
    VAULT_DELIVERY("vault_delivery"),
    MOB_KILL("mob_kill"),
    RARE_MOB_KILL("rare_mob_kill"),
    BOSS_KILL("boss_kill"),
    BLOCK_MINE("block_mine"),
    CROP_HARVEST("crop_harvest"),
    FARMING_ECOSYSTEM("farming_ecosystem"),
    CONSTRUCTION("construction"),
    REDSTONE_AUTOMATION("redstone_automation"),
    FISH_CATCH("fish_catch"),
    ANIMAL_INTERACTION("animal_interaction"),
    STRUCTURE_DISCOVERY("structure_discovery"),
    ARCHAEOLOGY_BRUSH("archaeology_brush"),
    STRUCTURE_LOOT("structure_loot"),
    TRIAL_VAULT_OPEN("trial_vault_open"),
    RAID_WIN("raid_win"),
    FOOD_CRAFT("food_craft"),
    BREW_POTION("brew_potion"),
    ITEM_CRAFT("item_craft"),
    NETHER_ACTIVITY("nether_activity"),
    OCEAN_ACTIVITY("ocean_activity"),
    PLACE_BLOCK("place_block"),
    TEAM_PHASE_QUEST("team_phase_quest"),
    SECRET_QUEST("secret_quest"),
    TRANSPORT_DISTANCE("transport_distance"),
    DIMENSION_TRAVEL("dimension_travel"),
    VILLAGER_TRADE("villager_trade"),
    ECONOMY_ADVANCED("economy_advanced"),
    PLAYTIME_MINUTES("playtime_minutes"),
    XP_PICKUP("xp_pickup");

    private final String id;

    ChallengeObjectiveType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static ChallengeObjectiveType fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (ChallengeObjectiveType value : values()) {
            if (value.id.equalsIgnoreCase(raw) || value.name().equalsIgnoreCase(raw)) {
                return value;
            }
        }
        return null;
    }
}
