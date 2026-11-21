package vn.tmc.central.attendanceprocessing.internal.repository;

import vn.tmc.central.attendanceprocessing.internal.model.SyncConflict;

import java.util.List;

/**
 * PHỤ TRÁCH: Task 3.
 * Port lưu trữ xung đột đồng bộ và cursor sequence liên tục.
 */
public interface SyncConflictRepository {
    void recordConflict(SyncConflict conflict);
    List<SyncConflict> findConflicts();
    long getLastContiguousSequenceNo(String edgeId);
    void updateLastContiguousSequenceNo(String edgeId, long sequenceNo);
}
