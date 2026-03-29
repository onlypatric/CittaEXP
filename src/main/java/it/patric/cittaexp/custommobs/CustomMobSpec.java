package it.patric.cittaexp.custommobs;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;

public record CustomMobSpec(
        String id,
        EntityType entityType,
        String displayName,
        boolean showName,
        Map<String, Double> attributes,
        Map<EquipmentSlot, CustomMobEquipmentItem> equipment,
        CustomMobFlags flags,
        Set<String> tags,
        boolean boss,
        List<String> mainAbilityIds,
        List<String> secondaryAbilityIds,
        List<String> passiveAbilityIds,
        List<String> traitIds,
        List<String> variantIds,
        List<MobPhaseRule> phaseRules,
        CustomMobBossbarSpec bossbarSpec,
        CustomMobLocalBroadcasts localBroadcasts,
        CustomMobCatalogMetadata metadata
) {
    public CustomMobSpec {
        attributes = Map.copyOf(attributes);
        equipment = Map.copyOf(equipment);
        tags = Set.copyOf(tags);
        mainAbilityIds = List.copyOf(mainAbilityIds);
        secondaryAbilityIds = List.copyOf(secondaryAbilityIds);
        passiveAbilityIds = List.copyOf(passiveAbilityIds);
        traitIds = List.copyOf(traitIds);
        variantIds = List.copyOf(variantIds);
        phaseRules = List.copyOf(phaseRules);
        bossbarSpec = bossbarSpec == null ? CustomMobBossbarSpec.DISABLED : bossbarSpec;
        localBroadcasts = localBroadcasts == null ? CustomMobLocalBroadcasts.EMPTY : localBroadcasts;
        metadata = metadata == null ? CustomMobCatalogMetadata.defaults() : metadata;
    }
}
