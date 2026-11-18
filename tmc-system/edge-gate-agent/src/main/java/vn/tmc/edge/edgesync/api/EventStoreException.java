package vn.tmc.edge.edgesync.api;

/** A persistence rejection or failure that the scan adapter can map to a Contract v1 code. */
public final class EventStoreException extends RuntimeException {
    public enum Code {
        STORAGE_UNAVAILABLE, REPLAY, DUPLICATE_SCAN, IDEMPOTENCY_CONFLICT
    }

    private final Code code;

    public EventStoreException(Code code) {
        this(code, null);
    }

    public EventStoreException(Code code, Throwable cause) {
        super(code.name(), cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
