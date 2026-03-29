package it.patric.cittaexp.utils;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class GuiItemFactory {

    private GuiItemFactory() {
    }

    public static ItemStack item(Material material, Component displayName) {
        return item(material, meta -> meta.displayName(displayName));
    }

    public static ItemStack item(Material material, Component displayName, List<Component> lore) {
        return item(material, meta -> {
            meta.displayName(displayName);
            meta.lore(lore);
        });
    }

    public static ItemStack item(Material material, Consumer<ItemMeta> editor) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        editor.accept(meta);
        sanitizeItalic(meta);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack playerHead(UUID playerId, Consumer<SkullMeta> editor) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta rawMeta = head.getItemMeta();
        if (!(rawMeta instanceof SkullMeta skullMeta)) {
            return head;
        }
        if (playerId.getMostSignificantBits() != 0L || playerId.getLeastSignificantBits() != 0L) {
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(playerId));
        }
        editor.accept(skullMeta);
        sanitizeItalic(skullMeta);
        head.setItemMeta(skullMeta);
        return head;
    }

    public static ItemStack customHead(String textureBase64, Consumer<SkullMeta> editor) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta rawMeta = head.getItemMeta();
        if (!(rawMeta instanceof SkullMeta skullMeta)) {
            return head;
        }
        applyTexture(skullMeta, textureBase64);
        editor.accept(skullMeta);
        sanitizeItalic(skullMeta);
        head.setItemMeta(skullMeta);
        return head;
    }

    public static ItemStack customHead(String textureBase64, Component displayName) {
        return customHead(textureBase64, meta -> meta.displayName(displayName));
    }

    public static ItemStack customHead(String textureBase64, Component displayName, List<Component> lore) {
        return customHead(textureBase64, meta -> {
            meta.displayName(displayName);
            meta.lore(lore);
        });
    }

    public static ItemStack plainHead(Component displayName) {
        return item(Material.PLAYER_HEAD, displayName);
    }

    public static ItemStack fillerPane() {
        return new ItemStack(Material.AIR);
    }

    private static void applyTexture(SkullMeta skullMeta, String textureBase64) {
        if (textureBase64 == null || textureBase64.isBlank()) {
            return;
        }
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", textureBase64));
            skullMeta.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // keep default head if texture parsing fails
        }
    }

    private static void sanitizeItalic(ItemMeta meta) {
        if (meta == null) {
            return;
        }
        Component displayName = meta.displayName();
        if (displayName != null) {
            meta.displayName(noItalic(displayName));
        }
        List<Component> lore = meta.lore();
        if (lore == null || lore.isEmpty()) {
            return;
        }
        meta.lore(lore.stream().map(GuiItemFactory::noItalic).toList());
    }

    private static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
