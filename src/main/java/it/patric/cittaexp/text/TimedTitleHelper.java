package it.patric.cittaexp.text;

import java.time.Duration;
import java.util.Collection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

public final class TimedTitleHelper {

    private static final Title.Times STANDARD_TIMES = Title.Times.times(
            Duration.ofSeconds(1),
            Duration.ofSeconds(10),
            Duration.ofSeconds(1)
    );

    private TimedTitleHelper() {
    }

    public static Title title(Component title, Component subtitle) {
        return Title.title(title, subtitle, STANDARD_TIMES);
    }

    public static void show(Player player, Component title, Component subtitle) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.showTitle(title(title, subtitle));
    }

    public static void show(Collection<? extends Player> players, Component title, Component subtitle) {
        if (players == null || players.isEmpty()) {
            return;
        }
        Title built = title(title, subtitle);
        for (Player player : players) {
            if (player != null && player.isOnline()) {
                player.showTitle(built);
            }
        }
    }
}
