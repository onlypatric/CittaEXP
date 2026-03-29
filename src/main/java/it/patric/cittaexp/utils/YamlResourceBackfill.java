package it.patric.cittaexp.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public final class YamlResourceBackfill {

    private YamlResourceBackfill() {
    }

    public static int ensure(Plugin plugin, String resourceName) {
        File file = new File(plugin.getDataFolder(), resourceName);
        if (!file.exists()) {
            plugin.saveResource(resourceName, false);
            return 0;
        }
        try (InputStream input = plugin.getResource(resourceName)) {
            if (input == null) {
                return 0;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
            YamlConfiguration live = YamlConfiguration.loadConfiguration(file);
            int added = mergeMissing(live, defaults);
            if (added > 0) {
                live.save(file);
                plugin.getLogger().info("[config-backfill] " + resourceName + " aggiornato con " + added + " chiavi mancanti");
            }
            return added;
        } catch (IOException exception) {
            plugin.getLogger().warning("[config-backfill] impossibile aggiornare " + resourceName + ": " + exception.getMessage());
            return 0;
        }
    }

    private static int mergeMissing(ConfigurationSection target, ConfigurationSection defaults) {
        int added = 0;
        for (String key : defaults.getKeys(false)) {
            Object defaultValue = defaults.get(key);
            if (defaultValue instanceof ConfigurationSection defaultSection) {
                ConfigurationSection targetSection = target.getConfigurationSection(key);
                if (targetSection == null) {
                    targetSection = target.createSection(key);
                }
                added += mergeMissing(targetSection, defaultSection);
                continue;
            }
            if (!target.contains(key)) {
                target.set(key, defaultValue);
                added++;
            }
        }
        return added;
    }
}
