package it.patric.cittaexp.challenges;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.util.BoundingBox;

public final class ExplorationDetectionService {

    public record Detection(
            String dedupeKey,
            String structureKey,
            String structureHash
    ) {
    }

    private final Set<String> structureAllowlist;

    public ExplorationDetectionService(Set<String> structureAllowlist) {
        this.structureAllowlist = structureAllowlist == null ? Set.of() : Set.copyOf(structureAllowlist);
    }

    @SuppressWarnings("removal")
    public Optional<Detection> resolve(Location to) {
        if (to == null || to.getWorld() == null) {
            return Optional.empty();
        }
        Chunk chunk = to.getChunk();
        Collection<GeneratedStructure> structures = chunk.getStructures();
        if (structures == null || structures.isEmpty()) {
            return Optional.empty();
        }
        for (GeneratedStructure structure : structures) {
            if (structure == null || structure.getStructure() == null || structure.getStructure().key() == null) {
                continue;
            }
            String structureKey = structure.getStructure().key().asString().toLowerCase(Locale.ROOT);
            if (!allowed(structureKey)) {
                continue;
            }
            BoundingBox box = structure.getBoundingBox();
            if (box == null || !box.contains(to.getX(), to.getY(), to.getZ())) {
                continue;
            }
            String hash = boundsHash(box);
            String dedupeKey = "explore:" + to.getWorld().getUID() + ':' + structureKey + ':' + hash;
            return Optional.of(new Detection(dedupeKey, structureKey, hash));
        }
        return Optional.empty();
    }

    private boolean allowed(String structureKey) {
        if (structureKey == null || structureKey.isBlank()) {
            return false;
        }
        if (structureAllowlist.isEmpty()) {
            return true;
        }
        return structureAllowlist.contains(structureKey);
    }

    private static String boundsHash(BoundingBox box) {
        return (int) Math.floor(box.getMinX()) + "_"
                + (int) Math.floor(box.getMinY()) + "_"
                + (int) Math.floor(box.getMinZ()) + "_"
                + (int) Math.floor(box.getMaxX()) + "_"
                + (int) Math.floor(box.getMaxY()) + "_"
                + (int) Math.floor(box.getMaxZ());
    }
}
