package it.patric.cittaexp.integration.huskclaims;

import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import net.william278.huskclaims.claim.Region;
import org.bukkit.World;

public final class TownClaimOverlapChecker {

    public record ChunkRange(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
    }

    private final HuskTownsApiHook huskTownsApiHook;

    public TownClaimOverlapChecker(HuskTownsApiHook huskTownsApiHook) {
        this.huskTownsApiHook = huskTownsApiHook;
    }

    public boolean overlapsTownClaim(World world, Region region) {
        if (world == null || region == null) {
            return false;
        }
        ChunkRange range = toChunkRange(region);
        for (int x = range.minChunkX(); x <= range.maxChunkX(); x++) {
            for (int z = range.minChunkZ(); z <= range.maxChunkZ(); z++) {
                if (huskTownsApiHook.getClaimAt(world.getChunkAt(x, z)).isPresent()) {
                    return true;
                }
            }
        }
        return false;
    }

    static ChunkRange toChunkRange(Region region) {
        return fromBlockBounds(
                region.getNearCorner().getBlockX(),
                region.getFarCorner().getBlockX(),
                region.getNearCorner().getBlockZ(),
                region.getFarCorner().getBlockZ()
        );
    }

    static ChunkRange fromBlockBounds(int minBlockX, int maxBlockX, int minBlockZ, int maxBlockZ) {
        return new ChunkRange(
                Math.floorDiv(Math.min(minBlockX, maxBlockX), 16),
                Math.floorDiv(Math.max(minBlockX, maxBlockX), 16),
                Math.floorDiv(Math.min(minBlockZ, maxBlockZ), 16),
                Math.floorDiv(Math.max(minBlockZ, maxBlockZ), 16)
        );
    }
}
