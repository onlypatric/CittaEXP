package it.patric.cittaexp.custommobs;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public record CustomMobCatalogMetadata(
        String packId,
        String arcId,
        Set<String> poolIds,
        Set<String> themeIds
) {
    public static final String DEFAULT_PACK_ID = "defense_codex_v1";
    public static final String DEFAULT_ARC_ID = "general";

    public CustomMobCatalogMetadata {
        packId = normalizeOrDefault(packId, DEFAULT_PACK_ID);
        arcId = normalizeOrDefault(arcId, DEFAULT_ARC_ID);
        poolIds = normalizeSet(poolIds);
        themeIds = normalizeSet(themeIds);
    }

    public static CustomMobCatalogMetadata defaults() {
        return new CustomMobCatalogMetadata(DEFAULT_PACK_ID, DEFAULT_ARC_ID, Set.of(), Set.of());
    }

    public boolean matchesPool(String poolId) {
        return poolIds.contains(normalize(poolId));
    }

    public boolean matchesArc(String arcId) {
        return this.arcId.equals(normalizeOrDefault(arcId, DEFAULT_ARC_ID));
    }

    public boolean matchesTheme(String themeId) {
        return themeIds.contains(normalize(themeId));
    }

    private static Set<String> normalizeSet(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream()
                .map(CustomMobCatalogMetadata::normalize)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalizeOrDefault(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_');
    }
}
