package vn.tmc.edge.edgesync.application;

import vn.tmc.edge.edgesync.domain.SyncJournal;
import vn.tmc.edge.edgesync.domain.SyncJournal.Schedule;
import vn.tmc.edge.edgesync.domain.SyncPort;
import vn.tmc.edge.edgesync.domain.SyncProtocolException;
import vn.tmc.edge.edgesync.domain.SyncTransportException;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** One scheduler tick. Retry deadlines and operator stops survive process restart. */
public final class SyncWorker {
    public enum Result { IDLE, APPLIED, RETRY_WAIT, STOPPED }

    private final UUID edgeId;
    private final SyncJournal journal;
    private final SyncPort transport;
    private final Clock clock;
    private final RandomGenerator random;

    public SyncWorker(UUID edgeId, SyncJournal journal, SyncPort transport, Clock clock, RandomGenerator random) {
        this.edgeId = Objects.requireNonNull(edgeId);
        this.journal = Objects.requireNonNull(journal);
        this.transport = Objects.requireNonNull(transport);
        this.clock = Objects.requireNonNull(clock);
        this.random = Objects.requireNonNull(random);
    }

    public Result runOnce() {
        var attempt = journal.claim(edgeId);
        if (attempt == null) {
            return journal.schedule(edgeId).stopReason() == null ? Result.IDLE : Result.STOPPED;
        }

        try {
            journal.complete(attempt, transport.send(attempt.request()));
            return Result.APPLIED;
        } catch (SyncProtocolException invalid) {
            return stop(attempt, "SYNC_PROTOCOL_ERROR");
        } catch (SyncTransportException failed) {
            return switch (failed.status()) {
                case 0, 500, 503 -> retry(attempt, jitterMillis(attempt.number()));
                case 429 -> retryAfter(attempt, failed.retryAfterSeconds());
                default -> stop(attempt, "HTTP_" + failed.status());
            };
        }
    }

    private Result retryAfter(SyncJournal.Attempt attempt, long seconds) {
        if (seconds < 0) {
            return stop(attempt, "INVALID_RETRY_AFTER");
        }
        try {
            return retry(attempt, Math.multiplyExact(seconds, 1000));
        } catch (ArithmeticException overflow) {
            return stop(attempt, "INVALID_RETRY_AFTER");
        }
    }

    private Result retry(SyncJournal.Attempt attempt, long delayMillis) {
        var retryAt = Instant.ofEpochMilli(Math.addExact(clock.millis(), delayMillis));
        journal.defer(attempt, new Schedule(retryAt, null));
        return Result.RETRY_WAIT;
    }

    private Result stop(SyncJournal.Attempt attempt, String reason) {
        journal.defer(attempt, new Schedule(null, reason));
        return Result.STOPPED;
    }

    private long jitterMillis(int attempt) {
        long ceiling = Math.min(300_000L, 5_000L << Math.min(attempt, 6));
        return random.nextLong(ceiling + 1);
    }
}
