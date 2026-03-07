package it.patric.cittaexp.economy.config;

import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class EconomyConfigLoader {

    private final JavaPlugin plugin;

    public EconomyConfigLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public EconomySettings load() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Cannot create plugin data folder: " + dataFolder.getAbsolutePath());
        }

        File configFile = new File(dataFolder, "economy.yml");
        if (!configFile.exists()) {
            plugin.saveResource("economy.yml", false);
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
        String root = "economy";
        EconomySettings.ScheduleSettings schedule = new EconomySettings.ScheduleSettings(
                cfg.getString(root + ".schedule.timezone", "Europe/Rome"),
                parseTime(cfg.getString(root + ".schedule.runTime", "00:05")),
                cfg.getBoolean(root + ".schedule.catchUp", true),
                cfg.getBoolean(root + ".schedule.skipCreationMonth", true),
                cfg.getInt(root + ".schedule.tickIntervalSeconds", 30)
        );
        EconomySettings.TaxSettings tax = new EconomySettings.TaxSettings(
                cfg.getLong(root + ".tax.borgoMonthlyCost", 1000L),
                cfg.getLong(root + ".tax.villaggioMonthlyCost", 2500L),
                cfg.getLong(root + ".tax.regnoMonthlyCost", 5000L),
                cfg.getLong(root + ".tax.regnoShopMonthlyExtraCost", 0L),
                cfg.getInt(root + ".tax.dueDayOfMonth", 1)
        );
        EconomySettings.CapitalSettings capital = new EconomySettings.CapitalSettings(
                cfg.getLong(root + ".capital.monthlyBonus", 1500L),
                cfg.getString(root + ".capital.slotKey", "PRIMARY")
        );
        return new EconomySettings(
                cfg.getBoolean(root + ".enabled", true),
                schedule,
                tax,
                capital
        );
    }

    private static LocalTime parseTime(String raw) {
        String value = raw == null ? "00:05" : raw.trim();
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException ex) {
            return LocalTime.of(0, 5);
        }
    }
}
