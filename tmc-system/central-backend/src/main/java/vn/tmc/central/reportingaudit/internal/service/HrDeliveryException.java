package vn.tmc.central.reportingaudit.internal.service;

public final class HrDeliveryException extends RuntimeException {
    private final String code;
    private final boolean retryable;
    public HrDeliveryException(String code,boolean retryable,String message){super(message);this.code=code;this.retryable=retryable;}
    public String code(){return code;}
    public boolean retryable(){return retryable;}
}
