package it.patric.cittaexp.custommobs;

import java.util.List;

public record MobAbilitySpec(
        String id,
        MobTriggerType trigger,
        int cooldownTicks,
        MobTargeterSpec targeting,
        List<MobConditionSpec> conditions,
        List<MobActionSpec> actions
) {
    public MobAbilitySpec {
        targeting = targeting == null ? MobTargeterSpec.SELF : targeting;
        conditions = List.copyOf(conditions);
        actions = List.copyOf(actions);
    }
}
