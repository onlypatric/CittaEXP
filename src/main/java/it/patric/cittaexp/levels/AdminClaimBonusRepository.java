package it.patric.cittaexp.levels;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.plugin.Plugin;

public final class AdminClaimBonusRepository {

    private final String jdbcUrl;

    public AdminClaimBonusRepository(Plugin plugin) {
        this(plugin.getDataFolder().toPath().resolve("admin-claim-bonuses.db"));
    }

    public AdminClaimBonusRepository(Path path) {
        this.jdbcUrl = "jdbc:sqlite:" + path;
    }

    public synchronized void initialize() {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_admin_claim_bonus (
                        town_id INTEGER NOT NULL PRIMARY KEY,
                        claim_bonus INTEGER NOT NULL
                    )
                    """);
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile inizializzare repository admin claim bonus", exception);
        }
    }

    public synchronized Map<Integer, Integer> loadAll() {
        Map<Integer, Integer> result = new HashMap<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT town_id, claim_bonus
                     FROM town_admin_claim_bonus
                     """);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getInt("town_id"), Math.max(0, rs.getInt("claim_bonus")));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare admin claim bonus", exception);
        }
        return result;
    }

    public synchronized void setBonus(int townId, int bonus) {
        int normalized = Math.max(0, bonus);
        if (normalized == 0) {
            deleteTown(townId);
            return;
        }
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO town_admin_claim_bonus(town_id, claim_bonus)
                     VALUES (?, ?)
                     ON CONFLICT(town_id) DO UPDATE SET claim_bonus = excluded.claim_bonus
                     """)) {
            statement.setInt(1, townId);
            statement.setInt(2, normalized);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile salvare admin claim bonus town=" + townId, exception);
        }
    }

    public synchronized void deleteTown(int townId) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     DELETE FROM town_admin_claim_bonus
                     WHERE town_id = ?
                     """)) {
            statement.setInt(1, townId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile eliminare admin claim bonus town=" + townId, exception);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }
}
