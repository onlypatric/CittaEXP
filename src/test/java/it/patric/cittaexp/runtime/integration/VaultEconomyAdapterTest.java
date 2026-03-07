package it.patric.cittaexp.runtime.integration;

import java.util.UUID;
import java.util.logging.Logger;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VaultEconomyAdapterTest {

    @Test
    void bindReturnsUnavailableWhenProviderMissing() {
        VaultEconomyAdapter.Binding binding = VaultEconomyAdapter.bind(() -> null, Logger.getLogger("test"));

        assertFalse(binding.port().available());
        assertEquals(AdapterState.UNAVAILABLE, binding.status().state());
    }

    @Test
    void withdrawAndDepositUseEconomyProvider() {
        Economy economy = mock(Economy.class);
        when(economy.withdrawPlayer(any(OfflinePlayer.class), anyDouble()))
                .thenReturn(new EconomyResponse(10D, 90D, EconomyResponse.ResponseType.SUCCESS, ""));
        when(economy.depositPlayer(any(OfflinePlayer.class), anyDouble()))
                .thenReturn(new EconomyResponse(10D, 110D, EconomyResponse.ResponseType.SUCCESS, ""));
        when(economy.getBalance(any(OfflinePlayer.class))).thenReturn(123.8D);

        @SuppressWarnings("unchecked")
        RegisteredServiceProvider<Economy> registration = mock(RegisteredServiceProvider.class);
        when(registration.getProvider()).thenReturn(economy);
        when(registration.getPlugin()).thenReturn(null);

        OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
        UUID player = UUID.randomUUID();

        VaultEconomyAdapter adapter = new VaultEconomyAdapter(
                () -> registration,
                ignored -> offlinePlayer,
                Logger.getLogger("test")
        );

        assertTrue(adapter.available());
        assertEquals(123L, adapter.balance(player));
        assertTrue(adapter.withdraw(player, 10L, "tax"));
        assertTrue(adapter.deposit(player, 10L, "bonus"));
    }

    @Test
    void withdrawRejectsNegativeAmount() {
        Economy economy = mock(Economy.class);
        @SuppressWarnings("unchecked")
        RegisteredServiceProvider<Economy> registration = mock(RegisteredServiceProvider.class);
        when(registration.getProvider()).thenReturn(economy);
        when(registration.getPlugin()).thenReturn(null);

        VaultEconomyAdapter adapter = new VaultEconomyAdapter(
                () -> registration,
                ignored -> mock(OfflinePlayer.class),
                Logger.getLogger("test")
        );

        assertFalse(adapter.withdraw(UUID.randomUUID(), -1L, "invalid"));
    }
}
