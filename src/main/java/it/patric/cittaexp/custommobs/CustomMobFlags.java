package it.patric.cittaexp.custommobs;

public record CustomMobFlags(
        boolean silent,
        boolean collidable,
        boolean invulnerable
) {

    public static final CustomMobFlags DEFAULT = new CustomMobFlags(false, true, false);
}
