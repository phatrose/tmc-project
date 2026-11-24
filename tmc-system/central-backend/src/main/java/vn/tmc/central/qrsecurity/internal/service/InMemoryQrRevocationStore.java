package vn.tmc.central.qrsecurity.internal.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import vn.tmc.contracts.RevocationChange;

/**
 * PHỤ TRÁCH: Task 6 (mới). Fixture in-memory cho QrRevocationStore, dùng trong
 * sườn và unit test. Adapter production thay bằng bảng PostgreSQL (có lịch sử,
 * append-only). Thread-safe bằng synchronized vì gọi từ request threads.
 * DELETE theo id chỉ xóa khỏi tập active; lịch sử giữ nguyên để đối soát (§8).
 */
public final class InMemoryQrRevocationStore implements QrRevocationStore {

    private final Map<String, RevocationChange> activeById = new LinkedHashMap<>();
    private final List<RevocationChange> history = new ArrayList<>();
    private long counter;

    @Override
    public synchronized void append(RevocationChange change) {
        Objects.requireNonNull(change, "change");
        if (RevocationChange.OP_UPSERT.equals(change.op())) {
            activeById.put(change.id(), change);
        } else if (RevocationChange.OP_DELETE.equals(change.op())) {
            activeById.remove(change.id());
        } else {
            throw new IllegalArgumentException("op không hợp lệ: " + change.op());
        }
        history.add(change);
        counter++;
    }

    @Override
    public synchronized List<RevocationChange> activeChanges() {
        return List.copyOf(activeById.values());
    }

    @Override
    public synchronized long count() {
        return counter;
    }
}
