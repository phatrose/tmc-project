package vn.tmc.edge.edgesync.domain;

/** Central's response cannot safely be applied to the batch. */
public final class SyncProtocolException extends RuntimeException {
    public SyncProtocolException(String message) {
        super(message);
    }

    public SyncProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
