package it.patric.cittaexp.persistence.port;

import it.patric.cittaexp.persistence.domain.CityMemberRecord;
import it.patric.cittaexp.persistence.domain.CityRecord;
import it.patric.cittaexp.persistence.domain.CityRoleRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CityReadPort {

    Optional<CityRecord> findCityById(UUID cityId);

    Optional<CityRecord> findCityByName(String name);

    Optional<CityRecord> findCityByTag(String tag);

    List<CityRecord> listCities();

    List<CityMemberRecord> listMembers(UUID cityId);

    List<CityRoleRecord> listRoles(UUID cityId);
}
