package it.patric.cittaexp.runtime.integration;

import it.patric.cittaexp.core.model.MemberClaimPermissions;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import net.william278.huskclaims.api.BukkitHuskClaimsAPI;
import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.claim.ClaimWorld;
import net.william278.huskclaims.claim.Region;
import net.william278.huskclaims.position.BlockPosition;
import net.william278.huskclaims.trust.TrustLevel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HuskClaimsAdapterTest {

    @Test
    void bindReturnsUnavailableWhenPluginMissing() {
        HuskClaimsAdapter.Binding binding = HuskClaimsAdapter.bind(
                null,
                new IntegrationSettings.ClaimSettings(100, 100),
                Logger.getLogger("test")
        );

        assertFalse(binding.port().available());
    }

    @Test
    void createAutoClaimFailsWhenWorldNotClaimable() {
        Plugin plugin = enabledPlugin();
        BukkitHuskClaimsAPI api = mock(BukkitHuskClaimsAPI.class);
        World world = mock(World.class);
        net.william278.huskclaims.position.World huskWorld = mock(net.william278.huskclaims.position.World.class);

        HuskClaimsAdapter adapter = new HuskClaimsAdapter(
                plugin,
                api,
                new IntegrationSettings.ClaimSettings(100, 100),
                Logger.getLogger("test")
        );

        when(api.getWorld(world)).thenReturn(huskWorld);
        when(api.isWorldClaimable(huskWorld)).thenReturn(false);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            var result = adapter.createAutoClaim100x100Async(UUID.randomUUID(), "world", 0, 0).join();
            assertFalse(result.success());
            assertTrue(result.reason().contains("world-not-claimable"));
        }
    }

    @Test
    void createAutoClaimUsesApiAndSucceeds() {
        Plugin plugin = enabledPlugin();
        BukkitHuskClaimsAPI api = mock(BukkitHuskClaimsAPI.class);
        World world = mock(World.class);
        net.william278.huskclaims.position.World huskWorld = mock(net.william278.huskclaims.position.World.class);
        ClaimWorld claimWorld = mock(ClaimWorld.class);
        Claim createdClaim = mock(Claim.class);
        TrustLevel leaderTrust = mock(TrustLevel.class);

        HuskClaimsAdapter adapter = new HuskClaimsAdapter(
                plugin,
                api,
                new IntegrationSettings.ClaimSettings(100, 100),
                Logger.getLogger("test")
        );

        when(api.getWorld(world)).thenReturn(huskWorld);
        when(api.isWorldClaimable(huskWorld)).thenReturn(true);
        when(api.getClaimWorld(huskWorld)).thenReturn(Optional.of(claimWorld));
        when(api.getBlockPosition(anyInt(), anyInt())).thenAnswer(invocation ->
                blockPosition(invocation.getArgument(0, Integer.class), invocation.getArgument(1, Integer.class))
        );
        when(api.isRegionClaimed(eq(claimWorld), any(Region.class))).thenReturn(false);
        when(api.getTrustLevels()).thenReturn(java.util.List.of(leaderTrust));
        when(leaderTrust.getPrivileges()).thenReturn(java.util.List.of(TrustLevel.Privilege.MANAGE_TRUSTEES));
        when(api.createAdminClaim(eq(claimWorld), any(Region.class)))
                .thenReturn(CompletableFuture.completedFuture(createdClaim));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            var result = adapter.createAutoClaim100x100Async(UUID.randomUUID(), "world", 200, 300).join();
            assertTrue(result.success());
            assertTrue(result.minX() < result.maxX());
            assertTrue(result.minZ() < result.maxZ());
        }
        verify(api).setTrustLevel(eq(createdClaim), eq(claimWorld), any(), eq(leaderTrust));
    }

    @Test
    void expandClaimReturnsFalseWhenNoClaimAtAnchor() {
        Plugin plugin = enabledPlugin();
        BukkitHuskClaimsAPI api = mock(BukkitHuskClaimsAPI.class);
        World world = mock(World.class);
        net.william278.huskclaims.position.World huskWorld = mock(net.william278.huskclaims.position.World.class);
        ClaimWorld claimWorld = mock(ClaimWorld.class);
        net.william278.huskclaims.position.Position position = mock(net.william278.huskclaims.position.Position.class);

        HuskClaimsAdapter adapter = new HuskClaimsAdapter(
                plugin,
                api,
                new IntegrationSettings.ClaimSettings(100, 100),
                Logger.getLogger("test")
        );

        when(world.getMinHeight()).thenReturn(0);
        when(api.getWorld(world)).thenReturn(huskWorld);
        when(api.isWorldClaimable(huskWorld)).thenReturn(true);
        when(api.getClaimWorld(huskWorld)).thenReturn(Optional.of(claimWorld));
        when(api.getPosition(any(Location.class))).thenReturn(position);
        when(api.getClaimAt(position)).thenReturn(Optional.empty());

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            assertFalse(adapter.expandClaimAsync("world", 10, 10, 1).join());
        }
    }

    @Test
    void syncClaimPermissionsReturnsFalseWhenClaimMissing() {
        Plugin plugin = enabledPlugin();
        BukkitHuskClaimsAPI api = mock(BukkitHuskClaimsAPI.class);
        World world = mock(World.class);
        net.william278.huskclaims.position.World huskWorld = mock(net.william278.huskclaims.position.World.class);
        ClaimWorld claimWorld = mock(ClaimWorld.class);
        net.william278.huskclaims.position.Position position = mock(net.william278.huskclaims.position.Position.class);

        HuskClaimsAdapter adapter = new HuskClaimsAdapter(
                plugin,
                api,
                new IntegrationSettings.ClaimSettings(100, 100),
                Logger.getLogger("test")
        );

        when(world.getMinHeight()).thenReturn(0);
        when(api.getWorld(world)).thenReturn(huskWorld);
        when(api.isWorldClaimable(huskWorld)).thenReturn(true);
        when(api.getClaimWorld(huskWorld)).thenReturn(Optional.of(claimWorld));
        when(api.getPosition(any(Location.class))).thenReturn(position);
        when(api.getClaimAt(position)).thenReturn(Optional.empty());

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            assertFalse(adapter.syncClaimPermissionsAsync(
                    "world",
                    0,
                    0,
                    UUID.randomUUID(),
                    MemberClaimPermissions.memberDefault(),
                    false
            ).join());
        }
    }

    private static BlockPosition blockPosition(int x, int z) {
        return new BlockPosition() {
            @Override
            public int getBlockX() {
                return x;
            }

            @Override
            public int getBlockZ() {
                return z;
            }

            @Override
            public long getLongChunkCoords() {
                return (((long) (x >> 4)) << 32) | ((z >> 4) & 0xffffffffL);
            }
        };
    }

    private static Plugin enabledPlugin() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        return plugin;
    }
}
