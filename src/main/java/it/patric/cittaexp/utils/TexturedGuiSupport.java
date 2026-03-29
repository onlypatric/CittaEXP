package it.patric.cittaexp.utils;

import dev.lone.itemsadder.api.CustomStack;
import dev.lone.itemsadder.api.FontImages.FontImageWrapper;
import dev.lone.itemsadder.api.FontImages.TexturedInventoryWrapper;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class TexturedGuiSupport {

    private TexturedGuiSupport() {
    }

    public static OpenResult createInventory(
            Plugin plugin,
            InventoryHolder holder,
            Consumer<Inventory> binder,
            int size,
            Component fallbackTitle,
            String plainTitle,
            int titleOffset,
            int textureOffset,
            String fontImageId,
            String logPrefix
    ) {
        Inventory fallback = Bukkit.createInventory(holder, size, fallbackTitle);
        binder.accept(fallback);

        Plugin itemsAdder = plugin.getServer().getPluginManager().getPlugin("ItemsAdder");
        if (itemsAdder == null || !itemsAdder.isEnabled()) {
            return new OpenResult(fallback, null);
        }

        FontImageWrapper background = FontImageWrapper.instance(fontImageId);
        if (background == null || !background.exists()) {
            return new OpenResult(fallback, null);
        }

        try {
            TexturedInventoryWrapper wrapper = new TexturedInventoryWrapper(
                    holder,
                    size,
                    "",//plainTitle,
                    titleOffset,
                    textureOffset,
                    background
            );
            Inventory internal = wrapper.getInternal();
            if (internal == null) {
                return new OpenResult(fallback, null);
            }
            binder.accept(internal);
            return new OpenResult(internal, wrapper);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[" + logPrefix + "] ItemsAdder textured open failed, fallback vanilla: " + throwable.getMessage());
            return new OpenResult(fallback, null);
        }
    }

    public static ItemStack transparentButtonBase(Plugin plugin, String transparentButtonId) {
        Plugin itemsAdder = plugin.getServer().getPluginManager().getPlugin("ItemsAdder");
        if (itemsAdder == null || !itemsAdder.isEnabled()) {
            return null;
        }
        try {
            CustomStack custom = CustomStack.getInstance(transparentButtonId);
            if (custom == null) {
                return null;
            }
            ItemStack stack = custom.getItemStack();
            return stack == null ? null : stack.clone();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static ItemStack overlayButton(
            ItemStack transparentBase,
            Component display,
            List<Component> lore,
            ItemStack fallbackItem
    ) {
        Component safeDisplay = noItalic(display);
        List<Component> safeLore = lore == null ? List.of() : lore.stream().map(TexturedGuiSupport::noItalic).toList();
        if (transparentBase == null) {
            return fallbackItem == null
                    ? GuiItemFactory.item(Material.PAPER, safeDisplay, safeLore)
                    : applyMeta(fallbackItem.clone(), safeDisplay, safeLore);
        }
        return applyMeta(transparentBase.clone(), safeDisplay, safeLore);
    }

    public static void fillGroup(Inventory inventory, int[] slots, ItemStack item) {
        for (int slot : slots) {
            inventory.setItem(slot, item == null ? null : item.clone());
        }
    }

    private static ItemStack applyMeta(ItemStack item, Component display, List<Component> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(display);
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public record OpenResult(Inventory inventory, TexturedInventoryWrapper texturedWrapper) {
    }
}
