package it.patric.cittaexp.runtime.integration;

import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class IntegrationConfigLoader {

    private final JavaPlugin plugin;

    public IntegrationConfigLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public IntegrationSettings load() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Cannot create plugin data folder: " + dataFolder.getAbsolutePath());
        }

        File configFile = new File(dataFolder, "integration.yml");
        if (!configFile.exists()) {
            plugin.saveResource("integration.yml", false);
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
        String root = "integration";
        return new IntegrationSettings(
                cfg.getBoolean(root + ".failFast", true),
                new IntegrationSettings.RankingSettings(
                        cfg.getInt(root + ".ranking.scanLimit", 5000)
                ),
                new IntegrationSettings.ClaimSettings(
                        cfg.getInt(root + ".claim.auto.width", 100),
                        cfg.getInt(root + ".claim.auto.height", 100),
                        cfg.getString(root + ".claim.commands.create", ""),
                        cfg.getString(root + ".claim.commands.expand", "")
                )
        );
    }
}
