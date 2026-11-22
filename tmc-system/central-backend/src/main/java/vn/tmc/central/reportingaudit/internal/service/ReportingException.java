package vn.tmc.central.reportingaudit.internal.service;

public final class ReportingException extends RuntimeException {
    private final String code;
    private final int status;
    public ReportingException(String code, String message, int status) { super(message); this.code = code; this.status = status; }
    public String code() { return code; }
    public int status() { return status; }
    public boolean retryable() { return status>=500||status==429; }
    public static ReportingException unavailable(String name) {
        return new ReportingException("DEPENDENCY_UNAVAILABLE", "Chưa có adapter " + name, 503);
    }
}
