package it.patric.cittaexp.runtime.integration;

import dev.patric.commonlib.api.port.ClaimsPort;
import it.patric.cittaexp.core.port.HuskClaimsPort;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;

public final class HuskClaimsAdapter implements HuskClaimsPort {

    private final ClaimsPort claimsPort;
    private final BooleanSupplier availableSupplier;
    private final IntegrationSettings.ClaimSettings claimSettings;
    private final CommandDispatcher commandDispatcher;
    private final PlayerLocationProvider playerLocationProvider;
    private final ConsoleSenderProvider consoleSenderProvider;
    private final Logger logger;

    public HuskClaimsAdapter(
            ClaimsPort claimsPort,
            BooleanSupplier availableSupplier,
            IntegrationSettings.ClaimSettings claimSettings,
            CommandDispatcher commandDispatcher,
            PlayerLocationProvider playerLocationProvider,
            ConsoleSenderProvider consoleSenderProvider,
            Logger logger
    ) {
        this.claimsPort = Objects.requireNonNull(claimsPort, "claimsPort");
        this.availableSupplier = Objects.requireNonNull(availableSupplier, "availableSupplier");
        this.claimSettings = Objects.requireNonNull(claimSettings, "claimSettings");
        this.commandDispatcher = Objects.requireNonNull(commandDispatcher, "commandDispatcher");
        this.playerLocationProvider = Objects.requireNonNull(playerLocationProvider, "playerLocationProvider");
        this.consoleSenderProvider = Objects.requireNonNull(consoleSenderProvider, "consoleSenderProvider");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public static Binding bind(
            ClaimsPort claimsPort,
            BooleanSupplier availableSupplier,
            IntegrationSettings.ClaimSettings claimSettings,
            Logger logger
    ) {
        HuskClaimsAdapter adapter = new HuskClaimsAdapter(
                claimsPort,
                availableSupplier,
                claimSettings,
                (sender, command) -> Bukkit.dispatchCommand(sender, command),
                playerUuid -> {
                    var player = Bukkit.getPlayer(playerUuid);
                    if (player == null) {
                        return null;
                    }
                    return player.getLocation();
                },
                Bukkit::getConsoleSender,
                logger
        );
        boolean available = adapter.available();
        AdapterStatus status = new AdapterStatus(
                "HuskClaims",
                available ? AdapterState.AVAILABLE : AdapterState.UNAVAILABLE,
                available ? "detected" : "n/a",
                available
                        ? "api-registered"
                        : IntegrationErrorCode.DEPENDENCY_UNAVAILABLE + ":plugin-unavailable:HuskClaims"
        );
        return new Binding(adapter, status);
    }

    @Override
    public boolean available() {
        return availableSupplier.getAsBoolean();
    }

    @Override
    public boolean hasClaimAt(UUID playerUuid) {
        if (!available() || playerUuid == null) {
            return false;
        }
        Location location = playerLocationProvider.location(playerUuid);
        if (location == null) {
            return false;
        }
        try {
            return claimsPort.isInsideClaim(playerUuid, location);
        } catch (RuntimeException ex) {
            logger.warning(
                    "[CittaEXP] HuskClaims hasClaimAt failed player="
                            + playerUuid
                            + " error="
                            + IntegrationErrorCode.EXTERNAL_INTEGRATION_ERROR
                            + ":"
                            + ex.getClass().getSimpleName()
            );
            return false;
        }
    }

    @Override
    public ClaimCreationResult createAutoClaim100x100(UUID playerUuid, String world, int centerX, int centerZ) {
        if (!available()) {
            return failureResult(
                    IntegrationErrorCode.DEPENDENCY_UNAVAILABLE + ":plugin-unavailable:HuskClaims",
                    centerX,
                    centerZ,
                    claimSettings.autoWidth(),
                    claimSettings.autoHeight()
            );
        }
        if (playerUuid == null || world == null || world.isBlank()) {
            return failureResult(
                    IntegrationErrorCode.VALIDATION_ERROR + ":invalid-input",
                    centerX,
                    centerZ,
                    claimSettings.autoWidth(),
                    claimSettings.autoHeight()
            );
        }

        int width = claimSettings.autoWidth();
        int height = claimSettings.autoHeight();
        int minX = centerX - (width / 2);
        int minZ = centerZ - (height / 2);
        int maxX = minX + width - 1;
        int maxZ = minZ + height - 1;

        if (hasClaimAt(playerUuid)) {
            return new ClaimCreationResult(
                    false,
                    minX,
                    minZ,
                    maxX,
                    maxZ,
                    IntegrationErrorCode.VALIDATION_ERROR + ":claim-already-present"
            );
        }

        String template = claimSettings.createCommandTemplate();
        if (template.isBlank()) {
            return new ClaimCreationResult(
                    false,
                    minX,
                    minZ,
                    maxX,
                    maxZ,
                    IntegrationErrorCode.VALIDATION_ERROR + ":create-command-template-missing"
            );
        }

        String command = render(
                template,
                Map.of(
                        "player_uuid", playerUuid.toString(),
                        "world", world,
                        "center_x", String.valueOf(centerX),
                        "center_z", String.valueOf(centerZ),
                        "min_x", String.valueOf(minX),
                        "min_z", String.valueOf(minZ),
                        "max_x", String.valueOf(maxX),
                        "max_z", String.valueOf(maxZ),
                        "width", String.valueOf(width),
                        "height", String.valueOf(height)
                )
        );
        boolean executed = execute(command);
        if (!executed) {
            return new ClaimCreationResult(
                    false,
                    minX,
                    minZ,
                    maxX,
                    maxZ,
                    IntegrationErrorCode.EXTERNAL_INTEGRATION_ERROR + ":create-command-failed"
            );
        }
        return new ClaimCreationResult(true, minX, minZ, maxX, maxZ, "ok");
    }

    @Override
    public boolean expandClaim(UUID cityId, int chunks, long cost) {
        if (!available()) {
            logger.warning("[CittaEXP] HuskClaims expandClaim rejected: " + IntegrationErrorCode.DEPENDENCY_UNAVAILABLE + ":plugin-unavailable:HuskClaims");
            return false;
        }
        if (cityId == null || chunks < 1 || cost < 0L) {
            logger.warning("[CittaEXP] HuskClaims expandClaim rejected: " + IntegrationErrorCode.VALIDATION_ERROR + ":invalid-input");
            return false;
        }
        String template = claimSettings.expandCommandTemplate();
        if (template.isBlank()) {
            logger.warning("[CittaEXP] HuskClaims expandClaim rejected: " + IntegrationErrorCode.VALIDATION_ERROR + ":expand-command-template-missing");
            return false;
        }
        String command = render(
                template,
                Map.of(
                        "city_id", cityId.toString(),
                        "chunks", String.valueOf(chunks),
                        "cost", String.valueOf(cost)
                )
        );
        boolean ok = execute(command);
        if (!ok) {
            logger.warning("[CittaEXP] HuskClaims expandClaim failed: " + IntegrationErrorCode.EXTERNAL_INTEGRATION_ERROR + ":expand-command-failed");
        }
        return ok;
    }

    private ClaimCreationResult failureResult(String reason, int centerX, int centerZ, int width, int height) {
        int minX = centerX - (width / 2);
        int minZ = centerZ - (height / 2);
        int maxX = minX + width - 1;
        int maxZ = minZ + height - 1;
        return new ClaimCreationResult(false, minX, minZ, maxX, maxZ, reason);
    }

    private boolean execute(String command) {
        String raw = command == null ? "" : command.trim();
        if (raw.isEmpty()) {
            return false;
        }
        String normalized = raw.startsWith("/") ? raw.substring(1) : raw;
        try {
            CommandSender sender = consoleSenderProvider.sender();
            if (sender == null) {
                logger.warning("[CittaEXP] HuskClaims command dispatch failed command=" + normalized + " error=missing-console-sender");
                return false;
            }
            return commandDispatcher.dispatch(sender, normalized);
        } catch (RuntimeException ex) {
            logger.warning(
                    "[CittaEXP] HuskClaims command dispatch failed command="
                            + normalized
                            + " error="
                            + ex.getClass().getSimpleName()
            );
            return false;
        }
    }

    private static String render(String template, Map<String, String> placeholders) {
        String rendered = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            rendered = rendered.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return rendered;
    }

    public record Binding(HuskClaimsPort port, AdapterStatus status) {
    }

    @FunctionalInterface
    public interface CommandDispatcher {
        boolean dispatch(CommandSender sender, String command);
    }

    @FunctionalInterface
    public interface PlayerLocationProvider {
        Location location(UUID playerUuid);
    }

    @FunctionalInterface
    public interface ConsoleSenderProvider {
        CommandSender sender();
    }
}
