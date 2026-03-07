package it.patric.cittaexp.persistence.port;

import java.sql.Connection;
import java.sql.SQLException;

public interface CityTxPort {

    <T> T withTransaction(SqlTransaction<T> transaction);

    @FunctionalInterface
    interface SqlTransaction<T> {
        T execute(Connection connection) throws SQLException;
    }
}
