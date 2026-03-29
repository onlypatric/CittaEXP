package it.patric.cittaexp.vault;

import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public final class CityVaultSettings {

    private final boolean enabled;
    private final int slotCount;
    private final int maxStackSize;

    private CityVaultSettings(boolean enabled, int slotCount, int maxStackSize) {
        this.enabled = enabled;
        this.slotCount = slotCount;
        this.maxStackSize = maxStackSize;
    }

    public static CityVaultSettings load(Plugin plugin) {
        plugin.saveResource("vault.yml", false);
        File file = new File(plugin.getDataFolder(), "vault.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        boolean enabled = cfg.getBoolean("enabled", true);
        int slotCount = Math.max(45, cfg.getInt("slotCount", 180));
        int maxStackSize = Math.max(1, Math.min(99, cfg.getInt("maxStackSize", 64)));
        return new CityVaultSettings(enabled, slotCount, maxStackSize);
    }

    public boolean enabled() {
        return enabled;
    }

    public int slotCount() {
        return slotCount;
    }

    public int maxStackSize() {
        return maxStackSize;
    }
}
