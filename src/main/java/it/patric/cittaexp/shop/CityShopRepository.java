package it.patric.cittaexp.shop;

import it.patric.cittaexp.shop.CityShopService.LocationKey;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.plugin.Plugin;

public final class CityShopRepository {

    public record ShopRecord(
            int shopId,
            int townId,
            String world,
            int signX,
            int signY,
            int signZ,
            int containerX,
            int containerY,
            int containerZ,
            String itemBase64,
            Long buyPrice,
            Long sellPrice,
            int tradeQuantity,
            UUID creatorUuid,
            Instant createdAt,
            Instant updatedAt
    ) {
        public LocationKey signKey() {
            return new LocationKey(world, signX, signY, signZ);
        }

        public LocationKey containerKey() {
            return new LocationKey(world, containerX, containerY, containerZ);
        }

        public ShopRecord withItemBase64(String updatedItemBase64, Instant updated) {
            return new ShopRecord(
                    shopId,
                    townId,
                    world,
                    signX,
                    signY,
                    signZ,
                    containerX,
                    containerY,
                    containerZ,
                    updatedItemBase64,
                    buyPrice,
                    sellPrice,
                    tradeQuantity,
                    creatorUuid,
                    createdAt,
                    updated
            );
        }
    }

    private final String jdbcUrl;

    public CityShopRepository(Plugin plugin) {
        this(plugin.getDataFolder().toPath().resolve("city-shops.db"));
    }

    public CityShopRepository(Path path) {
        this.jdbcUrl = "jdbc:sqlite:" + path;
    }

    public synchronized void initialize() {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS city_shops (
                        shop_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        town_id INTEGER NOT NULL,
                        world TEXT NOT NULL,
                        sign_x INTEGER NOT NULL,
                        sign_y INTEGER NOT NULL,
                        sign_z INTEGER NOT NULL,
                        container_x INTEGER NOT NULL,
                        container_y INTEGER NOT NULL,
                        container_z INTEGER NOT NULL,
                        item_base64 TEXT,
                        buy_price INTEGER,
                        sell_price INTEGER,
                        trade_quantity INTEGER NOT NULL,
                        creator_uuid TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL,
                        UNIQUE(world, sign_x, sign_y, sign_z),
                        UNIQUE(world, container_x, container_y, container_z)
                    )
                    """);
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile inizializzare repository city shops", exception);
        }
    }

    public synchronized List<ShopRecord> loadAll() {
        List<ShopRecord> records = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM city_shops");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                records.add(mapRecord(resultSet));
            }
            return records;
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile leggere city shops", exception);
        }
    }

    public synchronized ShopRecord insertShop(
            int townId,
            String world,
            LocationKey signKey,
            LocationKey containerKey,
            Long buyPrice,
            Long sellPrice,
            int tradeQuantity,
            UUID creatorUuid,
            Instant createdAt,
            Instant updatedAt
    ) {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO city_shops(
                    town_id, world,
                    sign_x, sign_y, sign_z,
                    container_x, container_y, container_z,
                    item_base64, buy_price, sell_price, trade_quantity,
                    creator_uuid, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, townId);
            statement.setString(2, world);
            statement.setInt(3, signKey.x());
            statement.setInt(4, signKey.y());
            statement.setInt(5, signKey.z());
            statement.setInt(6, containerKey.x());
            statement.setInt(7, containerKey.y());
            statement.setInt(8, containerKey.z());
            statement.setString(9, null);
            if (buyPrice == null) {
                statement.setNull(10, java.sql.Types.BIGINT);
            } else {
                statement.setLong(10, buyPrice);
            }
            if (sellPrice == null) {
                statement.setNull(11, java.sql.Types.BIGINT);
            } else {
                statement.setLong(11, sellPrice);
            }
            statement.setInt(12, tradeQuantity);
            statement.setString(13, creatorUuid.toString());
            statement.setString(14, createdAt.toString());
            statement.setString(15, updatedAt.toString());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("Nessuna chiave generata per city shop");
                }
                return new ShopRecord(
                        keys.getInt(1),
                        townId,
                        world,
                        signKey.x(),
                        signKey.y(),
                        signKey.z(),
                        containerKey.x(),
                        containerKey.y(),
                        containerKey.z(),
                        null,
                        buyPrice,
                        sellPrice,
                        tradeQuantity,
                        creatorUuid,
                        createdAt,
                        updatedAt
                );
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile creare city shop", exception);
        }
    }

    public synchronized void updateConfiguredItem(int shopId, String itemBase64, Instant updatedAt) {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(
                "UPDATE city_shops SET item_base64 = ?, updated_at = ? WHERE shop_id = ?"
        )) {
            statement.setString(1, itemBase64);
            statement.setString(2, updatedAt.toString());
            statement.setInt(3, shopId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile aggiornare item city shop", exception);
        }
    }

    public synchronized boolean deleteShop(int shopId) {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM city_shops WHERE shop_id = ?"
        )) {
            statement.setInt(1, shopId);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile rimuovere city shop", exception);
        }
    }

    public synchronized int deleteShopsByTown(int townId) {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM city_shops WHERE town_id = ?"
        )) {
            statement.setInt(1, townId);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile rimuovere city shops per town", exception);
        }
    }

    public synchronized Optional<ShopRecord> findBySign(LocationKey signKey) {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM city_shops WHERE world = ? AND sign_x = ? AND sign_y = ? AND sign_z = ?
                """)) {
            statement.setString(1, signKey.world());
            statement.setInt(2, signKey.x());
            statement.setInt(3, signKey.y());
            statement.setInt(4, signKey.z());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRecord(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile leggere city shop dal cartello", exception);
        }
    }

    private ShopRecord mapRecord(ResultSet resultSet) throws SQLException {
        long buyValue = resultSet.getLong("buy_price");
        Long buyPrice = resultSet.wasNull() ? null : buyValue;
        long sellValue = resultSet.getLong("sell_price");
        Long sellPrice = resultSet.wasNull() ? null : sellValue;
        return new ShopRecord(
                resultSet.getInt("shop_id"),
                resultSet.getInt("town_id"),
                resultSet.getString("world"),
                resultSet.getInt("sign_x"),
                resultSet.getInt("sign_y"),
                resultSet.getInt("sign_z"),
                resultSet.getInt("container_x"),
                resultSet.getInt("container_y"),
                resultSet.getInt("container_z"),
                resultSet.getString("item_base64"),
                buyPrice,
                sellPrice,
                resultSet.getInt("trade_quantity"),
                UUID.fromString(resultSet.getString("creator_uuid")),
                Instant.parse(resultSet.getString("created_at")),
                Instant.parse(resultSet.getString("updated_at"))
        );
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }
}
