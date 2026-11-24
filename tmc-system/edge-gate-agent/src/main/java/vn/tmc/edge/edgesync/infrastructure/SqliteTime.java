package vn.tmc.edge.edgesync.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

final class SqliteTime {
    private SqliteTime() {}

    static Instant nullableInstant(ResultSet row, String column) throws SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? null : Instant.ofEpochMilli(value);
    }
}
