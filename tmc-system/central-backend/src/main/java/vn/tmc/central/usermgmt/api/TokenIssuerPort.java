package vn.tmc.central.usermgmt.api;

import java.util.List;

/**
 * PHỤ TRÁCH: Task 1 / Task 8 — tích hợp với Task 6.
 * Port phát hành token ký bất đối xứng ES256 (Action Grant theo CONTRACT-V1.md §5).
 */
public interface TokenIssuerPort {

    /**
     * Claims chuẩn CONTRACT-V1.md §5 cho Action Grant (aud=tmc-edge-action, TTL 60s).
     */
    record ActionGrantClaims(
            String sub,
            String action,
            String edgeId,
            String gateId,
            String employeeId,
            String reason,
            Role role,
            List<String> scopes
    ) {}

    /**
     * Phát hành token legacy (giữ tương thích ngược).
     */
    String issueToken(String userId, Role role, List<String> scopes);

    /**
     * Phát hành Action Grant ES256 đầy đủ claims binding theo CONTRACT-V1.md §5.
     */
    default String issueActionGrant(ActionGrantClaims claims) {
        return issueToken(claims.sub(), claims.role(), claims.scopes());
    }
}
