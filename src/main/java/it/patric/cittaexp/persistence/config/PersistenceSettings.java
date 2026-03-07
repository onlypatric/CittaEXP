package it.patric.cittaexp.persistence.config;

import java.util.Locale;
import java.util.Objects;

public record PersistenceSettings(
        PersistenceMode configuredMode,
        boolean fallbackEnabled,
        MysqlSettings mysql,
        SqliteSettings sqlite,
        ReplaySettings replay
) {

    public PersistenceSettings {
        configuredMode = Objects.requireNonNull(configuredMode, "configuredMode");
        mysql = Objects.requireNonNull(mysql, "mysql");
        sqlite = Objects.requireNonNull(sqlite, "sqlite");
        replay = Objects.requireNonNull(replay, "replay");
    }

    public enum PersistenceMode {
        AUTO,
        MYSQL,
        SQLITE;

        public static PersistenceMode fromConfig(String raw) {
            String normalized = raw == null ? "auto" : raw.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "auto" -> AUTO;
                case "mysql" -> MYSQL;
                case "sqlite" -> SQLITE;
                default -> AUTO;
            };
        }
    }

    public record MysqlSettings(
            String host,
            int port,
            String database,
            String username,
            String password,
            String params
    ) {

        public MysqlSettings {
            host = requireText(host, "host");
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("port must be between 1 and 65535");
            }
            database = requireText(database, "database");
            username = requireText(username, "username");
            password = password == null ? "" : password;
            params = params == null ? "" : params;
        }
    }

    public record SqliteSettings(String file) {

        public SqliteSettings {
            file = requireText(file, "file");
        }
    }

    public record ReplaySettings(int batchSize, int intervalSeconds) {

        public ReplaySettings {
            if (batchSize < 1) {
                throw new IllegalArgumentException("batchSize must be >= 1");
            }
            if (intervalSeconds < 1) {
                throw new IllegalArgumentException("intervalSeconds must be >= 1");
            }
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
