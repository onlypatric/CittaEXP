package it.patric.cittaexp.runtime.integration;

import it.patric.cittaexp.core.port.HuskClaimsPort;
import it.patric.cittaexp.core.model.MemberClaimPermissions;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.Comparator;
import java.util.Locale;
import net.william278.huskclaims.trust.TrustLevel;
import net.william278.huskclaims.user.User;
import java.util.logging.Logger;
import net.william278.huskclaims.api.BukkitHuskClaimsAPI;
import net.william278.huskclaims.api.HuskClaimsAPI;
import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.claim.ClaimWorld;
import net.william278.huskclaims.claim.Region;
import net.william278.huskclaims.position.BlockPosition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class HuskClaimsAdapter implements HuskClaimsPort {

    private final Plugin huskClaimsPlugin;
    private final BukkitHuskClaimsAPI api;
    private final IntegrationSettings.ClaimSettings claimSettings;
    private final Logger logger;

    HuskClaimsAdapter(
            Plugin huskClaimsPlugin,
            BukkitHuskClaimsAPI api,
            IntegrationSettings.ClaimSettings claimSettings,
            Logger logger
    ) {
        this.huskClaimsPlugin = Objects.requireNonNull(huskClaimsPlugin, "huskClaimsPlugin");
        this.api = Objects.requireNonNull(api, "api");
        this.claimSettings = Objects.requireNonNull(claimSettings, "claimSettings");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public static Binding bind(
            Plugin huskClaimsPlugin,
            IntegrationSettings.ClaimSettings claimSettings,
            Logger logger
    ) {
        if (huskClaimsPlugin == null || !huskClaimsPlugin.isEnabled()) {
            return new Binding(
                    new UnavailableHuskClaimsPort(),
                    new AdapterStatus(
                            "HuskClaims",
                            AdapterState.UNAVAILABLE,
                            safeVersion(huskClaimsPlugin),
                            IntegrationErrorCode.DEPENDENCY_UNAVAILABLE + ":plugin-unavailable:HuskClaims"
                    )
            );
        }

        try {
            BukkitHuskClaimsAPI api = BukkitHuskClaimsAPI.getInstance();
            HuskClaimsAdapter adapter = new HuskClaimsAdapter(huskClaimsPlugin, api, claimSettings, logger);
            return new Binding(
                    adapter,
                    new AdapterStatus(
                            "HuskClaims",
                            AdapterState.AVAILABLE,
                            safeVersion(huskClaimsPlugin),
                            "api-registered"
                    )
            );
        } catch (HuskClaimsAPI.NotRegisteredException ex) {
            logger.warning("[CittaEXP] HuskClaims API non registrata: " + ex.getClass().getSimpleName());
            return new Binding(
                    new UnavailableHuskClaimsPort(),
                    new AdapterStatus(
                            "HuskClaims",
                            AdapterState.UNAVAILABLE,
                            safeVersion(huskClaimsPlugin),
                            IntegrationErrorCode.DEPENDENCY_UNAVAILABLE + ":api-not-registered:HuskClaimsAPI"
                    )
            );
        } catch (RuntimeException ex) {
            logger.warning("[CittaEXP] HuskClaims binding failed: " + ex.getClass().getSimpleName());
            return new Binding(
                    new UnavailableHuskClaimsPort(),
                    new AdapterStatus(
                            "HuskClaims",
                            AdapterState.UNAVAILABLE,
                            safeVersion(huskClaimsPlugin),
                            IntegrationErrorCode.EXTERNAL_INTEGRATION_ERROR + ":binding-failed:" + ex.getClass().getSimpleName()
                    )
            );
        }
    }

    @Override
    public boolean available() {
        return huskClaimsPlugin.isEnabled();
    }

    @Override
    public boolean hasClaimAt(UUID playerUuid) {
        if (!available() || playerUuid == null) {
            return false;
        }
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) {
            return false;
        }
        try {
            return api.getClaimAt(api.getPosition(player.getLocation())).isPresent();
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
    public CompletableFuture<ClaimCreationResult> createAutoClaim100x100Async(
            UUID playerUuid,
            String world,
            int centerX,
            int centerZ
    ) {
        if (!available()) {
            return CompletableFuture.completedFuture(failureResult(
                    IntegrationErrorCode.DEPENDENCY_UNAVAILABLE + ":plugin-unavailable:HuskClaims",
                    centerX,
                    centerZ
            ));
        }
        if (playerUuid == null || world == null || world.isBlank()) {
            return CompletableFuture.completedFuture(failureResult(
                    IntegrationErrorCode.VALIDATION_ERROR + ":invalid-input",
                    centerX,
                    centerZ
            ));
        }

        Optional<ClaimWorld> maybeClaimWorld = claimWorld(world);
        if (maybeClaimWorld.isEmpty()) {
            return CompletableFuture.completedFuture(failureResult(
                    IntegrationErrorCode.VALIDATION_ERROR + ":world-not-claimable",
                    centerX,
                    centerZ
            ));
        }
        ClaimWorld claimWorld = maybeClaimWorld.get();

        Region region = targetRegion(centerX, centerZ);
        try {
            if (api.isRegionClaimed(claimWorld, region)) {
                return CompletableFuture.completedFuture(failureResult(
                        IntegrationErrorCode.VALIDATION_ERROR + ":region-already-claimed",
                        centerX,
                        centerZ
                ));
            }
        } catch (RuntimeException ex) {
            return CompletableFuture.completedFuture(failureResult(
                    IntegrationErrorCode.EXTERNAL_INTEGRATION_ERROR + ":claim-check-failed:" + ex.getClass().getSimpleName(),
                    centerX,
                    centerZ
            ));
        }

        return api.createAdminClaim(claimWorld, region)
                .handle((created, throwable) -> {
                    if (throwable != null) {
                        Throwable cause = unwrap(throwable);
                        return failureResult(
                                IntegrationErrorCode.EXTERNAL_INTEGRATION_ERROR
                                        + ":create-claim-failed:"
                                        + cause.getClass().getSimpleName(),
                                centerX,
                                centerZ
                        );
                    }
                    if (!grantLeaderTrust(claimWorld, created, playerUuid)) {
                        safeDeleteClaim(claimWorld, created);
                        return failureResult(
                                IntegrationErrorCode.EXTERNAL_INTEGRATION_ERROR + ":trust-grant-failed",
                                centerX,
                                centerZ
                        );
                    }
                    return successResult(centerX, centerZ);
                });
    }

    @Override
    public CompletableFuture<Boolean> expandClaimAsync(String world, int centerX, int centerZ, int chunks) {
        if (!available()) {
            logger.warning("[CittaEXP] HuskClaims expand rejected: " + IntegrationErrorCode.DEPENDENCY_UNAVAILABLE + ":plugin-unavailable:HuskClaims");
            return CompletableFuture.completedFuture(false);
        }
        if (world == null || world.isBlank() || chunks < 1) {
            logger.warning("[CittaEXP] HuskClaims expand rejected: " + IntegrationErrorCode.VALIDATION_ERROR + ":invalid-input");
            return CompletableFuture.completedFuture(false);
        }

        Optional<ClaimWorld> maybeClaimWorld = claimWorld(world);
        if (maybeClaimWorld.isEmpty()) {
            logger.warning("[CittaEXP] HuskClaims expand rejected: " + IntegrationErrorCode.VALIDATION_ERROR + ":world-not-claimable");
            return CompletableFuture.completedFuture(false);
        }

        ClaimWorld claimWorld = maybeClaimWorld.get();
        Optional<Claim> maybeClaim = claimAt(world, centerX, centerZ);
        if (maybeClaim.isEmpty()) {
            logger.warning("[CittaEXP] HuskClaims expand rejected: " + IntegrationErrorCode.VALIDATION_ERROR + ":claim-not-found");
            return CompletableFuture.completedFuture(false);
        }

        Claim claim = maybeClaim.get();
        int deltaBlocks = chunks * 16;
        Region expanded;
        try {
            expanded = claim.getRegion().getResized(deltaBlocks, deltaBlocks, deltaBlocks, deltaBlocks);
        } catch (IllegalArgumentException ex) {
            logger.warning("[CittaEXP] HuskClaims expand rejected: " + IntegrationErrorCode.VALIDATION_ERROR + ":invalid-resize");
            return CompletableFuture.completedFuture(false);
        }

        return api.resizeClaim(claimWorld, claim, expanded)
                .handle((updated, throwable) -> {
                    if (throwable != null) {
                        Throwable cause = unwrap(throwable);
                        logger.warning(
                                "[CittaEXP] HuskClaims expand failed: "
                                        + IntegrationErrorCode.EXTERNAL_INTEGRATION_ERROR
                                        + ":"
                                        + cause.getClass().getSimpleName()
                        );
                        return false;
                    }
                    return true;
                });
    }

    @Override
    public CompletableFuture<Boolean> deleteClaimAtAsync(String world, int blockX, int blockZ) {
        if (!available()) {
            return CompletableFuture.completedFuture(false);
        }
        if (world == null || world.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        Optional<ClaimWorld> maybeClaimWorld = claimWorld(world);
        if (maybeClaimWorld.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        Optional<Claim> maybeClaim = claimAt(world, blockX, blockZ);
        if (maybeClaim.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }

        ClaimWorld claimWorld = maybeClaimWorld.get();
        Claim claim = maybeClaim.get();
        try {
            if (claim.isChildClaim()) {
                api.deleteChildClaim(claimWorld, claim);
            } else {
                api.deleteClaim(claimWorld, claim);
            }
            return CompletableFuture.completedFuture(true);
        } catch (RuntimeException ex) {
            logger.warning(
                    "[CittaEXP] HuskClaims delete claim failed world="
                            + world
                            + " x="
                            + blockX
                            + " z="
                            + blockZ
                            + " error="
                            + ex.getClass().getSimpleName()
            );
            return CompletableFuture.completedFuture(false);
        }
    }

    @Override
    public CompletableFuture<Boolean> syncClaimPermissionsAsync(
            String world,
            int anchorX,
            int anchorZ,
            UUID playerUuid,
            MemberClaimPermissions permissions,
            boolean managementTrusted
    ) {
        if (!available()) {
            return CompletableFuture.completedFuture(false);
        }
        if (world == null || world.isBlank() || playerUuid == null) {
            return CompletableFuture.completedFuture(false);
        }
        Optional<ClaimWorld> maybeClaimWorld = claimWorld(world);
        Optional<Claim> maybeClaim = claimAt(world, anchorX, anchorZ);
        if (maybeClaimWorld.isEmpty() || maybeClaim.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        ClaimWorld claimWorld = maybeClaimWorld.get();
        Claim claim = maybeClaim.get();
        MemberClaimPermissions normalized = normalizePermissions(permissions);
        Optional<TrustLevel> targetLevel = resolveTrustLevel(normalized, managementTrusted);
        if (targetLevel.isEmpty()) {
            logger.warning("[CittaEXP] HuskClaims trust sync failed: no matching trust level");
            return CompletableFuture.completedFuture(false);
        }

        User trustable = User.of(playerUuid, playerUuid.toString());
        try {
            Optional<TrustLevel> current = api.getTrustLevel(claim, claimWorld, trustable);
            if (current.isPresent() && current.get().getId().equalsIgnoreCase(targetLevel.get().getId())) {
                return CompletableFuture.completedFuture(true);
            }
            api.setTrustLevel(claim, claimWorld, trustable, targetLevel.get());
            return CompletableFuture.completedFuture(true);
        } catch (RuntimeException ex) {
            logger.warning(
                    "[CittaEXP] HuskClaims trust sync failed world="
                            + world
                            + " x="
                            + anchorX
                            + " z="
                            + anchorZ
                            + " player="
                            + playerUuid
                            + " error="
                            + ex.getClass().getSimpleName()
            );
            return CompletableFuture.completedFuture(false);
        }
    }

    @Override
    public CompletableFuture<Boolean> clearClaimPermissionsAsync(String world, int anchorX, int anchorZ, UUID playerUuid) {
        if (!available()) {
            return CompletableFuture.completedFuture(false);
        }
        if (world == null || world.isBlank() || playerUuid == null) {
            return CompletableFuture.completedFuture(false);
        }
        Optional<ClaimWorld> maybeClaimWorld = claimWorld(world);
        Optional<Claim> maybeClaim = claimAt(world, anchorX, anchorZ);
        if (maybeClaimWorld.isEmpty() || maybeClaim.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        ClaimWorld claimWorld = maybeClaimWorld.get();
        Claim claim = maybeClaim.get();
        User trustable = User.of(playerUuid, playerUuid.toString());
        try {
            if (api.getTrustLevel(claim, claimWorld, trustable).isEmpty()) {
                return CompletableFuture.completedFuture(true);
            }
            Optional<TrustLevel> removalLevel = resolveClearLevel();
            if (removalLevel.isPresent()) {
                api.setTrustLevel(claim, claimWorld, trustable, removalLevel.get());
            } else {
                api.setTrustLevel(claim, claimWorld, trustable, null);
            }
            return CompletableFuture.completedFuture(true);
        } catch (RuntimeException ex) {
            logger.warning(
                    "[CittaEXP] HuskClaims trust clear failed world="
                            + world
                            + " x="
                            + anchorX
                            + " z="
                            + anchorZ
                            + " player="
                            + playerUuid
                            + " error="
                            + ex.getClass().getSimpleName()
            );
            return CompletableFuture.completedFuture(false);
        }
    }

    private Optional<ClaimWorld> claimWorld(String worldName) {
        World bukkitWorld = Bukkit.getWorld(worldName);
        if (bukkitWorld == null) {
            return Optional.empty();
        }
        try {
            var world = api.getWorld(bukkitWorld);
            if (!api.isWorldClaimable(world)) {
                return Optional.empty();
            }
            return api.getClaimWorld(world);
        } catch (RuntimeException ex) {
            logger.warning(
                    "[CittaEXP] HuskClaims claimWorld resolve failed world="
                            + worldName
                            + " error="
                            + ex.getClass().getSimpleName()
            );
            return Optional.empty();
        }
    }

    private boolean grantLeaderTrust(ClaimWorld claimWorld, Claim claim, UUID leaderUuid) {
        Optional<TrustLevel> leaderTrust = resolveLeaderTrustLevel();
        if (leaderTrust.isEmpty()) {
            logger.warning("[CittaEXP] HuskClaims trust grant failed: no trust level available");
            return false;
        }
        try {
            api.setTrustLevel(claim, claimWorld, User.of(leaderUuid, leaderUuid.toString()), leaderTrust.get());
            return true;
        } catch (RuntimeException ex) {
            logger.warning(
                    "[CittaEXP] HuskClaims trust grant failed player="
                            + leaderUuid
                            + " error="
                            + ex.getClass().getSimpleName()
            );
            return false;
        }
    }

    private Optional<TrustLevel> resolveLeaderTrustLevel() {
        var trustLevels = api.getTrustLevels();
        if (trustLevels == null || trustLevels.isEmpty()) {
            return Optional.empty();
        }
        return trustLevels.stream()
                .filter(level -> level.getPrivileges().contains(TrustLevel.Privilege.MANAGE_TRUSTEES))
                .max(TrustLevel::compareTo)
                .or(() -> trustLevels.stream().max(TrustLevel::compareTo));
    }

    private Optional<TrustLevel> resolveTrustLevel(MemberClaimPermissions permissions, boolean managementTrusted) {
        var trustLevels = api.getTrustLevels();
        if (trustLevels == null || trustLevels.isEmpty()) {
            return Optional.empty();
        }
        if (managementTrusted) {
            return resolveLeaderTrustLevel();
        }
        if (permissions.build()) {
            return matchByHint(trustLevels, "build", "builder", "trusted")
                    .or(() -> trustLevels.stream().max(Comparator.comparingInt(TrustLevel::getWeight)));
        }
        if (permissions.container()) {
            return matchByHint(trustLevels, "container", "chest", "storage")
                    .or(() -> trustLevels.stream()
                            .sorted(Comparator.comparingInt(TrustLevel::getWeight))
                            .skip(Math.max(0, trustLevels.size() / 2))
                            .findFirst());
        }
        if (permissions.access()) {
            return matchByHint(trustLevels, "access", "visitor", "entry")
                    .or(() -> trustLevels.stream().min(Comparator.comparingInt(TrustLevel::getWeight)));
        }
        return resolveClearLevel();
    }

    private Optional<TrustLevel> resolveClearLevel() {
        var trustLevels = api.getTrustLevels();
        if (trustLevels == null || trustLevels.isEmpty()) {
            return Optional.empty();
        }
        return matchByHint(trustLevels, "none", "default", "untrusted", "remove")
                .or(() -> trustLevels.stream().min(Comparator.comparingInt(TrustLevel::getWeight)));
    }

    private static Optional<TrustLevel> matchByHint(
            java.util.List<TrustLevel> trustLevels,
            String... hints
    ) {
        return trustLevels.stream()
                .filter(level -> {
                    String id = safeText(level.getId());
                    String aliases = level.getCommandAliases() == null
                            ? ""
                            : safeText(String.join(" ", level.getCommandAliases()));
                    String haystack = (id + " " + aliases).toLowerCase(Locale.ROOT);
                    for (String hint : hints) {
                        if (haystack.contains(hint.toLowerCase(Locale.ROOT))) {
                            return true;
                        }
                    }
                    return false;
                })
                .max(Comparator.comparingInt(TrustLevel::getWeight));
    }

    private static MemberClaimPermissions normalizePermissions(MemberClaimPermissions permissions) {
        MemberClaimPermissions raw = permissions == null ? MemberClaimPermissions.none() : permissions;
        boolean access = raw.access() || raw.container() || raw.build();
        boolean container = raw.container() || raw.build();
        boolean build = raw.build();
        return new MemberClaimPermissions(access, container, build);
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    private void safeDeleteClaim(ClaimWorld claimWorld, Claim claim) {
        try {
            if (claim.isChildClaim()) {
                api.deleteChildClaim(claimWorld, claim);
            } else {
                api.deleteClaim(claimWorld, claim);
            }
        } catch (RuntimeException ex) {
            logger.warning(
                    "[CittaEXP] HuskClaims safe delete after trust failure failed: "
                            + ex.getClass().getSimpleName()
            );
        }
    }

    private Optional<Claim> claimAt(String worldName, int blockX, int blockZ) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return Optional.empty();
        }
        try {
            Location location = new Location(world, blockX, world.getMinHeight(), blockZ);
            return api.getClaimAt(api.getPosition(location));
        } catch (RuntimeException ex) {
            logger.warning(
                    "[CittaEXP] HuskClaims claim lookup failed world="
                            + worldName
                            + " x="
                            + blockX
                            + " z="
                            + blockZ
                            + " error="
                            + ex.getClass().getSimpleName()
            );
            return Optional.empty();
        }
    }

    private Region targetRegion(int centerX, int centerZ) {
        int width = claimSettings.autoWidth();
        int height = claimSettings.autoHeight();
        int minX = centerX - (width / 2);
        int minZ = centerZ - (height / 2);
        int maxX = minX + width - 1;
        int maxZ = minZ + height - 1;

        BlockPosition near = api.getBlockPosition(minX, minZ);
        BlockPosition far = api.getBlockPosition(maxX, maxZ);
        return Region.from(near, far);
    }

    private ClaimCreationResult successResult(int centerX, int centerZ) {
        int width = claimSettings.autoWidth();
        int height = claimSettings.autoHeight();
        int minX = centerX - (width / 2);
        int minZ = centerZ - (height / 2);
        int maxX = minX + width - 1;
        int maxZ = minZ + height - 1;
        return new ClaimCreationResult(true, minX, minZ, maxX, maxZ, "ok");
    }

    private ClaimCreationResult failureResult(String reason, int centerX, int centerZ) {
        int width = claimSettings.autoWidth();
        int height = claimSettings.autoHeight();
        int minX = centerX - (width / 2);
        int minZ = centerZ - (height / 2);
        int maxX = minX + width - 1;
        int maxZ = minZ + height - 1;
        return new ClaimCreationResult(false, minX, minZ, maxX, maxZ, reason);
    }

    private static String safeVersion(Plugin plugin) {
        if (plugin == null || plugin.getDescription() == null || plugin.getDescription().getVersion() == null) {
            return "n/a";
        }
        return plugin.getDescription().getVersion();
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }

    public record Binding(HuskClaimsPort port, AdapterStatus status) {
    }

    private static final class UnavailableHuskClaimsPort implements HuskClaimsPort {

        @Override
        public boolean available() {
            return false;
        }

        @Override
        public boolean hasClaimAt(UUID playerUuid) {
            return false;
        }

        @Override
        public CompletableFuture<ClaimCreationResult> createAutoClaim100x100Async(
                UUID playerUuid,
                String world,
                int centerX,
                int centerZ
        ) {
            return CompletableFuture.completedFuture(new ClaimCreationResult(
                    false,
                    centerX,
                    centerZ,
                    centerX,
                    centerZ,
                    IntegrationErrorCode.DEPENDENCY_UNAVAILABLE + ":plugin-unavailable:HuskClaims"
            ));
        }

        @Override
        public CompletableFuture<Boolean> expandClaimAsync(String world, int centerX, int centerZ, int chunks) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletableFuture<Boolean> deleteClaimAtAsync(String world, int blockX, int blockZ) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletableFuture<Boolean> syncClaimPermissionsAsync(
                String world,
                int anchorX,
                int anchorZ,
                UUID playerUuid,
                MemberClaimPermissions permissions,
                boolean managementTrusted
        ) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletableFuture<Boolean> clearClaimPermissionsAsync(String world, int anchorX, int anchorZ, UUID playerUuid) {
            return CompletableFuture.completedFuture(false);
        }
    }
}
