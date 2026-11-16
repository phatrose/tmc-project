# Mục PDF 6 — Edge SQLite và Offline Store-and-Forward

Tài liệu tham chiếu theo mục của PDF. Khi khác nhau, áp dụng [DECISIONS.md](../DECISIONS.md). Phân công triển khai xem [TEAM.md](../TEAM.md).

Nguồn: [main.pdf](../reference/main.pdf) phần 6; PDF tr. 82–90.

## Việc cần làm

- [ ] Thiết kế và hiện thực SQLite WAL, cache, queue, batch membership, monotonic sequence và recovery.
- [ ] Store-before-forward: policy/crypto đạt, reserve replay + event + delivery commit rồi mới command.
- [ ] Sync/retry/ACK theo §4–6; tách event bất biến khỏi delivery state và kết quả command.
- [ ] Triển khai bootstrap/revision, connectivity monitor, retention/capacity và mock turnstile; phối hợp Mục PDF 7 về key/cache.

## SQLite: chín bảng theo §6

1. `local_attendance_events`: event/payload/hash bất biến.
2. `event_delivery`: PENDING, ACKNOWLEDGED, FAILED; ack_status/time/failure/retention.
3. `local_sync_batches`: READY, IN_FLIGHT, RETRY_WAIT, COMPLETED; retry metadata.
4. `local_sync_batch_items`: membership và thứ tự ổn định qua restart.
5. `local_replay_cache`: unique jti, reserve cùng transaction event.
6. `cached_revocations`: revision, type/subject/revoked_at và nguồn tin cậy.
7. `trusted_signing_keys`: public key, ACTIVE/VERIFY_ONLY/REVOKED, trust TTL.
8. `cached_access_policy`: quyền tối thiểu theo nhân viên/cổng/hướng/ca, version.
9. `edge_runtime_state`: sequence, revision, bootstrap/contact và contiguous ACK cursor.

## Bất biến và recovery

- Verify/policy đạt → transaction cấp sequence + reserve jti + append event/delivery → commit WAL → commandId mở cổng; lưu lỗi thì không mở.
- Sync chạy nền: tối đa 50 event/lô, FIFO sequenceNo, một lô active; lưu membership trước HTTP.
- Restart IN_FLIGHT → RETRY_WAIT với cùng IDs/hash/payload; không đoán Central chưa commit và không tự mở cổng lại.
- ACK riêng từng event. CONFLICT/REJECTED sang FAILED, giữ để điều tra; không chặn vô hạn PENDING phía sau.
- Timeout/5xx: giữ PENDING, full jitter từ d0=5s tới dmax=5min; 429 theo Retry-After; 401/403 không retry mù.
- Bootstrap dự kiến mỗi 30s, revision chỉ tiến sau transaction cache commit. Không đồng bộ từng token QR xuống Edge.
- Revocation cache cũ nhưng hợp lệ có thể tiếp tục offline theo §7; thiếu/hỏng/không xác thực/revision lùi thì từ chối. Trusted key hết hạn tin cậy thì từ chối QR mới.
- Chỉ dọn bản sao ACKNOWLEDGED theo retention. Mốc 30 ngày/50K event dùng để cảnh báo, không cho phép xóa PENDING/FAILED.

## Vị trí bắt đầu

| File | Vai trò |
|---|---|
| [EdgeApplication.java](../../edge-gate-agent/src/main/java/vn/tmc/edge/EdgeApplication.java) | Bootstrap Edge |
| [SyncWorker.java](../../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/application/SyncWorker.java) | Use case / worker |
| [BootstrapWorker.java](../../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/application/BootstrapWorker.java) | Use case / worker |
| [RecoveryWorker.java](../../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/application/RecoveryWorker.java) | Use case / worker |
| [ConnectivityMonitor.java](../../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/application/ConnectivityMonitor.java) | Use case / worker |
| [SqliteStore.java](../../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/infrastructure/SqliteStore.java) | Adapter / Controller |
| [MockTurnstileAdapter.java](../../edge-gate-agent/src/main/java/vn/tmc/edge/gatestation/infrastructure/MockTurnstileAdapter.java) | Adapter / Controller |

## Bàn giao

- [ ] Ghi contract đầu vào/đầu ra và Task phối hợp trước khi nối module.
- [ ] Cập nhật [yêu cầu liên quan](../REQUIREMENTS.md) và test Mục PDF 8 khi có implementation.
- [ ] Nộp bằng chứng thực tế; không đổi trạng thái chỉ vì có file sườn.

## Checklist bổ sung tại vị trí triển khai

### `edge-gate-agent/src/main/java/vn/tmc/edge/domain`

- [ ] Chốt ProcessGateAccessUseCase, ManualGateOverrideUseCase.
- [ ] Chốt QrVerificationPort, AccessPolicyPort, TurnstileControlPort, LocalEventStorePort, SyncPort và BootstrapPort.
- [ ] Model: event bất biến, delivery state, batch state, credential đã xác minh, policy snapshot và command result riêng.
- [ ] Domain không import Spring/JPA/JDBC/HTTP/JWS library. Adapter phụ thuộc vào port, không đảo chiều.
- [ ] Dùng EventEnvelope chung; không sao chép lại schema riêng hoặc gộp kết quả cổng vào event gốc.
- [ ] LSP: adapter thật/mock giữ semantics timeout/ACK/error; kiểm chứng bằng test Mục PDF 8.
