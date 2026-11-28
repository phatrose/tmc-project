# Contract v1 — Ví dụ request/response

Đọc cùng [CONTRACT-V1.md](CONTRACT-V1.md). Fixture dưới đây là dữ liệu giả, không có token thật hoặc secret. Đây là mẫu contract, không phải log API đã chạy. Các trường uuid/Instant/hash có giá trị cụ thể để hai bên dùng chung.

## 1. Phiên đăng nhập

POST /api/v1/auth/login với username/password do môi trường test cấp. Response shape:

```json
{
  "accessToken": "TEST_ONLY_NOT_A_VALID_ACCESS_TOKEN",
  "tokenType": "Bearer",
  "expiresAt": "2026-11-17T02:15:00.000Z",
  "actor": {
    "userId": "00000000-0000-4000-8000-000000000010",
    "employeeId": "00000000-0000-4000-8000-000000000011",
    "roles": [
      "EMPLOYEE"
    ],
    "permissions": [],
    "factoryIds": [
      "00000000-0000-4000-8000-000000000023"
    ],
    "departmentIds": [],
    "scope": "SELF"
  }
}
```

accessToken ở đây không phải token thật và không dùng làm QR. Quyền SELF xem công/lấy QR được backend suy ra từ role/subject theo contract; không cần employee.manage.

## 2. Claims QR trước khi ký

Header:

```json
{
  "alg": "ES256",
  "kid": "qr-key-2026-11"
}
```

Payload:

```json
{
  "sub": "00000000-0000-4000-8000-000000000011",
  "iss": "tmc-central",
  "aud": "tmc-gate",
  "iat": 1794880785,
  "nbf": 1794880785,
  "exp": 1794880845,
  "jti": "00000000-0000-4000-8000-000000000012"
}
```

Task 6 dùng private key fixture của môi trường test để ký, không commit production key. Các timestamp cố định chỉ dùng với clock test tương ứng, không dùng token cũ để demo thời gian thực.

## 3. Lượt quét hợp lệ đã lưu, thiết bị chưa xác nhận

Response POST /edge/v1/scans:

```json
{
  "scanId": "00000000-0000-4000-8000-000000000024",
  "decision": "ALLOW",
  "reasonCode": "OK",
  "eventId": "01a1e73b-8500-7000-8000-000000000001",
  "commandId": "00000000-0000-4000-8000-000000000013",
  "commandStatus": "TIMEOUT",
  "occurredAt": "2026-11-17T02:00:00.000Z",
  "mode": "OFFLINE"
}
```

UI phải hiện quyền đã được chấp nhận nhưng lệnh thiết bị timeout, không báo người đã qua cổng. Retry cùng scanId không mở thêm.

## 4. Batch hai event và mixed ACK

POST /api/v1/attendance/sync với certificate edgeId/factoryId tương ứng. Event 1 hợp lệ, event 2 cố ý có direction=SIDEWAYS để test REJECTED. Đây là fixture âm, không được dùng direction này trong event thật.

```json
{
  "syncBatchId": "00000000-0000-4000-8000-000000000031",
  "events": [
    {
      "eventId": "01a1e73b-8500-7000-8000-000000000001",
      "eventType": "attendance.event.recorded",
      "schemaVersion": 1,
      "edgeId": "00000000-0000-4000-8000-000000000021",
      "gateId": "00000000-0000-4000-8000-000000000022",
      "factoryId": "00000000-0000-4000-8000-000000000023",
      "sequenceNo": 1,
      "correlationId": "00000000-0000-4000-8000-000000000024",
      "occurredAt": "2026-11-17T02:00:00.000Z",
      "payloadSha256": "5889002999b73c833ff21dfe5ca348309c44f227c59e8b981d80e9e4a96b9c02",
      "payload": "{\"commandId\":\"00000000-0000-4000-8000-000000000013\",\"direction\":\"IN\",\"employeeId\":\"00000000-0000-4000-8000-000000000011\",\"jti\":\"00000000-0000-4000-8000-000000000012\",\"kid\":\"qr-key-2026-11\",\"operatorId\":null,\"reason\":null,\"source\":\"OFFLINE\",\"tokenIssuedAt\":\"2026-11-17T01:59:45.000Z\"}"
    },
    {
      "eventId": "01a1e73b-8500-7000-8000-000000000002",
      "eventType": "attendance.event.recorded",
      "schemaVersion": 1,
      "edgeId": "00000000-0000-4000-8000-000000000021",
      "gateId": "00000000-0000-4000-8000-000000000022",
      "factoryId": "00000000-0000-4000-8000-000000000023",
      "sequenceNo": 2,
      "correlationId": "00000000-0000-4000-8000-000000000025",
      "occurredAt": "2026-11-17T02:00:00.000Z",
      "payloadSha256": "cb05d28357dcbc4a92f840947a1d323a5265bad05b7c195c78dbc72dec0a2377",
      "payload": "{\"commandId\":\"00000000-0000-4000-8000-000000000016\",\"direction\":\"SIDEWAYS\",\"employeeId\":\"00000000-0000-4000-8000-000000000011\",\"jti\":\"00000000-0000-4000-8000-000000000015\",\"kid\":\"qr-key-2026-11\",\"operatorId\":null,\"reason\":null,\"source\":\"OFFLINE\",\"tokenIssuedAt\":\"2026-11-17T01:59:45.000Z\"}"
    }
  ]
}
```

Response 200 khi event 1 commit, event 2 bị từ chối:

```json
{
  "syncBatchId": "00000000-0000-4000-8000-000000000031",
  "acks": [
    {
      "eventId": "01a1e73b-8500-7000-8000-000000000001",
      "status": "ACCEPTED",
      "acceptedAt": "2026-11-17T02:10:00.000Z",
      "code": null
    },
    {
      "eventId": "01a1e73b-8500-7000-8000-000000000002",
      "status": "REJECTED",
      "acceptedAt": null,
      "code": "INVALID_PAYLOAD"
    }
  ],
  "lastContiguousSequenceNo": 1
}
```

Edge lưu event 1 ACKNOWLEDGED, event 2 FAILED và giữ lại để điều tra. Cursor=1, không nhảy qua sequence 2. Event sequence 3 ở lô sau vẫn được gửi. Retry nguyên lô trên: event 1 trở thành DUPLICATE với acceptedAt cũ, event 2 vẫn REJECTED.

Payload trên là **string** JSON canonical có escape ở wire; hash tính trên bytes UTF-8 của giá trị string sau JSON decode, không trên dấu ngoặc kép/escape của envelope. Các key fixture là ASCII và giá trị không chứa số dấu phẩy động nên thứ tự/số đã khớp tập con JCS; implementation vẫn phải dùng JCS đầy đủ.

## 5. Lỗi request cấp HTTP

Ví dụ certificate không khớp edge/factory trong batch: HTTP 403, không trả mixed ACK.

```json
{
  "code": "DEVICE_BINDING_MISMATCH",
  "message": "Thiết bị không thuộc phạm vi yêu cầu.",
  "requestId": "00000000-0000-4000-8000-000000000041",
  "retryable": false
}
```

## 6. FULL bootstrap cho thiết bị chưa provision đủ

Fixture này cố ý chưa có public key/quyền. Request afterRevision=0; response 200 hợp lệ về hình dạng nhưng Edge **không được báo ready offline hoặc mở cổng**, vì chưa có key/policy hợp lệ.

```json
{
  "mode": "FULL",
  "baseRevision": 0,
  "revision": 1,
  "generatedAt": "2026-11-17T02:00:00.000Z",
  "trustedUntil": "2026-11-19T02:00:00.000Z",
  "factoryId": "00000000-0000-4000-8000-000000000023",
  "edgeId": "00000000-0000-4000-8000-000000000021",
  "revocations": [],
  "keySet": {
    "version": 1,
    "generatedAt": "2026-11-17T02:00:00.000Z",
    "trustedUntil": "2026-11-19T02:00:00.000Z",
    "keys": []
  },
  "policy": {
    "version": 1,
    "employees": []
  },
  "gates": []
}
```

Khi Task 5/6 đã provision, Task 7 thay fixture bằng gate/key P-256 hợp lệ và window chính xác. Không điền x/y ngẫu nhiên vì có thể không nằm trên đường cong.

## 7. Thời gian và lịch công mẫu

Ca 22:00 ngày 30/11/2026 đến 06:00 ngày 01/12/2026 tại Asia/Ho_Chi_Minh thuộc workDate=2026-11-30 và period=2026-11. Chốt bắt đầu 06:30 ngày 01/12 (2026-11-30T23:30:00.000Z), hoàn tất xuất trước 12:00 địa phương. Test phải dùng timezone/clock cố định; không lấy ngày UTC làm ngày công.
