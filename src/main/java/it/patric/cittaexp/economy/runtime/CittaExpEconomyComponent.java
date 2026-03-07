package it.patric.cittaexp.economy.runtime;

import dev.patric.commonlib.api.CommonComponent;
import dev.patric.commonlib.api.CommonContext;
import it.patric.cittaexp.core.port.CityTreasuryLedgerPort;
import it.patric.cittaexp.core.port.EconomyStatePort;
import it.patric.cittaexp.core.port.RankingPort;
import it.patric.cittaexp.core.port.TaxPolicyPort;
import it.patric.cittaexp.core.port.VaultEconomyPort;
import it.patric.cittaexp.core.service.CapitalService;
import it.patric.cittaexp.core.service.EconomyDiagnosticsService;
import it.patric.cittaexp.core.service.RankingReadService;
import it.patric.cittaexp.core.service.TaxService;
import it.patric.cittaexp.economy.config.EconomyConfigLoader;
import it.patric.cittaexp.economy.config.EconomySettings;
import it.patric.cittaexp.persistence.port.CityReadPort;
import it.patric.cittaexp.persistence.port.CityTxPort;
import it.patric.cittaexp.persistence.port.CityWritePort;

public final class CittaExpEconomyComponent implements CommonComponent {

    private DefaultEconomyService service;
    private EconomySettings settings;

    @Override
    public String id() {
        return "cittaexp-economy";
    }

    @Override
    public void onLoad(CommonContext context) {
        this.settings = new EconomyConfigLoader(context.plugin()).load();
        context.services().register(EconomySettings.class, settings);
    }

    @Override
    public void onEnable(CommonContext context) {
        service = new DefaultEconomyService(
                context.services().require(CityReadPort.class),
                context.services().require(CityWritePort.class),
                context.services().require(CityTxPort.class),
                context.services().require(TaxPolicyPort.class),
                context.services().require(CityTreasuryLedgerPort.class),
                context.services().require(EconomyStatePort.class),
                context.services().require(VaultEconomyPort.class),
                context.services().require(RankingPort.class),
                context.scheduler(),
                settings,
                context.logger()
        );
        context.services().register(TaxService.class, service);
        context.services().register(CapitalService.class, service);
        context.services().register(RankingReadService.class, service);
        context.services().register(EconomyDiagnosticsService.class, service);
        service.start();
    }

    @Override
    public void onDisable(CommonContext context) {
        if (service != null) {
            service.stop();
        }
    }
}
