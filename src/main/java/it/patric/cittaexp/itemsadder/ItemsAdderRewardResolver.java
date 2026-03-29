package it.patric.cittaexp.itemsadder;

import dev.lone.itemsadder.api.CustomStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class ItemsAdderRewardResolver {

    public record RewardSpec(String itemId, int amount) {
    }

    private final Plugin plugin;

    public ItemsAdderRewardResolver(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean available() {
        Plugin itemsAdder = plugin.getServer().getPluginManager().getPlugin("ItemsAdder");
        return itemsAdder != null && itemsAdder.isEnabled();
    }

    public Optional<ItemStack> itemStack(String itemId, int amount) {
        if (!available() || itemId == null || itemId.isBlank() || amount <= 0) {
            return Optional.empty();
        }
        CustomStack custom = CustomStack.getInstance(itemId.trim());
        if (custom == null || custom.getItemStack() == null) {
            return Optional.empty();
        }
        ItemStack stack = custom.getItemStack().clone();
        stack.setAmount(amount);
        return Optional.of(stack);
    }

    public List<ItemStack> resolveAll(List<RewardSpec> rewards) {
        List<ItemStack> resolved = new ArrayList<>();
        if (rewards == null || rewards.isEmpty() || !available()) {
            return resolved;
        }
        for (RewardSpec reward : rewards) {
            if (reward == null) {
                continue;
            }
            itemStack(reward.itemId(), reward.amount()).ifPresent(resolved::add);
        }
        return List.copyOf(resolved);
    }
}
