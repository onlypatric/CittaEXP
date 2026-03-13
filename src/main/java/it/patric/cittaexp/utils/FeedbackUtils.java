package it.patric.cittaexp.utils;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

public final class FeedbackUtils {

    private FeedbackUtils() {
    }

    public static void send(
            CommandSender sender,
            PluginConfigUtils cfg,
            String path,
            String fallback,
            TagResolver... tags
    ) {
        sender.sendMessage(cfg.msg(path, fallback, tags));
    }
}
