package vn.tmc.central.reportingaudit.internal.model;

public record ReportFile(byte[] bytes, String mediaType, String extension) {
    public ReportFile { bytes = bytes.clone(); }
    @Override public byte[] bytes() { return bytes.clone(); }
}
