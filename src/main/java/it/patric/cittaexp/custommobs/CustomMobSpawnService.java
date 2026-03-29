package it.patric.cittaexp.custommobs;

import it.patric.cittaexp.text.MiniMessageHelper;
import it.patric.cittaexp.utils.EntityGlowService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class CustomMobSpawnService {

    private final Plugin plugin;
    private volatile CustomMobRegistry mobRegistry;
    private volatile MobTraitRegistry traitRegistry;
    private volatile MobVariantRegistry variantRegistry;
    private final CustomMobMarkers markers;
    private final MobEffectsEngine effectsEngine;
    private final EntityGlowService glowService;

    public CustomMobSpawnService(
            Plugin plugin,
            CustomMobRegistry mobRegistry,
            MobTraitRegistry traitRegistry,
            MobVariantRegistry variantRegistry,
            CustomMobMarkers markers,
            MobEffectsEngine effectsEngine
    ) {
        this.plugin = plugin;
        this.mobRegistry = mobRegistry;
        this.traitRegistry = traitRegistry;
        this.variantRegistry = variantRegistry;
        this.markers = markers;
        this.effectsEngine = effectsEngine;
        this.glowService = new EntityGlowService(plugin);
    }

    public void reloadRegistries(
            CustomMobRegistry mobRegistry,
            MobTraitRegistry traitRegistry,
            MobVariantRegistry variantRegistry
    ) {
        this.mobRegistry = mobRegistry;
        this.traitRegistry = traitRegistry;
        this.variantRegistry = variantRegistry;
    }

    public LivingEntity spawn(String mobId, Location location, CustomMobSpawnContext context) {
        CustomMobSpec baseSpec = mobRegistry.get(mobId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown custom mob id: " + mobId));
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("Spawn location world is null");
        }

        ResolvedCustomMobSpec resolvedSpec = resolveSpec(baseSpec);
        LivingEntity entity = (LivingEntity) location.getWorld().spawnEntity(location, baseSpec.entityType());
        applySpec(entity, resolvedSpec);
        markers.apply(entity.getPersistentDataContainer(), resolvedSpec, context);
        glowService.applyRedGlow(entity);
        entity.addScoreboardTag("cittaexp-custommob");
        entity.addScoreboardTag("cittaexp-custommob:" + resolvedSpec.id());
        if (resolvedSpec.boss()) {
            entity.addScoreboardTag("cittaexp-custommob-boss");
        }
        if (context.summoned()) {
            entity.addScoreboardTag("cittaexp-custommob-summoned");
        }
        if (resolvedSpec.selectedVariantId() != null) {
            entity.addScoreboardTag("cittaexp-variant:" + resolvedSpec.selectedVariantId());
        }
        if (resolvedSpec.selectedTraitId() != null) {
            entity.addScoreboardTag("cittaexp-trait:" + resolvedSpec.selectedTraitId());
        }
        for (String tag : resolvedSpec.tags()) {
            entity.addScoreboardTag("cittaexp-tag:" + tag);
        }
        if (context.spawnAdapter() != null) {
            context.spawnAdapter().afterSpawn(entity, context);
        }
        try {
            effectsEngine.registerSpawnedMob(entity, baseSpec, resolvedSpec, context);
        } catch (Exception exception) {
            plugin.getLogger().warning("[custom-mobs] runtime registration failed mobId=" + resolvedSpec.id() + " reason=" + exception.getMessage());
        }
        return entity;
    }

    private ResolvedCustomMobSpec resolveSpec(CustomMobSpec baseSpec) {
        MobVariantSpec variant = chooseWeightedVariant(baseSpec.variantIds());
        MobTraitSpec trait = chooseWeightedTrait(baseSpec.traitIds());

        String displayName = composeName(baseSpec.displayName(), variant == null ? "" : variant.namePrefix(), variant == null ? "" : variant.nameSuffix());
        displayName = composeName(displayName, trait == null ? "" : trait.namePrefix(), trait == null ? "" : trait.nameSuffix());

        Map<String, Double> attributes = new LinkedHashMap<>(baseSpec.attributes());
        Map<EquipmentSlot, CustomMobEquipmentItem> equipment = new LinkedHashMap<>(baseSpec.equipment());
        Set<String> tags = new LinkedHashSet<>(baseSpec.tags());
        List<String> abilities = new ArrayList<>();
        abilities.addAll(baseSpec.mainAbilityIds());
        abilities.addAll(baseSpec.secondaryAbilityIds());
        abilities.addAll(baseSpec.passiveAbilityIds());

        applyModifier(variant, attributes, equipment, tags, abilities);
        applyModifier(trait, attributes, equipment, tags, abilities);

        return new ResolvedCustomMobSpec(
                baseSpec.id(),
                displayName,
                baseSpec.showName() || !displayName.isBlank(),
                Map.copyOf(attributes),
                Map.copyOf(equipment),
                baseSpec.flags(),
                Set.copyOf(tags),
                baseSpec.boss(),
                List.copyOf(new LinkedHashSet<>(abilities)),
                trait == null ? null : trait.id(),
                variant == null ? null : variant.id(),
                baseSpec.phaseRules(),
                baseSpec.bossbarSpec(),
                baseSpec.localBroadcasts()
        );
    }

    private static String composeName(String baseName, String prefix, String suffix) {
        String current = baseName == null ? "" : baseName.trim();
        String out = current;
        if (prefix != null && !prefix.isBlank()) {
            out = prefix.trim() + (out.isBlank() ? "" : " " + out);
        }
        if (suffix != null && !suffix.isBlank()) {
            out = out.isBlank() ? suffix.trim() : out + " " + suffix.trim();
        }
        return out.trim();
    }

    private static void applyModifier(
            MobVariantSpec modifier,
            Map<String, Double> attributes,
            Map<EquipmentSlot, CustomMobEquipmentItem> equipment,
            Set<String> tags,
            List<String> abilities
    ) {
        if (modifier == null) {
            return;
        }
        attributes.putAll(modifier.attributes());
        equipment.putAll(modifier.equipment());
        tags.addAll(modifier.tags());
        abilities.removeAll(modifier.removeAbilities());
        abilities.addAll(modifier.addAbilities());
    }

    private static void applyModifier(
            MobTraitSpec modifier,
            Map<String, Double> attributes,
            Map<EquipmentSlot, CustomMobEquipmentItem> equipment,
            Set<String> tags,
            List<String> abilities
    ) {
        if (modifier == null) {
            return;
        }
        mergeTraitAttributes(attributes, modifier.attributes());
        equipment.putAll(modifier.equipment());
        tags.addAll(modifier.tags());
        abilities.removeAll(modifier.removeAbilities());
        abilities.addAll(modifier.addAbilities());
    }

    private static void mergeTraitAttributes(Map<String, Double> attributes, Map<String, Double> modifierAttributes) {
        for (var entry : modifierAttributes.entrySet()) {
            String key = entry.getKey();
            Double value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (isMovementAttribute(key)) {
                attributes.merge(key, value, Math::max);
            } else {
                attributes.put(key, value);
            }
        }
    }

    private static boolean isMovementAttribute(String key) {
        return key != null && switch (key.toUpperCase(java.util.Locale.ROOT)) {
            case "GENERIC_MOVEMENT_SPEED", "MOVEMENT_SPEED",
                    "GENERIC_FLYING_SPEED", "FLYING_SPEED",
                    "GENERIC_MOVEMENT_EFFICIENCY", "MOVEMENT_EFFICIENCY",
                    "GENERIC_WATER_MOVEMENT_EFFICIENCY", "WATER_MOVEMENT_EFFICIENCY" -> true;
            default -> false;
        };
    }

    private MobTraitSpec chooseWeightedTrait(List<String> ids) {
        List<Weighted<MobTraitSpec>> weighted = new ArrayList<>();
        int totalWeight = 0;
        for (String id : ids) {
            Optional<MobTraitSpec> resolved = traitRegistry.get(id);
            if (resolved.isEmpty()) {
                continue;
            }
            weighted.add(new Weighted<>(resolved.get(), resolved.get().weight()));
            totalWeight += resolved.get().weight();
        }
        return pickWeighted(weighted, totalWeight);
    }

    private MobVariantSpec chooseWeightedVariant(List<String> ids) {
        List<Weighted<MobVariantSpec>> weighted = new ArrayList<>();
        int totalWeight = 0;
        for (String id : ids) {
            Optional<MobVariantSpec> resolved = variantRegistry.get(id);
            if (resolved.isEmpty()) {
                continue;
            }
            weighted.add(new Weighted<>(resolved.get(), resolved.get().weight()));
            totalWeight += resolved.get().weight();
        }
        return pickWeighted(weighted, totalWeight);
    }

    private <T> T pickWeighted(List<Weighted<T>> weighted, int totalWeight) {
        if (weighted.isEmpty()) {
            return null;
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight) + 1;
        int cursor = 0;
        for (Weighted<T> entry : weighted) {
            cursor += entry.weight();
            if (roll <= cursor) {
                return entry.value();
            }
        }
        return weighted.get(weighted.size() - 1).value();
    }

    private record Weighted<T>(T value, int weight) {
    }

    private void applySpec(LivingEntity entity, ResolvedCustomMobSpec spec) {
        if (!spec.displayName().isBlank()) {
            entity.customName(MiniMessageHelper.parse(spec.displayName()));
        }
        entity.setCustomNameVisible(spec.showName());
        entity.setSilent(spec.flags().silent());
        entity.setCollidable(spec.flags().collidable());
        entity.setInvulnerable(spec.flags().invulnerable());

        for (var entry : spec.attributes().entrySet()) {
            try {
                Attribute attribute = CustomMobParsingSupport.parseAttribute(entry.getKey());
                Optional.ofNullable(entity.getAttribute(attribute)).ifPresent(instance -> instance.setBaseValue(entry.getValue()));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("[custom-mobs] invalid attribute key for mob spawn key=" + entry.getKey() + " reason=" + exception.getMessage());
            }
        }
        if (entity.getAttribute(Attribute.MAX_HEALTH) != null) {
            entity.setHealth(entity.getAttribute(Attribute.MAX_HEALTH).getValue());
        }
        if (entity instanceof Mob mob) {
            mob.setAware(true);
            mob.setAI(true);
        }

        EntityEquipment equipment = entity.getEquipment();
        if (equipment != null) {
            for (var entry : spec.equipment().entrySet()) {
                applyEquipment(equipment, entry.getKey(), entry.getValue());
            }
        }
    }

    private void applyEquipment(EntityEquipment equipment, EquipmentSlot slot, CustomMobEquipmentItem equipmentItem) {
        ItemStack item = equipmentItem == null ? null : new ItemStack(equipmentItem.material());
        if (item != null && !equipmentItem.enchants().isEmpty()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                equipmentItem.enchants().forEach((enchantmentId, level) -> {
                    try {
                        meta.addEnchant(CustomMobTypeResolver.parseEnchantment(enchantmentId), level, true);
                    } catch (IllegalArgumentException exception) {
                        plugin.getLogger().warning("[custom-mobs] invalid enchantment key for equipment enchant=" + enchantmentId + " reason=" + exception.getMessage());
                    }
                });
                item.setItemMeta(meta);
            }
        }
        switch (slot) {
            case HAND -> equipment.setItemInMainHand(item);
            case OFF_HAND -> equipment.setItemInOffHand(item);
            case FEET -> equipment.setBoots(item);
            case LEGS -> equipment.setLeggings(item);
            case CHEST -> equipment.setChestplate(item);
            case HEAD -> equipment.setHelmet(item);
            default -> {
            }
        }
    }
}
