package vn.tmc.central.qrsecurity.internal.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.tmc.central.qrsecurity.api.QrCredentialView;
import vn.tmc.central.qrsecurity.api.QrIssuer;
import vn.tmc.central.qrsecurity.api.QrRevocationQuery;
import vn.tmc.central.qrsecurity.api.QrSecurityException;
import vn.tmc.central.qrsecurity.internal.key.QrSigningKeyHandle;
import vn.tmc.central.qrsecurity.internal.key.QrSigningKeyProvider;
import vn.tmc.central.qrsecurity.internal.model.QrCredential;
import vn.tmc.central.qrsecurity.internal.repository.QrCredentialRepository;
import vn.tmc.central.usermgmt.api.ActorContext;
import vn.tmc.central.usermgmt.api.EmployeeManagement;
import vn.tmc.central.usermgmt.api.EmployeeView;
import vn.tmc.contracts.JwsCompact;
import vn.tmc.contracts.JwsHeader;
import vn.tmc.contracts.QrClaims;
import vn.tmc.contracts.QrKeySet;
import vn.tmc.contracts.QrKeySetEntry;
import vn.tmc.contracts.QrTokenResponse;
import vn.tmc.contracts.RevocationChange;

/**
 * PHỤ TRÁCH: Task 6. Hợp đồng phát hành/lifecycle credential QR và ký JWS.
 * NGUỒN: CONTRACT-V1.md §3 (quyền), §4 (bảng API Task 6), §8 (KeySet/revocation),
 * §10 (QrIssuer.issue); docs/tasks/07-qr-security.md; FR-UQ-02..05, FR-QR-01/02/05.
 *
 * Quy tắc hợp đồng chính đã hiện thực:
 * - issue(ActorContext): employeeId lấy từ context tin cậy, KHÔNG nhận employeeId
 *   client gửi (GET /me/qr-token không có employeeId, §4). Nhân viên phải đang
 *   làm việc và có credential ACTIVE (FR-UQ-02); mỗi lần cấp sinh jti UUIDv4 mới.
 * - JWS: header {alg:ES256, kid}; payload {sub, iss:"tmc-central", aud:"tmc-gate",
 *   iat, nbf=iat, exp=iat+60, jti}; NumericDate giây UTC; TTL 60 cố định trong v1.
 * - Reissue phát iat SAU cutoff của SUB revocation (chờ sang giây tiếp nếu cần).
 * - Revoke/Reissue phát RevocationChange kind=SUB (SUB từ chối token có
 *   tokenIssuedAt<=revokedBefore; JTI/KID do khẩn cấp thêm sau).
 * - Auditor bị cấm cấp/thu hồi QR dù có role khác (§3); mutation cần
 *   permission credential.manage và scope nhà máy, không chỉ tên role.
 * - keySet(): shape QrKeySet §8 (x/y base64url, không có tham số riêng d),
 *   trustedUntil = generatedAt + 48h cho FULL.
 *
 * Còn lại cho adapter/wiring: private key thật từ KMS/HSM (QrSigningKeyProvider),
 * transaction JPA của repository, log/audit không chứa token (AuditPort Task 4).
 * PHỐI HỢP: Task 1 (ActorContext/EmployeeManagement), Task 4 (AuditPort),
 * Task 5 (rotation scheduler), Task 7 (bootstrap tiêu thụ QrRevocationQuery),
 * Task 8 (wiring + mapping QrSecurityException → ApiError).
 */
public final class QrCredentialService implements QrIssuer, QrRevocationQuery {

    /** Permission chuẩn §3 cho lifecycle credential. */
    public static final String PERMISSION_MANAGE_CREDENTIAL = "credential.manage";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final QrCredentialRepository repository;
    private final EmployeeManagement employeeManagement;
    private final QrSigningKeyProvider keyProvider;
    private final QrRevocationStore revocationStore;
    private final Clock clock;

    public QrCredentialService(QrCredentialRepository repository,
                               EmployeeManagement employeeManagement,
                               QrSigningKeyProvider keyProvider,
                               QrRevocationStore revocationStore,
                               Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.employeeManagement = Objects.requireNonNull(employeeManagement, "employeeManagement");
        this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider");
        this.revocationStore = Objects.requireNonNull(revocationStore, "revocationStore");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // ---------- QrIssuer: GET /api/v1/me/qr-token ----------

    @Override
    public QrTokenResponse issue(ActorContext actor) {
        if (actor == null) {
            throw QrSecurityException.unauthenticated("Cần phiên hợp lệ (Bearer) để lấy QR");
        }
        String employeeId = actor.employeeId();
        if (employeeId == null || employeeId.isBlank()) {
            throw QrSecurityException.invalidState("Tài khoản chưa gắn nhân viên nên không có QR cá nhân");
        }
        EmployeeView employee = employeeManagement.findEmployee(employeeId);
        if (employee == null) {
            throw QrSecurityException.notFound("Không tìm thấy nhân viên gắn với phiên hiện tại");
        }
        if (!employee.active()) {
            throw QrSecurityException.invalidState("Nhân viên không còn làm việc nên không cấp được QR");
        }
        repository.findActiveByEmployeeId(employeeId)
                .orElseThrow(() -> QrSecurityException.invalidState(
                        "Chưa có credential QR ACTIVE; HR cần cấp credential trước (FR-UQ-02)"));
        return signToken(employeeId);
    }

    /**
     * Ký token mới: jti UUIDv4 mới mỗi lần gọi; iat được đẩy sau cutoff SUB
     * revocation gần nhất của nhân viên để token mới không bị cache Edge từ chối.
     */
    private QrTokenResponse signToken(String employeeId) {
        Instant now = clock.instant();
        long iat = now.getEpochSecond();
        Instant cutoff = latestSubCutoff(employeeId);
        if (cutoff != null && iat <= cutoff.getEpochSecond()) {
            iat = cutoff.getEpochSecond() + 1;
        }
        long exp = iat + QrClaims.DEFAULT_TTL_SECONDS;
        String jti = UUID.randomUUID().toString();
        QrSigningKeyHandle handle = keyProvider.activeSigningKey();
        try {
            String headerB64 = JwsCompact.encodeBase64Url(
                    JSON.writeValueAsBytes(new JwsHeader(JwsHeader.ALGORITHM, handle.kid())));
            String payloadB64 = JwsCompact.encodeBase64Url(JSON.writeValueAsBytes(new QrClaims(
                    employeeId, QrClaims.ISSUER, QrClaims.AUDIENCE, iat, iat, exp, jti)));
            byte[] signature = JwsCompact.signEs256(
                    JwsCompact.signingInput(headerB64, payloadB64), handle.privateKey());
            String token = headerB64 + "." + payloadB64 + "." + JwsCompact.encodeBase64Url(signature);
            return new QrTokenResponse(token, Instant.ofEpochSecond(exp));
        } catch (JsonProcessingException e) {
            throw new QrSecurityException("INTERNAL_ERROR", 500, true, "Serialize claims QR thất bại");
        }
    }

    private Instant latestSubCutoff(String employeeId) {
        return revocationStore.activeChanges().stream()
                .filter(change -> RevocationChange.KIND_SUB.equals(change.kind()))
                .filter(change -> employeeId.equals(change.value()))
                .map(RevocationChange::revokedBefore)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
    }

    // ---------- lifecycle credential: POST /employees/{id}/qr-credential[...] ----------

    /** POST /api/v1/employees/{id}/qr-credential → 201; đã ACTIVE trả 409. */
    public QrCredentialView grantCredential(ActorContext actor, String employeeId, String reason) {
        requireManagePermission(actor, "cấp credential QR");
        validateReason(reason);
        String id = requireUuid(employeeId, "employeeId");
        EmployeeView employee = requireEmployee(id, true);
        requireScope(actor, employee);
        if (repository.findActiveByEmployeeId(id).isPresent()) {
            throw QrSecurityException.invalidState("Nhân viên đã có credential QR ACTIVE");
        }
        QrCredential created = new QrCredential(newId(), id, QrCredential.State.ACTIVE, clock.instant(), null);
        repository.save(created);
        return created.view();
    }

    /** POST /api/v1/employees/{id}/qr-credential/revoke → 200 Credential REVOKED. */
    public QrCredentialView revokeCredential(ActorContext actor, String employeeId, String reason) {
        requireManagePermission(actor, "thu hồi credential QR");
        validateReason(reason);
        String id = requireUuid(employeeId, "employeeId");
        EmployeeView employee = requireEmployee(id, false); // thu hồi cho phép cả nhân viên đã ngừng làm việc
        requireScope(actor, employee);
        QrCredential active = repository.findActiveByEmployeeId(id)
                .orElseThrow(() -> QrSecurityException.invalidState("Nhân viên không có credential ACTIVE để thu hồi"));
        Instant now = clock.instant();
        QrCredential revoked = new QrCredential(active.id(), id, QrCredential.State.REVOKED, active.createdAt(), now);
        repository.save(revoked);
        appendSubRevocation(id, now);
        return revoked.view();
    }

    /**
     * POST /api/v1/employees/{id}/qr-credential/reissue → 201 credential mới;
     * thu hồi cũ nguyên tử (repository.replaceActive cùng transaction).
     */
    public QrCredentialView reissueCredential(ActorContext actor, String employeeId, String reason) {
        requireManagePermission(actor, "cấp lại credential QR");
        validateReason(reason);
        String id = requireUuid(employeeId, "employeeId");
        EmployeeView employee = requireEmployee(id, false);
        requireScope(actor, employee);
        Instant now = clock.instant();
        QrCredential created = new QrCredential(newId(), id, QrCredential.State.ACTIVE, now, null);
        Optional<QrCredential> old = repository.findActiveByEmployeeId(id);
        if (old.isPresent()) {
            QrCredential revoked = new QrCredential(old.get().id(), id, QrCredential.State.REVOKED,
                    old.get().createdAt(), now);
            repository.replaceActive(revoked, created);
        } else {
            repository.save(created);
        }
        appendSubRevocation(id, now);
        return created.view();
    }

    /** SUB: Edge từ chối token có tokenIssuedAt <= revokedBefore (§8). */
    private void appendSubRevocation(String employeeId, Instant cutoff) {
        Instant truncated = cutoff.truncatedTo(ChronoUnit.SECONDS);
        revocationStore.append(new RevocationChange(newId(), RevocationChange.OP_UPSERT,
                RevocationChange.KIND_SUB, employeeId, truncated, truncated));
    }

    // ---------- GET /api/v1/qr/keys/refresh (mTLS Edge) ----------

    /**
     * KeySet cho Edge refresh khóa; xác thực mTLS + binding edgeId/factoryId
     * thuộc tầng adapter (Task 8), module chỉ nhận identity đã tin cậy. Edge
     * KHÔNG tải khóa từ URL trong token (§4).
     */
    public QrKeySet refreshKeys(String edgeId, String factoryId) {
        if (edgeId == null || edgeId.isBlank() || factoryId == null || factoryId.isBlank()) {
            throw QrSecurityException.deviceBindingMismatch(
                    "GET /qr/keys/refresh yêu cầu mTLS certificate gắn edgeId/factoryId");
        }
        requireUuid(edgeId, "edgeId");
        requireUuid(factoryId, "factoryId");
        return keySet();
    }

    public QrKeySet keySet() {
        Instant generatedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        List<QrKeySetEntry> entries = new ArrayList<>();
        for (QrSigningKeyHandle handle : keyProvider.allKeys()) {
            String x = JwsCompact.encodeBase64Url(JwsCompact.extractX(handle.publicKey()));
            String y = JwsCompact.encodeBase64Url(JwsCompact.extractY(handle.publicKey()));
            entries.add(new QrKeySetEntry(handle.kid(), JwsHeader.KEY_TYPE, JwsHeader.CURVE,
                    JwsHeader.ALGORITHM, JwsHeader.USE, x, y, handle.state(),
                    handle.notBefore(), handle.notAfter()));
        }
        Instant trustedUntil = generatedAt.plus(Duration.ofHours(QrKeySet.TRUST_WINDOW_HOURS));
        return new QrKeySet(keyProvider.keySetVersion(), generatedAt, trustedUntil, entries);
    }

    // ---------- QrRevocationQuery cho Task 7 ----------

    @Override
    public List<RevocationChange> activeRevocations() {
        return revocationStore.activeChanges();
    }

    @Override
    public long revision() {
        return revocationStore.count();
    }

    // ---------- kiểm tra quyền và helpers ----------

    private void requireManagePermission(ActorContext actor, String action) {
        if (actor == null) {
            throw QrSecurityException.unauthenticated("Cần phiên hợp lệ (Bearer) để " + action);
        }
        if (actor.isAuditor()) {
            throw QrSecurityException.forbidden("Auditor bị cấm " + action + " (CONTRACT-V1.md §3)");
        }
        if (!actor.hasPermission(PERMISSION_MANAGE_CREDENTIAL)) {
            throw QrSecurityException.forbidden(action + " cần permission credential.manage");
        }
    }

    /** Scope: GLOBAL đi qua; còn lại nhân viên phải thuộc nhà máy được gán. */
    private void requireScope(ActorContext actor, EmployeeView employee) {
        if (ActorContext.SCOPE_GLOBAL.equals(actor.scope())) {
            return;
        }
        if (employee == null || !actor.factoryIds().contains(employee.factoryId())) {
            throw QrSecurityException.scopeMismatch("Nhân viên nằm ngoài phạm vi nhà máy được gán cho bạn");
        }
    }

    private EmployeeView requireEmployee(String employeeId, boolean requireActive) {
        EmployeeView employee = employeeManagement.findEmployee(employeeId);
        if (employee == null) {
            throw QrSecurityException.notFound("Không tìm thấy nhân viên " + employeeId);
        }
        if (requireActive && !employee.active()) {
            throw QrSecurityException.invalidState("Nhân viên không còn làm việc");
        }
        return employee;
    }

    private void validateReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw QrSecurityException.validation("reason bắt buộc và không được rỗng");
        }
        if (reason.length() > 500) {
            throw QrSecurityException.validation("reason tối đa 500 ký tự");
        }
    }

    private static String requireUuid(String value, String field) {
        if (value == null) {
            throw QrSecurityException.validation(field + " bắt buộc");
        }
        try {
            return UUID.fromString(value.trim()).toString();
        } catch (IllegalArgumentException e) {
            throw QrSecurityException.validation(field + " phải là UUID chữ thường");
        }
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
