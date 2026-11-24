package vn.tmc.edge.edgesync.infrastructure;

import vn.tmc.contracts.BootstrapResponse;
import vn.tmc.contracts.BootstrapResponse.*;
import vn.tmc.edge.edgesync.api.EventStoreException;
import vn.tmc.edge.edgesync.domain.BootstrapCache;
import vn.tmc.edge.edgesync.domain.BootstrapProtocolException;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/** Replaces scoped snapshots in one SQLite transaction and verifies their coherence on read. */
public final class SqliteBootstrapCache implements BootstrapCache {
    private final SqliteDatabase database;
    private final Identity identity;
    private final Clock clock;

    SqliteBootstrapCache(SqliteDatabase database, Identity identity, Clock clock) {
        this.database = database;
        this.identity = identity;
        this.clock = clock;
    }

    @Override
    public Optional<Snapshot> snapshot() {
        try {
            return database.read(db -> readSnapshot(db, metadata(db)));
        } catch (EventStoreException unavailable) {
            return Optional.empty();
        }
    }

    @Override
    public ApplyResult apply(BootstrapResponse bundle) {
        Instant now = Instant.ofEpochMilli(clock.millis());
        BootstrapValidation.validate(identity, bundle, now);
        return database.write(db -> {
            var previous = metadata(db);
            if (bundle.revision() < previous.revision()
                    || (previous.generatedAt() != null && bundle.generatedAt().isBefore(previous.generatedAt()))) {
                throw new BootstrapProtocolException("Bootstrap revision or generation time moved backwards");
            }

            var active = new TreeMap<String, RevocationChange>();
            Instant trustedUntil = bundle.trustedUntil();
            if (bundle.mode() == Mode.DELTA) {
                if (previous.revision() != bundle.baseRevision()) {
                    return ApplyResult.FULL_REQUIRED;
                }
                var old = readSnapshot(db, previous);
                if (old.isEmpty() || !old.orElseThrow().trustedAt(now)) {
                    return ApplyResult.FULL_REQUIRED;
                }
                // Only FULL renews the trust horizon. DELTA may shorten it.
                if (previous.trustedUntil().isBefore(trustedUntil)) {
                    trustedUntil = previous.trustedUntil();
                }
                old.orElseThrow().revocations().forEach(entry -> active.put(entry.id().toString(), entry));
            }
            for (var change : bundle.revocations()) {
                if (change.op() == Operation.DELETE) {
                    active.remove(change.id().toString());
                } else {
                    active.put(change.id().toString(), change);
                }
            }
            var snapshot = new Snapshot(identity, bundle.revision(), bundle.generatedAt(), trustedUntil,
                    bundle.keySet(), bundle.policy(), bundle.gates(), List.copyOf(active.values()));
            save(db, snapshot, now);
            return ApplyResult.APPLIED;
        });
    }

    private record Metadata(long revision, Instant generatedAt, Instant trustedUntil, String hash) {}

    private Metadata metadata(Connection db) throws SQLException {
        try (var statement = db.prepareStatement("""
                SELECT factory_id, bootstrap_revision, bootstrap_generated_at, bootstrap_trusted_until,
                    bootstrap_snapshot_hash FROM edge_runtime_state WHERE edge_id = ?
                """)) {
            statement.setString(1, identity.edgeId().toString());
            try (var row = statement.executeQuery()) {
                if (!row.next()) {
                    return new Metadata(0, null, null, null);
                }
                if (!identity.factoryId().toString().equals(row.getString("factory_id"))) {
                    throw new SQLException("Stored Edge factory differs from provisioned identity");
                }
                return new Metadata(row.getLong("bootstrap_revision"),
                        SqliteTime.nullableInstant(row, "bootstrap_generated_at"),
                        SqliteTime.nullableInstant(row, "bootstrap_trusted_until"),
                        row.getString("bootstrap_snapshot_hash"));
            }
        }
    }

    private Optional<Snapshot> readSnapshot(Connection db, Metadata metadata) throws SQLException {
        if (metadata.revision() < 1 || metadata.generatedAt() == null || metadata.trustedUntil() == null || metadata.hash() == null) {
            return Optional.empty();
        }
        try (var statement = db.prepareStatement("""
                SELECT keys.snapshot, policy.snapshot, gates.snapshot
                FROM trusted_signing_keys keys JOIN cached_access_policy policy USING (edge_id)
                JOIN cached_gates gates USING (edge_id) WHERE keys.edge_id = ?
                """)) {
            statement.setString(1, identity.edgeId().toString());
            try (var row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                var snapshot = new Snapshot(identity, metadata.revision(), metadata.generatedAt(), metadata.trustedUntil(),
                        WireJson.read(row.getBytes(1), KeySet.class), WireJson.read(row.getBytes(2), PolicySnapshot.class),
                        WireJson.read(row.getBytes(3), Gates.class).values(), revocations(db));
                return metadata.hash().equals(fingerprint(snapshot)) ? Optional.of(snapshot) : Optional.empty();
            } catch (IOException | IllegalArgumentException | NullPointerException malformed) {
                return Optional.empty();
            }
        }
    }

    private List<RevocationChange> revocations(Connection db) throws SQLException, IOException {
        var revocations = new ArrayList<RevocationChange>();
        try (var statement = db.prepareStatement("SELECT id, entry FROM cached_revocations WHERE edge_id = ? ORDER BY id")) {
            statement.setString(1, identity.edgeId().toString());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    var entry = WireJson.read(rows.getBytes(2), RevocationChange.class);
                    if (!rows.getString(1).equals(entry.id().toString())) {
                        throw new IOException("Cached revocation ID differs from its entry");
                    }
                    revocations.add(entry);
                }
            }
        }
        return revocations;
    }

    private static String fingerprint(Snapshot snapshot) throws IOException {
        return CanonicalPayload.sha256(WireJson.write(snapshot));
    }

    private void save(Connection db, Snapshot snapshot, Instant now) throws SQLException, IOException {
        String edge = identity.edgeId().toString();
        try (var statement = db.prepareStatement("""
                INSERT INTO edge_runtime_state(edge_id, factory_id) VALUES (?, ?) ON CONFLICT (edge_id) DO NOTHING
                """)) {
            statement.setString(1, edge);
            statement.setString(2, identity.factoryId().toString());
            statement.executeUpdate();
        }

        put(db, "trusted_signing_keys", edge, snapshot.keySet());
        put(db, "cached_access_policy", edge, snapshot.policy());
        put(db, "cached_gates", edge, new Gates(snapshot.gates()));
        try (var delete = db.prepareStatement("DELETE FROM cached_revocations WHERE edge_id = ?");
             var insert = db.prepareStatement("INSERT INTO cached_revocations(edge_id, id, entry) VALUES (?, ?, ?)")) {
            delete.setString(1, edge);
            delete.executeUpdate();
            insert.setString(1, edge);
            for (var revocation : snapshot.revocations()) {
                insert.setString(2, revocation.id().toString());
                insert.setBytes(3, WireJson.write(revocation));
                insert.executeUpdate();
            }
        }

        try (var statement = db.prepareStatement("""
                UPDATE edge_runtime_state SET bootstrap_revision = ?, bootstrap_generated_at = ?,
                    bootstrap_trusted_until = ?, bootstrap_snapshot_hash = ?, last_bootstrap_at = ?
                WHERE edge_id = ? AND factory_id = ?
                """)) {
            statement.setLong(1, snapshot.revision());
            statement.setLong(2, snapshot.generatedAt().toEpochMilli());
            statement.setLong(3, snapshot.trustedUntil().toEpochMilli());
            statement.setString(4, fingerprint(snapshot));
            statement.setLong(5, now.toEpochMilli());
            statement.setString(6, edge);
            statement.setString(7, identity.factoryId().toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Bootstrap did not update provisioned runtime state");
            }
        }
    }

    private record Gates(List<Gate> values) {}

    private static void put(Connection db, String table, String edge, Object snapshot) throws SQLException, IOException {
        try (var statement = db.prepareStatement("""
                INSERT INTO %s(edge_id, snapshot) VALUES (?, ?)
                ON CONFLICT (edge_id) DO UPDATE SET snapshot = excluded.snapshot
                """.formatted(table))) {
            statement.setString(1, edge);
            statement.setBytes(2, WireJson.write(snapshot));
            statement.executeUpdate();
        }
    }
}
