package it.patric.cittaexp.levels;

import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.william278.husktowns.claim.Claim;
import net.william278.husktowns.claim.TownClaim;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;

public final class TownClaimTileScanner {

    private final HuskTownsApiHook huskTownsApiHook;

    public TownClaimTileScanner(HuskTownsApiHook huskTownsApiHook) {
        this.huskTownsApiHook = huskTownsApiHook;
    }

    public void forEachTileEntity(int townId, Consumer<BlockState> consumer) {
        for (World world : Bukkit.getWorlds()) {
            for (TownClaim claim : huskTownsApiHook.api().getClaims(world)) {
                if (claim.town().getId() != townId) {
                    continue;
                }
                Claim rawClaim = claim.claim();
                Chunk chunk = world.getChunkAt(rawClaim.getChunk().getX(), rawClaim.getChunk().getZ());
                for (BlockState state : chunk.getTileEntities()) {
                    consumer.accept(state);
                }
            }
        }
    }

    public long countMatchingTileEntities(int townId, Predicate<BlockState> matcher) {
        final long[] count = {0L};
        forEachTileEntity(townId, state -> {
            if (matcher.test(state)) {
                count[0]++;
            }
        });
        return count[0];
    }
}
