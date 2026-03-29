package it.patric.cittaexp.custommobs;

import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;

public final class CustomMobTypeResolver {

    private static final Map<String, String> ATTRIBUTE_LEGACY_ALIASES = Map.ofEntries(
            Map.entry("generic_max_health", "max_health"),
            Map.entry("generic_follow_range", "follow_range"),
            Map.entry("generic_knockback_resistance", "knockback_resistance"),
            Map.entry("generic_movement_speed", "movement_speed"),
            Map.entry("generic_flying_speed", "flying_speed"),
            Map.entry("generic_attack_damage", "attack_damage"),
            Map.entry("generic_attack_knockback", "attack_knockback"),
            Map.entry("generic_attack_speed", "attack_speed"),
            Map.entry("generic_armor", "armor"),
            Map.entry("generic_armor_toughness", "armor_toughness"),
            Map.entry("generic_luck", "luck"),
            Map.entry("generic_scale", "scale"),
            Map.entry("generic_safe_fall_distance", "safe_fall_distance"),
            Map.entry("generic_fall_damage_multiplier", "fall_damage_multiplier"),
            Map.entry("generic_jump_strength", "jump_strength"),
            Map.entry("generic_burning_time", "burning_time"),
            Map.entry("generic_explosion_knockback_resistance", "explosion_knockback_resistance"),
            Map.entry("generic_movement_efficiency", "movement_efficiency"),
            Map.entry("generic_oxygen_bonus", "oxygen_bonus"),
            Map.entry("generic_water_movement_efficiency", "water_movement_efficiency"),
            Map.entry("generic_tempt_range", "tempt_range"),
            Map.entry("player_block_interaction_range", "block_interaction_range"),
            Map.entry("player_entity_interaction_range", "entity_interaction_range"),
            Map.entry("player_block_break_speed", "block_break_speed"),
            Map.entry("player_mining_efficiency", "mining_efficiency"),
            Map.entry("player_sneaking_speed", "sneaking_speed"),
            Map.entry("zombie_spawn_reinforcements", "spawn_reinforcements"),
            Map.entry("horse_jump_strength", "jump_strength")
    );

    private CustomMobTypeResolver() {
    }

    public static Attribute parseAttribute(String raw) {
        return resolve(raw, "attribute", () -> Registry.ATTRIBUTE, Attribute.class, ATTRIBUTE_LEGACY_ALIASES);
    }

    public static Sound parseSound(String raw) {
        return resolve(raw, "sound", () -> Registry.SOUNDS, Sound.class, Map.of());
    }

    public static PotionEffectType parsePotionEffectType(String raw) {
        return resolve(raw, "potion effect", () -> Registry.POTION_EFFECT_TYPE, PotionEffectType.class, Map.of());
    }

    public static Particle parseParticle(String raw) {
        return resolve(raw, "particle", () -> Registry.PARTICLE_TYPE, Particle.class, Map.of());
    }

    public static EntityType parseEntityType(String raw) {
        return resolve(raw, "entity type", () -> Registry.ENTITY_TYPE, EntityType.class, Map.of());
    }

    public static Material parseMaterial(String raw) {
        return resolve(raw, "material", () -> Registry.MATERIAL, Material.class, Map.of());
    }

    public static Enchantment parseEnchantment(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("invalid enchantment: " + raw);
        }
        String trimmed = raw.trim();
        String normalized = normalizeLegacyToken(trimmed);
        NamespacedKey namespaced = parseNamespacedKey(trimmed);
        try {
            Registry<Enchantment> registry = Registry.ENCHANTMENT;
            if (namespaced != null) {
                Enchantment direct = registry.get(namespaced);
                if (direct != null) {
                    return direct;
                }
            }
            return registry.stream()
                    .filter(candidate -> matches(candidate, normalized, normalized))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("invalid enchantment: " + raw));
        } catch (Throwable throwable) {
            throw new IllegalArgumentException("invalid enchantment: " + raw, throwable);
        }
    }

    private static <T extends Keyed> T resolve(
            String raw,
            String label,
            Supplier<Registry<T>> registrySupplier,
            Class<T> fallbackType,
            Map<String, String> aliases
    ) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("invalid " + label + ": " + raw);
        }
        String trimmed = raw.trim();
        String normalized = normalizeLegacyToken(trimmed);
        String aliasNormalized = aliases.getOrDefault(normalized, normalized);
        T resolved = resolveFromRegistry(trimmed, normalized, aliasNormalized, registrySupplier);
        if (resolved != null) {
            return resolved;
        }
        resolved = resolveFromStaticFields(normalized, aliasNormalized, fallbackType);
        if (resolved != null) {
            return resolved;
        }
        throw new IllegalArgumentException("invalid " + label + ": " + raw);
    }

    private static <T extends Keyed> T resolveFromRegistry(
            String raw,
            String normalizedLegacyToken,
            String aliasNormalizedToken,
            Supplier<Registry<T>> registrySupplier
    ) {
        Registry<T> registry;
        try {
            registry = registrySupplier.get();
        } catch (Throwable ignored) {
            return null;
        }
        NamespacedKey namespaced = parseNamespacedKey(raw);
        if (namespaced != null) {
            T direct = registry.get(namespaced);
            if (direct != null) {
                return direct;
            }
        }
        return registry.stream()
                .filter(candidate -> matches(candidate, normalizedLegacyToken, aliasNormalizedToken))
                .findFirst()
                .orElse(null);
    }

    private static <T extends Keyed> T resolveFromStaticFields(String normalizedLegacyToken, String aliasNormalizedToken, Class<T> type) {
        for (java.lang.reflect.Field field : type.getFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (!type.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                Object rawValue = field.get(null);
                if (!(rawValue instanceof Keyed keyed)) {
                    continue;
                }
                if (matches(keyed, normalizedLegacyToken, aliasNormalizedToken)) {
                    return type.cast(rawValue);
                }
                String fieldName = normalizeLegacyToken(field.getName());
                if (fieldName.equals(normalizedLegacyToken) || fieldName.equals(aliasNormalizedToken)) {
                    return type.cast(rawValue);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static boolean matches(Keyed candidate, String normalizedLegacyToken, String aliasNormalizedToken) {
        String fullKeyNormalized = normalizeNamespacedKey(candidate.getKey());
        String keyOnlyNormalized = normalizeKeyOnly(candidate.getKey().getKey());
        return fullKeyNormalized.equals(normalizedLegacyToken)
                || keyOnlyNormalized.equals(normalizedLegacyToken)
                || fullKeyNormalized.equals(aliasNormalizedToken)
                || keyOnlyNormalized.equals(aliasNormalizedToken)
                || normalizedLegacyToken.endsWith("_" + keyOnlyNormalized)
                || aliasNormalizedToken.endsWith("_" + keyOnlyNormalized);
    }

    private static NamespacedKey parseNamespacedKey(String raw) {
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        if (!trimmed.contains(":")) {
            return NamespacedKey.minecraft(normalizeKeyOnly(trimmed));
        }
        String[] parts = trimmed.split(":", 2);
        if (parts[0].isBlank() || parts[1].isBlank()) {
            return null;
        }
        return NamespacedKey.fromString(parts[0] + ":" + normalizeKeyOnly(parts[1]));
    }

    private static String normalizeLegacyToken(String raw) {
        return raw.trim()
                .toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_')
                .replace('.', '_')
                .replace(':', '_');
    }

    private static String normalizeNamespacedKey(NamespacedKey key) {
        return normalizeLegacyToken(key.toString());
    }

    private static String normalizeKeyOnly(String raw) {
        return raw.trim()
                .toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_')
                .replace('.', '_');
    }
}
