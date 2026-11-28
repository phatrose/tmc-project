# TMC — Hệ thống Kiểm soát Ra/Vào Cổng & Chấm công bằng Mã QR Động

**TMC (Time & Access Management Control)** là hệ thống kiểm soát ra/vào cổng nhà máy và chấm công tự động sử dụng **mã QR động ký số bất đối xứng (ES256)** kết hợp kiến trúc **Central – Edge** cho phép quét cổng ổn định cả khi mất kết nối mạng WAN. Dự án giải quyết bài toán chống gian lận chấm công (chụp ảnh/chia sẻ QR, quét lặp), đảm bảo độ trễ mở cổng thấp tại nhà máy và đồng bộ dữ liệu minh bạch phục vụ tính lương (Payroll) và kiểm toán (Audit).

---

## Tính năng nổi bật

- **Mã QR động ES256 & Chống giả mạo**: Cấp phát mã QR ký số ECDSA P-256 có thời hạn ngắn (60 giây), dùng một lần (`jti` chống replay), tự động làm mới trước khi hết hạn và lưu khóa ký bền vững có hỗ trợ xoay vòng (`kid` rotation).
- **Vận hành Kiosk & Cổng tại Edge (Online / Offline-first)**: Quét QR tự động qua camera trình duyệt (`BarcodeDetector` + fallback `jsQR`), xác minh chữ ký cục bộ không phụ thuộc WAN, lưu sự kiện bất biến vào SQLite WAL và đồng bộ hàng loạt theo lô (`SyncBatch`) về Central khi có mạng.
- **Bảo mật thiết bị mTLS & Mở cổng có giám sát**: Xác thực hai chiều mTLS giữa **Edge ↔ Central** và **Kiosk ↔ Edge** bằng `SAN URI` đối chiếu bảng provisioning (không tin header tự khai); mở cổng thủ công (`MANUAL`) hoặc khẩn cấp (`EMERGENCY`) yêu cầu tái xác thực (`Action Grant` ES256 hạn 60 giây).
- **Chấm công, Điều chỉnh công & Khóa kỳ lương (Payroll)**: Tổng hợp công ngày tự động (hỗ trợ ca đêm), quy trình đề xuất/phê duyệt điều chỉnh công tách biệt vai trò (cấm tự duyệt, kiểm soát xung đột phiên bản `409` và `Idempotency-Key` chống gửi trùng), chốt kỳ và mở lại kỳ lương có lưu vết.
- **Báo cáo, Xuất tệp & Nhật ký kiểm toán (Audit)**: Tra cứu bảng công và nhật ký bất biến theo phạm vi (`SELF` / `DEPARTMENT` / `FACTORY` / `GLOBAL`), tạo tác vụ xuất file **CSV / XLSX / PDF** bất đồng bộ và tải tệp trực tiếp trên giao diện web.
- **Quản trị hệ thống & Phân quyền RBAC**: Quản lý nhà máy, cổng turnstile, ca làm việc, ngày lễ, trạng thái thiết bị, yêu cầu quyền riêng tư (DSR) và phân quyền chặt theo 7 vai trò (`EMPLOYEE`, `SUPERVISOR`, `HR`, `GUARD`, `ADMIN`, `EXECUTIVE`, `AUDITOR`).

---

## Công nghệ sử dụng (Tech Stack)

| Lớp / Thành phần | Công nghệ chính |
|---|---|
| **Central Backend** | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA / JDBC, Spring Modulith, Flyway |
| **Edge Gate Agent** | Java 21, JDK `HttpsServer` (mTLS), SQLite (WAL mode), `java-json-canonicalization` (RFC 8785 JCS) |
| **Cơ sở dữ liệu** | PostgreSQL 16 (Central), SQLite 3 (Edge cục bộ) |
| **Frontend Web** | React 19, Vite 8, `jsqr` (giải mã QR trên camera), bộ sinh QR thuần JS (`qrEncoder.js`), Nginx (reverse proxy) |
| **Bảo mật & Ký số** | ES256 (ECDSA P-256 / JWS Compact), X.509 mTLS (`SAN URI`), BCrypt, Cookie phiên `HttpOnly; SameSite=Strict` + Bearer token |
| **Kiểm thử & Hạ tầng** | JUnit 5, Mockito, Testcontainers (PostgreSQL 16), Node Test Runner, Docker & Docker Compose, k6 |

---

## Hướng dẫn cài đặt

### 1. Yêu cầu môi trường
- **Chạy bằng Docker (khuyến nghị)**: Docker Desktop (hỗ trợ `docker compose`).
- **Chạy / biên dịch trực tiếp từ mã nguồn**:
  - JDK 21+ và Maven 3.9+
  - Node.js 22.12+ và npm

### 2. Tải mã nguồn và cấu hình biến môi trường
```bash
git clone <url-repository>
cd tmc-project/tmc-system
```

Tạo file `.env` từ mẫu (không commit file `.env` lên Git):
```bash
cp .env.example .env
```

Cập nhật mật khẩu cơ sở dữ liệu trong `.env` (ví dụ cho môi trường local):
```env
TMC_DB_NAME=tmc
TMC_DB_USER=tmc
TMC_DB_PASSWORD=<mat-khau-postgres-cua-ban>
TMC_PREVIEW_PASSWORD=123456
```

### 3. Khởi chạy toàn bộ hệ thống bằng Docker Compose
Chạy ở chế độ **local preview** (PostgreSQL 16 + Central Backend + Frontend Nginx + dữ liệu tài khoản mẫu):
```bash
docker compose -f compose.yml -f compose.preview.yml up -d --build
```

*(Nếu cần chạy song song ở cổng cô lập `15173` / `18080` với volume riêng để tránh đụng dịch vụ khác trên máy:)*
```bash
docker compose -f compose.yml -f compose.preview.yml -f compose.verify.yml -p tmc-verify-isolated up -d --build
```

Sau khi các container ở trạng thái `healthy`:
- **Giao diện Web**: http://127.0.0.1:5173 (hoặc http://127.0.0.1:15173 nếu dùng `compose.verify.yml`)
- **Central API Health**: http://127.0.0.1:8080/actuator/health (hoặc `18080`)

---

## Cách sử dụng & Ví dụ

### 1. Tài khoản dùng thử (chế độ `integration-preview`)
Mật khẩu mặc định từ biến `TMC_PREVIEW_PASSWORD` (ví dụ `123456`):

| Tài khoản | Vai trò | Phạm vi | Chức năng có thể thử |
|---|---|---|---|
| `admin` | `ADMIN`, `HR`, `EMPLOYEE` | `GLOBAL` | Quản trị (`#/admin`), Nhân sự (`#/employees`), Chấm công (`#/attendance`), Báo cáo & Xuất CSV/XLSX/PDF (`#/reports`) |
| `hr` | `HR`, `EMPLOYEE` | `GLOBAL` | Quản lý nhân viên, phê duyệt điều chỉnh công, chốt kỳ Payroll |
| `guard` | `GUARD`, `EMPLOYEE` | `FACTORY` | Vận hành cổng (`#/guard`), Kiosk quét QR (`#/kiosk`) |
| `auditor` | `AUDITOR` | `GLOBAL` | Chỉ đọc & xuất nhật ký kiểm toán (`#/reports`, mọi thao tác ghi đều bị chặn) |
| `emp` | `EMPLOYEE` | `SELF` | Xem mã QR động ES256 (`#/qr`), gửi yêu cầu điều chỉnh công |

### 2. Ví dụ gọi API & kết quả đầu ra

**Đăng nhập lấy phiên (`POST /api/v1/auth/login`):**
```bash
curl -s -X POST http://127.0.0.1:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"emp","password":"123456"}'
```

Kết quả mẫu (`200 OK`, kèm header `Set-Cookie: tmc_session=...; HttpOnly; SameSite=Strict`):
```json
{
  "accessToken": "<opaque-session-token>",
  "tokenType": "Bearer",
  "expiresAt": "2026-11-28T07:35:00.000Z",
  "actor": {
    "userId": "00000000-0000-4000-8000-000000000106",
    "employeeId": "00000000-0000-4000-8000-000000000206",
    "roles": ["EMPLOYEE"],
    "permissions": ["employee.self"],
    "scope": "SELF"
  }
}
```

**Lấy mã QR động ES256 (`GET /api/v1/me/qr-token`):**
```bash
curl -s http://127.0.0.1:8080/api/v1/me/qr-token \
  -H "Authorization: Bearer <accessToken>"
```

Kết quả mẫu (`200 OK`, `Cache-Control: no-store`):
```json
{
  "token": "eyJhbGciOiJFUzI1NiIsImtpZCI6InFyLS4uLiJ9.eyJzdWIiOi4uLn0.<es256-signature>",
  "expiresAt": "2026-11-28T07:21:00.000Z"
}
```

### 3. Chạy các bộ kiểm thử tự động

```bash
cd tmc-system

# 1. Kiểm thử Central Backend (122 unit tests + 52 PostgreSQL 16 integration tests qua Testcontainers)
mvn -B -ntp -pl central-backend -am verify -Pintegration

# 2. Kiểm thử Edge Gate Agent (231 tests gồm SQLite WAL, mTLS HTTPS, Action Grant ES256 và benchmark 800 lượt quét)
mvn -B -ntp -pl edge-gate-agent -am test

# 3. Kiểm thử và đóng gói Frontend (32 tests + Vite build)
cd frontend && npm ci && npm test && npm run build
```

### 4. Dừng hệ thống
```bash
cd tmc-system
docker compose -f compose.yml -f compose.preview.yml down
# Thêm cờ -v nếu muốn xóa luôn dữ liệu volume PostgreSQL & keystore cục bộ
```

---

## Cấu trúc thư mục & Tài liệu tham khảo

| Thành phần | Mô tả |
|---|---|
| [tmc-system/central-backend](tmc-system/central-backend) | 5 module nghiệp vụ (`usermgmt`, `qrsecurity`, `attendanceprocessing`, `reportingaudit`, `systemadmin`) và migration Flyway `V001..V013` |
| [tmc-system/edge-gate-agent](tmc-system/edge-gate-agent) | 2 module Edge (`gatestation`, `edgesync`) chạy `HttpsServer` mTLS và lưu trữ cục bộ SQLite WAL |
| [tmc-system/shared-contracts](tmc-system/shared-contracts) | DTO và hợp đồng dữ liệu dùng chung (`EventEnvelope`, `SyncBatchRequest`, `BootstrapResponse`, `QrClaims`...) |
| [tmc-system/frontend](tmc-system/frontend) | Ứng dụng web React + Vite cho Nhân viên, Kiosk, Bảo vệ, HR, Kiểm toán và Quản trị |
| [tmc-system/docs/CONTRACT-V1.md](tmc-system/docs/CONTRACT-V1.md) | Hợp đồng API, bảo mật, đồng bộ và quy tắc nghiệp vụ v1 |
| [tmc-system/docs/COMPLETION-CHECKLIST.md](tmc-system/docs/COMPLETION-CHECKLIST.md) | Bảng theo dõi kiểm chứng chi tiết các hạng mục hệ thống |
| [tmc-system/docs/MTLS-DECISION-PROPOSAL.md](tmc-system/docs/MTLS-DECISION-PROPOSAL.md) | Kiến trúc mTLS & quản lý danh tính thiết bị (môi trường tích hợp local và đề xuất production) |
