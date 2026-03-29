package it.patric.cittaexp.defense;

import it.patric.cittaexp.custommobs.CustomMobTypeResolver;
import java.util.Locale;
import org.bukkit.entity.EntityType;

public record CityDefenseSpawnRef(
        Kind kind,
        EntityType vanillaType,
        String customMobId
) {

    public enum Kind {
        VANILLA,
        CUSTOM
    }

    public CityDefenseSpawnRef {
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        if (kind == Kind.VANILLA && vanillaType == null) {
            throw new IllegalArgumentException("vanillaType is required for VANILLA refs");
        }
        if (kind == Kind.CUSTOM && (customMobId == null || customMobId.isBlank())) {
            throw new IllegalArgumentException("customMobId is required for CUSTOM refs");
        }
    }

    public static CityDefenseSpawnRef vanilla(EntityType type) {
        return new CityDefenseSpawnRef(Kind.VANILLA, type, null);
    }

    public static CityDefenseSpawnRef custom(String mobId) {
        return new CityDefenseSpawnRef(Kind.CUSTOM, null, mobId.trim());
    }

    public static CityDefenseSpawnRef parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("spawn ref is blank");
        }
        String normalized = raw.trim();
        if (normalized.regionMatches(true, 0, "custom:", 0, "custom:".length())) {
            String mobId = normalized.substring("custom:".length()).trim();
            if (mobId.isBlank()) {
                throw new IllegalArgumentException("custom spawn ref missing mob id");
            }
            return custom(mobId);
        }
        try {
            return vanilla(CustomMobTypeResolver.parseEntityType(normalized));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid spawn ref: " + raw, exception);
        }
    }

    public boolean isVanilla() {
        return kind == Kind.VANILLA;
    }

    public boolean isCustom() {
        return kind == Kind.CUSTOM;
    }

    public String configToken() {
        return isCustom() ? "custom:" + customMobId : vanillaType.name();
    }
}
