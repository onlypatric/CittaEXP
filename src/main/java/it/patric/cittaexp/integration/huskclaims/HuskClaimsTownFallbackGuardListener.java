package it.patric.cittaexp.integration.huskclaims;

import it.patric.cittaexp.utils.ExceptionUtils;
import it.patric.cittaexp.utils.PluginConfigUtils;
import java.util.concurrent.atomic.AtomicLong;
import net.william278.huskclaims.claim.Region;
import net.william278.huskclaims.event.BukkitCreateChildClaimEvent;
import net.william278.huskclaims.event.BukkitCreateClaimEvent;
import net.william278.huskclaims.event.BukkitResizeChildClaimEvent;
import net.william278.huskclaims.event.BukkitResizeClaimEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public final class HuskClaimsTownFallbackGuardListener implements Listener {

    private final Plugin plugin;
    private final PluginConfigUtils cfg;
    private final HuskClaimsHookStateService hookStateService;
    private final TownClaimOverlapChecker overlapChecker;
    private final boolean denyOnGuardError;
    private final AtomicLong cancelledCount = new AtomicLong();
    private final AtomicLong errorCount = new AtomicLong();
    private volatile String lastError = "-";

    public HuskClaimsTownFallbackGuardListener(
            Plugin plugin,
            HuskClaimsHookStateService hookStateService,
            TownClaimOverlapChecker overlapChecker,
            boolean denyOnGuardError
    ) {
        this.plugin = plugin;
        this.cfg = new PluginConfigUtils(plugin);
        this.hookStateService = hookStateService;
        this.overlapChecker = overlapChecker;
        this.denyOnGuardError = denyOnGuardError;
    }

    public long cancelledCount() {
        return cancelledCount.get();
    }

    public long errorCount() {
        return errorCount.get();
    }

    public String lastError() {
        return lastError;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreateClaim(BukkitCreateClaimEvent event) {
        guard(event.getPlayer(), event, event.getRegion(), "create-claim");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreateChildClaim(BukkitCreateChildClaimEvent event) {
        guard(event.getPlayer(), event, event.getChildRegion(), "create-child-claim");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onResizeClaim(BukkitResizeClaimEvent event) {
        guard(event.getPlayer(), event, event.getNewRegion(), "resize-claim");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onResizeChildClaim(BukkitResizeChildClaimEvent event) {
        guard(event.getPlayer(), event, event.getNewChildRegion(), "resize-child-claim");
    }

    private void guard(Player player, Cancellable cancellable, Region region, String action) {
        if (hookStateService.mode() != HuskClaimsHookStateService.GuardMode.FALLBACK_GUARD_ACTIVE) {
            return;
        }

        try {
            if (!overlapChecker.overlapsTownClaim(player.getWorld(), region)) {
                return;
            }
            cancellable.setCancelled(true);
            cancelledCount.incrementAndGet();
            player.sendMessage(cfg.msg(
                    "city.huskclaims.guard.user.blocked",
                    "<red>Claim bloccato: area gestita da una citta.</red> <gray>Usa i flussi /city per i territori citta.</gray>"
            ));
        } catch (RuntimeException ex) {
            String safe = ExceptionUtils.safeMessage(ex, "errore sconosciuto");
            lastError = action + ":" + safe;
            errorCount.incrementAndGet();
            plugin.getLogger().warning("[huskclaims-guard] action=" + action + " failed reason=" + safe);
            if (!denyOnGuardError) {
                return;
            }
            cancellable.setCancelled(true);
            player.sendMessage(cfg.msg(
                    "city.huskclaims.guard.user.error",
                    "<red>Operazione claim bloccata temporaneamente per sicurezza. Riprova tra poco.</red>"
            ));
        }
    }

}
