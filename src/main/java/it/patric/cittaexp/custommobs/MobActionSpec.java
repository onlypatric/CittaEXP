package it.patric.cittaexp.custommobs;

import java.util.List;
import org.bukkit.Particle;

public sealed interface MobActionSpec permits
        MobActionSpec.ParticlesActionSpec,
        MobActionSpec.SoundActionSpec,
        MobActionSpec.ApplyPotionActionSpec,
        MobActionSpec.DirectDamageActionSpec,
        MobActionSpec.SummonActionSpec,
        MobActionSpec.LaunchProjectileActionSpec,
        MobActionSpec.MotionActionSpec,
        MobActionSpec.SetTempFlagActionSpec {

    MobActionType type();

    record ParticlesActionSpec(
            Particle particle,
            int count,
            double offsetX,
            double offsetY,
            double offsetZ,
            double extra
    ) implements MobActionSpec {
        @Override
        public MobActionType type() {
            return MobActionType.PARTICLES;
        }
    }

    record SoundActionSpec(
            String soundKey,
            float volume,
            float pitch
    ) implements MobActionSpec {
        @Override
        public MobActionType type() {
            return MobActionType.SOUND;
        }
    }

    record ApplyPotionActionSpec(
            String effectKey,
            int durationTicks,
            int amplifier,
            boolean ambient,
            boolean particles,
            boolean icon,
            int fireTicks
    ) implements MobActionSpec {
        @Override
        public MobActionType type() {
            return MobActionType.APPLY_POTION;
        }
    }

    record DirectDamageActionSpec(double amount) implements MobActionSpec {
        @Override
        public MobActionType type() {
            return MobActionType.DIRECT_DAMAGE;
        }
    }

    record SummonActionSpec(
            String spawnRef,
            int count,
            double spreadRadius
    ) implements MobActionSpec {
        @Override
        public MobActionType type() {
            return MobActionType.SUMMON;
        }
    }

    record LaunchProjectileActionSpec(
            double speed,
            double range,
            double hitboxRadius,
            Particle particle,
            int particleCount,
            int count,
            double spread,
            List<MobActionSpec> hitActions
    ) implements MobActionSpec {
        public LaunchProjectileActionSpec {
            count = Math.max(1, count);
            spread = Math.max(0.0D, spread);
            hitActions = List.copyOf(hitActions);
        }

        @Override
        public MobActionType type() {
            return MobActionType.LAUNCH_PROJECTILE;
        }
    }

    record MotionActionSpec(
            MobActionType motionType,
            double strength
    ) implements MobActionSpec {
        @Override
        public MobActionType type() {
            return motionType;
        }
    }

    record SetTempFlagActionSpec(
            String flag,
            int durationTicks
    ) implements MobActionSpec {
        @Override
        public MobActionType type() {
            return MobActionType.SET_TEMP_FLAG;
        }
    }
}
