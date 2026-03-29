package it.patric.cittaexp.integration.husktowns;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import net.william278.husktowns.town.Town;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class HuskTownsClaimWorldSanityService {

    private static final long INITIAL_DELAY_TICKS = 200L;
    private static final long PERIOD_TICKS = 20L * 60L * 10L;

    private final Plugin plugin;
    private final Logger logger;
    private final HuskTownsApiHook huskTownsApiHook;
    private final File databaseFile;
    private volatile ClaimWorldDiagnostics latestDiagnostics = ClaimWorldDiagnostics.unchecked();
    private int taskId = -1;

    public HuskTownsClaimWorldSanityService(
            Plugin plugin,
            Logger logger,
            HuskTownsApiHook huskTownsApiHook,
            File databaseFile
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.huskTownsApiHook = huskTownsApiHook;
        this.databaseFile = databaseFile;
    }

    public void start() {
        stop();
        this.latestDiagnostics = analyzeDatabaseOnlyNow();
        logDiagnostics(latestDiagnostics, true);
        this.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            ClaimWorldDiagnostics diagnostics = analyzeNow();
            ClaimWorldDiagnostics previous = latestDiagnostics;
            latestDiagnostics = diagnostics;
            if (!diagnostics.equals(previous)) {
                logDiagnostics(diagnostics, false);
            }
        }, INITIAL_DELAY_TICKS, PERIOD_TICKS);
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    public ClaimWorldDiagnostics latestDiagnostics() {
        return latestDiagnostics;
    }

    public boolean isClaimWritesSafe() {
        return latestDiagnostics.healthy();
    }

    public ClaimWorldDiagnostics analyzeNow() {
        return analyze(true);
    }

    private ClaimWorldDiagnostics analyzeDatabaseOnlyNow() {
        return analyze(false);
    }

    private ClaimWorldDiagnostics analyze(boolean includeRuntimeComparison) {
        if (databaseFile == null || !databaseFile.isFile()) {
            return ClaimWorldDiagnostics.error("database claims non trovato: " + databaseFile);
        }

        List<ClaimWorldRow> rows = new ArrayList<>();
        int databaseTownClaims = 0;
        int databaseAdminClaims = 0;
        String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
        String sql = """
                SELECT id, server_name, world_uuid, world_name, world_environment,
                       length(claims) AS claims_blob_bytes,
                       json(claims) AS claims_json
                FROM husktowns_claim_worlds
                ORDER BY server_name, world_uuid, id
                """;
        try (Connection connection = DriverManager.getConnection(url);
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String claimsJson = resultSet.getString("claims_json");
                ClaimCount claimCount = countClaims(claimsJson);
                rows.add(new ClaimWorldRow(
                        resultSet.getInt("id"),
                        resultSet.getString("server_name"),
                        resultSet.getString("world_uuid"),
                        resultSet.getString("world_name"),
                        resultSet.getString("world_environment"),
                        resultSet.getInt("claims_blob_bytes"),
                        claimCount.townClaims(),
                        claimCount.adminClaims()
                ));
                databaseTownClaims += claimCount.townClaims();
                databaseAdminClaims += claimCount.adminClaims();
            }
        } catch (SQLException exception) {
            return ClaimWorldDiagnostics.error("impossibile analizzare husktowns_claim_worlds: " + exception.getMessage());
        } catch (RuntimeException exception) {
            return ClaimWorldDiagnostics.error("impossibile decodificare i claim HuskTowns: " + exception.getMessage());
        }

        Map<String, List<ClaimWorldRow>> rowsByWorld = new LinkedHashMap<>();
        for (ClaimWorldRow row : rows) {
            rowsByWorld.computeIfAbsent(row.serverName() + "|" + row.worldUuid(), ignored -> new ArrayList<>()).add(row);
        }
        List<DuplicateClaimWorld> duplicates = rowsByWorld.values().stream()
                .filter(list -> list.size() > 1)
                .map(DuplicateClaimWorld::new)
                .toList();

        int runtimeTownClaims = includeRuntimeComparison
                ? huskTownsApiHook.getPlayableTowns().stream().mapToInt(Town::getClaimCount).sum()
                : databaseTownClaims;

        boolean duplicatesFound = !duplicates.isEmpty();
        boolean runtimeClaimMismatch = includeRuntimeComparison && runtimeTownClaims != databaseTownClaims;
        return new ClaimWorldDiagnostics(
                !duplicatesFound && !runtimeClaimMismatch,
                duplicatesFound,
                runtimeClaimMismatch,
                runtimeTownClaims,
                databaseTownClaims,
                databaseAdminClaims,
                duplicates,
                includeRuntimeComparison ? null : "runtime-check-pending",
                Instant.now()
        );
    }

    private void logDiagnostics(ClaimWorldDiagnostics diagnostics, boolean startup) {
        String prefix = startup ? "[husktowns-sanity] startup check:" : "[husktowns-sanity] state changed:";
        if (diagnostics.errorMessage() != null && !"runtime-check-pending".equals(diagnostics.errorMessage())) {
            logger.severe(prefix + " " + diagnostics.errorMessage());
            return;
        }
        if (diagnostics.healthy()) {
            logger.info(prefix + " healthy runtimeTownClaims=" + diagnostics.runtimeTownClaims()
                    + " databaseTownClaims=" + diagnostics.databaseTownClaims()
                    + " databaseAdminClaims=" + diagnostics.databaseAdminClaims());
            return;
        }
        if (diagnostics.duplicatesFound()) {
            for (DuplicateClaimWorld duplicate : diagnostics.duplicates()) {
                StringBuilder builder = new StringBuilder(prefix)
                        .append(" DUPLICATE claim_world rilevato:");
                for (ClaimWorldRow row : duplicate.rows()) {
                    builder.append(" [id=").append(row.id())
                            .append(", server=").append(row.serverName())
                            .append(", world=").append(row.worldName())
                            .append(", uuid=").append(row.worldUuid())
                            .append(", env=").append(row.worldEnvironment())
                            .append(", blob=").append(row.claimsBlobBytes())
                            .append(", townClaims=").append(row.townClaims())
                            .append(", adminClaims=").append(row.adminClaims())
                            .append(']');
                }
                logger.severe(builder.toString());
            }
        }
        if (diagnostics.runtimeClaimMismatch()) {
            logger.severe(prefix + " runtime/db mismatch runtimeTownClaims=" + diagnostics.runtimeTownClaims()
                    + " databaseTownClaims=" + diagnostics.databaseTownClaims()
                    + " databaseAdminClaims=" + diagnostics.databaseAdminClaims()
                    + ". Le operazioni claim CittaEXP verranno bloccate in modalita sicura.");
        }
    }

    private static ClaimCount countClaims(String claimsJson) {
        if (claimsJson == null || claimsJson.isBlank()) {
            return new ClaimCount(0, 0);
        }
        JsonObject root = JsonParser.parseString(claimsJson).getAsJsonObject();
        int townClaims = 0;
        int adminClaims = 0;

        JsonObject claims = root.has("claims") && root.get("claims").isJsonObject()
                ? root.getAsJsonObject("claims")
                : null;
        if (claims != null) {
            for (Map.Entry<String, JsonElement> entry : claims.entrySet()) {
                if (entry.getValue().isJsonArray()) {
                    townClaims += entry.getValue().getAsJsonArray().size();
                }
            }
        }
        if (root.has("admin_claims") && root.get("admin_claims").isJsonArray()) {
            adminClaims = root.getAsJsonArray("admin_claims").size();
        }
        return new ClaimCount(townClaims, adminClaims);
    }

    private record ClaimCount(int townClaims, int adminClaims) {
    }

    public record ClaimWorldRow(
            int id,
            String serverName,
            String worldUuid,
            String worldName,
            String worldEnvironment,
            int claimsBlobBytes,
            int townClaims,
            int adminClaims
    ) {
    }

    public record DuplicateClaimWorld(List<ClaimWorldRow> rows) {
    }

    public record ClaimWorldDiagnostics(
            boolean healthy,
            boolean duplicatesFound,
            boolean runtimeClaimMismatch,
            int runtimeTownClaims,
            int databaseTownClaims,
            int databaseAdminClaims,
            List<DuplicateClaimWorld> duplicates,
            String errorMessage,
            Instant checkedAt
    ) {
        public static ClaimWorldDiagnostics unchecked() {
            return new ClaimWorldDiagnostics(false, false, false, 0, 0, 0, List.of(), "check non ancora eseguito", Instant.now());
        }

        public static ClaimWorldDiagnostics error(String message) {
            return new ClaimWorldDiagnostics(false, false, false, 0, 0, 0, List.of(), message, Instant.now());
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ClaimWorldDiagnostics that)) {
                return false;
            }
            return healthy == that.healthy
                    && duplicatesFound == that.duplicatesFound
                    && runtimeClaimMismatch == that.runtimeClaimMismatch
                    && runtimeTownClaims == that.runtimeTownClaims
                    && databaseTownClaims == that.databaseTownClaims
                    && databaseAdminClaims == that.databaseAdminClaims
                    && Objects.equals(duplicates, that.duplicates)
                    && Objects.equals(errorMessage, that.errorMessage);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    healthy,
                    duplicatesFound,
                    runtimeClaimMismatch,
                    runtimeTownClaims,
                    databaseTownClaims,
                    databaseAdminClaims,
                    duplicates,
                    errorMessage
            );
        }
    }
}
