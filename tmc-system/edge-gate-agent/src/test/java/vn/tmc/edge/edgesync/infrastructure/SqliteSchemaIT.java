package vn.tmc.edge.edgesync.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;
import vn.tmc.edge.edgesync.api.EventStoreException;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SqliteSchemaIT {
    @TempDir
    Path tempDir;

    @Test
    void freshDatabase_createsRequiredSchemaAndEnablesWal() throws SQLException {
        var database = new SqliteDatabase(tempDir.resolve("edge.db"));
        try (var connection = database.open(); var statement = connection.createStatement()) {
            assertEquals("wal", scalar(connection, "PRAGMA journal_mode"));
            assertEquals("2", scalar(connection, "PRAGMA synchronous")); // FULL
            assertEquals("1", scalar(connection, "PRAGMA user_version"));
            try (var tables = statement.executeQuery("SELECT name FROM sqlite_schema WHERE type = 'table'")) {
                Set<String> names = new HashSet<>();
                while (tables.next()) {
                    names.add(tables.getString(1));
                }
                assertEquals(Set.of("edge_runtime_state", "local_events", "event_delivery",
                        "local_replay_cache", "local_scan_outcomes", "local_gate_commands"), names);
            }
        }
    }

    @Test
    void reopenExistingDatabase_isNonDestructiveAndMigrationIsIdempotent() throws SQLException {
        var file = tempDir.resolve("edge with spaces.db");
        var first = new SqliteDatabase(file);
        try (var connection = first.open(); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO edge_runtime_state (edge_id, factory_id, last_sequence_no) VALUES ('edge', 'factory', 42)");
        }
        var reopened = new SqliteDatabase(file);
        try (var connection = reopened.open()) {
            assertEquals("42", scalar(connection, "SELECT last_sequence_no FROM edge_runtime_state"));
            assertEquals("1", scalar(connection, "PRAGMA user_version"));
        }
    }

    @Test
    void foreignKeys_areEnabledAndEnforced() throws SQLException {
        var database = new SqliteDatabase(tempDir.resolve("edge.db"));
        try (var connection = database.open(); var statement = connection.createStatement()) {
            assertEquals("1", scalar(connection, "PRAGMA foreign_keys"));
            var error = assertThrows(SQLiteException.class, () -> statement.executeUpdate(
                    "INSERT INTO event_delivery (event_id, state) VALUES ('missing', 'PENDING')"));
            assertEquals(SQLiteErrorCode.SQLITE_CONSTRAINT_FOREIGNKEY, error.getResultCode());
        }
    }

    @Test
    void requiredUniquenessConstraints_areEnforced() throws SQLException {
        var database = new SqliteDatabase(tempDir.resolve("edge.db"));
        try (var connection = database.open(); var statement = connection.createStatement()) {
            var replay = """
                    INSERT INTO local_replay_cache (kind, jti, expires_at, direction, gate_id, reserved_at, purge_after)
                    VALUES ('%s', 'token-id', 1000, 'IN', 'gate', 0, 6000)
                    """;
            statement.executeUpdate(replay.formatted("QR"));
            statement.executeUpdate(replay.formatted("ACTION"));
            assertEquals(SQLiteErrorCode.SQLITE_CONSTRAINT_PRIMARYKEY, assertThrows(SQLiteException.class,
                    () -> statement.executeUpdate(replay.formatted("QR"))).getResultCode());

            statement.executeUpdate("INSERT INTO edge_runtime_state (edge_id, factory_id) VALUES ('edge', 'factory')");
            var event = """
                    INSERT INTO local_events (event_id, event_type, schema_version, edge_id, gate_id, factory_id,
                        sequence_no, correlation_id, occurred_at, payload_sha256, payload_text, committed_at)
                    VALUES ('%s', 'test', 1, 'edge', 'gate', 'factory', 1, 'scan', 0, '%s', '{}', 0)
                    """;
            statement.executeUpdate(event.formatted("event-1", "0".repeat(64)));
            assertEquals(SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE, assertThrows(SQLiteException.class,
                    () -> statement.executeUpdate(event.formatted("event-2", "0".repeat(64)))).getResultCode());
        }
    }

    @Test
    void unsupportedVersion_failsWithoutOverwritingIt() throws SQLException {
        var file = tempDir.resolve("edge.db");
        var database = new SqliteDatabase(file);
        try (var connection = database.open(); var statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version=2");
        }
        var error = assertThrows(EventStoreException.class, () -> new SqliteDatabase(file));
        assertEquals(EventStoreException.Code.STORAGE_UNAVAILABLE, error.code());
        try (var connection = database.open()) {
            assertEquals("2", scalar(connection, "PRAGMA user_version"));
        }
    }

    static String scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next(), sql);
            return result.getString(1);
        }
    }
}
