package vn.tmc.edge.gatestation.infrastructure;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import vn.tmc.contracts.RevocationChange;
import vn.tmc.edge.gatestation.api.QrRevocationCachePort;

/**
 * PHỤ TRÁCH: Task 6 (mới). Cache thu hồi in-memory cho JwsVerifier.
 * replaceAll validate từng RevocationChange (constructor của record đã chặn
 * SUB thiếu revokedBefore, JTI/KID có revokedBefore) rồi swap snapshot nguyên
 * tử. isRevoked theo §8: SUB khớp value và tokenIssuedAt <= revokedBefore;
 * JTI/KID khớp value. Thread-safe qua volatile snapshot bất biến.
 */
public final class InMemoryQrRevocationCache implements QrRevocationCachePort {

    private volatile Map<String, RevocationChange> activeById = Map.of();

    @Override
    public boolean isRevoked(String subject, String jti, String kid, Instant tokenIssuedAt) {
        for (RevocationChange change : activeById.values()) {
            switch (change.kind()) {
                case RevocationChange.KIND_SUB -> {
                    if (subject != null && subject.equals(change.value())
                            && tokenIssuedAt != null && change.revokedBefore() != null
                            && !tokenIssuedAt.isAfter(change.revokedBefore())) {
                        return true;
                    }
                }
                case RevocationChange.KIND_JTI -> {
                    if (jti != null && jti.equals(change.value())) {
                        return true;
                    }
                }
                case RevocationChange.KIND_KID -> {
                    if (kid != null && kid.equals(change.value())) {
                        return true;
                    }
                }
                default -> throw new IllegalStateException("kind revocation lạ: " + change.kind());
            }
        }
        return false;
    }

    @Override
    public void replaceAll(List<RevocationChange> changes) {
        Objects.requireNonNull(changes, "changes");
        Map<String, RevocationChange> built = new HashMap<>();
        for (RevocationChange change : changes) {
            Objects.requireNonNull(change, "change");
            if (RevocationChange.OP_UPSERT.equals(change.op())) {
                built.put(change.id(), change);
            } else if (RevocationChange.OP_DELETE.equals(change.op())) {
                built.remove(change.id());
            } else {
                throw new IllegalArgumentException("op revocation không hợp lệ: " + change.op());
            }
        }
        activeById = Map.copyOf(built);
    }

    @Override
    public int size() {
        return activeById.size();
    }
}
