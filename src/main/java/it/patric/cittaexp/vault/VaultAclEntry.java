package it.patric.cittaexp.vault;

import java.time.Instant;
import java.util.UUID;

public record VaultAclEntry(
        UUID playerId,
        boolean canDeposit,
        boolean canWithdraw,
        Instant updatedAt
) {
}
