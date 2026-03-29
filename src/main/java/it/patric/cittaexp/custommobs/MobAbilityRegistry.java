package it.patric.cittaexp.custommobs;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class MobAbilityRegistry {

    public record LoadResult(
            MobAbilityRegistry registry,
            int loadedCount,
            int skippedCount,
            List<String> warnings
    ) {
    }

    private final Map<String, MobAbilitySpec> abilities;

    public MobAbilityRegistry(Map<String, MobAbilitySpec> abilities) {
        this.abilities = Map.copyOf(abilities);
    }

    public static LoadResult load(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            return new LoadResult(new MobAbilityRegistry(Map.of()), 0, 0, List.of());
        }
        File[] files = directory.listFiles((dir, name) -> {
            String normalized = name.toLowerCase(Locale.ROOT);
            return normalized.endsWith(".yml") && !normalized.equals("pack-manifest.yml");
        });
        if (files == null || files.length == 0) {
            return new LoadResult(new MobAbilityRegistry(Map.of()), 0, 0, List.of());
        }
        Arrays.sort(files);
        Map<String, MobAbilitySpec> loaded = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        int skipped = 0;
        for (File file : files) {
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                MobAbilitySpec spec = parseAbility(yaml);
                if (loaded.containsKey(spec.id())) {
                    skipped++;
                    warnings.add(file.getName() + ": duplicate ability id '" + spec.id() + "'");
                    continue;
                }
                loaded.put(spec.id(), spec);
            } catch (Exception exception) {
                skipped++;
                warnings.add(file.getName() + ": " + exception.getMessage());
            }
        }
        return new LoadResult(new MobAbilityRegistry(loaded), loaded.size(), skipped, List.copyOf(warnings));
    }

    private static MobAbilitySpec parseAbility(YamlConfiguration yaml) {
        String id = CustomMobParsingSupport.requireString(yaml, "id");
        MobTriggerType trigger = CustomMobParsingSupport.parseEnum(
                MobTriggerType.class,
                CustomMobParsingSupport.requireString(yaml, "trigger"),
                "trigger"
        );
        MobTargeterSpec targeting = parseTargeting(yaml);
        int cooldownTicks = Math.max(0, yaml.getInt("cooldownTicks", 0));
        List<MobConditionSpec> conditions = parseConditions(yaml);
        List<Map<?, ?>> rawActions = yaml.getMapList("actions");
        if (rawActions.isEmpty()) {
            throw new IllegalArgumentException("missing actions");
        }
        List<MobActionSpec> actions = new ArrayList<>();
        for (Map<?, ?> rawAction : rawActions) {
            actions.add(parseAction(rawAction, true));
        }
        return new MobAbilitySpec(id, trigger, cooldownTicks, targeting, conditions, actions);
    }

    private static MobTargeterSpec parseTargeting(YamlConfiguration yaml) {
        Object raw = yaml.get("targeting");
        if (raw == null) {
            return MobTargeterSpec.SELF;
        }
        if (raw instanceof String stringValue) {
            MobTargeterType type = CustomMobParsingSupport.parseEnum(MobTargeterType.class, stringValue, "targeting");
            return new MobTargeterSpec(type, 0.0D, defaultMaxTargets(type));
        }
        if (raw instanceof ConfigurationSection section) {
            MobTargeterType type = CustomMobParsingSupport.parseEnum(
                    MobTargeterType.class,
                    CustomMobParsingSupport.requireString(section, "type"),
                    "targeting"
            );
            double radius = section.getDouble("radius", defaultRadius(type));
            int maxTargets = Math.max(0, section.getInt("maxTargets", defaultMaxTargets(type)));
            return new MobTargeterSpec(type, radius, maxTargets);
        }
        throw new IllegalArgumentException("invalid targeting");
    }

    private static List<MobConditionSpec> parseConditions(YamlConfiguration yaml) {
        List<Map<?, ?>> rawConditions = yaml.getMapList("conditions");
        if (rawConditions.isEmpty()) {
            return List.of();
        }
        List<MobConditionSpec> conditions = new ArrayList<>();
        for (Map<?, ?> rawCondition : rawConditions) {
            conditions.add(parseCondition(rawCondition));
        }
        return List.copyOf(conditions);
    }

    private static MobConditionSpec parseCondition(Map<?, ?> rawCondition) {
        MobConditionType type = CustomMobParsingSupport.parseEnum(
                MobConditionType.class,
                requireMapString(rawCondition, "type"),
                "condition type"
        );
        return switch (type) {
            case HP_BELOW -> new MobConditionSpec.HpBelowConditionSpec(getDouble(rawCondition, "ratio", 1.0D));
            case HP_ABOVE -> new MobConditionSpec.HpAboveConditionSpec(getDouble(rawCondition, "ratio", 0.0D));
            case TARGET_EXISTS -> new MobConditionSpec.TargetExistsConditionSpec();
            case HAS_TAG -> new MobConditionSpec.HasTagConditionSpec(requireMapString(rawCondition, "tag").toLowerCase(Locale.ROOT));
            case WAVE_AT_LEAST -> new MobConditionSpec.WaveAtLeastConditionSpec(Math.max(1, getInt(rawCondition, "wave", 1)));
            case TRIGGER_SOURCE_IS -> {
                Object rawKinds = rawCondition.get("sourceKinds");
                Set<MobTriggerSourceKind> kinds = new LinkedHashSet<>();
                if (rawKinds instanceof List<?> list && !list.isEmpty()) {
                    for (Object value : list) {
                        if (!(value instanceof String token)) {
                            throw new IllegalArgumentException("condition sourceKinds contains non-string");
                        }
                        kinds.add(CustomMobParsingSupport.parseEnum(MobTriggerSourceKind.class, token, "source kind"));
                    }
                } else {
                    kinds.add(CustomMobParsingSupport.parseEnum(
                            MobTriggerSourceKind.class,
                            requireMapString(rawCondition, "sourceKind"),
                            "source kind"
                    ));
                }
                yield new MobConditionSpec.TriggerSourceConditionSpec(kinds);
            }
            case HAS_TRAIT -> new MobConditionSpec.HasTraitConditionSpec(requireMapString(rawCondition, "traitId"));
            case HAS_VARIANT -> new MobConditionSpec.HasVariantConditionSpec(requireMapString(rawCondition, "variantId"));
            case TEMP_FLAG_PRESENT -> new MobConditionSpec.TempFlagPresentConditionSpec(requireMapString(rawCondition, "flag"));
        };
    }

    static MobActionSpec parseAction(Map<?, ?> rawAction, boolean allowProjectile) {
        MobActionType actionType = parseActionType(rawAction);
        return switch (actionType) {
            case PARTICLES -> new MobActionSpec.ParticlesActionSpec(
                    CustomMobTypeResolver.parseParticle(requireMapString(rawAction, "particle")),
                    Math.max(0, getInt(rawAction, "count", 1)),
                    getDouble(rawAction, "offsetX", 0.0D),
                    getDouble(rawAction, "offsetY", 0.0D),
                    getDouble(rawAction, "offsetZ", 0.0D),
                    getDouble(rawAction, "extra", 0.0D)
            );
            case SOUND -> new MobActionSpec.SoundActionSpec(
                    requireMapString(rawAction, "sound"),
                    (float) getDouble(rawAction, "volume", 1.0D),
                    (float) getDouble(rawAction, "pitch", 1.0D)
            );
            case APPLY_POTION, APPLY_POTION_TO_SELF -> new MobActionSpec.ApplyPotionActionSpec(
                    requireMapStringAlias(rawAction, "effect", "potion"),
                    getInt(rawAction, "durationTicks", 100),
                    Math.max(0, getInt(rawAction, "amplifier", 0)),
                    getBoolean(rawAction, "ambient", false),
                    getBoolean(rawAction, "particles", true),
                    getBoolean(rawAction, "icon", true),
                    Math.max(0, getInt(rawAction, "fireTicks", 0))
            );
            case DIRECT_DAMAGE -> new MobActionSpec.DirectDamageActionSpec(Math.max(0.0D, getDouble(rawAction, "amount", 0.0D)));
            case SUMMON -> new MobActionSpec.SummonActionSpec(
                    requireMapStringAlias(rawAction, "spawnRef", "ref"),
                    Math.max(1, getInt(rawAction, "count", 1)),
                    Math.max(0.0D, getDoubleAlias(rawAction, 1.5D, "spreadRadius", "spread"))
            );
            case LAUNCH_PROJECTILE -> {
                if (!allowProjectile) {
                    throw new IllegalArgumentException("nested launchProjectile is not supported");
                }
                List<Map<?, ?>> rawPayload = getMapList(rawAction, "hitActions");
                if (rawPayload.isEmpty()) {
                    throw new IllegalArgumentException("launchProjectile missing hitActions");
                }
                List<MobActionSpec> hitActions = new ArrayList<>();
                for (Map<?, ?> nested : rawPayload) {
                    hitActions.add(parseAction(nested, false));
                }
                String projectileKey = getOptionalString(rawAction, "projectile");
                yield new MobActionSpec.LaunchProjectileActionSpec(
                        Math.max(0.05D, getDouble(rawAction, "speed", 0.7D)),
                        Math.max(1.0D, getDouble(rawAction, "range", 16.0D)),
                        Math.max(0.2D, getDouble(rawAction, "hitboxRadius", 1.2D)),
                        CustomMobTypeResolver.parseParticle(projectileParticleKey(rawAction, projectileKey)),
                        Math.max(0, getInt(rawAction, "particleCount", 1)),
                        Math.max(1, getInt(rawAction, "count", 1)),
                        Math.max(0.0D, getDoubleAlias(rawAction, 0.0D, "spread", "spreadRadius")),
                        hitActions
                );
            }
            case KNOCKBACK, PULL, PUSH -> new MobActionSpec.MotionActionSpec(
                    actionType,
                    Math.max(0.0D, getDouble(rawAction, "strength", 0.6D))
            );
            case SET_TEMP_FLAG -> new MobActionSpec.SetTempFlagActionSpec(
                    requireMapString(rawAction, "flag"),
                    Math.max(1, getInt(rawAction, "durationTicks", 40))
            );
        };
    }

    private static MobActionType parseActionType(Map<?, ?> rawAction) {
        Object rawType = rawAction.get("type");
        if (!(rawType instanceof String typeString) || typeString.isBlank()) {
            throw new IllegalArgumentException("action missing type");
        }
        return CustomMobParsingSupport.parseEnum(MobActionType.class, typeString, "action type");
    }

    private static List<Map<?, ?>> getMapList(Map<?, ?> map, String key) {
        Object raw = map.get(key);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<?, ?>> result = new ArrayList<>();
        for (Object value : list) {
            if (!(value instanceof Map<?, ?> nested)) {
                throw new IllegalArgumentException(key + " contains non-map entry");
            }
            result.add(nested);
        }
        return result;
    }

    static String requireMapString(Map<?, ?> rawAction, String key) {
        Object value = rawAction.get(key);
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new IllegalArgumentException("action missing " + key);
        }
        return stringValue.trim();
    }

    static String requireMapStringAlias(Map<?, ?> rawAction, String primary, String alias) {
        Object primaryValue = rawAction.get(primary);
        if (primaryValue instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue.trim();
        }
        Object aliasValue = rawAction.get(alias);
        if (aliasValue instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue.trim();
        }
        throw new IllegalArgumentException("action missing " + primary);
    }

    static String getOptionalString(Map<?, ?> rawAction, String key) {
        Object value = rawAction.get(key);
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue.trim();
        }
        return null;
    }

    static int getInt(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }

    static double getDouble(Map<?, ?> map, String key, double fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return fallback;
    }

    static double getDoubleAlias(Map<?, ?> map, double fallback, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
        }
        return fallback;
    }

    static boolean getBoolean(Map<?, ?> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return fallback;
    }

    private static int defaultMaxTargets(MobTargeterType type) {
        return switch (type) {
            case SELF, CURRENT_TARGET, GUARDIAN, NEAREST_PLAYER -> 1;
            case PLAYERS_IN_RADIUS, ENTITIES_IN_RADIUS -> 0;
        };
    }

    private static double defaultRadius(MobTargeterType type) {
        return switch (type) {
            case PLAYERS_IN_RADIUS, ENTITIES_IN_RADIUS -> 6.0D;
            default -> 0.0D;
        };
    }

    private static String projectileParticleKey(Map<?, ?> rawAction, String projectileKey) {
        String explicitParticle = getOptionalString(rawAction, "particle");
        if (explicitParticle != null) {
            return explicitParticle;
        }
        if (projectileKey == null) {
            throw new IllegalArgumentException("launchProjectile missing particle");
        }
        return switch (projectileKey.trim().toUpperCase(Locale.ROOT)) {
            case "TRIDENT" -> "BUBBLE";
            case "ARROW" -> "CRIT";
            default -> "CRIT";
        };
    }

    public Optional<MobAbilitySpec> get(String id) {
        return Optional.ofNullable(abilities.get(id));
    }

    public Map<String, MobAbilitySpec> all() {
        return Collections.unmodifiableMap(abilities);
    }
}
