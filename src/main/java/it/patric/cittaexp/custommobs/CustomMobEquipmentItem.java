package it.patric.cittaexp.custommobs;

import java.util.Map;
import org.bukkit.Material;

public record CustomMobEquipmentItem(
        Material material,
        Map<String, Integer> enchants
) {
    public CustomMobEquipmentItem {
        if (material == null) {
            throw new IllegalArgumentException("material is required");
        }
        enchants = Map.copyOf(enchants);
    }
}
