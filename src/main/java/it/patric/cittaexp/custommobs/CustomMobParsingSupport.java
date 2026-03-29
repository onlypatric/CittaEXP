package it.patric.cittaexp.custommobs;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.EquipmentSlot;

final class CustomMobParsingSupport {

    private CustomMobParsingSupport() {
    }

    static String requireString(ConfigurationSection yaml, String path) {
        String value = yaml.getString(path);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing " + path);
        }
        return value.trim();
    }

    static <E extends Enum<E>> E parseEnum(Class<E> enumType, String raw, String label) {
        try {
            return Enum.valueOf(enumType, normalizeEnumToken(raw));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid " + label + ": " + raw, exception);
        }
    }

    static Attribute parseAttribute(String raw) {
        return CustomMobTypeResolver.parseAttribute(raw);
    }

    static Map<String, Double> parseAttributes(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Double> values = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("invalid attribute key");
            }
            values.put(key.trim(), section.getDouble(key));
        }
        return values;
    }

    static Map<EquipmentSlot, CustomMobEquipmentItem> parseEquipment(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<EquipmentSlot, CustomMobEquipmentItem> values = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            EquipmentSlot slot = switch (key.toLowerCase(Locale.ROOT)) {
                case "helmet" -> EquipmentSlot.HEAD;
                case "chestplate" -> EquipmentSlot.CHEST;
                case "leggings" -> EquipmentSlot.LEGS;
                case "boots" -> EquipmentSlot.FEET;
                case "mainhand", "main_hand" -> EquipmentSlot.HAND;
                case "offhand", "off_hand" -> EquipmentSlot.OFF_HAND;
                default -> throw new IllegalArgumentException("invalid equipment slot: " + key);
            };
            Object rawValue = section.get(key);
            if (rawValue instanceof String rawMaterial) {
                values.put(slot, new CustomMobEquipmentItem(CustomMobTypeResolver.parseMaterial(rawMaterial), Map.of()));
                continue;
            }
            ConfigurationSection itemSection = section.getConfigurationSection(key);
            if (itemSection == null) {
                throw new IllegalArgumentException("invalid equipment item at slot: " + key);
            }
            Material material = CustomMobTypeResolver.parseMaterial(requireString(itemSection, "material"));
            Map<String, Integer> enchants = parseEnchantments(itemSection.getConfigurationSection("enchants"));
            values.put(slot, new CustomMobEquipmentItem(material, enchants));
        }
        return values;
    }

    static Map<String, Integer> parseEnchantments(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Integer> values = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("invalid enchantment key");
            }
            String enchantment = normalizeEnumToken(key).toLowerCase(Locale.ROOT);
            int level = Math.max(1, section.getInt(key, 1));
            values.put(enchantment, level);
        }
        return values;
    }

    static Set<String> parseNormalizedStrings(List<String> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String value : rawValues) {
            if (value == null || value.isBlank()) {
                continue;
            }
            result.add(value.trim().toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(result);
    }

    static CustomMobCatalogMetadata parseCatalogMetadata(
            ConfigurationSection yaml,
            String inferredArcId,
            Set<String> defaultPools,
            Set<String> defaultThemes
    ) {
        String packId = yaml == null ? null : yaml.getString("packId", CustomMobCatalogMetadata.DEFAULT_PACK_ID);
        String arcId = yaml == null ? inferredArcId : yaml.getString("arc", inferredArcId);
        Set<String> pools = yaml == null ? defaultPools : parseNormalizedStrings(yaml.getStringList("pools"));
        Set<String> themes = yaml == null ? defaultThemes : parseNormalizedStrings(yaml.getStringList("themes"));
        if (pools.isEmpty()) {
            pools = defaultPools;
        }
        if (themes.isEmpty()) {
            themes = defaultThemes;
        }
        return new CustomMobCatalogMetadata(packId, arcId, pools, themes);
    }

    static void requireKnownAbilities(List<String> abilityIds, MobAbilityRegistry abilityRegistry, String label) {
        for (String abilityId : abilityIds) {
            if (abilityId == null || abilityId.isBlank()) {
                throw new IllegalArgumentException("invalid empty ability reference in " + label);
            }
            if (abilityRegistry.get(abilityId.trim()).isEmpty()) {
                throw new IllegalArgumentException("unknown ability id referenced by " + label + ": " + abilityId);
            }
        }
    }

    static String normalizeEnumToken(String raw) {
        String trimmed = raw.trim().replace('-', '_').replace(' ', '_');
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < trimmed.length(); i++) {
            char current = trimmed.charAt(i);
            if (Character.isUpperCase(current) && i > 0) {
                char previous = trimmed.charAt(i - 1);
                if (previous != '_' && !Character.isUpperCase(previous)) {
                    builder.append('_');
                }
            }
            builder.append(Character.toUpperCase(current));
        }
        return builder.toString();
    }
}
