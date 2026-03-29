package it.patric.cittaexp.custommobs;

import java.util.List;

public record MobPhaseRule(
        int phaseNumber,
        String phaseId,
        String label,
        double hpBelowRatio,
        List<String> addAbilities,
        List<String> removeAbilities,
        List<MobActionSpec> entryActions,
        String namePrefix,
        String nameSuffix
) {
    public MobPhaseRule {
        phaseNumber = Math.max(1, phaseNumber);
        phaseId = phaseId == null ? "" : phaseId.trim();
        label = label == null ? "" : label.trim();
        hpBelowRatio = Math.max(0.0D, Math.min(1.0D, hpBelowRatio));
        addAbilities = List.copyOf(addAbilities);
        removeAbilities = List.copyOf(removeAbilities);
        entryActions = List.copyOf(entryActions);
        namePrefix = namePrefix == null ? "" : namePrefix.trim();
        nameSuffix = nameSuffix == null ? "" : nameSuffix.trim();
    }
}
