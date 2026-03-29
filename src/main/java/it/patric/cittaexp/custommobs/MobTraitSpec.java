package it.patric.cittaexp.custommobs;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.inventory.EquipmentSlot;

public record MobTraitSpec(
        String id,
        int weight,
        String namePrefix,
        String nameSuffix,
        Map<String, Double> attributes,
        Map<EquipmentSlot, CustomMobEquipmentItem> equipment,
        Set<String> tags,
        List<String> addAbilities,
        List<String> removeAbilities,
        CustomMobCatalogMetadata metadata
) {
    public MobTraitSpec {
        weight = Math.max(1, weight);
        namePrefix = namePrefix == null ? "" : namePrefix;
        nameSuffix = nameSuffix == null ? "" : nameSuffix;
        attributes = Map.copyOf(attributes);
        equipment = Map.copyOf(equipment);
        tags = Set.copyOf(tags);
        addAbilities = List.copyOf(addAbilities);
        removeAbilities = List.copyOf(removeAbilities);
        metadata = metadata == null ? CustomMobCatalogMetadata.defaults() : metadata;
    }
}
