package it.patric.cittaexp.custommobs;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.inventory.EquipmentSlot;

record ResolvedCustomMobSpec(
        String id,
        String displayName,
        boolean showName,
        Map<String, Double> attributes,
        Map<EquipmentSlot, CustomMobEquipmentItem> equipment,
        CustomMobFlags flags,
        Set<String> tags,
        boolean boss,
        List<String> abilityIds,
        String selectedTraitId,
        String selectedVariantId,
        List<MobPhaseRule> phaseRules,
        CustomMobBossbarSpec bossbarSpec,
        CustomMobLocalBroadcasts localBroadcasts
) {
}
