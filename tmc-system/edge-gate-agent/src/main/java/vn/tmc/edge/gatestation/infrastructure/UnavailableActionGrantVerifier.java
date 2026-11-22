package vn.tmc.edge.gatestation.infrastructure;

import vn.tmc.edge.gatestation.domain.ActionGrantVerificationPort;
import vn.tmc.edge.gatestation.domain.GateStationException;
import vn.tmc.edge.gatestation.domain.GateStationException.Kind;

import java.time.Instant;

/**
 * PHỤ TRÁCH: Task 2. Placeholder fail-closed cho tới khi Task 6 cấp adapter ES256 thật cho action grant.
 * Tuyệt đối không "cho qua" khi chưa có verifier: mọi override trả 503 DEPENDENCY_UNAVAILABLE. Quét QR tự động không
 * phụ thuộc lớp này nên vẫn hoạt động bình thường.
 */
public final class UnavailableActionGrantVerifier implements ActionGrantVerificationPort {
    @Override
    public VerifiedGrant verify(String grant, Instant now) {
        throw new GateStationException(Kind.DEPENDENCY_UNAVAILABLE, "Chưa có bộ xác minh action grant; không thể mở thủ công");
    }
}
