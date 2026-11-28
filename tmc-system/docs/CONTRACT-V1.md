# Contract v1 — TMC


## 1. Chủ trì và thứ tự thực hiện

| Contract | Chủ trì | Bên cùng chốt/triển khai |
|---|---|---|
| Session, ActorContext, role/scope | Task 1 | Task 8 client; các module dùng quyền |
| Nhà máy/cổng/ca và quyền khu vực | Task 5 cấu hình; Task 1 quyền | Task 2/3/7 |
| QR, claims và verifier | Task 6 | Task 1/2/7 |
| Scan, override và command result | Task 2 | Task 3/6/7 |
| EventEnvelope và event payload | Task 3 | Task 2/7 |
| Batch/ACK/bootstrap, port SQLite | Task 7 | Task 3 nhận sync; Task 1/5/6 cấp dữ liệu cache |
| Tính công → báo cáo, audit và HR | Task 4 contract xuất; Task 3 dữ liệu công | Task 1/5 |
| HTTP client, ApiError và quy chuẩn | Task 8 | Task 1–7 |

Thứ tự: (1) tạo DTO/interface + fixture theo v1; (2) các bên rà chữ ký; (3) làm implementation/mock song song; (4) tích hợp và chạy contract tests. Một file chỉ có một Task chủ trì. Thay đổi contract phải nêu consumer bị ảnh hưởng trong PR; breaking change cần bên cung cấp và bên dùng cùng xác nhận, cập nhật phiên bản/kế hoạch chuyển đổi. Task 8 điều phối, không tự quyết mọi nghiệp vụ.

## 2. Quy ước dữ liệu và HTTP

- Central `/api/v1`; Edge LAN `/edge/v1`; HR mock `/hr/v1`. Base URL cấu hình theo môi trường. Dùng HTTPS, JSON UTF-8, camelCase; enum UPPER_SNAKE_CASE.
- Object response trả trực tiếp, không bọc `data`. Danh sách: `Page<T>={items:T[],page:int,size:int,totalElements:int}`; page bắt đầu 0, size mặc định 20, tối đa 100. Sắp theo id tăng dần, riêng event/audit theo occurredAt giảm dần rồi id giảm dần.
- ID wire là chuỗi UUID chữ thường. `eventId` dùng UUIDv7; tài nguyên, batch, scan, command và correlation ID dùng UUIDv4. `employeeCode` là mã nghiệp vụ, không thay employeeId.
- Instant wire là UTC ISO 8601 đúng milliseconds, ví dụ `2026-11-17T02:00:00.000Z`. Ngày công `YYYY-MM-DD`, kỳ `YYYY-MM`, giờ ca `HH:mm:ss`, timezone IANA mặc định `Asia/Ho_Chi_Minh`. NumericDate trong JWT dùng giây.
- sequence/revision/cursor là JSON integer trong [0,9007199254740991]. Sequence event bắt đầu 1, tăng bền vững theo edgeId; restart không reset. Mất DB không được dùng lại edgeId với sequence từ đầu, phải reprovision.
- `schemaVersion` event v1 chốt là integer `1`, không phải string `1.0`. Phiên bản tài liệu và wire version là hai khái niệm riêng.
- Ký hiệu `?` trong tài liệu là nullable; response có field với null. Request create phải gửi trường nullable với null nếu không dùng. Mảng rỗng là `[]`. Các field khác bắt buộc, không null. Trừ ghi rõ khác, string không rỗng.
- Request không được tự quyết actor/role/scope/receivedAt/trạng thái duyệt. Server lấy từ danh tính tin cậy. Unknown request field bị 400; consumer bỏ qua response field chưa dùng để hỗ trợ bổ sung tương thích.
- `X-Request-Id` là UUIDv4, server tạo nếu client không gửi, response echo. Không dùng token/password làm correlation; không log secret hoặc toàn bộ JWS.
- GET/PUT trả 200, POST tạo resource 201, job async 202, logout 204; bảng endpoint có thể chỉ rõ ngoại lệ. Lỗi dùng ApiError dưới đây.
- PUT gửi toàn bộ field editable và version hiện tại; version sai trả 409, thành công tăng 1. Create khởi tạo version=1. V1 không dùng PATCH.
- POST mutation ngoài login, action-grant, scan, sync và heartbeat phải có `Idempotency-Key: UUIDv4`. Scope key theo actor+method+path; lưu hash input/outcome ít nhất 48 giờ. Cùng key/cùng nội dung trả outcome cũ; khác nội dung 409. Scan dùng scanId, sync dùng syncBatchId. Retry không tạo key mới khi kết quả cũ chưa rõ.
- Mọi from/to dạng timestamp hoặc date là khoảng [from,to); từ đầu ngày from đến trước đầu ngày to trong timezone nhà máy đối với LocalDate. from < to, tối đa 366 ngày khi query báo cáo/công. Nếu không truyền filter thời gian event/audit, mặc định 7 ngày gần nhất.

### ApiError và retry

```json
{"code":"VALIDATION_ERROR","message":"size phải từ 1 đến 100","requestId":"00000000-0000-4000-8000-000000000001","retryable":false}
```

| HTTP | code | Client |
|---|---|---|
| 400 | VALIDATION_ERROR, MALFORMED_JSON | Sửa input, không retry tự động |
| 401 | UNAUTHENTICATED, SESSION_EXPIRED | Đăng nhập lại; không lặp vô hạn |
| 403 | FORBIDDEN, SCOPE_MISMATCH, DEVICE_BINDING_MISMATCH | Không retry |
| 404 | NOT_FOUND | Không tồn tại; không tiết lộ tài nguyên ngoài scope |
| 409 | VERSION_CONFLICT, IDEMPOTENCY_CONFLICT, INVALID_STATE | Đọc trạng thái mới/xử lý xung đột |
| 429 | RATE_LIMITED | retryable=true, Retry-After là số giây |
| 503 | DEPENDENCY_UNAVAILABLE, STORAGE_UNAVAILABLE | retryable=true, retry cùng key khi phù hợp |
| 500 | INTERNAL_ERROR | retryable=true; tra requestId, không lộ stack trace |

DENY kinh doanh tại Edge là ScanResult 200, không phải lỗi HTTP. ACK CONFLICT/REJECTED từng event cũng nằm trong response 200, không trả HTTP 409 cho toàn lô vì một event xấu.

## 3. Danh tính, quyền và client — Task 1/8

| API Central | Input | Output |
|---|---|---|
| POST `/auth/login` | `{username:string,password:string}` | 200 Session |
| GET `/auth/me` | Bearer accessToken | 200 ActorContext |
| POST `/auth/logout` | Bearer, body `{}` | 204, thu hồi phiên |

Session = `{accessToken:string,tokenType:"Bearer",expiresAt:Instant,actor:ActorContext}`. Chọn token opaque ngẫu nhiên, hạn 15 phút; backend lưu hash và kiểm tra phiên từng request. V1 không refresh token, hết hạn đăng nhập lại. FE giữ token trong bộ nhớ, không URL/localStorage; reload cần đăng nhập lại. Token phiên **khác token QR**, không dùng để quét cổng. Khóa tài khoản hoặc đổi quyền phải thu hồi phiên cũ.

ActorContext = `{userId:UUID,employeeId:UUID?,roles:Role[],permissions:string[],factoryIds:UUID[],departmentIds:UUID[],scope:"SELF"|"DEPARTMENT"|"FACTORY"|"GLOBAL"}`. Task 1 cấp một scope hiệu lực, GLOBAL chỉ cấp rõ cho Admin. Resource query luôn giao với scope tin cậy; không deserialize ActorContext từ request body rồi coi là đã xác thực.

| Role | Quyền mặc định v1 |
|---|---|
| EMPLOYEE | QR của mình, công của mình, gửi yêu cầu điều chỉnh của mình; SELF |
| SUPERVISOR | Xem công/bất thường trong department được gán; không tự duyệt công |
| HR | Hồ sơ, quyền khu vực, credential, duyệt công và chốt/xuất Payroll trong factory được gán |
| GUARD | Vận hành cổng/manual/emergency và hiện diện trong factory được gán |
| ADMIN | Tài khoản/role và cấu hình/vận hành hệ thống; không mặc định được duyệt công thay HR |
| EXECUTIVE | Báo cáo/dashboard theo factory được gán; không sửa nghiệp vụ |
| AUDITOR | Tra cứu audit, override, lịch sử điều chỉnh và kỳ công; xuất chỉ khi được cấp audit.export |

Permission chuẩn: `employee.manage`, `access-policy.manage`, `credential.manage`, `attendance.approve`, `payroll.export`, `system.manage`, `gate.override`, `gate.emergency`, `audit.read`, `audit.export`, `report.read`, `report.export`. HR có report.read/report.export, Executive chỉ report.read mặc định; export cần cấp thêm. Các mutation phải có permission tương ứng và scope, không chỉ kiểm tra tên role.

Auditor bị cấm sửa event, duyệt công, mở cổng, cấp/thu hồi QR, đổi tài khoản/quyền/cấu hình, xóa audit. Nếu một phiên có role AUDITOR thì các cấm vẫn có hiệu lực dù có role khác. Người tạo điều chỉnh không được tự duyệt, kể cả có nhiều role. Chỉ ẩn nút UI là chưa đủ.

Edge → Central dùng mTLS certificate gắn edgeId/factoryId. Scanner → Edge dùng mTLS certificate kiosk gắn gateId. Danh tính thiết bị cấp qua provisioning, không qua API CRUD trả secret. Central không đọc SQLite, Edge không kết nối PostgreSQL.

## 4. API module Central — request/response để FE và BE làm song song

Các path đều cộng base `/api/v1`. Bearer/scope bắt buộc trừ API dành mTLS ghi riêng. ID trong URL không được đổi qua body. Tài nguyên đã có dữ kiện công dùng active=false thay hard-delete.

### Task 1: nhân sự, tài khoản và access policy

| API | Request/filter | Response / quyền |
|---|---|---|
| GET `/employees` | factoryId?,departmentId?,active?,page,size | Page<Employee>; SELF chỉ bản thân, HR theo scope |
| POST `/employees` | EmployeeWrite | 201 Employee; employee.manage |
| GET `/employees/{id}` | — | Employee; SELF hoặc HR trong scope |
| PUT `/employees/{id}` | EmployeeWrite + version | Employee; employee.manage |
| POST `/employees/imports` | `{records:EmployeeImport[]}` tối đa 100 | 200 `{results:[{employeeCode,status:"CREATED"|"UPDATED"|"UNCHANGED"|"REJECTED",code:string?}]}`; employee.manage |
| GET `/employees/{id}/access-policy` | — | EmployeePolicy; SELF hoặc access-policy.manage |
| PUT `/employees/{id}/access-policy` | `{assignments:Assignment[],version}` | EmployeePolicy; access-policy.manage |
| GET/POST `/departments`, GET/PUT `/departments/{id}` | GET page,size,factoryId?; write `{factoryId,name}` + version khi PUT | Page<Department>/Department; đọc theo scope, ghi employee.manage |
| GET `/accounts` | page,size | Page<Account>; system.manage |
| POST `/accounts` | `{username,employeeId:UUID?,roles:Role[],factoryIds:UUID[],departmentIds:UUID[],scope,initialPassword}` | 201 Account; system.manage |
| PUT `/accounts/{id}` | `{roles,factoryIds,departmentIds,scope,active,version}` | Account; system.manage; thu hồi phiên khi đổi quyền |

EmployeeWrite = `{employeeCode:string,displayName:string,factoryId:UUID,departmentId:UUID?,active:boolean}`. Employee thêm `{id,version,updatedAt:Instant}`. employeeCode unique dài 1–64, displayName 1–200. Department = `{id,factoryId,name,version}`.

EmployeeImport = EmployeeWrite + `{sourceUpdatedAt:Instant}`; upsert theo employeeCode, bản cũ hơn/giống trả UNCHANGED; từng record độc lập. Tắt active tạo outbox thu hồi credential/cập nhật policy cùng transaction, không gọi mạng trước commit.

Account = `{id,username,employeeId,roles,factoryIds,departmentIds,scope,active,version}`; không trả password/hash. Password tối thiểu 12 ký tự, tối đa 128; không cắt ngắn âm thầm. Username 3–64, unique không phân biệt hoa thường.

Assignment = `{gateIds:UUID[],shiftIds:UUID[],validFrom:Instant,validUntil:Instant?}`; hai mảng ít nhất 1 ID, không dùng wildcard. EmployeePolicy = `{employeeId,assignments,version}`. Factory/gate/shift do Task 5 quản lý; Task 1 tạo quyền tham chiếu chúng và cung cấp policy nguồn cho Task 7.

### Task 5: nhà máy/cổng/ca và vận hành

GET danh sách `/factories`, `/gates`, `/shifts` trả Page<T>; filter factoryId với gates/shifts. GET `/{resource}/{id}` trả object. POST tạo; PUT cập nhật version; ghi yêu cầu system.manage, đọc theo scope cho các vai trò cần cấu hình.

- FactoryWrite `{name,zoneId}`; Factory thêm `{id,version}`; zoneId mặc định Asia/Ho_Chi_Minh.
- GateWrite `{name,factoryId,edgeId,direction:"IN"|"OUT",turnstileApiId:string,enabled:boolean}`; Gate thêm `{id,version}`.
- ShiftWrite `{factoryId,name,startsAt:"HH:mm:ss",endsAt:"HH:mm:ss",lateGraceMinutes:int,earlyGraceMinutes:int}`; Shift thêm `{id,version}`; endsAt<=startsAt nghĩa qua ngày, tối đa 24 giờ. Grace trong [0,120]. Gán người qua Assignment, không qua ShiftWrite.
- GET `/system/config` → `{version,duplicateScanWindowSeconds:30,qrTtlSeconds:60,revocationPollSeconds:30,offlineTargetHours:24}`. PUT cùng shape/version; system.manage. V1 cố định các giá trị trên; muốn thay behavior phải sửa contract.
- GET `/operations/edges?factoryId=&page=&size=` → Page<EdgeHealth> với `{edgeId,factoryId,lastSeenAt:Instant?,pendingCount,failedCount,diskFreeBytes,cacheRevision,mode:"ONLINE"|"OFFLINE"|"UNKNOWN"}`. Central đánh OFFLINE khi heartbeat quá 90 giây; không dùng trạng thái Central này để sửa source event.
- POST `/operations/jobs` `{type:"BACKUP"|"RESTORE"|"ARCHIVE"|"ANONYMIZE",factoryId,backupId:UUID?,employeeId:UUID?,reason}` → 202 Job; GET `/operations/jobs/{id}` → Job. RESTORE cần backupId, ANONYMIZE cần employeeId. system.manage và re-auth trong 5 phút bằng POST `/auth/reauth` `{password}` → 200 `{reauthenticatedUntil:Instant}` gắn phiên server. Không cho input shell command/file path.
- Job = `{id,type,state:"QUEUED"|"RUNNING"|"SUCCEEDED"|"FAILED",createdAt,completedAt:Instant?,errorCode:string?}`. Scheduler/restore an toàn và retention phải được Task 5/8 kiểm thử; endpoint không cho phép xóa dữ kiện công tùy ý.
- POST `/privacy/requests` `{type:"ACCESS"|"RECTIFY"|"ANONYMIZE",reason}` → 201 PrivacyRequest; GET cùng path trả Page theo SELF/Admin. PrivacyRequest `{id,userId,type,reason,state:"SUBMITTED"|"APPROVED"|"REJECTED",createdAt,decisionReason:string?,version}`. PUT `/privacy/requests/{id}/decision` `{decision:"APPROVE"|"REJECT",reason,version}` → object mới; system.manage, audit; không xóa event gốc để xử lý DSR.

### Task 6: QR

| API | Request | Response |
|---|---|---|
| GET `/me/qr-token` | Không nhận employeeId | 200 `{token:string,expiresAt:Instant}`; Cache-Control: no-store |
| POST `/employees/{id}/qr-credential` | `{reason}` | 201 Credential; đã ACTIVE trả 409 |
| POST `/employees/{id}/qr-credential/revoke` | `{reason}` | 200 Credential REVOKED |
| POST `/employees/{id}/qr-credential/reissue` | `{reason}` | 201 Credential mới; thu hồi cũ nguyên tử |
| GET `/qr/keys/refresh` | mTLS Edge | 200 KeySet ở mục 8; không nhận URL từ token |

Credential = `{id,employeeId,state:"ACTIVE"|"REVOKED",createdAt,revokedAt:Instant?}`. Mutation cần credential.manage và scope; một người tối đa một ACTIVE. Nhân viên có phiên hợp lệ mới lấy QR của mình, không lấy ID tùy ý từ client.

Header `{alg:"ES256",kid:string}`; payload `{sub:employeeId,iss:"tmc-central",aud:"tmc-gate",iat:int,nbf:int,exp:int,jti:UUIDv4}`. nbf=iat, exp=iat+60. Không factoryId, kiểm tra nhà máy/khu vực/ca qua cache. Skew v1 tối đa 5 giây: iat<=now+5, nbf<=now+5, now<exp+5, lifetime đúng 60. Không có thời gian tin cậy thì fail closed. Token đăng nhập/action grant không được dùng như QR.

Mỗi lần cấp có jti mới. QR chỉ hiển thị app/web sau đăng nhập; email/SMS chỉ thông báo/liên kết, không QR/JWS. Unknown kid từ chối lượt hiện tại, refresh chạy nền, không chờ Central trong scan.

## 5. Scan, điều khiển cổng và manual — Task 2/6/7

### API Edge LAN

| API (base `/edge/v1`) | Input | Output |
|---|---|---|
| POST `/scans` | mTLS kiosk; `{scanId:UUID,gateId:UUID,token:string}` | 200 ScanResult; store không hoạt động trả 503 ApiError |
| GET `/scans/{scanId}` | cùng kiosk/scope | ScanResult gần nhất hoặc 404 |
| POST `/overrides` | mTLS kiosk; `{scanId,gateId,employeeId:UUID?,reason,mode:"MANUAL"|"EMERGENCY",grant:string}` | 200 ScanResult; kiểm tra grant và scope |
| GET `/presence` | kiosk có quyền đọc tại factory | `{asOf:Instant,coverage:"LOCAL_EDGE",items:[{employeeId,lastDirection,lastOccurredAt}]}` |

ScanResult = `{scanId,decision:"ALLOW"|"DENY"|"EMERGENCY",reasonCode:string,eventId:UUID?,commandId:UUID?,commandStatus:"NOT_SENT"|"UNLOCK_ACCEPTED"|"FAILED"|"TIMEOUT",occurredAt:Instant,mode:"ONLINE"|"OFFLINE"}`. ALLOW là đã đủ điều kiện và lưu event, không khẳng định người đã qua cổng; UI phải phân biệt commandStatus. Chưa có cảm biến passage thì lỗi/timeout thiết bị phải gắn bất thường khi tính công.

reasonCode: OK, INVALID_TOKEN, TOKEN_EXPIRED, UNKNOWN_KID, REVOKED, REPLAY, OUTSIDE_POLICY, DUPLICATE_SCAN, GATE_DISABLED, CACHE_UNAVAILABLE, CLOCK_UNTRUSTED, EMERGENCY. Response không chứa raw token.

- Edge tự lấy time, edge/factory/direction từ cấu hình và clock tin cậy. Retry cùng scanId/cùng input trả outcome đã lưu; đổi input cùng scanId trả 409. Chỉ lưu fingerprint token, không token thô; dedup scan/command tối thiểu 48 giờ. Sau crash trạng thái command chưa rõ là TIMEOUT, không tự mở lại.
- ALLOW: verify → policy → transaction reserve replay + chống trùng nghiệp vụ + allocate sequence/event + delivery/command intent → commit → Turnstile → lưu EVT-03 riêng. Lỗi commit không mở cổng.
- Duplicate scan nghiệp vụ: cùng employeeId+gateId+direction trong 30 giây kể từ ALLOW/MANUAL gần nhất → DENY DUPLICATE_SCAN. Đây không phải replay jti hoặc dedup eventId.
- DENY ghi EVT-02 nếu store hoạt động; không đưa PII/claims đọc từ token chưa xác minh vào log. MANUAL ghi EVT-01 source MANUAL, operator/reason bắt buộc; EMERGENCY chỉ command/audit, không tạo công giả.
- Source ONLINE khi monitor nền có liên lạc Central thành công trong 60 giây gần nhất; ngoài ngưỡng OFFLINE. Không ping Central trong scan, không đổi source lúc sync lại.
- Presence LOCAL_EDGE không phải danh sách đầy đủ nhà máy khi nhiều Edge/mất WAN; UI bắt buộc hiện coverage/asOf. Snapshot Central cũng phải hiện độ trễ/máy offline.

### Re-auth cho override: lựa chọn v1

V1 manual/emergency qua web cần Central online để lấy action grant; quét QR tự động vẫn offline. Không tuyên bố manual/emergency web hoạt động suốt 24 giờ mất WAN; đây không phải cơ chế thoát hiểm vật lý.

POST Central `/auth/action-grants` với Bearer và `{password,action:"MANUAL"|"EMERGENCY",edgeId,gateId,employeeId:UUID?,reason}` → 200 `{grant:string,expiresAt:Instant}`. Require gate.override/gate.emergency và scope; reason 10–500 ký tự. MANUAL cần employeeId; EMERGENCY employeeId=null. Re-auth sai trả 401; không log password.

Grant ES256, header kid; payload `{sub:userId,iss:"tmc-central",aud:"tmc-edge-action",iat,nbf,exp,jti,action,edgeId,gateId,employeeId,reason}`, hạn 60 giây. Edge kiểm tra chữ ký bằng key tin cậy, thời gian/skew như QR, binding khớp body/certificate, reserve jti loại ACTION nguyên tử. QR aud=tmc-gate không dùng thay grant. MANUAL vẫn kiểm tra policy người được mở; không âm thầm bỏ qua quyền khu vực. Cấp grant quyền vượt policy cần contract mới.

### Turnstile mock

`unlock(commandId,gateId)` → `{commandId,status:"UNLOCK_ACCEPTED"|"FAILED"|"TIMEOUT",completedAt:Instant,code:string?}`. Timeout adapter 1 giây; dedup commandId ít nhất 48 giờ; không retry mù kết quả chưa rõ. Mock phải có kịch bản lỗi/timeout, không chỉ luôn success. p95 scan ≤2 giây là mục tiêu đo, không được bỏ durability để đạt thời gian.

## 6. Envelope và payload sự kiện — Task 3

Envelope: `{eventId:UUIDv7,eventType:string,schemaVersion:1,edgeId:UUID,gateId:UUID,factoryId:UUID,sequenceNo:int,correlationId:UUID,occurredAt:Instant,payloadSha256:string,payload:string}`. Event nền không thuộc một gate cụ thể dùng gateId của gate gốc trong từng nhóm report, không dùng UUID giả; nhóm replay report theo gate. Scan dùng scanId làm correlationId. Không có delivery state trong envelope.

**Wire payload là chuỗi JSON canonical, không phải JSON object.** Tạo object payload → canonical hóa JCS RFC 8785 → UTF-8 → SHA-256 hex chữ thường 64 ký tự. Lưu nguyên chuỗi/hash, không dựng lại khi retry. Cấm duplicate keys, NaN/Infinity và số ngoài phạm vi. Central parse để kiểm tra schema/canonical/hash. Payload hash không thay kiểm tra toàn bộ metadata envelope.

| eventType | Payload JSON bên trong chuỗi |
|---|---|
| attendance.event.recorded | `{employeeId,direction:"IN"|"OUT",source:"ONLINE"|"OFFLINE"|"MANUAL",jti:UUID?,kid:string?,tokenIssuedAt:Instant?,commandId:UUID,operatorId:UUID?,reason:string?}` |
| access.attempt.denied | `{employeeId:UUID?,reasonCode:string,jti:UUID?,kid:string?,source:"ONLINE"|"OFFLINE"}` |
| gate.command.result | `{commandId:UUID,attendanceEventId:UUID?,mode:"QR"|"MANUAL"|"EMERGENCY",operatorId:UUID?,reason:string?,status:"UNLOCK_ACCEPTED"|"FAILED"|"TIMEOUT",completedAt:Instant,code:string?}` |
| replay.cache.reported | `{items:[{jti:UUID,firstEventId:UUID,seenAt:Instant,expiresAt:Instant}]}` tối đa 50 items |

QR event bắt buộc jti/kid/tokenIssuedAt, operatorId/reason=null. MANUAL bắt buộc operatorId/reason, ba trường token=null. DENY chỉ có employeeId/jti/kid khi đã xác minh chữ ký/claims cần thiết. Source phản ánh lúc tạo, không đổi OFFLINE thành ONLINE lúc gửi.

Dedup eventId và unique(edgeId,sequenceNo). DUPLICATE chỉ khi toàn bộ envelope bất biến khớp; cùng eventId nhưng đổi type/time/gate/hash/payload là CONFLICT. receivedAt do Central bổ sung khi lưu, không thay occurredAt. Không từ chối event đến muộn chỉ vì QR hiện đã hết hạn. Dữ kiện nghi replay/thu hồi muộn vẫn lưu và gắn REPLAY_SUSPECTED/POST_REVOKE_VIOLATION, không xóa chứng cứ.

## 7. Sync và ACK — Task 3/7

POST Central `/attendance/sync`, mTLS. Request `{syncBatchId:UUIDv4,events:EventEnvelope[]}`: 1–50 event, sequence tăng dần, cùng identity certificate, body tối đa 1 MiB. Trùng eventId/sequence trong một lô là 400. V1 một batch active mỗi Edge; giữ ID/membership bền vững trước gửi.

Response 200 `{syncBatchId,acks:[{eventId,status:"ACCEPTED"|"DUPLICATE"|"CONFLICT"|"REJECTED",acceptedAt:Instant?,code:string?}],lastContiguousSequenceNo:int}`. Đúng một ACK/event, cùng thứ tự request. ACCEPTED/DUPLICATE có acceptedAt là commit lần đầu, code=null; CONFLICT/REJECTED acceptedAt=null, code bắt buộc.

- Code: EVENT_CONTENT_CONFLICT, SEQUENCE_CONFLICT, UNSUPPORTED_SCHEMA, UNSUPPORTED_EVENT_TYPE, INVALID_PAYLOAD, HASH_MISMATCH, UNKNOWN_GATE. Sai certificate/edge/factory trả 403 request, không trộn vào ACK event.
- Mỗi event một transaction, ACK sau commit. Lỗi payload xác định được → REJECTED; lỗi DB tạm thời → HTTP 503, không biến thành lỗi vĩnh viễn. Một số event đã commit trước 503 vẫn tồn tại, retry cùng lô nhận DUPLICATE.
- `(edgeId,syncBatchId)` lưu fingerprint nội dung/membership; retry cùng ID nhưng đổi lô trả 409 IDEMPOTENCY_CONFLICT. Với retry hợp lệ, ACCEPTED có thể thành DUPLICATE; không yêu cầu response bytes y hệt.
- Cursor là sequence lớn nhất mà mọi sequence từ 1 tới đó đã được chấp nhận/lưu. FAILED tạo gap, cursor không nhảy qua. ACK/xóa phải dựa từng event, không chỉ cursor; FAILED không chặn lô sau. Operator điều tra gap riêng, không tự reset sequence.
- Edge kiểm tra batchId và đủ tập eventId/status, không trùng/lạ/thiếu. Response ACK hỏng → protocol error, giữ lô retry/quarantine; không apply một phần response hỏng.
- Apply ACK/cursor/batch cùng transaction SQLite. ACCEPTED/DUPLICATE → ACKNOWLEDGED; CONFLICT/REJECTED → FAILED; timeout/mất ACK giữ PENDING. Batch COMPLETED khi mọi thành viên terminal. Không có SYNCED/PENDING_SYNC; trạng thái batch READY/IN_FLIGHT/RETRY_WAIT/COMPLETED là trục riêng.
- Connect timeout 3 giây, request timeout 10 giây; retry full jitter [0,min(300,5×2^attempt)] giây; 429 theo Retry-After. 401/403 dừng sync và cảnh báo. Retry giữ ID/hash/payload. Không tự xóa PENDING/FAILED; 30 ngày/50K là mốc cảnh báo.

## 8. Bootstrap/cache/revocation — Task 1/5/6/7

GET `/edge/bootstrap?afterRevision=0` qua mTLS. Task 7 tổng hợp public API Task 1/5/6; không đọc repository internal của module khác. Revision tăng khi policy, gate config, key hoặc revocation đổi; phải tạo snapshot nhất quán cho đúng factory/edge.

BootstrapResponse = `{mode:"FULL"|"DELTA",baseRevision:int,revision:int,generatedAt:Instant,trustedUntil:Instant,factoryId:UUID,edgeId:UUID,revocations:RevocationChange[],keySet:KeySet,policy:PolicySnapshot,gates:Gate[]}`.

- afterRevision=0 → FULL/baseRevision=0; có lịch sử đủ → DELTA/baseRevision bằng afterRevision; thiếu lịch sử → FULL. Revision không đổi trả 304 không body, header X-Revision. 304 không gia hạn trust; cần renew gọi afterRevision=0.
- DELTA chỉ incremental revocations; keySet/policy/gates luôn là snapshot đầy đủ thay thế nguyên tử. FULL chứa toàn bộ revocation còn hiệu lực. Revocations phải có lịch sử tại Central để đối soát event đến muộn.
- RevocationChange = `{id:UUID,op:"UPSERT"|"DELETE",kind:"SUB"|"JTI"|"KID",value:string,revokedBefore:Instant?,effectiveAt:Instant}`. SUB từ chối token có tokenIssuedAt<=revokedBefore; JTI/KID áp dụng mọi token khớp, revokedBefore=null. value là employeeId/jti/kid theo kind. DELETE theo id xóa khỏi active cache, không xóa lịch sử Central. Reissue phát iat sau cutoff (chờ sang giây tiếp nếu cần); nghỉ việc còn được policy active=false chặn.
- PolicySnapshot = `{version:int,employees:[{employeeId,active:boolean,windows:[{gateId,direction:"IN"|"OUT",startsAt:Instant,endsAt:Instant}]}]}`. Khoảng UTC [startsAt,endsAt), được Central sinh từ Assignment/Shift/timezone, phủ đến trustedUntil. Không phù hợp hoặc active=false → DENY; không tra mạng trong scan.
- KeySet = `{version:int,generatedAt:Instant,trustedUntil:Instant,keys:[{kid,kty:"EC",crv:"P-256",alg:"ES256",use:"sig",x:string,y:string,state:"ACTIVE"|"VERIFY_ONLY"|"REVOKED",notBefore:Instant,notAfter:Instant}]}`. x/y base64url, không private parameter d. Key REVOKED bị từ chối ngay trong cache. Rotation 90 ngày, overlap ít nhất 48h, phân phối key mới trước chuyển ký.
- Online poll mỗi 30 giây, mục tiêu revocation ≤60 giây. FULL cấp trustedUntil=generatedAt+48h, refresh FULL khi còn dưới 36h. Chỉ báo ready offline khi key/policy/cache còn hợp lệ ít nhất 24h; không giả vờ đủ trust nếu key notAfter sớm hơn.
- Xác thực TLS/binding, revision rồi commit cache và cursor cùng transaction. DELTA base không khớp local → lấy FULL. Revision rollback/cache thiếu/hỏng/khóa hết tin cậy → fail closed. Offline dùng cache hợp lệ cũ, chấp nhận cửa sổ thu hồi trễ; không tự gia hạn trust khi mất WAN.
- Reconnect gửi event và lấy revocation; Task 3 đối soát occurredAt/tokenIssuedAt với lịch sử effectiveAt để phát POST_REVOKE_VIOLATION, HR/an ninh xem qua Task 4.
- POST `/edge/heartbeat` mTLS `{cacheRevision,pendingCount,failedCount,diskFreeBytes,oldestPendingAt:Instant?,observedAt:Instant}` → 200 `{serverTime:Instant}`; chu kỳ 30 giây, không tạo event công. Task 7 gửi, Task 5 cung cấp màn hình.

## 9. Chấm công, báo cáo, audit và Payroll — Task 3/4

| API Central | Input/filter | Output / quyền |
|---|---|---|
| GET `/attendance/events` | employeeId?,factoryId?,from?,to?,page,size | Page<AttendanceEventView>; SELF/department/factory scope |
| GET `/attendance/records` | employeeId?,factoryId?,from,to (LocalDate),page,size | Page<DailyAttendance> |
| GET `/attendance/presence` | factoryId | `{asOf,coverage:"CENTRAL_SYNCED",items:[{employeeId,lastDirection,lastOccurredAt}],offlineEdgeIds:UUID[]}`; Guard/HR scope |
| POST `/attendance/adjustments` | `{eventId,proposedOccurredAt:Instant?,proposedDirection:"IN"|"OUT"|null,reason}` | 201 Adjustment; SELF hoặc HR scope |
| GET `/attendance/adjustments` | employeeId?,state?,page,size | Page<Adjustment>; SELF/HR, Auditor đọc lịch sử theo scope |
| POST `/attendance/adjustments/{id}/decision` | `{decision:"APPROVE"|"REJECT",reason,version}` | Adjustment; attendance.approve và khác người tạo |
| GET `/reports/attendance` | factoryId,from,to (LocalDate),employeeId?,page,size | Page<DailyAttendance>; report.read trong scope |
| GET `/reports/dashboard` | factoryId,date | `{asOf,expectedCount,presentCount,absentCount,anomalyCount,offlineEdgeIds:UUID[]}`; report.read |
| POST `/reports/exports` | `{factoryId,period,format:"CSV"|"XLSX"|"PDF"}` | 202 ReportExport; report.export |
| GET `/reports/exports/{id}` | — | ReportExport; chủ tạo hoặc HR có quyền export, cùng scope |
| GET `/reports/exports/{id}/file` | — | 200 binary đúng MIME khi READY, 409 chưa sẵn sàng; cùng quyền như đọc job |
| POST `/payroll/periods/{period}/close` | `{factoryId,reason}` | 202 PayrollBatch; payroll.export |
| POST `/payroll/periods/{period}/reopen` | `{factoryId,reason,version}` | Period; payroll.export và audit |
| GET `/payroll/batches/{id}` | — | PayrollBatch; HR trong scope |
| GET `/payroll/periods/{period}/history` | factoryId,page,size | Page<PeriodHistory>; HR hoặc audit.read theo scope |
| GET `/audit/events` | actorId?,action?,from?,to?,page,size | Page<AuditEntry>; audit.read theo scope |
| POST `/audit/exports` | `{factoryId,from,to,format:"CSV"|"XLSX"|"PDF"}` | 202 ReportExport; audit.export |
| GET `/audit/exports/{id}` và `/{id}/file` | — | Như report job/file, nhưng yêu cầu audit.export và scope |

AttendanceEventView = `{envelope:EventEnvelope,receivedAt:Instant,flags:string[]}`. DailyAttendance = `{employeeId,factoryId,shiftId,workDate,firstIn:Instant?,lastOut:Instant?,workedMinutes:int?,lateMinutes:int,earlyMinutes:int,flags:string[],version:int}`. Thiếu IN/OUT thì workedMinutes=null, không coi 0 giờ đã xác nhận. API từ chối filter vượt scope.

Adjustment = `{id,eventId,employeeId,createdBy,proposedOccurredAt,proposedDirection,reason,state:"PENDING"|"APPROVED"|"REJECTED",decidedBy:UUID?,decisionReason:string?,createdAt,decidedAt:Instant?,version}`. Ít nhất một proposed field không null; reason 10–500. Chỉ quyết định từ PENDING, version chống hai người duyệt. Lịch sử read-only, không đổi event gốc; bản ghi dẫn xuất dùng adjustment APPROVED.

ReportExport = `{id,state:"QUEUED"|"RUNNING"|"READY"|"FAILED",format,createdAt,expiresAt:Instant?,errorCode:string?}`. File download luôn auth, không URL public chứa token; expiresAt khi READY = createdAt+24h. Sau hạn GET file trả 404, có thể tạo export mới.

Period = `{factoryId,period,state:"OPEN"|"CLOSING"|"CLOSED"|"REOPENED",version,closedAt:Instant?}`. PeriodHistory = `{id,factoryId,period,action:"CLOSE"|"REOPEN",actorId:UUID?,reason,occurredAt,version}`; actorId=null chỉ cho scheduler có audit device/system identity.

Ca đêm kết thúc 06:00 ngày 01 thuộc tháng bắt đầu ca. Scheduler bắt đầu close 06:30 ngày 01 theo timezone Factory; không close sớm, 23:00 chỉ tạm. Export/đối soát trước 12:00. Event đến muộn vẫn lưu nhưng không tự đổi batch đã xuất; gắn LATE_EVENT_AFTER_CLOSE, HR reopen có lý do, version/batch mới khi xuất lại.

PayrollBatch = `{id,factoryId,period,periodVersion,state:"QUEUED"|"SENDING"|"EXPORT_SUCCESS"|"PARTIAL"|"FAILED",recordCount,acceptedCount,checksum,createdAt,completedAt:Instant?,errorCode:string?}`. Close tạo snapshot dữ liệu và job/outbox; Period CLOSED khi snapshot được cố định, export còn chạy độc lập. Deadline trễ phải cảnh báo, không ghi SUCCESS giả.

AuditEntry = `{id,actorId:UUID?,deviceId:UUID?,factoryId:UUID?,action,resourceType,resourceId:string,result:"SUCCESS"|"DENIED"|"FAILURE",occurredAt,correlationId,reason:string?}`. Audit append-only; không password/token/PII dư thừa. Manual override và lịch sử kỳ/điều chỉnh đều có action chuẩn `GATE_MANUAL`, `GATE_EMERGENCY`, `ATTENDANCE_ADJUSTMENT`, `PERIOD_CLOSE`, `PERIOD_REOPEN`; dùng resourceId để tra chi tiết. HR/Guard chỉ đọc audit phạm vi nghiệp vụ được cấp rõ, không mặc định toàn bộ audit.

### HR mock

POST `/hr/v1/payroll/batches` với service credential ngoài source; input `{batchId,factoryId,period,periodVersion,checksum,records:[{employeeId,workDate,shiftId,workedMinutes,lateMinutes,earlyMinutes}]}`. Không xuất workedMinutes=null.

Sort records theo employeeId,workDate,shiftId rồi checksum SHA-256 của JCS UTF-8. Cùng batchId/cùng nội dung trả ACK cũ; khác nội dung 409. Response 200 `{batchId,checksum,receivedCount,acceptedCount,rejected:[{employeeId,workDate,shiftId,code}]}`; accepted+rejected=received. SUCCESS chỉ khi đúng checksum, đủ count và không rejected. Retry giữ batch; sửa dữ liệu phải tạo periodVersion/batch mới, không ghi đè batch cũ. Task 4 mock cả timeout/partial/duplicate, không chỉ success.

## 10. Interface nội bộ và ranh giới transaction

Đây là chữ ký đích cần tạo/sửa, không phải khẳng định các class đã tồn tại đầy đủ. Context do trusted adapter tạo, không nhận trực tiếp từ body người dùng. Domain exception chứa code hợp đồng; không dùng null để giấu lỗi.

| Port/method | Input → Output | Chủ trì |
|---|---|---|
| Authentication.authenticate | LoginRequest → Session | 1 |
| Authorization.require | ActorContext,permission,resourceScope → void hoặc FORBIDDEN | 1 |
| EmployeeManagement.get | ActorContext,employeeId → EmployeeView tương ứng Employee | 1 |
| ConfigurationQuery.snapshot | factoryId → Factory/Gate/Shift snapshot cùng version | 5 |
| QrIssuer.issue | ActorContext → QrTokenResponse | 6; employeeId từ context |
| QrVerificationPort.verify | rawToken,now → VerifiedQr | 6; chưa phải ALLOW |
| AccessPolicyPort.evaluate | employeeId,gateId,direction,occurredAt → PolicyDecision | 2; chỉ cache cục bộ |
| LocalEventStorePort.reserveAndAppend | scanId,EventDraft,ReplayReservation? → CommittedEvent | 7; một transaction replay/sequence/event/delivery/command intent |
| LocalEventStorePort.append | EventDraft → CommittedEvent | 7; DENY/command/replay report, không bypass reserve cho QR ALLOW |
| LocalEventStorePort.pendingBatch | maxEvents<=50 → SyncBatchRequest? | 7; null = chưa có việc, persist membership trước trả |
| LocalEventStorePort.applyAcknowledgements | SyncBatchAck → void | 7; validate toàn response, ACK/cursor/batch nguyên tử |
| TurnstileControlPort.unlock | commandId,gateId → CommandResult | 2; chỉ sau commit |
| ScanQrUseCase.scan | ScanRequest,KioskContext → ScanResult | 2 |
| SyncPort.send | SyncBatchRequest → SyncBatchAck | 7; transport không update SQLite |
| BootstrapPort.fetch | afterRevision → BootstrapFetchResult | 7; phân biệt 304 và body |
| AttendanceSync.synchronize | DeviceContext,SyncBatchRequest → SyncBatchAck | 3 |
| AttendanceQueries.dailyRecords | ActorContext,AttendanceFilter → Page<DailyAttendance> | 3; Task 4 không đọc repository internal |
| AuditPort.append | AuditDraft → UUID | 4; phối hợp outbox cho thao tác đã commit |
| PayrollExportPort.export | ActorContext,factoryId,period → PayrollBatch | 4 |

- LoginRequest `{username,password}`; password nhạy cảm, không log. Context và DTO API có typed class/record tương ứng, không Map tùy ý ở module boundary.
- EventDraft `{eventType,schemaVersion:1,edgeId,gateId,factoryId,correlationId,occurredAt,payload:typed object}` không có eventId/sequence/hash. Store cấp UUIDv7, sequence, canonical/hash bên trong transaction; rollback không tạo sequence gap. Không tạo sequence bên ngoài rồi gọi append.
- ReplayReservation `{kind:"QR"|"ACTION",jti,expiresAt,employeeId:UUID?,direction,gateId}`; cùng transaction kiểm tra unique(kind,jti), business duplicate và lưu. QR reserve hết hạn sau exp+5s; token hết hạn bị verifier từ chối nên dọn reservation không cho phép replay token cũ. MANUAL dùng ACTION grant, không cần jti QR; EMERGENCY không tạo attendance event.
- CommittedEvent `{envelope:EventEnvelope,committedAt:Instant}`. Command intent/dedup scanId lưu cùng transaction để restart không phát lệnh lặp. EMERGENCY cần port `commitEmergency(scanId,CommandIntent,ReplayReservation)` → `CommittedCommand{commandId,committedAt}`; không ép tạo EVT-01 để dùng reserveAndAppend. Task 7 sở hữu port này, Task 2 dùng.
- CommandIntent `{commandId,gateId,operatorId:UUID?,reason:string?,mode:"QR"|"MANUAL"|"EMERGENCY"}`; QR/MANUAL commandId lấy từ attendance payload, EMERGENCY từ intent riêng. CommandResult là shape Turnstile ở mục 5.
- PolicyDecision `{allowed:boolean,reasonCode,policyVersion}`; KioskContext `{kioskId,edgeId,factoryId,gateIds:UUID[]}`; DeviceContext `{edgeId,factoryId}`; đều là dữ liệu tin cậy của adapter/certificate, không do client tự cấp.
- BootstrapFetchResult `{notModified:boolean,revision:int,bundle:BootstrapResponse?}`; 304 thì true/null; 200 thì false/bundle.
- AttendanceFilter `{factoryId:UUID?,employeeId:UUID?,from:LocalDate,to:LocalDate,page,size}` theo quy ước khoảng mục 2. AuditDraft là AuditEntry bỏ id, actor/device lấy từ context.

## 11. Ví dụ wire và điều kiện kiểm thử

Ví dụ envelope/payload canonical và ACK mixed có tại [CONTRACT-EXAMPLES.md](CONTRACT-EXAMPLES.md). Các ID trong fixture là dữ liệu giả để test, không phải tài khoản/thiết bị thật. Payload hash phải tính khớp, không copy placeholder hash vào integration test.

Khi implementation có, phải kiểm thử tối thiểu:

1. Phiên hết hạn/khóa; giả employeeId không đọc công/lấy QR người khác; Auditor bị chặn mutation ở backend.
2. Sai alg/kid/iss/aud/time bị từ chối; race cùng jti chỉ một transaction được mở; cache sai nhà máy/ca từ chối.
3. Crash trước commit không mở; sau commit trước command không tự mở lại; scanId retry không command lặp.
4. Sync lần đầu ACCEPTED, retry DUPLICATE; thay metadata hoặc payload cùng ID CONFLICT; mixed ACK không rollback cả lô.
5. DB lỗi sau vài commit trả 503, retry đúng; ACK hỏng giữ PENDING; FAILED gap không ngăn batch sau.
6. DELTA sai base lấy FULL; crash cache không lùi revision; 304 không gia hạn trust; offline 24h từ ready rồi đối soát thu hồi khi reconnect.
7. Ca đêm cuối tháng, 06:30 close, trước 12:00 export; event muộn không sửa kỳ đóng; HR partial/count mismatch không SUCCESS giả.
8. FE mock/BE dùng cùng shape/null/enum và lỗi; nối API thật vẫn chạy cùng kịch bản.

## 12. Những file sườn phải cập nhật theo v1

**Lượt chốt này chỉ sửa Markdown, chưa sửa Java/React hay tạo endpoint chạy thật.** Đừng coi stub hiện có là contract ưu tiên hơn tài liệu này.

| File/nhóm | Thay đổi cần làm trên nhánh của Task |
|---|---|
| EventEnvelope, Task 3 | Giữ schemaVersion int=1, payload String canonical; validation/hash/ID đầy đủ |
| AttendancePayload, Task 3 | Thêm tokenIssuedAt; typed payload cho DENY/command/replay report |
| BootstrapResponse, Task 7 | Thay String/list sơ sài bằng mode/baseRevision/trust, typed KeySet/PolicySnapshot/RevocationChange có thời điểm |
| SyncBatchRequest/SyncBatchAck, Task 7/3 | Validate membership, nullability, error code, immutable list và cursor semantics |
| Authentication/Service, Task 1 | Session thay String; ActorContext/permission, login/reauth/action grant |
| QrIssuer/QrController, Task 6 | Trusted ActorContext thay employeeId tùy ý; đủ claims/lifecycle |
| QrVerificationPort/AccessPolicyPort, Task 6/2 | now/context và PolicyDecision thay boolean thiếu lý do |
| LocalEventStorePort/SqliteStore, Task 7 | EventDraft → CommittedEvent, allocator trong transaction; emergency intent riêng |
| ScanQrUseCase/ScanController/TurnstileControlPort, Task 2 | ScanResult/CommandResult thay void; dedup scan/command |
| BootstrapPort/worker, Task 7 | 304/FULL/DELTA, bundle coherent và trust renewal |
| API/view/controller nghiệp vụ, chủ module | Request/response/context theo bảng; không lộ JPA/internal entity |
| api.js và Page, Task 8/chủ trang | Session memory; phân biệt HTTP error, scan DENY và ACK event; loading/empty/error |

Các checklist cũ ghi “chốt sau” về wire/API được thay bằng v1 này. Chữ ký nội bộ không nằm trong bảng vẫn do chủ module quyết định nếu không ảnh hưởng consumer; không tự thêm business behavior ngoài DECISIONS.


