package it.patric.cittaexp.style;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class CityBannerDisplayService {

    private final Logger logger;
    private final NamespacedKey cityIdKey;

    public CityBannerDisplayService(Plugin plugin, Logger logger) {
        Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.cityIdKey = new NamespacedKey(plugin, "city_banner_city_id");
    }

    public boolean render(UUID cityId, CityStyleRecord style) {
        Objects.requireNonNull(cityId, "cityId");
        Objects.requireNonNull(style, "style");
        if (!style.bannerLocation().configured()) {
            return false;
        }
        World world = Bukkit.getWorld(style.bannerLocation().world());
        if (world == null) {
            logger.warning("[CittaEXP][style] banner world not found cityId=" + cityId + " world=" + style.bannerLocation().world());
            return false;
        }

        remove(cityId);
        Material material = resolveBannerMaterial(style.bannerSpec());
        try {
            BlockData blockData = Bukkit.createBlockData(material);
            Location location = new Location(
                    world,
                    style.bannerLocation().x(),
                    style.bannerLocation().y(),
                    style.bannerLocation().z(),
                    style.bannerLocation().yaw(),
                    style.bannerLocation().pitch()
            );
            BlockDisplay display = world.spawn(location, BlockDisplay.class, entity -> {
                entity.setBlock(blockData);
                entity.setPersistent(false);
                entity.getPersistentDataContainer().set(cityIdKey, PersistentDataType.STRING, cityId.toString());
            });
            display.customName(Component.text("Stendardo Citta"));
            display.setCustomNameVisible(false);
            return true;
        } catch (RuntimeException ex) {
            logger.log(Level.WARNING, "[CittaEXP][style] banner display render failed cityId=" + cityId, ex);
            return false;
        }
    }

    public int remove(UUID cityId) {
        Objects.requireNonNull(cityId, "cityId");
        int removed = 0;
        String expected = cityId.toString();
        for (World world : Bukkit.getWorlds()) {
            for (BlockDisplay display : world.getEntitiesByClass(BlockDisplay.class)) {
                String value = display.getPersistentDataContainer().get(cityIdKey, PersistentDataType.STRING);
                if (expected.equalsIgnoreCase(value)) {
                    display.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    private static Material resolveBannerMaterial(String bannerSpec) {
        String raw = bannerSpec == null ? "" : bannerSpec.trim();
        if (raw.isBlank()) {
            return Material.WHITE_BANNER;
        }
        String normalized = raw.toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
        Material material = Material.matchMaterial(normalized, true);
        if (material == null && !normalized.endsWith("_BANNER")) {
            material = Material.matchMaterial(normalized + "_BANNER", true);
        }
        if (material == null || !material.isBlock() || !material.name().endsWith("_BANNER")) {
            return Material.WHITE_BANNER;
        }
        return material;
    }
}
