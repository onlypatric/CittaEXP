package it.patric.cittaexp.persistence.runtime;

import it.patric.cittaexp.persistence.config.PersistenceSettings;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public final class PersistenceModeManager {

    private final PersistenceSettings settings;
    private final Logger logger;
    private final AtomicReference<PersistenceRuntimeMode> runtimeMode;
    private volatile long lastModeSwitchEpochMilli;
    private volatile String lastSwitchReason;

    public PersistenceModeManager(PersistenceSettings settings, Logger logger) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.runtimeMode = new AtomicReference<>(PersistenceRuntimeMode.UNAVAILABLE);
        this.lastModeSwitchEpochMilli = System.currentTimeMillis();
        this.lastSwitchReason = "bootstrap";
    }

    public synchronized PersistenceRuntimeMode initialize() {
        PersistenceSettings.PersistenceMode configured = settings.configuredMode();
        return switch (configured) {
            case SQLITE -> switchTo(PersistenceRuntimeMode.SQLITE_FALLBACK, "configured-sqlite");
            case MYSQL -> initializeMysqlPreferred();
            case AUTO -> initializeAuto();
        };
    }

    public synchronized void refreshHealth() {
        PersistenceRuntimeMode current = runtimeMode.get();
        if (current == PersistenceRuntimeMode.MYSQL_ACTIVE && !canReachMysql()) {
            if (settings.fallbackEnabled()) {
                switchTo(PersistenceRuntimeMode.SQLITE_FALLBACK, "mysql-unreachable");
            } else {
                switchTo(PersistenceRuntimeMode.UNAVAILABLE, "mysql-unreachable-no-fallback");
            }
            return;
        }

        if (current == PersistenceRuntimeMode.SQLITE_FALLBACK && canReachMysql()) {
            switchTo(PersistenceRuntimeMode.MYSQL_ACTIVE, "mysql-recovered");
        }
    }

    public synchronized void reportMysqlFailure(String reason) {
        if (runtimeMode.get() != PersistenceRuntimeMode.MYSQL_ACTIVE) {
            return;
        }
        if (settings.fallbackEnabled()) {
            switchTo(PersistenceRuntimeMode.SQLITE_FALLBACK, "mysql-write-failed:" + reason);
        } else {
            switchTo(PersistenceRuntimeMode.UNAVAILABLE, "mysql-write-failed-no-fallback:" + reason);
        }
    }

    public PersistenceRuntimeMode currentMode() {
        return runtimeMode.get();
    }

    public long lastModeSwitchEpochMilli() {
        return lastModeSwitchEpochMilli;
    }

    public String lastSwitchReason() {
        return lastSwitchReason;
    }

    public boolean mysqlReachable() {
        return canReachMysql();
    }

    private PersistenceRuntimeMode initializeAuto() {
        if (canReachMysql()) {
            return switchTo(PersistenceRuntimeMode.MYSQL_ACTIVE, "auto-mysql-reachable");
        }
        if (settings.fallbackEnabled()) {
            return switchTo(PersistenceRuntimeMode.SQLITE_FALLBACK, "auto-fallback-sqlite");
        }
        return switchTo(PersistenceRuntimeMode.UNAVAILABLE, "auto-mysql-down-fallback-disabled");
    }

    private PersistenceRuntimeMode initializeMysqlPreferred() {
        if (canReachMysql()) {
            return switchTo(PersistenceRuntimeMode.MYSQL_ACTIVE, "mysql-reachable");
        }
        if (settings.fallbackEnabled()) {
            return switchTo(PersistenceRuntimeMode.SQLITE_FALLBACK, "mysql-configured-fallback");
        }
        return switchTo(PersistenceRuntimeMode.UNAVAILABLE, "mysql-configured-down-no-fallback");
    }

    private PersistenceRuntimeMode switchTo(PersistenceRuntimeMode next, String reason) {
        PersistenceRuntimeMode previous = runtimeMode.getAndSet(next);
        lastModeSwitchEpochMilli = System.currentTimeMillis();
        lastSwitchReason = reason;
        if (previous != next) {
            logger.warning("[CittaEXP][persistence] mode switch " + previous + " -> " + next + " (" + reason + ")");
        }
        return next;
    }

    private boolean canReachMysql() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection ignored = DriverManager.getConnection(mysqlUrl(), settings.mysql().username(), settings.mysql().password())) {
                return true;
            }
        } catch (ClassNotFoundException | SQLException ex) {
            return false;
        }
    }

    public String mysqlUrl() {
        String base = "jdbc:mysql://"
                + settings.mysql().host()
                + ":"
                + settings.mysql().port()
                + "/"
                + settings.mysql().database();
        String params = settings.mysql().params();
        return params.isBlank() ? base : base + "?" + params;
    }
}
