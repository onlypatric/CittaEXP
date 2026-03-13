package it.patric.cittaexp.utils;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class GuiLayoutUtils {

    private GuiLayoutUtils() {
    }

    public static void fillAllExcept(Inventory inventory, ItemStack fillItem, int... excludedSlots) {
        Set<Integer> excluded = new HashSet<>();
        for (int slot : excludedSlots) {
            excluded.add(slot);
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (excluded.contains(slot)) {
                continue;
            }
            inventory.setItem(slot, fillItem.clone());
        }
    }
}
