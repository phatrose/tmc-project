package vn.tmc.edge.edgesync.infrastructure;

import vn.tmc.edge.edgesync.api.EventStoreException;
import vn.tmc.edge.edgesync.domain.BootstrapCache.Identity;

import java.sql.Connection;
import java.sql.SQLException;

import static vn.tmc.edge.edgesync.api.EventStoreException.Code.REPROVISION_REQUIRED;

final class SqliteIdentity {
    private SqliteIdentity() {}

    static void verify(Connection db, Identity identity, boolean verifyAllSequences) throws SQLException {
        try (var statement = db.prepareStatement("""
                SELECT factory_id, last_sequence_no,
                    COALESCE((SELECT max(sequence_no) FROM local_events WHERE edge_id = state.edge_id), 0),
                    (SELECT count(*) FROM edge_runtime_state)
                FROM edge_runtime_state state WHERE edge_id = ?
                """)) {
            statement.setString(1, identity.edgeId().toString());
            try (var row = statement.executeQuery()) {
                if (!row.next() || !identity.factoryId().toString().equals(row.getString(1))
                        || row.getLong(2) != row.getLong(3) || row.getLong(4) != 1) {
                    throw new EventStoreException(REPROVISION_REQUIRED);
                }
            }
        }
        if (verifyAllSequences) {
            // V1 keeps the full event history, so count and maximum must both match the durable allocator.
            try (var statement = db.prepareStatement("""
                    SELECT last_sequence_no = (SELECT count(*) FROM local_events) FROM edge_runtime_state
                    """); var row = statement.executeQuery()) {
                if (!row.next() || !row.getBoolean(1)) {
                    throw new EventStoreException(REPROVISION_REQUIRED);
                }
            }
        }
    }
}
