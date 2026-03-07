package it.patric.cittaexp.runtime.integration;

import dev.patric.commonlib.api.CommonComponent;
import dev.patric.commonlib.api.CommonContext;
import dev.patric.commonlib.api.port.ClaimsPort;
import it.patric.cittaexp.core.port.HuskClaimsPort;
import it.patric.cittaexp.core.port.RankingPort;
import it.patric.cittaexp.core.port.VaultEconomyPort;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;

public final class RequiredIntegrationsComponent implements CommonComponent, RequiredIntegrationStatusService {

    private volatile IntegrationStatusSnapshot snapshot = new IntegrationStatusSnapshot(List.of(), RankingScanStats.empty());
    private volatile Supplier<RankingScanStats> rankingStatsSupplier = RankingScanStats::empty;
    private IntegrationSettings settings;

    @Override
    public String id() {
        return "cittaexp-required-integrations";
    }

    @Override
    public void onLoad(CommonContext context) {
        this.settings = new IntegrationConfigLoader(context.plugin()).load();
        context.services().register(IntegrationSettings.class, settings);
        context.services().register(RequiredIntegrationStatusService.class, this);
        this.snapshot = new IntegrationStatusSnapshot(
                List.of(
                        new AdapterStatus("Vault", AdapterState.UNAVAILABLE, "n/a", "bootstrap-pending"),
                        new AdapterStatus("HuskClaims", AdapterState.UNAVAILABLE, "n/a", "bootstrap-pending"),
                        new AdapterStatus("ClassificheEXP", AdapterState.UNAVAILABLE, "n/a", "bootstrap-pending")
                ),
                RankingScanStats.empty()
        );
    }

    @Override
    public void onEnable(CommonContext context) {
        ClaimsPort claimsPort = context.services().require(ClaimsPort.class);

        VaultEconomyAdapter.Binding vaultBinding = VaultEconomyAdapter.bind(
                () -> context.plugin().getServer().getServicesManager().getRegistration(Economy.class),
                context.logger()
        );
        HuskClaimsAdapter.Binding huskBinding = HuskClaimsAdapter.bind(
                claimsPort,
                () -> {
                    Plugin plugin = context.plugin().getServer().getPluginManager().getPlugin("HuskClaims");
                    return plugin != null && plugin.isEnabled();
                },
                settings.claim(),
                context.logger()
        );

        Plugin classifichePlugin = context.plugin().getServer().getPluginManager().getPlugin("ClassificheEXP");
        ClassificheExpRankingAdapter.Binding rankingBinding = ClassificheExpRankingAdapter.bind(
                classifichePlugin,
                settings.ranking().scanLimit(),
                context.logger()
        );

        context.services().register(VaultEconomyPort.class, vaultBinding.port());
        context.services().register(HuskClaimsPort.class, huskBinding.port());
        context.services().register(RankingPort.class, rankingBinding.port());

        List<AdapterStatus> statuses = new ArrayList<>();
        statuses.add(vaultBinding.status());
        statuses.add(huskBinding.status());
        statuses.add(rankingBinding.status());

        this.rankingStatsSupplier = rankingBinding.port() instanceof ClassificheExpRankingAdapter adapter
                ? adapter::scanStats
                : () -> rankingBinding.initialStats();
        this.snapshot = new IntegrationStatusSnapshot(statuses, rankingStatsSupplier.get());

        if (settings.failFast() && !snapshot.allAvailable()) {
            String details = snapshot.adapters().stream()
                    .filter(value -> value.state() == AdapterState.UNAVAILABLE)
                    .map(value -> value.name() + ":" + value.reason())
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("unknown");
            throw new IllegalStateException("Required integrations unavailable: " + details);
        }
    }

    @Override
    public IntegrationStatusSnapshot snapshot() {
        IntegrationStatusSnapshot current = snapshot;
        RankingScanStats fresh = rankingStatsSupplier.get();
        if (fresh == null) {
            return current;
        }
        return new IntegrationStatusSnapshot(current.adapters(), fresh);
    }
}
