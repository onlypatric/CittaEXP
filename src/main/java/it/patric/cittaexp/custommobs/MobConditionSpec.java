package it.patric.cittaexp.custommobs;

import java.util.Set;

public sealed interface MobConditionSpec permits
        MobConditionSpec.HpBelowConditionSpec,
        MobConditionSpec.HpAboveConditionSpec,
        MobConditionSpec.TargetExistsConditionSpec,
        MobConditionSpec.HasTagConditionSpec,
        MobConditionSpec.WaveAtLeastConditionSpec,
        MobConditionSpec.TriggerSourceConditionSpec,
        MobConditionSpec.HasTraitConditionSpec,
        MobConditionSpec.HasVariantConditionSpec,
        MobConditionSpec.TempFlagPresentConditionSpec {

    record HpBelowConditionSpec(double ratio) implements MobConditionSpec {
    }

    record HpAboveConditionSpec(double ratio) implements MobConditionSpec {
    }

    record TargetExistsConditionSpec() implements MobConditionSpec {
    }

    record HasTagConditionSpec(String tag) implements MobConditionSpec {
    }

    record WaveAtLeastConditionSpec(int wave) implements MobConditionSpec {
    }

    record TriggerSourceConditionSpec(Set<MobTriggerSourceKind> allowedKinds) implements MobConditionSpec {
        public TriggerSourceConditionSpec {
            allowedKinds = Set.copyOf(allowedKinds);
        }
    }

    record HasTraitConditionSpec(String traitId) implements MobConditionSpec {
    }

    record HasVariantConditionSpec(String variantId) implements MobConditionSpec {
    }

    record TempFlagPresentConditionSpec(String flag) implements MobConditionSpec {
    }
}
