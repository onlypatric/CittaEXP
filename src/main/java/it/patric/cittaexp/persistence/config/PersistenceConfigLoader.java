package it.patric.cittaexp.persistence.config;

import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PersistenceConfigLoader {

    private final JavaPlugin plugin;

    public PersistenceConfigLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public PersistenceSettings load() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Cannot create plugin data folder: " + dataFolder.getAbsolutePath());
        }

        File configFile = new File(dataFolder, "persistence.yml");
        if (!configFile.exists()) {
            plugin.saveResource("persistence.yml", false);
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
        String root = "persistence";
        PersistenceSettings.PersistenceMode mode = PersistenceSettings.PersistenceMode.fromConfig(
                cfg.getString(root + ".mode", "auto")
        );
        boolean fallbackEnabled = cfg.getBoolean(root + ".fallback.enabled", true);

        PersistenceSettings.MysqlSettings mysql = new PersistenceSettings.MysqlSettings(
                cfg.getString(root + ".mysql.host", "127.0.0.1"),
                cfg.getInt(root + ".mysql.port", 3306),
                cfg.getString(root + ".mysql.database", "cittaexp"),
                cfg.getString(root + ".mysql.username", "root"),
                cfg.getString(root + ".mysql.password", ""),
                cfg.getString(root + ".mysql.params", "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC")
        );

        PersistenceSettings.SqliteSettings sqlite = new PersistenceSettings.SqliteSettings(
                cfg.getString(root + ".sqlite.file", "data/cittaexp.sqlite")
        );

        PersistenceSettings.ReplaySettings replay = new PersistenceSettings.ReplaySettings(
                cfg.getInt(root + ".replay.batchSize", 100),
                cfg.getInt(root + ".replay.intervalSeconds", 10)
        );

        return new PersistenceSettings(mode, fallbackEnabled, mysql, sqlite, replay);
    }
}
