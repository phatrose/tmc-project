# Task 6 — QR và bảo mật token: bàn giao theo CONTRACT-V1.md

Người phụ trách: Task 6 (nhánh `task-6/qr-token-security`). Tài liệu này ghi
contract đầu vào/đầu ra, điểm phối hợp và trạng thái bàn giao cho PR về `main`.

## 1. Phạm vi đã hiện thực

Theo `docs/TEAM.md` (Task 6 — QR và bảo mật token), `docs/DECISIONS.md` §3–§7,
`docs/tasks/07-qr-security.md` và **CONTRACT-V1.md §3, §4, §5, §8, §10, §12**.

### shared-contracts (DTO hợp đồng)

| File | Nội dung |
|---|---|
| `JwsHeader.java` | Header `{alg, kid}`; hằng số ALGORITHM/KEY_TYPE/CURVE/USE. |
| `QrClaims.java` | 7 claims; ISSUER `tmc-central`, AUDIENCE `tmc-gate`, TTL 60, SKEW 5. |
| `QrTokenResponse.java` | Không đổi từ sườn (token + expiresAt, toString che token). |
| `QrKeySet.java` (mới) | KeySet §8: version/generatedAt/trustedUntil/keys; hằng số trust window 48h, refresh FULL <36h, rotation 90 ngày overlap ≥48h. |
| `QrKeySetEntry.java` (mới) | Một khóa public: kid/kty/crv/alg/use/x/y/state/notBefore/notAfter; state ACTIVE/VERIFY_ONLY/REVOKED. |
| `RevocationChange.java` (mới) | {id, op, kind SUB/JTI/KID, value, revokedBefore?, effectiveAt}; record chặn sai semantic ngay khi khởi tạo. |
| `JwsCompact.java` (mới) | Tiện ích ES256 thuần JDK: base64url không padding, tách compact, DER↔raw r||s 64 byte, sign/verify, dựng/extract khóa P-256 từ x/y. |

### central-backend — module `qrsecurity`

| File | Nội dung |
|---|---|
| `api/QrIssuer.java` | `issue(ActorContext) → QrTokenResponse` đúng §10; employeeId từ context. |
| `api/QrCredentialView.java` (mới) | Shape Credential §4 trả ra ngoài API. |
| `api/QrRevocationQuery.java` (mới) | Public API cho Task 7 tổng hợp revocations vào bootstrap. |
| `api/QrSecurityException.java` (mới) | Lỗi nghiệp vụ mang code ApiError §2 + HTTP status + retryable. |
| `internal/model/QrCredential.java` | Model + State ACTIVE/REVOKED, bất biến revokedAt. |
| `internal/repository/QrCredentialRepository.java` | Port dữ liệu; `replaceActive` = reissue nguyên tử (một transaction ở adapter). |
| `internal/key/QrSigningKeyHandle.java`, `QrSigningKeyProvider.java` (mới) | Port khóa ký; private key chỉ qua provider (KMS/HSM ở production). |
| `internal/key/FixtureSigningKeyProvider.java` (mới) | Fixture sinh keypair P-256 cho test/demo — KHÔNG dùng production. |
| `internal/service/QrRevocationStore.java`, `InMemoryQrRevocationStore.java` (mới) | Lưu revocation SUB/JTI/KID; production dùng bảng append-only có lịch sử. |
| `internal/service/QrCredentialService.java` | Issue (jti mới mỗi lần, iat sau cutoff SUB), grant (409 nếu đã ACTIVE), revoke (200 REVOKED + SUB), reissue (201, thu hồi cũ nguyên tử), refreshKeys → QrKeySet, kiểm permission `credential.manage` + scope + cấm Auditor (§3). |
| `internal/controller/QrController.java` | 5 route §4 (`/me/qr-token`, grant/revoke/reissue, `/qr/keys/refresh`); mapping HTTP + Cache-Control: no-store chờ wiring Task 8. |

### usermgmt (file của Task 1, cần Task 1 rà)

| File | Nội dung |
|---|---|
| `api/ActorContext.java` (mới) | Shape §3 do **Task 6 dựng tạm** để `QrIssuer.issue` biên dịch. Task 1 là chủ trì Session/ActorContext: thay thế/điều chỉnh trong PR của mình, Task 6 theo đổi. |

### edge-gate-agent — module `gate-station`

| File | Nội dung |
|---|---|
| `domain/QrVerificationPort.java` | `verify(rawToken, now) → VerifiedQr` đúng §10; `now` là thời gian tin cậy. |
| `domain/VerifiedQr.java` | Claims đã xác minh + kid; helper `jti()`, `employeeId()`, `tokenIssuedAt()`, `replayReservationExpiresAt()` (= exp+5s cho ReplayReservation §10). |
| `domain/QrRejectReason.java` (mới) | reasonCode phần xác minh §5: INVALID_TOKEN, TOKEN_EXPIRED, UNKNOWN_KID, REVOKED, CACHE_UNAVAILABLE. |
| `domain/QrVerificationRejectException.java` (mới) | Lỗi từ chối mang reasonCode, message an toàn (không token/PII). |
| `api/QrKeyCachePort.java`, `QrTrustedKey.java` (mới) | Public API cache khóa; Task 7 nạp sau bootstrap/refresh. |
| `api/QrRevocationCachePort.java` (mới) | Public API cache thu hồi; ngữ nghĩa SUB/JTI/KID §8. |
| `infrastructure/InMemoryQrKeyCache.java` (mới) | Swap snapshot nguyên tử; validate kty/crv/alg/use + điểm trên đường cong; revision lùi bị từ chối. |
| `infrastructure/InMemoryQrRevocationCache.java` (mới) | Snapshot bất biến; SUB theo tokenIssuedAt, JTI/KID khớp value, DELETE theo id. |
| `infrastructure/JwsVerifier.java` | Verify đủ 8 bước: cấu trúc/limit → cache khóa → header 2 trường + allowlist ES256 → kid/state/window → chữ ký → 7 claims + iss/aud + lifetime 60 → skew 5s → revocation. JSON strict duplicate keys; từ chối padding; không gọi Central trong scan. |

### frontend

| File | Nội dung |
|---|---|
| `src/lib/qrEncoder.js` (mới) | Bộ sinh QR byte-mode thuần JS (không thêm npm dependency vì package.json do Task 8 quản). Đã đối chiếu ma trận từng ô với bộ sinh tham chiếu (14 case × 8 mask, v1–v17, mọi mức EC, UTF-8) và giải mã độc lập bằng jsQR — bằng chứng trong `docs/task6-verification/`. |
| `src/pages/QrPage.jsx` | Chỉ hiện QR sau đăng nhập (props `session` từ wiring Task 8); GET `/me/qr-token` với Bearer + X-Request-Id, `cache: no-store`; đếm ngược theo `expiresAt`, tự cấp lại còn ≤10 giây, cấp lại khi tab visible; phân biệt 401 (đăng nhập lại), 409 INVALID_STATE (liên hệ HR), lỗi khác theo ApiError; token chỉ trong React state, không log/localStorage, không hiện token thô. |

## 2. Quyết định kỹ thuật cần nhóm duyệt

1. **Thư viện JWS**: dùng JCA chuẩn Java 21 (`SHA256withECDSA`) + converter
   DER↔raw, không thêm nimbus-jose-jwt/BouncyCastle — README yêu cầu Task 6
   chốt thư viện trước khi bổ sung; chọn phương án không thêm dependency mới
   để không đụng pom (Task 8 quản pom). Nếu nhóm muốn thư viện ngoài, đổi
   hai điểm ký/xác minh trong `JwsCompact`, phần còn lại không đổi.
2. **QrKeySet/QrKeySetEntry/RevocationChange** là shape Task 6 chủ trì theo §1;
   Task 7 nhúng vào BootstrapResponse có kiểu (§12 yêu cầu Task 7 thay
   BootstrapResponse sơ sài) thay vì giữ `publicJwks: String`.
3. **ActorContext** do Task 1 sở hữu; file tạm đúng §3 để biên dịch.
4. **reasonCode phần verify** dùng đúng danh mục §5; các tên cũ trong chương
   báo cáo (`REPLAY_DETECTED`, `INVALID_LIFETIME`...) không còn dùng.
5. **Clock**: service nhận `java.time.Clock` để test cố định thời gian;
   wiring Task 8 cung cấp `Clock.systemUTC()`.

## 3. Điểm phối hợp còn lại (không tự làm thay)

- **Task 1**: ActorContext thật + `Authorization.require`; session 15 phút,
  token phiên ≠ token QR (§3). Khi Authorization có thật, phần kiểm quyền trong
  QrCredentialService chuyển sang port đó.
- **Task 2**: bắt `QrVerificationRejectException` → ScanResult DENY với
  reasonCode; CLOCK_UNTRUSTED kiểm ở luồng gọi (fail closed trước khi verify);
  reserve `ReplayReservation(kind=QR, jti, expiresAt=exp+5s, employeeId,
  direction, gateId)` dùng helper của `VerifiedQr`.
- **Task 3**: thêm `tokenIssuedAt` vào AttendancePayload (§12); QR event bắt
  buộc jti/kid/tokenIssuedAt, operatorId/reason=null (§6).
- **Task 5**: rotation scheduler 90 ngày (pre-publish VERIFY_ONLY → ACTIVE →
  overlap ≥48h → REVOKED) dùng QrSigningKeyProvider; cấu hình hệ thống.
- **Task 7**: sau bootstrap/refresh mTLS, gọi `QrKeyCachePort.replaceAll` +
  `QrRevocationCachePort.replaceAll`; nhúng QrKeySet typed vào BootstrapResponse;
  reserve replay `exp+5s` ở `reserveAndAppend`.
- **Task 4**: audit cho grant/revoke/reissue (AuditPort.append khi có) — điểm
  gọi đã chừa trong service, không log token.
- **Task 8**: bean wiring + Spring annotations cho QrController;
  `Cache-Control: no-store` trên /me/qr-token; mTLS binding cho
  /qr/keys/refresh rồi mới gọi `refreshKeys(edgeId, factoryId)`;
  `QrSecurityException → ApiError` (code/message/requestId/retryable, đúng HTTP
  status); session FE truyền `props.session` cho QrPage; không thêm npm
  dependency cho QR (đã có `src/lib/qrEncoder.js`).

## 4. Kiểm thử kèm theo

- `central-backend/src/test/.../QrCredentialServiceTest.java`: 13 test —
  claims/ký đúng contract, jti mới mỗi lần, thiếu credential/inactive →
  INVALID_STATE, permission/scope/Auditor bị chặn, revoke phát SUB đúng cutoff,
  reissue nguyên tử + iat sau cutoff, validate input, shape QrKeySet §8,
  device binding cho keys/refresh.
- `edge-gate-agent/src/test/.../JwsVerifierTest.java`: 18 test — verify hợp lệ
  + biên exp+4/exp+5, sai alg/none/kid/chữ ký, iat/nbf tương lai, lifetime 61,
  iss/aud sai, thừa/thiếu claim, duplicate key JSON, số thập phân NumericDate,
  jti không v4, token hỏng/padding, key REVOKED, khóa ngoài cửa sổ, cache rỗng
  fail closed, SUB theo tokenIssuedAt + token sau cutoff, JTI/KID + DELETE.
- QR frontend: `docs/task6-verification/` chứa script đối chiếu ma trận với
  bộ sinh tham chiếu (112/112 pass trên 14 case × 8 mask) và giải mã độc lập
  jsQR 3/3 pass, gồm token JWS thật (auto v14). `npm run build` frontend pass.

Lệnh chạy: `mvn -pl shared-contracts,central-backend,edge-gate-agent test`
(JDK 21; Testcontainers chưa dùng cho phần này — repository JPA adapter sẽ bổ
sung sau).

## 5. Chưa làm (nêu rõ, không khoe PASS)

- Adapter JPA/PostgreSQL cho QrCredentialRepository/QrRevocationStore +
  migration Flyway (bảng `qr_credential`, `qr_revocation`).
- KMS/HSM thật cho khóa ký (đang là FixtureSigningKeyProvider; tuyệt đối không
  dùng fixture trong môi trường thật).
- Scheduler rotation 90 ngày (Task 5 phối hợp), mTLS thật cho keys/refresh
  (Task 8), HTTP filter gắn ActorContext từ phiên.
- Audit các mutation QR (chờ AuditPort của Task 4).
- Không chạy `mvn test` trong môi trường bàn giao (máy dựng task thiếu JDK
  compiler); các test đã viết theo API JUnit 5 + Jackson có trong pom — người
  nhận PR chạy lại trước khi merge theo `CONTRIBUTING.md` (không ghi PASS
  nghiệp vụ chỉ vì compile thành công).
