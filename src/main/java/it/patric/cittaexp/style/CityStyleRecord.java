package it.patric.cittaexp.style;

import java.util.Objects;
import java.util.UUID;

public record CityStyleRecord(
        UUID cityId,
        String bannerSpec,
        String armorSpec,
        BannerLocation bannerLocation,
        long updatedAtEpochMilli
) {

    public CityStyleRecord {
        cityId = Objects.requireNonNull(cityId, "cityId");
        bannerSpec = sanitize(bannerSpec);
        armorSpec = sanitize(armorSpec);
        bannerLocation = bannerLocation == null ? BannerLocation.empty() : bannerLocation;
        if (updatedAtEpochMilli < 0L) {
            throw new IllegalArgumentException("updatedAtEpochMilli must be >= 0");
        }
    }

    public record BannerLocation(
            String world,
            double x,
            double y,
            double z,
            float yaw,
            float pitch
    ) {

        public static BannerLocation empty() {
            return new BannerLocation("", 0D, 0D, 0D, 0F, 0F);
        }

        public BannerLocation {
            world = sanitize(world);
        }

        public boolean configured() {
            return !world.isBlank();
        }
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }
}
