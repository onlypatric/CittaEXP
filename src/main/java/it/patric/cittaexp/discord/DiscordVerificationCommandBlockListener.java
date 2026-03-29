package it.patric.cittaexp.discord;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public final class DiscordVerificationCommandBlockListener implements Listener {

    private static final String BYPASS_PERMISSION = "cittaexp.discord.verify.bypass";

    private final DiscordCommandService discordCommandService;

    public DiscordVerificationCommandBlockListener(DiscordCommandService discordCommandService) {
        this.discordCommandService = discordCommandService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.getPlayer().hasPermission(BYPASS_PERMISSION)) {
            return;
        }
        String raw = event.getMessage();
        if (raw == null || raw.isBlank()) {
            return;
        }
        String normalized = raw.trim().toLowerCase();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        if (isVerifyCommand(normalized)) {
            return;
        }

        if (isCityCommand(normalized)
                && !discordCommandService.requireVerified(event.getPlayer(), "/city")) {
            event.setCancelled(true);
            return;
        }

        if (isCreateCommand(normalized)
                && !discordCommandService.requireVerified(event.getPlayer(), "/city create")) {
            event.setCancelled(true);
            return;
        }
        if (isInviteAcceptCommand(normalized)
                && !discordCommandService.requireVerified(event.getPlayer(), "/city invite accept")) {
            event.setCancelled(true);
        }
    }

    private static boolean isCreateCommand(String normalized) {
        return starts(normalized, "city create")
                || starts(normalized, "town create")
                || starts(normalized, "t create")
                || starts(normalized, "husktowns:town create")
                || starts(normalized, "husktowns:t create");
    }

    private static boolean isVerifyCommand(String normalized) {
        return starts(normalized, "verify");
    }

    private static boolean isCityCommand(String normalized) {
        return starts(normalized, "city");
    }

    private static boolean isInviteAcceptCommand(String normalized) {
        return starts(normalized, "city invite accept")
                || starts(normalized, "town invite accept")
                || starts(normalized, "t invite accept")
                || starts(normalized, "husktowns:town invite accept")
                || starts(normalized, "husktowns:t invite accept");
    }

    private static boolean starts(String normalized, String needle) {
        return normalized.equals(needle) || normalized.startsWith(needle + " ");
    }
}
