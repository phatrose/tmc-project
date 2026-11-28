package vn.tmc.edge.gatestation.domain;

import java.util.Objects;

/**
 * PHỤ TRÁCH: Task 2. Kết quả kiểm tra quyền cục bộ (CONTRACT-V1.md §10):
 * {allowed, reasonCode, policyVersion}. reasonCode thuộc danh mục §5: OK, OUTSIDE_POLICY,
 * GATE_DISABLED, CACHE_UNAVAILABLE.
 */
public record PolicyDecision(boolean allowed, String reasonCode, long policyVersion) {
    public PolicyDecision {
        Objects.requireNonNull(reasonCode, "reasonCode");
    }

    public static PolicyDecision allow(long policyVersion) {
        return new PolicyDecision(true, "OK", policyVersion);
    }

    public static PolicyDecision deny(String reasonCode, long policyVersion) {
        return new PolicyDecision(false, reasonCode, policyVersion);
    }
}
