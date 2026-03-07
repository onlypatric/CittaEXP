package it.patric.cittaexp.core.service;

import it.patric.cittaexp.core.model.City;
import it.patric.cittaexp.core.model.CityTier;
import java.util.UUID;

public interface CityLifecycleService {

    City createCity(CreateCityCommand command);

    City transferLeadership(UUID cityId, UUID currentLeader, UUID nextLeader, String reason);

    City requestTierUpgrade(UUID cityId, UUID requester, CityTier targetTier, String reason);

    City requestDeletion(UUID cityId, UUID requester, String reason);

    record CreateCityCommand(
            UUID creatorUuid,
            String cityName,
            String cityTag,
            String world,
            int centerX,
            int centerZ,
            String bannerSpec,
            String armorSpec
    ) {
    }
}
