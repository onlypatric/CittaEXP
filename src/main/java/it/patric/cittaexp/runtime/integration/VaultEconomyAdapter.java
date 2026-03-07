package it.patric.cittaexp.runtime.integration;

import it.patric.cittaexp.core.port.VaultEconomyPort;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class VaultEconomyAdapter implements VaultEconomyPort {

    private final Supplier<RegisteredServiceProvider<Economy>> providerLookup;
    private final PlayerResolver playerResolver;
    private final Logger logger;

    public VaultEconomyAdapter(Supplier<RegisteredServiceProvider<Economy>> providerLookup, Logger logger) {
        this(providerLookup, Bukkit::getOfflinePlayer, logger);
    }

    VaultEconomyAdapter(
            Supplier<RegisteredServiceProvider<Economy>> providerLookup,
            PlayerResolver playerResolver,
            Logger logger
    ) {
        this.providerLookup = Objects.requireNonNull(providerLookup, "providerLookup");
        this.playerResolver = Objects.requireNonNull(playerResolver, "playerResolver");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public static Binding bind(Supplier<RegisteredServiceProvider<Economy>> providerLookup, Logger logger) {
        VaultEconomyAdapter adapter = new VaultEconomyAdapter(providerLookup, logger);
        RegisteredServiceProvider<Economy> provider = adapter.provider();
        if (provider == null || provider.getProvider() == null) {
            return new Binding(
                    adapter,
                    new AdapterStatus(
                            "Vault",
                            AdapterState.UNAVAILABLE,
                            "n/a",
                            IntegrationErrorCode.DEPENDENCY_UNAVAILABLE + ":api-missing:Economy"
                    )
            );
        }
        return new Binding(
                adapter,
                new AdapterStatus(
                        "Vault",
                        AdapterState.AVAILABLE,
                        safeVersion(provider),
                        "api-registered"
                )
        );
    }

    @Override
    public boolean available() {
        RegisteredServiceProvider<Economy> provider = provider();
        return provider != null && provider.getProvider() != null;
    }

    @Override
    public long balance(UUID playerUuid) {
        if (playerUuid == null) {
            return 0L;
        }
        Economy economy = economy();
        if (economy == null) {
            return 0L;
        }
        try {
            OfflinePlayer player = playerResolver.offlinePlayer(playerUuid);
            return normalizeCurrency(economy.getBalance(player));
        } catch (RuntimeException ex) {
            logger.warning(
                    "[CittaEXP] Vault balance failed player="
                            + playerUuid
                            + " error="
                            + IntegrationErrorCode.EXTERNAL_INTEGRATION_ERROR
                            + ":"
                            + ex.getClass().getSimpleName()
            );
            return 0L;
        }
    }

    @Override
    public boolean withdraw(UUID playerUuid, long amount, String reason) {
        return applyTransaction("withdraw", playerUuid, amount, reason);
    }

    @Override
    public boolean deposit(UUID playerUuid, long amount, String reason) {
        return applyTransaction("deposit", playerUuid, amount, reason);
    }

    private boolean applyTransaction(String type, UUID playerUuid, long amount, String reason) {
        if (playerUuid == null) {
            return false;
        }
        if (amount < 0L) {
            logger.warning("[CittaEXP] Vault " + type + " rejected: " + IntegrationErrorCode.VALIDATION_ERROR + ":negative-amount");
            return false;
        }
        if (amount == 0L) {
            return true;
        }

        Economy economy = economy();
        if (economy == null) {
            logger.warning("[CittaEXP] Vault " + type + " rejected: " + IntegrationErrorCode.DEPENDENCY_UNAVAILABLE + ":api-missing:Economy");
            return false;
        }

        OfflinePlayer player = playerResolver.offlinePlayer(playerUuid);
        EconomyResponse response;
        try {
            if ("withdraw".equals(type)) {
                response = economy.withdrawPlayer(player, amount);
            } else {
                response = economy.depositPlayer(player, amount);
            }
        } catch (RuntimeException ex) {
            logger.warning(
                    "[CittaEXP] Vault " + type + " failed player="
                            + playerUuid
                            + " amount="
                            + amount
                            + " reason="
                            + safeReason(reason)
                            + " error="
                            + IntegrationErrorCode.EXTERNAL_INTEGRATION_ERROR
                            + ":"
                            + ex.getClass().getSimpleName()
            );
            return false;
        }

        if (!response.transactionSuccess()) {
            logger.warning(
                    "[CittaEXP] Vault " + type + " denied player="
                            + playerUuid
                            + " amount="
                            + amount
                            + " reason="
                            + safeReason(reason)
                            + " error="
                            + IntegrationErrorCode.EXTERNAL_INTEGRATION_ERROR
                            + ":"
                            + (response.errorMessage == null ? "unknown" : response.errorMessage)
            );
            return false;
        }
        return true;
    }

    private Economy economy() {
        RegisteredServiceProvider<Economy> provider = provider();
        if (provider == null) {
            return null;
        }
        return provider.getProvider();
    }

    private RegisteredServiceProvider<Economy> provider() {
        try {
            return providerLookup.get();
        } catch (RuntimeException ex) {
            logger.warning("[CittaEXP] Vault provider lookup failed: " + ex.getClass().getSimpleName());
            return null;
        }
    }

    private static long normalizeCurrency(double value) {
        if (Double.isNaN(value) || value <= 0D) {
            return 0L;
        }
        if (value >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return (long) Math.floor(value);
    }

    private static String safeVersion(RegisteredServiceProvider<Economy> provider) {
        try {
            if (provider.getPlugin() == null || provider.getPlugin().getDescription() == null) {
                return "unknown";
            }
            String version = provider.getPlugin().getDescription().getVersion();
            return version == null || version.isBlank() ? "unknown" : version;
        } catch (RuntimeException ex) {
            return "unknown";
        }
    }

    private static String safeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "-";
        }
        return reason;
    }

    public record Binding(VaultEconomyPort port, AdapterStatus status) {
    }

    @FunctionalInterface
    interface PlayerResolver {
        OfflinePlayer offlinePlayer(UUID playerUuid);
    }
}
