package it.patric.cittaexp.core.port;

import java.util.UUID;

public interface VaultEconomyPort {

    boolean available();

    long balance(UUID playerUuid);

    boolean withdraw(UUID playerUuid, long amount, String reason);

    boolean deposit(UUID playerUuid, long amount, String reason);
}
