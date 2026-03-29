package it.patric.cittaexp.wiki;

public enum CityWikiChapter {
    HOME("home"),
    CITY_HUB("city_hub"),
    MEMBERS_ROLES("members_roles"),
    TERRITORIES("territories"),
    BANK_VAULT("bank_vault"),
    LEVELS_GROWTH("levels_growth"),
    DIPLOMACY("diplomacy"),
    CHALLENGES("challenges"),
    DEFENSE("defense"),
    DISCORD_LINK("discord_link"),
    BASIC_COMMANDS("basic_commands");

    private final String configKey;

    CityWikiChapter(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public static CityWikiChapter fromButtonId(String buttonId) {
        if (buttonId == null || buttonId.isBlank()) {
            return null;
        }
        return switch (buttonId.trim().toLowerCase()) {
            case "home" -> HOME;
            case "city_hub", "hub" -> CITY_HUB;
            case "members_roles", "members" -> MEMBERS_ROLES;
            case "territories", "territori" -> TERRITORIES;
            case "bank_vault", "bank", "vault" -> BANK_VAULT;
            case "levels_growth", "levels", "livelli" -> LEVELS_GROWTH;
            case "diplomacy", "relations", "diplomazia" -> DIPLOMACY;
            case "challenges", "sfide" -> CHALLENGES;
            case "defense", "difesa" -> DEFENSE;
            case "discord_link", "discord" -> DISCORD_LINK;
            case "basic_commands", "commands", "comandi" -> BASIC_COMMANDS;
            default -> null;
        };
    }
}
