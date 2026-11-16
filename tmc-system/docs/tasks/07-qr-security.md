# Mục PDF 7 — Dynamic QR và JWS

Tài liệu tham chiếu theo mục của PDF. Khi khác nhau, áp dụng [DECISIONS.md](../DECISIONS.md). Phân công triển khai xem [TEAM.md](../TEAM.md).

Nguồn: [main.pdf](../reference/main.pdf) phần 7; PDF tr. 91–97.

## Việc cần làm

- [ ] Central issuer JWS/ZXing và lifecycle credential; Edge verifier chỉ giữ public key.
- [ ] ES256 allowlist; iat/nbf/exp/iss/aud/jti; kid ở protected header; TTL mặc định 60 giây.
- [ ] Replay, revocation, rotation và cache trust theo §7; không hứa ngăn replay tuyệt đối giữa hai Edge offline.
- [ ] Thống nhất schema/token/error với Mục PDF 4/5/6; test token âm, race replay và thu hồi/rotation cùng Mục PDF 8.

## Issuer và verifier

- Central ký ES256/JWS Compact; private key trong secret store. Token không chứa PII ngoài định danh tối thiểu.
- Payload: jti, sub, iat, nbf, exp, iss, aud. `kid` ở JOSE protected header. TTL phát hành mặc định 60s; clock skew phải chốt và không tùy tiện kéo dài TTL.
- Verifier ghim thuật toán/issuer/audience; từ chối alg none, malformed, unknown kid, sai signature/thời gian.
- Replay jti và event commit nguyên tử tại Edge sau khi các kiểm tra cần thiết đạt; chống trùng nghiệp vụ 30s là kiểm tra riêng.
- Unknown kid: từ chối lượt scan hiện tại; refresh endpoint mTLS chạy nền, không tải URL từ token và không chờ Central trong scan. Khóa/cache hết tin cậy thì fail closed; provisioning phải đáp ứng cửa sổ offline 24 giờ.
- Revocation theo sub/jti/kid với revision; online mục tiêu ≤60s ở §6–7. Offline dùng snapshot hợp lệ cũ, đối soát POST_REVOKE_VIOLATION khi reconnect.
- Rotation mặc định 90 ngày: phân phối public key mới → chuyển ký → overlap key cũ → thu hồi. Mapping GRACE tại Central sang VERIFY_ONLY ở Edge cần contract rõ.
- Replay đồng thời giữa hai Edge offline và ảnh QR bị dùng trước chủ thể là rủi ro còn lại, không ghi bảo đảm loại bỏ tuyệt đối.

## Test cần phối hợp

Sai chữ ký/alg/iss/aud, hết hạn/chưa hiệu lực, lifetime sai, replay đồng thời, revocation thiếu/hỏng/lùi, stale offline, key rotation/grace/TTL và kiểm tra không rò private key/JWS vào log.

## Vị trí bắt đầu

| File | Vai trò |
|---|---|
| [QrCredentialService.java](../../central-backend/src/main/java/vn/tmc/central/qrsecurity/internal/service/QrCredentialService.java) | service |
| [JwsVerifier.java](../../edge-gate-agent/src/main/java/vn/tmc/edge/gatestation/infrastructure/JwsVerifier.java) | Adapter / Controller |
| [QrPage.jsx](../../frontend/src/pages/QrPage.jsx) | View |

## Bàn giao

- [ ] Ghi contract đầu vào/đầu ra và Task phối hợp trước khi nối module.
- [ ] Cập nhật [yêu cầu liên quan](../REQUIREMENTS.md) và test Mục PDF 8 khi có implementation.
- [ ] Nộp bằng chứng thực tế; không đổi trạng thái chỉ vì có file sườn.
