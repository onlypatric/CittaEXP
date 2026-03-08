package it.patric.cittaexp.style;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public final class YamlCityStyleService implements CityStyleService {

    private final File file;
    private final Logger logger;
    private final Object lock = new Object();

    public YamlCityStyleService(Plugin plugin, Logger logger) {
        Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Cannot create plugin data folder: " + dataFolder.getAbsolutePath());
        }
        this.file = new File(dataFolder, "city-style.yml");
    }

    @Override
    public Optional<CityStyleRecord> find(UUID cityId) {
        Objects.requireNonNull(cityId, "cityId");
        synchronized (lock) {
            YamlConfiguration yaml = load();
            return Optional.ofNullable(read(yaml, cityId));
        }
    }

    @Override
    public CityStyleRecord saveOnCreate(UUID cityId, String bannerSpec, String armorSpec) {
        Objects.requireNonNull(cityId, "cityId");
        synchronized (lock) {
            YamlConfiguration yaml = load();
            CityStyleRecord current = read(yaml, cityId);
            CityStyleRecord next = new CityStyleRecord(
                    cityId,
                    nonBlank(bannerSpec, current == null ? "" : current.bannerSpec()),
                    nonBlank(armorSpec, current == null ? "" : current.armorSpec()),
                    current == null ? CityStyleRecord.BannerLocation.empty() : current.bannerLocation(),
                    System.currentTimeMillis()
            );
            write(yaml, next);
            save(yaml);
            return next;
        }
    }

    @Override
    public CityStyleRecord updateBanner(UUID cityId, String bannerSpec) {
        Objects.requireNonNull(cityId, "cityId");
        synchronized (lock) {
            YamlConfiguration yaml = load();
            CityStyleRecord current = read(yaml, cityId);
            CityStyleRecord next = new CityStyleRecord(
                    cityId,
                    requireText(bannerSpec, "bannerSpec"),
                    current == null ? "" : current.armorSpec(),
                    current == null ? CityStyleRecord.BannerLocation.empty() : current.bannerLocation(),
                    System.currentTimeMillis()
            );
            write(yaml, next);
            save(yaml);
            return next;
        }
    }

    @Override
    public CityStyleRecord updateArmor(UUID cityId, String armorSpec) {
        Objects.requireNonNull(cityId, "cityId");
        synchronized (lock) {
            YamlConfiguration yaml = load();
            CityStyleRecord current = read(yaml, cityId);
            CityStyleRecord next = new CityStyleRecord(
                    cityId,
                    current == null ? "" : current.bannerSpec(),
                    requireText(armorSpec, "armorSpec"),
                    current == null ? CityStyleRecord.BannerLocation.empty() : current.bannerLocation(),
                    System.currentTimeMillis()
            );
            write(yaml, next);
            save(yaml);
            return next;
        }
    }

    @Override
    public CityStyleRecord setBannerLocation(UUID cityId, String world, double x, double y, double z, float yaw, float pitch) {
        Objects.requireNonNull(cityId, "cityId");
        synchronized (lock) {
            YamlConfiguration yaml = load();
            CityStyleRecord current = read(yaml, cityId);
            CityStyleRecord next = new CityStyleRecord(
                    cityId,
                    current == null ? "" : current.bannerSpec(),
                    current == null ? "" : current.armorSpec(),
                    new CityStyleRecord.BannerLocation(requireText(world, "world"), x, y, z, yaw, pitch),
                    System.currentTimeMillis()
            );
            write(yaml, next);
            save(yaml);
            return next;
        }
    }

    @Override
    public CityStyleRecord clearBannerLocation(UUID cityId) {
        Objects.requireNonNull(cityId, "cityId");
        synchronized (lock) {
            YamlConfiguration yaml = load();
            CityStyleRecord current = read(yaml, cityId);
            CityStyleRecord next = new CityStyleRecord(
                    cityId,
                    current == null ? "" : current.bannerSpec(),
                    current == null ? "" : current.armorSpec(),
                    CityStyleRecord.BannerLocation.empty(),
                    System.currentTimeMillis()
            );
            write(yaml, next);
            save(yaml);
            return next;
        }
    }

    private YamlConfiguration load() {
        return YamlConfiguration.loadConfiguration(file);
    }

    private static CityStyleRecord read(YamlConfiguration yaml, UUID cityId) {
        ConfigurationSection section = yaml.getConfigurationSection("cities." + cityId);
        if (section == null) {
            return null;
        }
        String world = section.getString("banner.world", "");
        CityStyleRecord.BannerLocation location = world == null || world.isBlank()
                ? CityStyleRecord.BannerLocation.empty()
                : new CityStyleRecord.BannerLocation(
                        world,
                        section.getDouble("banner.x"),
                        section.getDouble("banner.y"),
                        section.getDouble("banner.z"),
                        (float) section.getDouble("banner.yaw"),
                        (float) section.getDouble("banner.pitch")
                );
        return new CityStyleRecord(
                cityId,
                section.getString("bannerSpec", ""),
                section.getString("armorSpec", ""),
                location,
                section.getLong("updatedAt", 0L)
        );
    }

    private static void write(YamlConfiguration yaml, CityStyleRecord record) {
        String root = "cities." + record.cityId();
        yaml.set(root + ".bannerSpec", record.bannerSpec());
        yaml.set(root + ".armorSpec", record.armorSpec());
        yaml.set(root + ".updatedAt", record.updatedAtEpochMilli());
        if (record.bannerLocation().configured()) {
            yaml.set(root + ".banner.world", record.bannerLocation().world());
            yaml.set(root + ".banner.x", record.bannerLocation().x());
            yaml.set(root + ".banner.y", record.bannerLocation().y());
            yaml.set(root + ".banner.z", record.bannerLocation().z());
            yaml.set(root + ".banner.yaw", record.bannerLocation().yaw());
            yaml.set(root + ".banner.pitch", record.bannerLocation().pitch());
        } else {
            yaml.set(root + ".banner", null);
        }
    }

    private void save(YamlConfiguration yaml) {
        try {
            yaml.save(file);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "[CittaEXP][style] cannot save city-style.yml", ex);
            throw new IllegalStateException("city-style-save-failed", ex);
        }
    }

    private static String requireText(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalStateException(field + "-blank");
        }
        return normalized;
    }

    private static String nonBlank(String incoming, String fallback) {
        String normalized = incoming == null ? "" : incoming.trim();
        if (!normalized.isBlank()) {
            return normalized;
        }
        return fallback == null ? "" : fallback.trim();
    }
}
