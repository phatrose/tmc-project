package vn.tmc.central.reportingaudit.internal.model;

import vn.tmc.central.reportingaudit.api.PayrollBatch;
import java.time.Instant;
import java.util.UUID;

public record OutboxBatch(PayrollBatch view,byte[] payload,String factoryZone,Instant deadline,UUID leaseId,int attempts) {
    public OutboxBatch { payload=payload.clone(); }
    @Override public byte[] payload(){return payload.clone();}
}
