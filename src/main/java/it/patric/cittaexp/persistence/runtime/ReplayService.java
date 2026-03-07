package it.patric.cittaexp.persistence.runtime;

import dev.patric.commonlib.api.CommonScheduler;
import dev.patric.commonlib.api.TaskHandle;
import it.patric.cittaexp.persistence.config.PersistenceSettings;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ReplayService {

    private final CityPersistenceService persistence;
    private final PersistenceSettings settings;
    private final CommonScheduler scheduler;
    private final Logger logger;
    private TaskHandle repeatingTask;

    public ReplayService(
            CityPersistenceService persistence,
            PersistenceSettings settings,
            CommonScheduler scheduler,
            Logger logger
    ) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void start() {
        if (repeatingTask != null && !repeatingTask.isCancelled()) {
            return;
        }
        long intervalTicks = Math.max(20L, settings.replay().intervalSeconds() * 20L);
        scheduler.runAsync(this::runCycle);
        repeatingTask = scheduler.runSyncRepeating(intervalTicks, intervalTicks, () -> scheduler.runAsync(this::runCycle));
    }

    public void stop() {
        if (repeatingTask != null) {
            repeatingTask.cancel();
            repeatingTask = null;
        }
    }

    private void runCycle() {
        try {
            persistence.refreshHealth();
            persistence.replayPendingToMysql(settings.replay().batchSize());
        } catch (RuntimeException ex) {
            logger.log(Level.WARNING, "[CittaEXP][persistence] replay cycle failed", ex);
        }
    }
}
