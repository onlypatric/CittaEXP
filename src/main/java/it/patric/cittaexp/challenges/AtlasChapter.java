package it.patric.cittaexp.challenges;

import org.bukkit.Material;

public enum AtlasChapter {
    EXTRACTION("Extraction", Material.IRON_PICKAXE),
    COMBAT("Combat", Material.DIAMOND_SWORD),
    AGRONOMY("Agronomy", Material.WHEAT),
    INDUSTRY("Industry", Material.PISTON),
    FRONTIER("Frontier", Material.COMPASS),
    CIVIC("Civic", Material.EMERALD);

    private final String defaultDisplayName;
    private final Material defaultIcon;

    AtlasChapter(String defaultDisplayName, Material defaultIcon) {
        this.defaultDisplayName = defaultDisplayName;
        this.defaultIcon = defaultIcon;
    }

    public String defaultDisplayName() {
        return defaultDisplayName;
    }

    public Material defaultIcon() {
        return defaultIcon;
    }

    public static AtlasChapter fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(java.util.Locale.ROOT);
        for (AtlasChapter value : values()) {
            if (value.name().equals(normalized)) {
                return value;
            }
        }
        return null;
    }
}
