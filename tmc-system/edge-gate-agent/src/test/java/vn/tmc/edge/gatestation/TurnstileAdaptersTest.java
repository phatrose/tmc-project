package vn.tmc.edge.gatestation;

import org.junit.jupiter.api.Test;
import vn.tmc.edge.edgesync.api.EventPayload.CommandStatus;
import vn.tmc.edge.gatestation.GateStationFixture.TestClock;
import vn.tmc.edge.gatestation.domain.CommandResult;
import vn.tmc.edge.gatestation.infrastructure.MockTurnstileAdapter;
import vn.tmc.edge.gatestation.infrastructure.MockTurnstileAdapter.Behavior;
import vn.tmc.edge.gatestation.infrastructure.TimeBoundedTurnstile;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static vn.tmc.edge.gatestation.GateStationFixture.GATE_ID;
import static vn.tmc.edge.gatestation.GateStationFixture.T0;

class TurnstileAdaptersTest {
    private final TestClock clock = new TestClock(T0);

    @Test
    void mock_acceptsByDefaultAndCountsPhysicalUnlocks() {
        var mock = new MockTurnstileAdapter(clock);
        CommandResult result = mock.unlock(UUID.randomUUID(), GATE_ID);
        assertEquals(CommandStatus.UNLOCK_ACCEPTED, result.status());
        assertNull(result.code());
        assertEquals(1, mock.physicalUnlockCount(GATE_ID));
    }

    @Test
    void mock_sameCommandIdReturnsSameResultAndNeverUnlocksTwice() {
        var mock = new MockTurnstileAdapter(clock);
        UUID commandId = UUID.randomUUID();
        var first = mock.unlock(commandId, GATE_ID);
        clock.advance(Duration.ofHours(47));
        var again = mock.unlock(commandId, GATE_ID);
        assertEquals(first, again);
        assertEquals(1, mock.physicalUnlockCount(GATE_ID));
    }

    @Test
    void mock_forgetsCommandsAfterDedupRetention() {
        var mock = new MockTurnstileAdapter(clock);
        UUID commandId = UUID.randomUUID();
        mock.unlock(commandId, GATE_ID);
        clock.advance(Duration.ofHours(49));
        mock.unlock(commandId, GATE_ID);
        assertEquals(2, mock.physicalUnlockCount(GATE_ID));
    }

    @Test
    void mock_scriptsFailureAndTimeoutThenFallsBackToDefault() {
        var mock = new MockTurnstileAdapter(clock).script(GATE_ID, Behavior.FAIL, Behavior.TIMEOUT);
        var failed = mock.unlock(UUID.randomUUID(), GATE_ID);
        var timeout = mock.unlock(UUID.randomUUID(), GATE_ID);
        var accepted = mock.unlock(UUID.randomUUID(), GATE_ID);
        assertEquals(CommandStatus.FAILED, failed.status());
        assertEquals("DEVICE_REJECTED", failed.code());
        assertEquals(CommandStatus.TIMEOUT, timeout.status());
        assertEquals(CommandStatus.UNLOCK_ACCEPTED, accepted.status());
        assertEquals(1, mock.physicalUnlockCount(GATE_ID), "chỉ ACCEPT mới là lần mở thật");
    }

    @Test
    void mock_scriptsArePerGate() {
        var other = UUID.randomUUID();
        var mock = new MockTurnstileAdapter(clock).script(other, Behavior.FAIL);
        assertEquals(CommandStatus.UNLOCK_ACCEPTED, mock.unlock(UUID.randomUUID(), GATE_ID).status());
    }

    @Test
    void timeBounded_passesThroughFastResults() {
        try (var bounded = new TimeBoundedTurnstile(new MockTurnstileAdapter(clock), Duration.ofSeconds(1), clock)) {
            assertEquals(CommandStatus.UNLOCK_ACCEPTED, bounded.unlock(UUID.randomUUID(), GATE_ID).status());
        }
    }

    @Test
    void timeBounded_turnsSlowDeviceIntoTimeoutInsteadOfWaiting() {
        var slow = new MockTurnstileAdapter(clock, Duration.ofSeconds(5));
        try (var bounded = new TimeBoundedTurnstile(slow, Duration.ofMillis(50), clock)) {
            long started = System.nanoTime();
            var result = bounded.unlock(UUID.randomUUID(), GATE_ID);
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            assertEquals(CommandStatus.TIMEOUT, result.status());
            assertEquals("ADAPTER_TIMEOUT", result.code());
            assertTrue(elapsedMillis < 1_000, "scan không được chờ hết độ trễ của thiết bị: " + elapsedMillis + " ms");
        }
    }

    @Test
    void timeBounded_turnsAdapterExceptionIntoFailed() {
        try (var bounded = new TimeBoundedTurnstile((commandId, gateId) -> { throw new IllegalStateException("bus"); },
                Duration.ofSeconds(1), clock)) {
            var result = bounded.unlock(UUID.randomUUID(), GATE_ID);
            assertEquals(CommandStatus.FAILED, result.status());
            assertEquals("ADAPTER_ERROR", result.code());
        }
    }

    @Test
    void timeBounded_rejectsNonPositiveTimeout() {
        assertThrows(IllegalArgumentException.class,
                () -> new TimeBoundedTurnstile(new MockTurnstileAdapter(clock), Duration.ZERO, clock));
    }

    @Test
    void commandResult_rejectsNotSent() {
        assertThrows(IllegalArgumentException.class,
                () -> new CommandResult(UUID.randomUUID(), CommandStatus.NOT_SENT, T0, null));
    }
}
