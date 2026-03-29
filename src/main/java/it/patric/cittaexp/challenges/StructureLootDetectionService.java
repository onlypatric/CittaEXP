package it.patric.cittaexp.challenges;

import com.destroystokyo.paper.loottable.LootableBlockInventory;
import com.destroystokyo.paper.loottable.LootableEntityInventory;
import com.destroystokyo.paper.loottable.LootableInventory;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.Inventory;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;

public final class StructureLootDetectionService {

    public record Detection(
            String dedupeKey,
            String lootTableKey,
            Location location
    ) {
    }

    private final List<String> lootTableAllowlist;

    public StructureLootDetectionService(List<String> lootTableAllowlist) {
        this.lootTableAllowlist = lootTableAllowlist == null ? List.of() : List.copyOf(lootTableAllowlist);
    }

    public Optional<Detection> resolve(Inventory inventory) {
        if (inventory == null) {
            return Optional.empty();
        }
        Location location = inventory.getLocation();
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        BlockState state = location.getBlock().getState();
        if (!(state instanceof Container) || !(state instanceof Lootable lootable)) {
            return Optional.empty();
        }
        LootTable lootTable = lootable.getLootTable();
        if (lootTable == null || lootTable.getKey() == null) {
            return Optional.empty();
        }
        String lootTableKey = lootTable.getKey().asString().toLowerCase(Locale.ROOT);
        if (!isAllowed(lootTableKey)) {
            return Optional.empty();
        }
        String dedupeKey = "loot:"
                + location.getWorld().getUID() + ':'
                + location.getBlockX() + ':'
                + location.getBlockY() + ':'
                + location.getBlockZ() + ':'
                + lootTableKey;
        return Optional.of(new Detection(dedupeKey, lootTableKey, location.clone()));
    }

    public Optional<Detection> resolve(LootableInventory lootableInventory) {
        if (lootableInventory == null) {
            return Optional.empty();
        }
        LootTable lootTable = lootableInventory.getLootTable();
        if (lootTable == null || lootTable.getKey() == null) {
            return Optional.empty();
        }
        String lootTableKey = lootTable.getKey().asString().toLowerCase(Locale.ROOT);
        if (!isAllowed(lootTableKey)) {
            return Optional.empty();
        }
        Location location = resolveLocation(lootableInventory);
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        String dedupeKey = "loot:"
                + location.getWorld().getUID() + ':'
                + location.getBlockX() + ':'
                + location.getBlockY() + ':'
                + location.getBlockZ() + ':'
                + lootTableKey;
        return Optional.of(new Detection(dedupeKey, lootTableKey, location.clone()));
    }

    private boolean isAllowed(String lootTableKey) {
        if (lootTableKey == null || lootTableKey.isBlank()) {
            return false;
        }
        if (lootTableAllowlist.isEmpty()) {
            return lootTableKey.contains("chests/");
        }
        for (String prefix : lootTableAllowlist) {
            if (prefix == null || prefix.isBlank()) {
                continue;
            }
            if (lootTableKey.startsWith(prefix.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static Location resolveLocation(LootableInventory lootableInventory) {
        if (lootableInventory instanceof LootableBlockInventory blockInventory) {
            return blockInventory.getBlock() == null ? null : blockInventory.getBlock().getLocation();
        }
        if (lootableInventory instanceof LootableEntityInventory entityInventory) {
            Entity entity = entityInventory.getEntity();
            return entity == null ? null : entity.getLocation();
        }
        return null;
    }
}
