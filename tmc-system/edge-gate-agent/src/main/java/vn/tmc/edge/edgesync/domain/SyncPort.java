package vn.tmc.edge.edgesync.domain;

import vn.tmc.contracts.SyncBatchAck;
import vn.tmc.contracts.SyncBatchRequest;

/** Transport only. Request failures are reported separately from per-event ACKs. */
@FunctionalInterface
public interface SyncPort {
    SyncBatchAck send(SyncBatchRequest batch);
}
