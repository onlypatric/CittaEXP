package it.patric.cittaexp.runtime.integration;

import dev.patric.commonlib.api.port.ClaimsPort;
import it.patric.cittaexp.core.port.HuskClaimsPort;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HuskClaimsAdapterTest {

    @Test
    void hasClaimAtUsesClaimsPortWithPlayerLocation() {
        ClaimsPort claimsPort = mock(ClaimsPort.class);
        UUID playerUuid = UUID.randomUUID();
        World world = mock(World.class);
        Location location = new Location(world, 12, 64, 34);
        when(claimsPort.isInsideClaim(eq(playerUuid), eq(location))).thenReturn(true);

        HuskClaimsAdapter adapter = new HuskClaimsAdapter(
                claimsPort,
                () -> true,
                new IntegrationSettings.ClaimSettings(100, 100, "cmd", "cmd2"),
                (sender, command) -> true,
                ignored -> location,
                () -> mock(CommandSender.class),
                Logger.getLogger("test")
        );

        assertTrue(adapter.hasClaimAt(playerUuid));
    }

    @Test
    void createAutoClaimReturnsValidationErrorWhenTemplateMissing() {
        ClaimsPort claimsPort = mock(ClaimsPort.class);
        UUID playerUuid = UUID.randomUUID();

        HuskClaimsAdapter adapter = new HuskClaimsAdapter(
                claimsPort,
                () -> true,
                new IntegrationSettings.ClaimSettings(100, 100, "", "expand"),
                (sender, command) -> true,
                ignored -> null,
                () -> mock(CommandSender.class),
                Logger.getLogger("test")
        );

        HuskClaimsPort.ClaimCreationResult result = adapter.createAutoClaim100x100(playerUuid, "world", 0, 0);

        assertFalse(result.success());
        assertTrue(result.reason().contains(IntegrationErrorCode.VALIDATION_ERROR.name()));
    }

    @Test
    void createAutoClaimDispatchesRenderedCommand() {
        ClaimsPort claimsPort = mock(ClaimsPort.class);
        when(claimsPort.isInsideClaim(any(), any())).thenReturn(false);
        UUID playerUuid = UUID.randomUUID();
        World world = mock(World.class);
        Location location = new Location(world, 0, 64, 0);
        AtomicReference<String> commandRef = new AtomicReference<>();

        HuskClaimsAdapter adapter = new HuskClaimsAdapter(
                claimsPort,
                () -> true,
                new IntegrationSettings.ClaimSettings(
                        100,
                        100,
                        "husk create <player_uuid> <world> <min_x> <min_z> <max_x> <max_z>",
                        "husk expand <city_id> <chunks>"
                ),
                (CommandSender sender, String command) -> {
                    commandRef.set(command);
                    return true;
                },
                ignored -> location,
                () -> mock(CommandSender.class),
                Logger.getLogger("test")
        );

        HuskClaimsPort.ClaimCreationResult result = adapter.createAutoClaim100x100(playerUuid, "world", 200, 300);

        assertTrue(result.success());
        assertTrue(commandRef.get().contains(playerUuid.toString()));
        assertTrue(commandRef.get().contains("world"));
        assertTrue(commandRef.get().contains("150"));
        assertTrue(commandRef.get().contains("349"));
    }

    @Test
    void expandClaimFailsForInvalidInput() {
        HuskClaimsAdapter adapter = new HuskClaimsAdapter(
                mock(ClaimsPort.class),
                () -> true,
                new IntegrationSettings.ClaimSettings(100, 100, "create", "expand <city_id> <chunks>"),
                (sender, command) -> true,
                ignored -> null,
                () -> mock(CommandSender.class),
                Logger.getLogger("test")
        );

        assertFalse(adapter.expandClaim(UUID.randomUUID(), 0, 10L));
        assertFalse(adapter.expandClaim(UUID.randomUUID(), 1, -1L));
    }
}
