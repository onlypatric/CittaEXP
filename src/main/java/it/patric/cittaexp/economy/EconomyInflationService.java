package it.patric.cittaexp.economy;

import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.william278.husktowns.town.Town;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class EconomyInflationService {

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final EconomyBalanceSettings settings;
    private final File stateFile;
    private final Object lock;

    private volatile EconomyInflationSnapshot snapshot;
    private volatile boolean frozen;
    private volatile double manualBiasPercent;
    private volatile double emissionAccumulator;
    private volatile Instant emissionUpdatedAt;
    private volatile BukkitTask recalcTask;

    public EconomyInflationService(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            EconomyBalanceSettings settings
    ) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.settings = settings;
        this.stateFile = new File(plugin.getDataFolder(), "economy-balance-state.yml");
        this.lock = new Object();
        this.snapshot = EconomyInflationSnapshot.neutral(false, 0.0D);
        this.frozen = false;
        this.manualBiasPercent = 0.0D;
        this.emissionAccumulator = 0.0D;
        this.emissionUpdatedAt = Instant.now();
    }

    public void start() {
        synchronized (lock) {
            loadState();
            snapshot = recalculateInternal(Instant.now(), false);
        }
        long periodTicks = Math.max(20L, settings.recalcIntervalSeconds() * 20L);
        recalcTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                () -> {
                    try {
                        recalculate();
                    } catch (RuntimeException exception) {
                        plugin.getLogger().warning("[economy] recalc failed reason="
                                + (exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
                    }
                },
                periodTicks,
                periodTicks
        );
    }

    public void stop() {
        if (recalcTask != null) {
            recalcTask.cancel();
            recalcTask = null;
        }
        synchronized (lock) {
            saveState();
        }
    }

    public EconomyInflationSnapshot snapshot() {
        return snapshot;
    }

    public EconomyInflationSnapshot recalculate() {
        synchronized (lock) {
            snapshot = recalculateInternal(Instant.now(), true);
            return snapshot;
        }
    }

    public void recordRewardEmission(double amount) {
        if (amount <= 0.0D) {
            return;
        }
        synchronized (lock) {
            Instant now = Instant.now();
            decayEmission(now);
            emissionAccumulator += amount;
            emissionUpdatedAt = now;
            saveState();
        }
    }

    public void freeze() {
        synchronized (lock) {
            frozen = true;
            snapshot = new EconomyInflationSnapshot(
                    snapshot.calculatedAt(),
                    true,
                    snapshot.telemetryReliable(),
                    manualBiasPercent,
                    snapshot.townCount(),
                    snapshot.totalTownTreasury(),
                    snapshot.averageTownTreasury(),
                    snapshot.recentRewardEmission(),
                    snapshot.observedIndex(),
                    snapshot.timeDriftIndex(),
                    snapshot.effectiveIndex(),
                    snapshot.costMultiplier(),
                    snapshot.rewardMultiplier()
            );
            saveState();
        }
    }

    public void unfreeze() {
        synchronized (lock) {
            frozen = false;
            snapshot = recalculateInternal(Instant.now(), true);
        }
    }

    public void setManualBiasPercent(double manualBiasPercent) {
        synchronized (lock) {
            this.manualBiasPercent = manualBiasPercent;
            snapshot = recalculateInternal(Instant.now(), true);
        }
    }

    public void resetManualBias() {
        setManualBiasPercent(0.0D);
    }

    public boolean frozen() {
        return frozen;
    }

    public double manualBiasPercent() {
        return manualBiasPercent;
    }

    private EconomyInflationSnapshot recalculateInternal(Instant now, boolean persist) {
        decayEmission(now);
        if (frozen) {
            EconomyInflationSnapshot frozenSnapshot = new EconomyInflationSnapshot(
                    snapshot.calculatedAt(),
                    true,
                    snapshot.telemetryReliable(),
                    manualBiasPercent,
                    snapshot.townCount(),
                    snapshot.totalTownTreasury(),
                    snapshot.averageTownTreasury(),
                    snapshot.recentRewardEmission(),
                    snapshot.observedIndex(),
                    snapshot.timeDriftIndex(),
                    snapshot.effectiveIndex(),
                    snapshot.costMultiplier(),
                    snapshot.rewardMultiplier()
            );
            if (persist) {
                snapshot = frozenSnapshot;
                saveState();
            }
            return frozenSnapshot;
        }

        List<Town> towns = huskTownsApiHook.getTowns();
        int townCount = towns == null ? 0 : (int) towns.stream().filter(town -> town != null).count();
        double totalTreasury = towns == null ? 0.0D : towns.stream()
                .filter(town -> town != null)
                .mapToDouble(town -> Math.max(0.0D, town.getMoney().doubleValue()))
                .sum();
        double averageTreasury = townCount <= 0 ? 0.0D : totalTreasury / townCount;
        boolean telemetryReliable = townCount >= settings.observation().minActiveTowns();

        double treasuryPressure = safeRatio(averageTreasury, settings.observation().targetAverageTownTreasury());
        double emissionPressure = safeRatio(emissionAccumulator, settings.observation().targetRecentRewardEmission());
        double observedIndex = weightedAverage(
                treasuryPressure,
                settings.observation().treasuryWeight(),
                emissionPressure,
                settings.observation().emissionWeight()
        );

        Instant previousAt = snapshot == null || snapshot.calculatedAt() == null ? now : snapshot.calculatedAt();
        double driftBase = snapshot == null ? 1.0D : Math.max(0.1D, snapshot.observedIndex());
        double elapsedDays = Math.max(0.0D, Duration.between(previousAt, now).toSeconds() / 86_400.0D);
        double timeDriftIndex = driftBase * Math.pow(1.0D + (settings.drift().dailyGrowthPercent() / 100.0D), elapsedDays);

        double rawIndex = telemetryReliable ? observedIndex : timeDriftIndex;
        double effectiveIndex = Math.max(0.1D, rawIndex * (1.0D + (manualBiasPercent / 100.0D)));
        double costMultiplier = settings.costCurve().clamp(Math.pow(effectiveIndex, settings.costCurve().exponent()));
        double rewardMultiplier = settings.rewardCurve().clamp(Math.pow(effectiveIndex, settings.rewardCurve().exponent()));

        EconomyInflationSnapshot next = new EconomyInflationSnapshot(
                now,
                false,
                telemetryReliable,
                manualBiasPercent,
                townCount,
                totalTreasury,
                averageTreasury,
                emissionAccumulator,
                observedIndex,
                timeDriftIndex,
                effectiveIndex,
                costMultiplier,
                rewardMultiplier
        );
        if (persist) {
            snapshot = next;
            saveState();
        }
        return next;
    }

    private void decayEmission(Instant now) {
        if (emissionAccumulator <= 0.0D) {
            emissionAccumulator = 0.0D;
            emissionUpdatedAt = now;
            return;
        }
        long elapsedSeconds = Math.max(0L, Duration.between(emissionUpdatedAt, now).toSeconds());
        double halfLifeHours = settings.observation().emissionHalfLifeHours();
        if (halfLifeHours <= 0.0D || elapsedSeconds <= 0L) {
            emissionUpdatedAt = now;
            return;
        }
        double decayFactor = Math.pow(0.5D, elapsedSeconds / (halfLifeHours * 3600.0D));
        emissionAccumulator = Math.max(0.0D, emissionAccumulator * decayFactor);
        emissionUpdatedAt = now;
    }

    private void loadState() {
        if (!stateFile.exists()) {
            snapshot = EconomyInflationSnapshot.neutral(false, 0.0D);
            frozen = false;
            manualBiasPercent = 0.0D;
            emissionAccumulator = 0.0D;
            emissionUpdatedAt = Instant.now();
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
        frozen = yaml.getBoolean("frozen", false);
        manualBiasPercent = yaml.getDouble("manualBiasPercent", 0.0D);
        emissionAccumulator = Math.max(0.0D, yaml.getDouble("emissionAccumulator", 0.0D));
        emissionUpdatedAt = parseInstant(yaml.getString("emissionUpdatedAt"), Instant.now());
        EconomyInflationSnapshot loaded = new EconomyInflationSnapshot(
                parseInstant(yaml.getString("snapshot.calculatedAt"), Instant.now()),
                frozen,
                yaml.getBoolean("snapshot.telemetryReliable", false),
                manualBiasPercent,
                Math.max(0, yaml.getInt("snapshot.townCount", 0)),
                Math.max(0.0D, yaml.getDouble("snapshot.totalTownTreasury", 0.0D)),
                Math.max(0.0D, yaml.getDouble("snapshot.averageTownTreasury", 0.0D)),
                Math.max(0.0D, yaml.getDouble("snapshot.recentRewardEmission", emissionAccumulator)),
                Math.max(0.1D, yaml.getDouble("snapshot.observedIndex", 1.0D)),
                Math.max(0.1D, yaml.getDouble("snapshot.timeDriftIndex", 1.0D)),
                Math.max(0.1D, yaml.getDouble("snapshot.effectiveIndex", 1.0D)),
                Math.max(0.1D, yaml.getDouble("snapshot.costMultiplier", 1.0D)),
                Math.max(0.1D, yaml.getDouble("snapshot.rewardMultiplier", 1.0D))
        );
        snapshot = loaded;
    }

    private void saveState() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("frozen", frozen);
        yaml.set("manualBiasPercent", manualBiasPercent);
        yaml.set("emissionAccumulator", emissionAccumulator);
        yaml.set("emissionUpdatedAt", emissionUpdatedAt.toString());
        yaml.set("snapshot.calculatedAt", snapshot.calculatedAt().toString());
        yaml.set("snapshot.telemetryReliable", snapshot.telemetryReliable());
        yaml.set("snapshot.townCount", snapshot.townCount());
        yaml.set("snapshot.totalTownTreasury", snapshot.totalTownTreasury());
        yaml.set("snapshot.averageTownTreasury", snapshot.averageTownTreasury());
        yaml.set("snapshot.recentRewardEmission", snapshot.recentRewardEmission());
        yaml.set("snapshot.observedIndex", snapshot.observedIndex());
        yaml.set("snapshot.timeDriftIndex", snapshot.timeDriftIndex());
        yaml.set("snapshot.effectiveIndex", snapshot.effectiveIndex());
        yaml.set("snapshot.costMultiplier", snapshot.costMultiplier());
        yaml.set("snapshot.rewardMultiplier", snapshot.rewardMultiplier());
        try {
            yaml.save(stateFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("[economy] save state failed reason="
                    + (exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
        }
    }

    private static Instant parseInstant(String raw, Instant fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(raw);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double safeRatio(double value, double target) {
        if (target <= 0.0D) {
            return 1.0D;
        }
        return Math.max(0.1D, value / target);
    }

    private static double weightedAverage(double first, double firstWeight, double second, double secondWeight) {
        double totalWeight = Math.max(0.0D, firstWeight) + Math.max(0.0D, secondWeight);
        if (totalWeight <= 0.0D) {
            return 1.0D;
        }
        return ((first * Math.max(0.0D, firstWeight)) + (second * Math.max(0.0D, secondWeight))) / totalWeight;
    }
}
