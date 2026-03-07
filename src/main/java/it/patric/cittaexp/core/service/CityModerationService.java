package it.patric.cittaexp.core.service;

import it.patric.cittaexp.core.model.City;
import java.util.UUID;

public interface CityModerationService {

    City freezeCity(String cityReference, UUID actorUuid, String reason);

    City unfreezeCity(String cityReference, UUID actorUuid, String reason);

    boolean isCityFrozen(UUID cityId);
}
