package vn.tmc.edge.gatestation;

import org.junit.jupiter.api.Test;
import vn.tmc.contracts.AttendancePayload.Direction;
import vn.tmc.contracts.BootstrapResponse;
import vn.tmc.edge.edgesync.domain.BootstrapCache;
import vn.tmc.edge.edgesync.domain.BootstrapCache.Snapshot;
import vn.tmc.edge.gatestation.infrastructure.CachedAccessPolicy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static vn.tmc.edge.gatestation.GateStationFixture.*;

/** Cache chính sách: cửa sổ [start,end), hướng, active, cổng tắt và mất tin cậy đều phải fail closed. */
class CachedAccessPolicyTest {
    private static final Instant START = T0;
    private static final Instant END = T0.plus(Duration.ofHours(8));

    private static Snapshot snapshot(boolean employeeActive, boolean gateEnabled, Direction windowDirection) {
        Instant generated = T0.minus(Duration.ofHours(1));
        Instant trust = generated.plus(Duration.ofHours(48));
        var keys = new BootstrapResponse.KeySet(1, generated, trust, List.of());
        var window = new BootstrapResponse.PolicyWindow(GATE_ID, windowDirection, START, END);
        var policy = new BootstrapResponse.PolicySnapshot(7,
                List.of(new BootstrapResponse.EmployeePolicy(EMPLOYEE_ID, employeeActive, List.of(window))));
        var gate = new BootstrapResponse.Gate(GATE_ID, 1, "North", FACTORY_ID, EDGE_ID, Direction.IN, "north", gateEnabled);
        return new Snapshot(new BootstrapCache.Identity(EDGE_ID, FACTORY_ID), 1, generated, trust, keys, policy,
                List.of(gate), List.of());
    }

    private static CachedAccessPolicy policyOver(Snapshot snapshot) {
        return new CachedAccessPolicy(new BootstrapCache() {
            @Override
            public Optional<Snapshot> snapshot() {
                return Optional.ofNullable(snapshot);
            }

            @Override
            public ApplyResult apply(BootstrapResponse bundle) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ApplyResult applyKeySet(BootstrapResponse.KeySet keySet) {
                throw new UnsupportedOperationException("Read-only policy fixture");
            }
        });
    }

    @Test
    void allowsInsideWindowAndReportsPolicyVersion() {
        var decision = policyOver(snapshot(true, true, Direction.IN))
                .evaluate(EMPLOYEE_ID, GATE_ID, Direction.IN, START.plusSeconds(1));
        assertTrue(decision.allowed());
        assertEquals("OK", decision.reasonCode());
        assertEquals(7, decision.policyVersion());
    }

    @Test
    void windowIsHalfOpen_startInclusiveEndExclusive() {
        var policy = policyOver(snapshot(true, true, Direction.IN));
        assertTrue(policy.evaluate(EMPLOYEE_ID, GATE_ID, Direction.IN, START).allowed());
        assertFalse(policy.evaluate(EMPLOYEE_ID, GATE_ID, Direction.IN, START.minusMillis(1)).allowed());
        assertFalse(policy.evaluate(EMPLOYEE_ID, GATE_ID, Direction.IN, END).allowed());
        assertEquals("OUTSIDE_POLICY", policy.evaluate(EMPLOYEE_ID, GATE_ID, Direction.IN, END).reasonCode());
    }

    @Test
    void directionMustMatchTheWindow() {
        var decision = policyOver(snapshot(true, true, Direction.OUT))
                .evaluate(EMPLOYEE_ID, GATE_ID, Direction.IN, START.plusSeconds(1));
        assertEquals("OUTSIDE_POLICY", decision.reasonCode());
    }

    @Test
    void inactiveEmployeeAndUnknownEmployeeAreOutsidePolicy() {
        var inactive = policyOver(snapshot(false, true, Direction.IN));
        assertEquals("OUTSIDE_POLICY", inactive.evaluate(EMPLOYEE_ID, GATE_ID, Direction.IN, START.plusSeconds(1)).reasonCode());
        var active = policyOver(snapshot(true, true, Direction.IN));
        assertEquals("OUTSIDE_POLICY", active.evaluate(UUID.randomUUID(), GATE_ID, Direction.IN, START.plusSeconds(1)).reasonCode());
    }

    @Test
    void disabledGateIsReportedBeforeEmployeePolicy() {
        var decision = policyOver(snapshot(true, false, Direction.IN))
                .evaluate(EMPLOYEE_ID, GATE_ID, Direction.IN, START.plusSeconds(1));
        assertEquals("GATE_DISABLED", decision.reasonCode());
    }

    @Test
    void unknownGateAndMissingOrExpiredSnapshotFailClosed() {
        var trusted = policyOver(snapshot(true, true, Direction.IN));
        assertEquals("CACHE_UNAVAILABLE", trusted.evaluate(EMPLOYEE_ID, UUID.randomUUID(), Direction.IN, START).reasonCode());
        assertTrue(trusted.find(UUID.randomUUID(), START).isEmpty());

        var empty = policyOver(null);
        assertEquals("CACHE_UNAVAILABLE", empty.evaluate(EMPLOYEE_ID, GATE_ID, Direction.IN, START).reasonCode());
        assertTrue(empty.find(GATE_ID, START).isEmpty());

        Instant afterTrust = T0.plus(Duration.ofHours(60));
        assertEquals("CACHE_UNAVAILABLE", trusted.evaluate(EMPLOYEE_ID, GATE_ID, Direction.IN, afterTrust).reasonCode());
        assertTrue(trusted.find(GATE_ID, afterTrust).isEmpty());
    }

    @Test
    void catalogExposesDirectionAndBinding() {
        var gate = policyOver(snapshot(true, true, Direction.IN)).find(GATE_ID, START).orElseThrow();
        assertEquals(Direction.IN, gate.direction());
        assertEquals(EDGE_ID, gate.edgeId());
        assertEquals(FACTORY_ID, gate.factoryId());
        assertTrue(gate.enabled());
    }
}
