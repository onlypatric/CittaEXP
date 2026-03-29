package it.patric.cittaexp.vault;

import java.util.Base64;
import org.bukkit.inventory.ItemStack;

public final class ItemStackCodec {

    private ItemStackCodec() {
    }

    public static String encode(ItemStack stack) {
        if (stack == null) {
            return "";
        }
        try {
            return Base64.getEncoder().encodeToString(stack.serializeAsBytes());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Impossibile serializzare ItemStack", exception);
        }
    }

    public static ItemStack decode(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(base64);
            if (raw.length == 0) {
                return null;
            }
            return ItemStack.deserializeBytes(raw);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Impossibile deserializzare ItemStack", exception);
        }
    }
}
