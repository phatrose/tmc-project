package vn.tmc.edge.gatestation.application;

import vn.tmc.edge.edgesync.api.EventPayload.CommandStatus;
import vn.tmc.edge.edgesync.api.ScanInputFingerprint;
import vn.tmc.edge.gatestation.domain.CommandResult;
import vn.tmc.edge.gatestation.domain.GateStationException;
import vn.tmc.edge.gatestation.domain.GateStationException.Kind;
import vn.tmc.edge.gatestation.domain.KioskContext;
import vn.tmc.edge.gatestation.domain.ScanResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 2.
 * Bộ nhớ trong tiến trình (có giới hạn kích thước và thời hạn 48 giờ, đúng mốc dedup của contract) cho hai việc mà
 * LocalEventStorePort của Task 7 hiện chưa phục vụ:
 * 1. Retry cùng scanId của một lượt DENY (store chỉ lưu outcome của ALLOW/EMERGENCY).
 * 2. Trạng thái command cuối cùng (UNLOCK_ACCEPTED/FAILED/TIMEOUT): store chỉ giữ NOT_SENT cho tới khi recovery
 *    đổi thành TIMEOUT, vì chưa có method ghi kết quả command.
 * Mất khi khởi động lại: sau restart, retry DENY được đánh giá lại và command chưa rõ hiển thị TIMEOUT (an toàn,
 * không tự mở lại). Hai khoảng hở này đã được ghi vào docs/tasks/handover-task2.md để Task 7 bổ sung port.
 * Thread-safe bằng khóa đơn giản; số thao tác mỗi lượt quét rất nhỏ nên không cần cấu trúc lock-free.
 */
public final class RecentScanResults {
    static final Duration RETENTION = Duration.ofHours(48);
    static final int MAX_ENTRIES = 20_000;

    private record Entry(UUID gateId, ScanInputFingerprint fingerprint, ScanResult result, Instant storedAt) {}

    private record Command(CommandResult result, Instant storedAt) {}

    private final Clock clock;
    private final Map<UUID, Entry> scans = boundedAccessOrderMap();
    private final Map<UUID, Command> commands = boundedAccessOrderMap();

    public RecentScanResults(Clock clock) {
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    private static <V> Map<UUID, V> boundedAccessOrderMap() {
        return new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, V> eldest) {
                return size() > MAX_ENTRIES;
            }
        };
    }

    /** Cùng scanId + cùng input → kết quả cũ; khác input → 409 IDEMPOTENCY_CONFLICT. */
    public synchronized Optional<ScanResult> find(UUID scanId, ScanInputFingerprint fingerprint) {
        purgeExpired();
        Entry entry = scans.get(scanId);
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.fingerprint().equals(fingerprint)) {
            throw new GateStationException(Kind.IDEMPOTENCY_CONFLICT, "scanId đã được dùng với nội dung khác");
        }
        return Optional.of(withKnownCommandStatus(entry.result()));
    }

    /** GET /scans/{scanId}: chỉ trả khi cổng thuộc phạm vi của kiosk đang hỏi. */
    public synchronized Optional<ScanResult> findForKiosk(UUID scanId, KioskContext kiosk) {
        purgeExpired();
        Entry entry = scans.get(scanId);
        if (entry == null || !kiosk.mayOperate(entry.gateId())) {
            return Optional.empty();
        }
        return Optional.of(withKnownCommandStatus(entry.result()));
    }

    public synchronized void remember(UUID scanId, UUID gateId, ScanInputFingerprint fingerprint, ScanResult result) {
        scans.put(scanId, new Entry(gateId, fingerprint, result, clock.instant()));
    }

    public synchronized void rememberCommand(CommandResult result) {
        commands.put(result.commandId(), new Command(result, clock.instant()));
    }

    /** Bổ sung command status đã biết cho outcome đọc từ store (đang NOT_SENT). */
    public synchronized ScanResult withKnownCommandStatus(ScanResult result) {
        if (result.commandId() == null || result.commandStatus() != CommandStatus.NOT_SENT) {
            return result;
        }
        Command known = commands.get(result.commandId());
        return known == null ? result : result.withCommandStatus(known.result().status());
    }

    private void purgeExpired() {
        Instant cutoff = clock.instant().minus(RETENTION);
        scans.values().removeIf(entry -> entry.storedAt().isBefore(cutoff));
        commands.values().removeIf(entry -> entry.storedAt().isBefore(cutoff));
    }
}
