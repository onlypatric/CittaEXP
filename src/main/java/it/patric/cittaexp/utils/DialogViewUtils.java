package it.patric.cittaexp.utils;

import io.papermc.paper.dialog.Dialog;
import java.lang.reflect.Method;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.Plugin;

public final class DialogViewUtils {

    private DialogViewUtils() {
    }

    public static void showDialog(
            Plugin plugin,
            Audience audience,
            Dialog dialog,
            Component unavailableMessage,
            String logPrefix
    ) {
        try {
            for (Method method : audience.getClass().getMethods()) {
                if (!method.getName().equals("showDialog") || method.getParameterCount() != 1) {
                    continue;
                }
                method.invoke(audience, dialog);
                return;
            }
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("[" + logPrefix + "] dialog open failed: " + ex.getMessage());
        }
        audience.sendMessage(unavailableMessage);
    }
}
