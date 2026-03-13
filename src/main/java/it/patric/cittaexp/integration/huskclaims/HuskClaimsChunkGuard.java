package it.patric.cittaexp.integration.huskclaims;

import net.william278.huskclaims.api.BukkitHuskClaimsAPI;
import net.william278.huskclaims.api.HuskClaimsAPI;
import net.william278.huskclaims.claim.Region;
import net.william278.huskclaims.position.BlockPosition;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

public final class HuskClaimsChunkGuard {

    private static final String HUSKCLAIMS_PLUGIN = "HuskClaims";

    private final Plugin plugin;
    private final BukkitHuskClaimsAPI api;

    public HuskClaimsChunkGuard(Plugin plugin) {
        this.plugin = plugin;
        this.api = resolveApi(plugin);
    }

    public boolean isAvailable() {
        return api != null;
    }

    public boolean isClaimedAt(Location location) {
        if (location == null || location.getWorld() == null || api == null) {
            return false;
        }
        return isClaimedChunk(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public boolean isClaimedChunk(World world, int chunkX, int chunkZ) {
        if (world == null || api == null) {
            return false;
        }
        try {
            int minX = chunkX << 4;
            int minZ = chunkZ << 4;
            int maxX = minX + 15;
            int maxZ = minZ + 15;

            BlockPosition near = api.getBlockPosition(minX, minZ);
            BlockPosition far = api.getBlockPosition(maxX, maxZ);
            Region chunkRegion = Region.from(near, far);
            return api.isRegionClaimed(api.getWorld(world), chunkRegion);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("HuskClaims check fallito, skip precheck. reason=" + ex.getMessage());
            return false;
        }
    }

    private BukkitHuskClaimsAPI resolveApi(Plugin plugin) {
        Plugin huskClaims = plugin.getServer().getPluginManager().getPlugin(HUSKCLAIMS_PLUGIN);
        if (huskClaims == null || !huskClaims.isEnabled()) {
            return null;
        }
        try {
            return BukkitHuskClaimsAPI.getInstance();
        } catch (HuskClaimsAPI.NotRegisteredException ex) {
            plugin.getLogger().warning("HuskClaims presente ma API non registrata: " + ex.getMessage());
            return null;
        }
    }
}
