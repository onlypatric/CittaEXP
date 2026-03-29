package it.patric.cittaexp.levels;

import it.patric.cittaexp.economy.EconomyBalanceCategory;
import it.patric.cittaexp.economy.EconomyValueService;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.william278.husktowns.town.Town;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.Plugin;

public final class CityTaxService {

    public record TaxDiagnostics(String lastRunMonth, Instant lastRunAt, String lastError) {
    }

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final CityLevelStore store;
    private final CityLevelSettings settings;
    private final EconomyValueService economyValueService;

    private volatile String lastError;
    private volatile Instant lastRunAt;
    private BukkitTask taxTask;

    public CityTaxService(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            CityLevelStore store,
            CityLevelSettings settings,
            EconomyValueService economyValueService
    ) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.store = store;
        this.settings = settings;
        this.economyValueService = economyValueService;
    }

    public void start() {
        long ticks = Math.max(20L, settings.taxCheckIntervalSeconds() * 20L);
        this.taxTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickTaxCycle, ticks, ticks);
    }

    public void stop() {
        if (taxTask != null) {
            taxTask.cancel();
            taxTask = null;
        }
    }

    public TaxDiagnostics diagnostics() {
        return new TaxDiagnostics(store.getLastTaxMonthKey().orElse("-"), lastRunAt, lastError);
    }

    private void tickTaxCycle() {
        try {
            ZoneId zoneId = ZoneId.of(settings.taxTimezone());
            ZonedDateTime now = ZonedDateTime.now(zoneId);
            if (now.getDayOfMonth() != 1 || now.getHour() != settings.taxRunHour() || now.getMinute() != settings.taxRunMinute()) {
                return;
            }
            String monthKey = YearMonth.from(now).toString();
            if (store.getLastTaxMonthKey().filter(monthKey::equals).isPresent()) {
                return;
            }

            List<Town> towns = huskTownsApiHook.getTowns();
            for (Town town : towns) {
                TownProgression progression = store.getOrCreateProgression(town.getId());
                TownStage stage = progression.stage();
                CityLevelSettings.StageSpec spec = settings.spec(stage);
                double effectiveTax = economyValueService.effectiveAmount(EconomyBalanceCategory.TOWN_MONTHLY_TAX, spec.monthlyTax());
                if (effectiveTax <= 0.0D) {
                    continue;
                }

                double debt = store.getDebt(town.getId());
                double due = debt + effectiveTax;
                double balance = town.getMoney().doubleValue();
                double paid = Math.min(due, Math.max(0.0D, balance));
                double debtAfter = Math.max(0.0D, due - paid);

                if (paid > 0.0D) {
                    UUID actorId = resolveActor(town).orElse(town.getMayor());
                    huskTownsApiHook.editTown(actorId, town.getId(), mutableTown -> {
                        BigDecimal current = mutableTown.getMoney();
                        BigDecimal payment = BigDecimal.valueOf(paid);
                        if (current.compareTo(payment) < 0) {
                            mutableTown.setMoney(BigDecimal.ZERO);
                        } else {
                            mutableTown.setMoney(current.subtract(payment));
                        }
                    });
                }

                String status = paid >= due ? "PAID" : (paid > 0.0D ? "PARTIAL" : "UNPAID");
                store.appendTaxLedger(town.getId(), monthKey, stage, due, paid, debtAfter, status);
                store.setDebt(town.getId(), debtAfter);
            }

            store.setLastTaxMonthKey(monthKey);
            lastRunAt = Instant.now();
            lastError = null;
            plugin.getLogger().info("[levels] tax cycle completed month=" + monthKey);
        } catch (Exception exception) {
            lastError = exception.getMessage() == null ? "tax-cycle-error" : exception.getMessage();
            plugin.getLogger().warning("[levels] tax cycle failed reason=" + lastError);
        }
    }

    private Optional<UUID> resolveActor(Town town) {
        for (UUID userId : town.getMembers().keySet()) {
            if (Bukkit.getPlayer(userId) != null) {
                return Optional.of(userId);
            }
        }
        return Optional.empty();
    }
}
