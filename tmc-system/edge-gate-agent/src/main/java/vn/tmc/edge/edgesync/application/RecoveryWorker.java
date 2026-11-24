package vn.tmc.edge.edgesync.application;

import java.util.Objects;

/** Runs before scan handling and sync scheduling start. Recovery has no device transport. */
public final class RecoveryWorker {
    private final Runnable recoverStore;

    public RecoveryWorker(Runnable recoverStore) {
        this.recoverStore = Objects.requireNonNull(recoverStore);
    }

    public void recover() {
        recoverStore.run();
    }
}
