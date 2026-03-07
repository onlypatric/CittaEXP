package it.patric.cittaexp.persistence.port;

import it.patric.cittaexp.persistence.domain.CityMemberRecord;
import it.patric.cittaexp.persistence.domain.CityRecord;
import it.patric.cittaexp.persistence.domain.CityRoleRecord;
import it.patric.cittaexp.persistence.domain.PersistenceWriteOutcome;
import java.util.UUID;

public interface CityWritePort {

    PersistenceWriteOutcome createCity(CityRecord city);

    PersistenceWriteOutcome updateCity(CityRecord city, int expectedRevision);

    PersistenceWriteOutcome upsertMember(CityMemberRecord member);

    PersistenceWriteOutcome deleteMember(UUID cityId, UUID playerUuid);

    PersistenceWriteOutcome upsertRole(CityRoleRecord role);

    PersistenceWriteOutcome deleteRole(UUID cityId, String roleKey);
}
