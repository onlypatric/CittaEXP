package it.patric.cittaexp.custommobs;

public record MobTargeterSpec(
        MobTargeterType type,
        double radius,
        int maxTargets
) {

    public static final MobTargeterSpec SELF = new MobTargeterSpec(MobTargeterType.SELF, 0.0D, 1);

    public MobTargeterSpec {
        radius = Math.max(0.0D, radius);
        maxTargets = Math.max(0, maxTargets);
    }
}
