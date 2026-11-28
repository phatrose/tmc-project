package vn.tmc.edge.edgesync.infrastructure;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteException;
import org.sqlite.SQLiteOpenMode;
import vn.tmc.edge.edgesync.api.EventStoreException;
import vn.tmc.edge.edgesync.domain.BootstrapCache.Identity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

import static vn.tmc.edge.edgesync.api.EventStoreException.Code.STORAGE_UNAVAILABLE;
import static vn.tmc.edge.edgesync.api.EventStoreException.Code.REPROVISION_REQUIRED;

/** Opens short-lived connections to a versioned Edge database. */
final class SqliteDatabase {
    private static final String[] MIGRATIONS = {
            "/schema.sql", "/schema-v2.sql", "/schema-v3.sql", "/schema-v4.sql", "/schema-v5.sql"
    };
    private final String jdbcUrl;
    private final Path file;
    private final Identity identity;

    SqliteDatabase(Path file) {
        this(file, null);
    }

    SqliteDatabase(Path file, Identity identity) {
        this.file = Objects.requireNonNull(file).toAbsolutePath();
        this.identity = identity;
        jdbcUrl = "jdbc:sqlite:" + this.file.toUri().toASCIIString();
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            requireWal(statement, "PRAGMA journal_mode=WAL");
            transaction(connection, "BEGIN IMMEDIATE", db -> {
                migrate(db);
                if (identity != null) {
                    SqliteIdentity.verify(db, identity, true);
                }
                return null;
            });
        } catch (SQLException | IOException failure) {
            throw storageFailure(failure);
        }
    }

    private Connection connect() throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        if (identity != null) {
            config.resetOpenMode(SQLiteOpenMode.CREATE);
        }
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
        return transaction("BEGIN IMMEDIATE", operation);
    }

    <T> T read(Write<T> operation) {
        return transaction("BEGIN", operation);
    }

    private <T> T transaction(String begin, Write<T> operation) {
        try (Connection connection = open()) {
            return transaction(connection, begin, db -> {
                if (identity != null) {
                    SqliteIdentity.verify(db, identity, false);
                }
                return operation.run(db);
            });
        } catch (SQLException | IOException failure) {
            throw storageFailure(failure);
        }
    }

    private EventStoreException storageFailure(Exception failure) {
        boolean corrupt = failure instanceof SQLiteException sqlite
                && (sqlite.getResultCode().name().startsWith("SQLITE_CORRUPT")
                    || sqlite.getResultCode().name().equals("SQLITE_NOTADB"));
        var code = identity != null && (Files.notExists(file) || corrupt) ? REPROVISION_REQUIRED : STORAGE_UNAVAILABLE;
        return new EventStoreException(code, failure);
    }

    private static <T> T transaction(Connection connection, String begin, Write<T> operation) throws SQLException, IOException {
        try (Statement transaction = connection.createStatement()) {
            transaction.execute(begin);
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

    private void migrate(Connection connection) throws SQLException, IOException {
        try (Statement statement = connection.createStatement()) {
            int version;
            try (var result = statement.executeQuery("PRAGMA user_version")) {
                result.next();
                version = result.getInt(1);
            }
            if (version == 0 && identity != null) {
                throw new EventStoreException(REPROVISION_REQUIRED);
            }
            if (version < 0 || version > MIGRATIONS.length) {
                throw new SQLException("Unsupported Edge schema version: " + version);
            }
            for (int index = version; index < MIGRATIONS.length; index++) {
                try (var schema = SqliteDatabase.class.getResourceAsStream(MIGRATIONS[index])) {
                    if (schema == null) {
                        throw new IOException("Missing Edge migration " + MIGRATIONS[index]);
                    }
                    // Migration files contain plain DDL without embedded statement terminators.
                    for (String sql : new String(schema.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                        if (!sql.isBlank()) {
                            statement.execute(sql);
                        }
                    }
                }
                statement.execute("PRAGMA user_version=" + (index + 1));
            }
        }
    }
}
