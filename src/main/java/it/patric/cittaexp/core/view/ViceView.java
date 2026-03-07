package it.patric.cittaexp.core.view;

import java.util.UUID;

public record ViceView(
        UUID cityId,
        UUID viceUuid,
        long updatedAtEpochMilli
) {
}
