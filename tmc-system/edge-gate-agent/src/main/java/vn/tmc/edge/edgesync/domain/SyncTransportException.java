package vn.tmc.edge.edgesync.domain;

/** Request-level failure. A status of zero means no HTTP response was received. */
public final class SyncTransportException extends RuntimeException {
    private final int status;
    private final long retryAfterSeconds;

    public SyncTransportException(int status, long retryAfterSeconds) {
        super("Central returned HTTP " + status);
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public SyncTransportException(Throwable cause) {
        super("Central request did not complete", cause);
        this.status = 0;
        this.retryAfterSeconds = 0;
    }

    public int status() {
        return status;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
