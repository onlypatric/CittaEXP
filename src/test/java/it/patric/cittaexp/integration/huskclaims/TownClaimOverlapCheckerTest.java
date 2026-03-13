package it.patric.cittaexp.integration.huskclaims;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TownClaimOverlapCheckerTest {

    @Test
    void fromBlockBoundsHandlesPositiveChunk() {
        TownClaimOverlapChecker.ChunkRange range = TownClaimOverlapChecker.fromBlockBounds(0, 15, 16, 31);
        Assertions.assertEquals(0, range.minChunkX());
        Assertions.assertEquals(0, range.maxChunkX());
        Assertions.assertEquals(1, range.minChunkZ());
        Assertions.assertEquals(1, range.maxChunkZ());
    }

    @Test
    void fromBlockBoundsHandlesNegativeEdges() {
        TownClaimOverlapChecker.ChunkRange range = TownClaimOverlapChecker.fromBlockBounds(-17, -16, -1, 0);
        Assertions.assertEquals(-2, range.minChunkX());
        Assertions.assertEquals(-1, range.maxChunkX());
        Assertions.assertEquals(-1, range.minChunkZ());
        Assertions.assertEquals(0, range.maxChunkZ());
    }

    @Test
    void fromBlockBoundsNormalizesSwappedCoordinates() {
        TownClaimOverlapChecker.ChunkRange range = TownClaimOverlapChecker.fromBlockBounds(33, -1, 48, 16);
        Assertions.assertEquals(-1, range.minChunkX());
        Assertions.assertEquals(2, range.maxChunkX());
        Assertions.assertEquals(1, range.minChunkZ());
        Assertions.assertEquals(3, range.maxChunkZ());
    }
}
