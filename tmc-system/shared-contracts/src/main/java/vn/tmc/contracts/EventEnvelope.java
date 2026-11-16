package vn.tmc.contracts;

import java.util.UUID;
import java.time.Instant;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: DTO envelope.
 * NGUỒN: main.pdf, §5.2.
 * TÀI LIỆU PDF: docs/tasks/04-api-contracts.md; docs/REQUIREMENTS.md.
 * TODO[Task 3]: Chốt eventId UUIDv7, eventType, schemaVersion, edgeId/gateId/factoryId, sequenceNo, correlationId, occurredAt, payloadSha256 và payload.
 * TODO[Task 3]: EVT-01..04 dùng chung envelope; hash canonical JSON; không PII/JWS thô.
 * PHỐI HỢP: Task 2, Task 4, Task 7.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Envelope chung EVT-01..04: eventId, nguồn thiết bị, sequence, thời gian và payload/hash.
 * - Cần làm: Chốt schemaVersion/wire format/canonical JSON trước khi nối API; giữ nguyên ID và payload qua retry.
 * - Lưu ý bàn giao: Không thêm delivery state, không lưu JWS thô; occurredAt là thời điểm sự kiện, không phải thời điểm sync.
 */
public record EventEnvelope(UUID eventId, String eventType, int schemaVersion, String edgeId, String gateId, String factoryId, long sequenceNo, String correlationId, Instant occurredAt, String payloadSha256, String payload) {
    // payload is the original JSON text; never reserialize it before hashing/retry.
    // TODO[Task 3]: settle canonicalization and wire representation before transport.
}
