package it.patric.cittaexp.style;

import java.util.Optional;
import java.util.UUID;

public interface CityStyleService {

    Optional<CityStyleRecord> find(UUID cityId);

    CityStyleRecord saveOnCreate(UUID cityId, String bannerSpec, String armorSpec);

    CityStyleRecord updateBanner(UUID cityId, String bannerSpec);

    CityStyleRecord updateArmor(UUID cityId, String armorSpec);

    CityStyleRecord setBannerLocation(UUID cityId, String world, double x, double y, double z, float yaw, float pitch);

    CityStyleRecord clearBannerLocation(UUID cityId);
}
