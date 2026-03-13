package it.patric.cittaexp.playersgui;

import java.util.UUID;

public record CityPlayersMemberView(
        UUID playerId,
        String displayName,
        int roleWeight
) {
}
