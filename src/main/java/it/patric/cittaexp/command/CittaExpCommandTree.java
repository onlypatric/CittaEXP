package it.patric.cittaexp.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import it.patric.cittaexp.integration.huskclaims.HuskClaimsHookStateService;
import it.patric.cittaexp.integration.huskclaims.HuskClaimsTownFallbackGuardListener;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.levels.CityLevelRepository;
import it.patric.cittaexp.levels.CityLevelStore;
import it.patric.cittaexp.levels.CityTaxService;
import it.patric.cittaexp.levels.StaffLevelCommand;
import it.patric.cittaexp.playersgui.CityPlayersGuiService;
import it.patric.cittaexp.playersgui.StaffCityPlayersCommand;
import it.patric.cittaexp.utils.PluginConfigUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

public final class CittaExpCommandTree {

    private static final String PROBE_PERMISSION = "cittaexp.admin.probe";

    private CittaExpCommandTree() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create(
            Plugin plugin,
            StaffLevelCommandContext staffContext,
            CityPlayersGuiService cityPlayersGuiService,
            HuskTownsApiHook huskTownsApiHook
    ) {
        PluginConfigUtils cfg = new PluginConfigUtils(plugin);
        return Commands.literal("cittaexp")
                .executes(ctx -> sendUsage(ctx.getSource().getSender()))
                .then(Commands.literal("probe").executes(ctx -> probe(ctx, plugin, staffContext)))
                .then(Commands.literal("staff")
                        .then(StaffLevelCommand.create(plugin, staffContext.requestService(), cfg))
                        .then(StaffCityPlayersCommand.create(cityPlayersGuiService, huskTownsApiHook)));
    }

    private static int sendUsage(CommandSender sender) {
        sender.sendMessage("[CittaEXP] Uso: /cittaexp <probe|staff level ...|staff city players <citta>>");
        return Command.SINGLE_SUCCESS;
    }

    private static int probe(
            CommandContext<CommandSourceStack> ctx,
            Plugin plugin,
            StaffLevelCommandContext staffContext
    ) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(PROBE_PERMISSION)) {
            sender.sendMessage("[CittaEXP] Permesso mancante: " + PROBE_PERMISSION);
            return Command.SINGLE_SUCCESS;
        }

        Plugin huskTowns = plugin.getServer().getPluginManager().getPlugin("HuskTowns");
        String status = huskTowns != null && huskTowns.isEnabled() ? "available" : "missing";
        String version = huskTowns != null ? huskTowns.getDescription().getVersion() : "-";
        sender.sendMessage("[CittaEXP] probe: mode=bootstrap, husktowns=" + status + ", version=" + version);
        CityLevelStore.StoreDiagnostics diagnostics = staffContext.store().diagnostics();
        CityLevelRepository.ScanStats scanStats = staffContext.store().scanStats();
        CityTaxService.TaxDiagnostics taxDiagnostics = staffContext.taxService().diagnostics();
        sender.sendMessage("[CittaEXP] levels: towns=" + scanStats.townCount()
                + ", xpScaled=" + scanStats.totalXpScaled()
                + ", pendingRequests=" + staffContext.store().countPendingRequests());
        sender.sendMessage("[CittaEXP] store: mode=" + diagnostics.storageMode()
                + ", cacheProgression=" + diagnostics.progressionCacheSize()
                + ", cacheApprovals=" + diagnostics.approvalCacheSize()
                + ", dirty=" + diagnostics.dirtyEntries()
                + ", outboxPending=" + diagnostics.outboxPending()
                + ", outboxFailed=" + diagnostics.outboxFailed());
        sender.sendMessage("[CittaEXP] store-mysql: lastSuccess=" + diagnostics.lastMySqlSuccessAt()
                + ", lastError=" + diagnostics.lastMySqlError()
                + ", lastErrorAt=" + diagnostics.lastMySqlErrorAt());
        sender.sendMessage("[CittaEXP] tax: lastMonth=" + taxDiagnostics.lastRunMonth()
                + ", lastRunAt=" + (taxDiagnostics.lastRunAt() == null ? "-" : taxDiagnostics.lastRunAt())
                + ", lastError=" + (taxDiagnostics.lastError() == null ? "-" : taxDiagnostics.lastError()));

        HuskClaimsHookStateService.Diagnostics claimsDiagnostics = staffContext.huskClaimsHookState().diagnostics();
        sender.sendMessage(format(
                plugin,
                "city.huskclaims.guard.probe.summary",
                "[CittaEXP] huskclaims_guard: mode={mode}, hookEnabled={hookEnabled}, reason={reason}",
                "{mode}", claimsDiagnostics.mode().probeValue(),
                "{hookEnabled}", Boolean.toString(claimsDiagnostics.huskTownsHookEnabled()),
                "{reason}", claimsDiagnostics.reasonCode()
        ));
        if (claimsDiagnostics.mode() == HuskClaimsHookStateService.GuardMode.FALLBACK_GUARD_ACTIVE) {
            HuskClaimsTownFallbackGuardListener fallbackListener = staffContext.huskClaimsFallbackListener();
            long cancelled = fallbackListener != null ? fallbackListener.cancelledCount() : 0L;
            long errors = fallbackListener != null ? fallbackListener.errorCount() : 0L;
            String lastError = fallbackListener != null ? fallbackListener.lastError() : "-";
            sender.sendMessage(format(
                    plugin,
                    "city.huskclaims.guard.probe.fallback_stats",
                    "[CittaEXP] huskclaims_guard(fallback): cancelled={cancelled}, errors={errors}, lastError={lastError}",
                    "{cancelled}", Long.toString(cancelled),
                    "{errors}", Long.toString(errors),
                    "{lastError}", lastError
            ));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static String format(
            Plugin plugin,
            String path,
            String fallback,
            String... replacements
    ) {
        String message = plugin.getConfig().getString(path, fallback);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        return message;
    }

    public record StaffLevelCommandContext(
            it.patric.cittaexp.levels.CityLevelRequestService requestService,
            CityLevelStore store,
            CityTaxService taxService,
            HuskClaimsHookStateService huskClaimsHookState,
            @Nullable HuskClaimsTownFallbackGuardListener huskClaimsFallbackListener
    ) {
    }
}
