package vn.tmc.edge.edgesync.domain;

public final class BootstrapProtocolException extends RuntimeException {
    public BootstrapProtocolException(String message) {
        super(message);
    }

    public BootstrapProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
