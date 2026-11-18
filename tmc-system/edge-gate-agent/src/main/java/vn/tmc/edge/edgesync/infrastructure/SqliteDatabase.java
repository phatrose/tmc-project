package vn.tmc.edge.edgesync.infrastructure;

import org.sqlite.SQLiteConfig;
import vn.tmc.edge.edgesync.api.EventStoreException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

import static vn.tmc.edge.edgesync.api.EventStoreException.Code.STORAGE_UNAVAILABLE;

/** Opens short-lived connections to a versioned Edge database. */
final class SqliteDatabase {
    private static final int SCHEMA_VERSION = 1;
    private final String jdbcUrl;

    SqliteDatabase(Path file) {
        jdbcUrl = "jdbc:sqlite:" + Objects.requireNonNull(file).toAbsolutePath().toUri().toASCIIString();
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            requireWal(statement, "PRAGMA journal_mode=WAL");
            write(connection, db -> {
                migrate(db);
                return null;
            });
        } catch (SQLException | IOException failure) {
            throw new EventStoreException(STORAGE_UNAVAILABLE, failure);
        }
    }

    private Connection connect() throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setSynchronous(SQLiteConfig.SynchronousMode.FULL);
        config.setBusyTimeout(2_000);
        return config.createConnection(jdbcUrl);
    }

    Connection open() throws SQLException {
        Connection connection = connect();
        try (Statement statement = connection.createStatement()) {
            requireWal(statement, "PRAGMA journal_mode");
        } catch (SQLException failure) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
        return connection;
    }

    @FunctionalInterface
    interface Write<T> {
        T run(Connection connection) throws SQLException, IOException;
    }

    <T> T write(Write<T> operation) {
        try (Connection connection = open()) {
            return write(connection, operation);
        } catch (SQLException | IOException failure) {
            throw new EventStoreException(STORAGE_UNAVAILABLE, failure);
        }
    }

    private static <T> T write(Connection connection, Write<T> operation) throws SQLException, IOException {
        try (Statement transaction = connection.createStatement()) {
            // Reserve the writer before the first read so competing writers see the winner's commit.
            transaction.execute("BEGIN IMMEDIATE");
            try {
                T result = operation.run(connection);
                transaction.execute("COMMIT");
                return result;
            } catch (SQLException | IOException | RuntimeException | Error failure) {
                try {
                    transaction.execute("ROLLBACK");
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        }
    }

    private static void requireWal(Statement statement, String pragma) throws SQLException {
        try (var result = statement.executeQuery(pragma)) {
            if (!result.next() || !"wal".equalsIgnoreCase(result.getString(1))) {
                throw new SQLException("Edge database must use WAL");
            }
        }
    }

    private static void migrate(Connection connection) throws SQLException, IOException {
        try (Statement statement = connection.createStatement()) {
            int version;
            try (var result = statement.executeQuery("PRAGMA user_version")) {
                result.next();
                version = result.getInt(1);
            }
            if (version == SCHEMA_VERSION) {
                return;
            }
            if (version != 0) {
                throw new SQLException("Unsupported Edge schema version: " + version);
            }
            try (var schema = SqliteDatabase.class.getResourceAsStream("/schema.sql")) {
                if (schema == null) {
                    throw new IOException("Missing Edge schema.sql");
                }
                // V1 contains plain DDL without embedded statement terminators.
                for (String sql : new String(schema.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                    if (!sql.isBlank()) {
                        statement.execute(sql);
                    }
                }
            }
            statement.execute("PRAGMA user_version=" + SCHEMA_VERSION);
        }
    }
}
