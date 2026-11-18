package vn.tmc.edge.edgesync.infrastructure;

import vn.tmc.edge.edgesync.api.LocalEventStorePort;
import vn.tmc.contracts.EventEnvelope;
import vn.tmc.contracts.SyncBatchRequest;
import vn.tmc.contracts.SyncBatchAck;

import java.nio.file.Path;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 7.
 * VAI TRÒ: Adapter / Controller.
 * NGUỒN: main.pdf, §6.1–6.3.
 * TÀI LIỆU PDF: docs/tasks/06-edge-offline.md; docs/REQUIREMENTS.md.
 * TODO[Task 7]: WAL và transaction cho 9 bảng; event bất biến, delivery/batch tách riêng.
 * TODO[Task 7]: Atomic replay reservation/event/sequence, durable batch membership, atomic cache revision.
 * PHỐI HỢP: Task 2, Task 3, Task 6, Task 8.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Hiện thực LocalEventStorePort bằng SQLite tại Edge; schema.sql là nơi bổ sung migration/schema.
 * - Cần làm: Dùng WAL và transaction bền vững; tách event bất biến, delivery, batch membership và cache revision.
 * - Lưu ý bàn giao: Kiểm thử crash giữa các bước, trùng jti và mất ACK; không xóa PENDING/FAILED để giảm dung lượng.
 */
public final class SqliteStore implements LocalEventStorePort {
    private final SqliteDatabase database;

    public SqliteStore(Path databaseFile) {
        database = new SqliteDatabase(databaseFile);
    }

    @Override
    public void append(EventEnvelope event) {
        throw new UnsupportedOperationException("TODO: append");
    }
    @Override
    public void reserveAndAppend(EventEnvelope event, String jti) {
        throw new UnsupportedOperationException("TODO: atomic replay reservation and persistence");
    }
    @Override
    public SyncBatchRequest pendingBatch() {
        throw new UnsupportedOperationException("TODO: durable batch selection");
    }
    @Override
    public void applyAcknowledgements(SyncBatchAck acknowledgement) {
        throw new UnsupportedOperationException("TODO: per-event ACK and cursor transaction");
    }
}
