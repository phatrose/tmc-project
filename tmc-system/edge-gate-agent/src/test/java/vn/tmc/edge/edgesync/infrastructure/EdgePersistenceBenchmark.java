package vn.tmc.edge.edgesync.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.tmc.contracts.AttendancePayload;
import vn.tmc.contracts.AttendancePayload.Direction;
import vn.tmc.contracts.BootstrapResponse;
import vn.tmc.contracts.BootstrapResponse.EmployeePolicy;
import vn.tmc.contracts.BootstrapResponse.Gate;
import vn.tmc.contracts.BootstrapResponse.KeyState;
import vn.tmc.contracts.BootstrapResponse.Mode;
import vn.tmc.contracts.BootstrapResponse.PolicySnapshot;
import vn.tmc.contracts.BootstrapResponse.PolicyWindow;
import vn.tmc.edge.edgesync.api.CommandCompletion;
import vn.tmc.edge.edgesync.api.EventDraft;
import vn.tmc.edge.edgesync.api.EventPayload;
import vn.tmc.edge.edgesync.api.EventPayload.CommandStatus;
import vn.tmc.edge.edgesync.api.ReplayReservation;
import vn.tmc.edge.edgesync.api.ScanInputFingerprint;
import vn.tmc.edge.edgesync.application.CachedAccess;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.tmc.edge.edgesync.infrastructure.SqliteBootstrapIT.IDENTITY;
import static vn.tmc.edge.edgesync.infrastructure.SqliteBootstrapIT.KID;
import static vn.tmc.edge.edgesync.infrastructure.SqliteBootstrapIT.full;
import static vn.tmc.edge.edgesync.infrastructure.SqliteEventStoreIT.*;
import static vn.tmc.edge.edgesync.infrastructure.SqliteSchemaIT.scalar;

/**
 * Non-gating local Edge persistence characterization (named *Benchmark so default CI runs exclude it).
 * Measures local cache lookup, SQLite WAL store-before-command, and durable command-result completion
 * with no WAN, no JWS signature verification, and no real Turnstile hardware I/O.
 */
class EdgePersistenceBenchmark {
    private static final int WARMUP_OPS = 20;
    private static final int MEASURED_OPS = 800;
    private static final int TOTAL_OPS = WARMUP_OPS + MEASURED_OPS;

    @TempDir
    Path tempDir;

    @Test
    void characterizeLocalEdgePersistenceLatencyFor800Operations() throws Exception {
        Path dbFile = tempDir.resolve("edge-bench.db");
        Instant baseTime = COMMITTED_AT;
        var store = SqliteStore.provisionNew(dbFile, Clock.fixed(baseTime, ZoneOffset.UTC), IDENTITY);
        var cache = store.bootstrapCache(IDENTITY);
        var seed = full(baseTime);
        var trust = baseTime.plus(Duration.ofHours(48));

        var employees = new ArrayList<EmployeePolicy>(TOTAL_OPS);
        for (int i = 0; i < TOTAL_OPS; i++) {
            UUID employeeId = id(10_000 + i);
            employees.add(new EmployeePolicy(employeeId, true,
                    List.of(new PolicyWindow(GATE_ID, Direction.IN, baseTime.minusSeconds(10), trust))));
        }
        cache.apply(new BootstrapResponse(Mode.FULL, 0, 1, seed.generatedAt(), seed.trustedUntil(),
                FACTORY_ID, EDGE_ID, List.of(), seed.keySet(), new PolicySnapshot(1, employees),
                List.of(new Gate(GATE_ID, 1, "North entrance", FACTORY_ID, EDGE_ID, Direction.IN, "north", true))));
        var access = new CachedAccess(cache, () -> {});

        long[] latenciesNanos = new long[MEASURED_OPS];
        Instant issuedAt = baseTime.minusSeconds(5);
        Instant expiresAt = baseTime.plusSeconds(55);

        for (int i = 0; i < TOTAL_OPS; i++) {
            UUID employeeId = id(10_000 + i);
            UUID scanId = id(20_000 + i);
            UUID jti = id(30_000 + i);
            UUID commandId = id(40_000 + i);
            Instant opTime = baseTime.plusMillis(i);

            long start = System.nanoTime();
            var fingerprint = ScanInputFingerprint.ofQr(GATE_ID, "synthetic-qr." + jti);
            assertTrue(store.findScanOutcome(scanId, fingerprint).isEmpty());
            var keyLookup = access.lookupKey(KID, opTime);
            assertEquals(CachedAccess.Decision.ALLOW, keyLookup.decision());
            assertEquals(KeyState.ACTIVE, keyLookup.key().state());
            var policyDecision = access.evaluate(employeeId, jti, KID, issuedAt, GATE_ID, opTime);
            assertEquals(CachedAccess.Decision.ALLOW, policyDecision.decision());

            var payload = new AttendancePayload(employeeId.toString(), policyDecision.gate().direction(),
                    AttendancePayload.Source.OFFLINE, jti.toString(), KID, issuedAt, commandId.toString(), null, null);
            var draft = new EventDraft(EDGE_ID, GATE_ID, FACTORY_ID, scanId, opTime,
                    new EventPayload.Attendance(payload));
            var reservation = new ReplayReservation(ReplayReservation.Kind.QR, jti, expiresAt, employeeId,
                    policyDecision.gate().direction(), GATE_ID, fingerprint);

            var committed = store.reserveAndAppend(scanId, draft, reservation);
            var command = committed.newCommand().orElseThrow();
            store.recordCommandResult(scanId, new CommandCompletion(
                    command.commandId(), CommandStatus.UNLOCK_ACCEPTED, opTime.plusMillis(10), null));
            long elapsed = System.nanoTime() - start;

            if (i >= WARMUP_OPS) {
                latenciesNanos[i - WARMUP_OPS] = elapsed;
            }
        }

        try (var db = DriverManager.getConnection("jdbc:sqlite:" + dbFile)) {
            int expectedEvents = TOTAL_OPS * 2;
            assertEquals(Integer.toString(expectedEvents), scalar(db, "SELECT count(*) FROM local_events"));
            assertEquals(Integer.toString(expectedEvents), scalar(db, "SELECT last_sequence_no FROM edge_runtime_state"));
            assertEquals(Integer.toString(expectedEvents), scalar(db, "SELECT count(*) FROM event_delivery WHERE state = 'PENDING'"));
            assertEquals(Integer.toString(TOTAL_OPS), scalar(db, "SELECT count(*) FROM local_gate_commands WHERE status = 'UNLOCK_ACCEPTED'"));
        }

        Arrays.sort(latenciesNanos);
        double p50 = millis(latenciesNanos, 0.50);
        double p95 = millis(latenciesNanos, 0.95);
        double p99 = millis(latenciesNanos, 0.99);
        double max = latenciesNanos[latenciesNanos.length - 1] / 1_000_000.0;

        System.out.printf(Locale.ROOT,
                "[LOCAL EDGE PERSISTENCE CHARACTERIZATION] count=%d warmup=%d p50=%.3fms p95=%.3fms p99=%.3fms max=%.3fms "
                        + "env=\"os=%s %s (%s), java=%s, cpus=%d, storage=temp-dir(%s)\"%n",
                MEASURED_OPS, WARMUP_OPS, p50, p95, p99, max,
                System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"),
                System.getProperty("java.version"), Runtime.getRuntime().availableProcessors(), dbFile.getParent());
    }

    private static double millis(long[] sortedNanos, double quantile) {
        int index = Math.max(0, Math.min(sortedNanos.length - 1, (int) Math.ceil(quantile * sortedNanos.length) - 1));
        return sortedNanos[index] / 1_000_000.0;
    }

    private static UUID id(int suffix) {
        return UUID.fromString("00000000-0000-4000-8000-%012d".formatted(suffix));
    }
}
