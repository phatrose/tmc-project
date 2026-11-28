package vn.tmc.central;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.tmc.central.attendanceprocessing.api.AttendanceFilter;
import vn.tmc.central.attendanceprocessing.internal.model.DailyAttendanceRecord;
import vn.tmc.central.attendanceprocessing.internal.repository.InMemoryAttendanceRepository;
import vn.tmc.central.attendanceprocessing.internal.service.AttendanceProcessingService;
import vn.tmc.central.usermgmt.api.*;
import vn.tmc.central.usermgmt.internal.model.UserAccount;
import vn.tmc.central.usermgmt.internal.repository.UserAccountRepository;
import vn.tmc.central.usermgmt.internal.service.AuthenticationService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * PHỤ TRÁCH: Task 8 — tích hợp với Task 1 & Task 3.
 * Kiểm thử hồi quy trực tiếp cho AUTH-07 (re-auth gắn phiên), GRANT-03/08 (đủ claims ES256),
 * và SCOPE-02/03/04/05/07/08 (fail-closed, DEPARTMENT, FACTORY, SELF, totalElements).
 */
class ContractSecurityAndScopeTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void auth07_reauthIsBoundToSpecificSessionNotUser() {
        UserAccountRepository repo = mock(UserAccountRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String userId = UUID.randomUUID().toString();
        UserAccount acc = new UserAccount("guard1", encoder.encode("StrongPass123!"), null,
                List.of(Role.GUARD), List.of(UUID.randomUUID().toString()), List.of(), ActorContext.SCOPE_FACTORY);
        when(repo.findByUsername("guard1")).thenReturn(Optional.of(acc));
        when(repo.findById(acc.getId())).thenReturn(Optional.of(acc));

        Es256ActionGrantIssuer issuer = new Es256ActionGrantIssuer("grant-k1", Clock.systemUTC(), JSON);
        AuthenticationService auth = new AuthenticationService(repo, encoder, issuer);

        Session sessionA = auth.authenticate(new Authentication.LoginRequest("guard1", "StrongPass123!"));
        Session sessionB = auth.authenticate(new Authentication.LoginRequest("guard1", "StrongPass123!"));

        auth.reauth("Bearer " + sessionA.accessToken(), new Authentication.ReauthRequest("StrongPass123!"));

        assertTrue(auth.isSessionReauthenticated("Bearer " + sessionA.accessToken()));
        assertFalse(auth.isSessionReauthenticated("Bearer " + sessionB.accessToken()));

        auth.logout("Bearer " + sessionA.accessToken());
        assertFalse(auth.isSessionReauthenticated("Bearer " + sessionA.accessToken()));
    }

    @Test
    void grant03_and_grant08_actionGrantContainsAllContractClaimsAndValidatesInputs() throws Exception {
        UserAccountRepository repo = mock(UserAccountRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String factoryId = UUID.randomUUID().toString();
        String edgeId = UUID.randomUUID().toString();
        String gateId = UUID.randomUUID().toString();
        String empId = UUID.randomUUID().toString();
        UserAccount acc = new UserAccount("guard2", encoder.encode("StrongPass123!"), null,
                List.of(Role.GUARD), List.of(factoryId), List.of(), ActorContext.SCOPE_FACTORY);
        String userId = acc.getId();
        when(repo.findByUsername("guard2")).thenReturn(Optional.of(acc));
        when(repo.findById(userId)).thenReturn(Optional.of(acc));

        Clock fixed = Clock.fixed(Instant.parse("2026-11-28T02:00:00Z"), ZoneOffset.UTC);
        Es256ActionGrantIssuer issuer = new Es256ActionGrantIssuer("grant-k1", fixed, JSON);
        AuthenticationService auth = new AuthenticationService(repo, encoder, issuer);
        Session session = auth.authenticate(new Authentication.LoginRequest("guard2", "StrongPass123!"));

        // Short reason rejected (GRANT-08)
        assertThrows(IllegalArgumentException.class, () -> auth.issueActionGrant(
                session.actor(),
                new Authentication.ActionGrantRequest("StrongPass123!", "MANUAL", edgeId, gateId, empId, "short")));

        // Valid MANUAL grant contains all 12 CONTRACT-V1 §5 claims (GRANT-03)
        ActionGrantResponse res = auth.issueActionGrant(
                session.actor(),
                new Authentication.ActionGrantRequest("StrongPass123!", "MANUAL", edgeId, gateId, empId, "Mo cong thu cong do mat ket noi the"));
        String[] parts = res.grant().split("\\.");
        assertEquals(3, parts.length);
        Map<String, Object> claims = JSON.readValue(Base64.getUrlDecoder().decode(parts[1]), new TypeReference<>() {});
        assertEquals(userId, claims.get("sub"));
        assertEquals("tmc-central", claims.get("iss"));
        assertEquals("tmc-edge-action", claims.get("aud"));
        assertEquals("MANUAL", claims.get("action"));
        assertEquals(edgeId, claims.get("edgeId"));
        assertEquals(gateId, claims.get("gateId"));
        assertEquals(empId, claims.get("employeeId"));
        assertEquals("Mo cong thu cong do mat ket noi the", claims.get("reason"));
        long iat = ((Number) claims.get("iat")).longValue();
        long exp = ((Number) claims.get("exp")).longValue();
        assertEquals(60L, exp - iat);
        assertNotNull(claims.get("jti"));
    }

    @Test
    void scope02_to_08_enforcesFailClosedDepartmentFactoryAndTotalElements() {
        InMemoryAttendanceRepository repo = new InMemoryAttendanceRepository();
        UUID factory1 = UUID.randomUUID();
        UUID factory2 = UUID.randomUUID();
        UUID empDeptA = UUID.randomUUID();
        UUID empDeptB = UUID.randomUUID();
        UUID empFactory2 = UUID.randomUUID();
        String deptA = UUID.randomUUID().toString();
        String deptB = UUID.randomUUID().toString();

        LocalDate day = LocalDate.of(2026, 11, 20);
        UUID shiftId = UUID.randomUUID();
        repo.save(new DailyAttendanceRecord("r1", empDeptA, factory1, day, shiftId, "SHIFT-1"));
        repo.save(new DailyAttendanceRecord("r2", empDeptB, factory1, day, shiftId, "SHIFT-1"));
        repo.save(new DailyAttendanceRecord("r3", empFactory2, factory2, day, shiftId, "SHIFT-1"));

        Map<UUID, String> empDepts = Map.of(empDeptA, deptA, empDeptB, deptB, empFactory2, deptA);
        AttendanceProcessingService service = new AttendanceProcessingService(repo, repo, repo, null, empDepts::get);
        AttendanceFilter allFilter = new AttendanceFilter(null, null, day, day.plusDays(1), 0, 20);

        // SCOPE-05: null actor or empty factoryIds/departmentIds must fail closed
        assertThrows(SecurityException.class, () -> service.dailyRecords(null, allFilter));
        ActorContext emptyFactoryHr = new ActorContext(UUID.randomUUID().toString(), null, List.of(Role.HR),
                List.of("attendance.read"), List.of(), List.of(), ActorContext.SCOPE_FACTORY);
        assertThrows(SecurityException.class, () -> service.dailyRecords(emptyFactoryHr, allFilter));

        ActorContext emptyDeptSup = new ActorContext(UUID.randomUUID().toString(), null, List.of(Role.SUPERVISOR),
                List.of("attendance.read"), List.of(factory1.toString()), List.of(), ActorContext.SCOPE_DEPARTMENT);
        assertThrows(SecurityException.class, () -> service.dailyRecords(emptyDeptSup, allFilter));

        // SCOPE-03 & SCOPE-08: Supervisor in Dept A only sees Dept A and totalElements == 1
        ActorContext supDeptA = new ActorContext(UUID.randomUUID().toString(), null, List.of(Role.SUPERVISOR),
                List.of("attendance.read"), List.of(factory1.toString()), List.of(deptA), ActorContext.SCOPE_DEPARTMENT);
        var supPage = service.dailyRecords(supDeptA, allFilter);
        assertEquals(1, supPage.totalElements());
        assertEquals(empDeptA, supPage.items().get(0).employeeId());
        assertThrows(SecurityException.class, () -> service.dailyRecords(supDeptA,
                new AttendanceFilter(null, empDeptB, day, day.plusDays(1), 0, 20)));

        // SCOPE-04: HR in Factory 1 only sees Factory 1 (2 records), cannot query Factory 2
        ActorContext hrF1 = new ActorContext(UUID.randomUUID().toString(), null, List.of(Role.HR),
                List.of("attendance.read"), List.of(factory1.toString()), List.of(), ActorContext.SCOPE_FACTORY);
        var hrPage = service.dailyRecords(hrF1, allFilter);
        assertEquals(2, hrPage.totalElements());
        assertThrows(SecurityException.class, () -> service.dailyRecords(hrF1,
                new AttendanceFilter(factory2, null, day, day.plusDays(1), 0, 20)));

        // SCOPE-02: SELF cannot read another employee
        ActorContext selfEmp = new ActorContext(UUID.randomUUID().toString(), empDeptA.toString(), List.of(Role.EMPLOYEE),
                List.of("employee.self"), List.of(factory1.toString()), List.of(deptA), ActorContext.SCOPE_SELF);
        assertThrows(SecurityException.class, () -> service.dailyRecords(selfEmp,
                new AttendanceFilter(null, empDeptB, day, day.plusDays(1), 0, 20)));
    }

    @Test
    void scope10_hrCannotApproveAdjustmentOutsideAssignedFactory() {
        InMemoryAttendanceRepository repo = new InMemoryAttendanceRepository();
        UUID factory1 = UUID.randomUUID();
        UUID factory2 = UUID.randomUUID();
        UUID empInFactory2 = UUID.randomUUID();

        var adjService = new vn.tmc.central.attendanceprocessing.internal.service.AttendanceAdjustmentService(
                repo, repo, empId -> empId.equals(empInFactory2) ? factory2.toString() : factory1.toString());

        ActorContext empActor = new ActorContext(UUID.randomUUID().toString(), empInFactory2.toString(),
                List.of(Role.EMPLOYEE), List.of("employee.self"), List.of(factory2.toString()), List.of(), ActorContext.SCOPE_SELF);
        var created = adjService.createAdjustment(empActor, new vn.tmc.central.attendanceprocessing.api.AdjustmentRequest(
                null, Instant.parse("2026-11-20T10:00:00Z"), "OUT", "Quen quet ra do mat dien tai cong F2"));

        ActorContext hrFactory1 = new ActorContext(UUID.randomUUID().toString(), null,
                List.of(Role.HR), List.of("attendance.approve"), List.of(factory1.toString()), List.of(), ActorContext.SCOPE_FACTORY);
        assertThrows(SecurityException.class, () -> adjService.decideAdjustment(
                hrFactory1, created.id(), new vn.tmc.central.attendanceprocessing.api.AdjustmentDecision("APPROVE", "Duyet", 1)));

        ActorContext hrFactory2 = new ActorContext(UUID.randomUUID().toString(), null,
                List.of(Role.HR), List.of("attendance.approve"), List.of(factory2.toString()), List.of(), ActorContext.SCOPE_FACTORY);
        var approved = adjService.decideAdjustment(
                hrFactory2, created.id(), new vn.tmc.central.attendanceprocessing.api.AdjustmentDecision("APPROVE", "Xac nhan dung", 1));
        assertEquals("APPROVED", approved.state());
    }

    @Test
    void idem02_06_07_concurrentDuplicateIdempotencyKeyExecutesOnceAndRejectsDifferentPayload() throws Exception {
        InMemoryAttendanceRepository repo = new InMemoryAttendanceRepository();
        var adjService = new vn.tmc.central.attendanceprocessing.internal.service.AttendanceAdjustmentService(repo, repo);
        var processing = new AttendanceProcessingService(repo, repo, repo, adjService);
        var controller = new vn.tmc.central.attendanceprocessing.internal.controller.AttendanceController(processing, adjService);

        ActorContext empActor = new ActorContext(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                List.of(Role.EMPLOYEE), List.of("employee.self"), List.of(UUID.randomUUID().toString()), List.of(), ActorContext.SCOPE_SELF);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(empActor, "tok", List.of()));
        try {
            var req = new vn.tmc.central.attendanceprocessing.api.AdjustmentRequest(
                    null, Instant.parse("2026-11-20T10:00:00Z"), "OUT", "Quen quet ra do cong bao tri dong thoi");
            // IDEM-02: non-UUIDv4 rejected
            assertThrows(IllegalArgumentException.class, () -> controller.createAdjustment(req, "not-a-uuid"));

            // IDEM-07: concurrent requests with same UUIDv4 execute side effect only ONCE
            String key = UUID.randomUUID().toString();
            var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
            var startLatch = new java.util.concurrent.CountDownLatch(1);
            List<java.util.concurrent.Future<UUID>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> {
                    org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(empActor, "tok", List.of()));
                    startLatch.await();
                    return controller.createAdjustment(req, key).getBody().id();
                }));
            }
            startLatch.countDown();
            Set<UUID> createdIds = new HashSet<>();
            for (var f : futures) createdIds.add(f.get());
            pool.shutdownNow();
            assertEquals(1, createdIds.size());
            assertEquals(1, repo.findAll().size());

            // IDEM-06: same key with different payload throws IdempotencyConflictException
            var otherReq = new vn.tmc.central.attendanceprocessing.api.AdjustmentRequest(
                    null, Instant.parse("2026-11-20T11:00:00Z"), "OUT", "Ly do khac cho cung mot idempotency key");
            assertThrows(vn.tmc.central.attendanceprocessing.internal.service.IdempotencyConflictException.class,
                    () -> controller.createAdjustment(otherReq, key));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void data07_deactivatingEmployeeWritesRevocationOutboxAndRevokesSessions() {
        var empRepo = mock(vn.tmc.central.usermgmt.internal.repository.EmployeeRepository.class);
        var policyRepo = mock(vn.tmc.central.usermgmt.internal.repository.PolicyAssignmentRepository.class);
        var deptRepo = mock(vn.tmc.central.usermgmt.internal.repository.DepartmentRepository.class);
        var accRepo = mock(vn.tmc.central.usermgmt.internal.repository.UserAccountRepository.class);
        var outboxRepo = mock(vn.tmc.central.usermgmt.internal.repository.EmployeeRevocationOutboxRepository.class);
        var auth = mock(Authentication.class);

        String factoryId = UUID.randomUUID().toString();
        var emp = new vn.tmc.central.usermgmt.internal.model.Employee("NV007", "Nguyen Van B", factoryId, null, true);
        var linkedAcc = new UserAccount("nv007", "hash", emp.getId(), List.of(Role.EMPLOYEE),
                List.of(factoryId), List.of(), ActorContext.SCOPE_SELF);
        when(empRepo.findById(emp.getId())).thenReturn(Optional.of(emp));
        when(accRepo.findByEmployeeId(emp.getId())).thenReturn(Optional.of(linkedAcc));

        var service = new vn.tmc.central.usermgmt.internal.service.EmployeeService(
                empRepo, policyRepo, deptRepo, accRepo, new BCryptPasswordEncoder(), auth, outboxRepo);
        ActorContext admin = new ActorContext(UUID.randomUUID().toString(), null, List.of(Role.ADMIN),
                List.of("employee.manage"), List.of(), List.of(), ActorContext.SCOPE_GLOBAL);

        // Cập nhật giữ active=true -> không tạo outbox thu hồi
        service.updateEmployee(admin, emp.getId(), new EmployeeManagement.UpdateEmployeeCommand(
                "NV007", "Nguyen Van B2", factoryId, null, true, emp.getVersion()));
        verify(outboxRepo, never()).save(any());

        // Tắt nhân viên (active=false) -> tạo outbox EMPLOYEE_DEACTIVATED và thu hồi phiên
        service.updateEmployee(admin, emp.getId(), new EmployeeManagement.UpdateEmployeeCommand(
                "NV007", "Nguyen Van B2", factoryId, null, false, emp.getVersion()));
        verify(outboxRepo, times(1)).save(argThat(entry ->
                emp.getId().equals(entry.getEmployeeId())
                        && "EMPLOYEE_DEACTIVATED".equals(entry.getEventType())
                        && "PENDING".equals(entry.getStatus())));
        verify(auth, times(1)).revokeSessionsForUser(linkedAcc.getId());
    }

    @Test
    void feAdmin05_and_08_enforcesOptimisticVersionConflictAndSessionReauthForSensitiveJobs() throws Exception {
        var adminPort = mock(vn.tmc.central.systemadmin.api.Administration.class);
        var gatePort = mock(vn.tmc.central.systemadmin.api.GateManagement.class);
        var auth = mock(Authentication.class);

        when(adminPort.updateConfiguration("dup.window", "30"))
                .thenReturn(new vn.tmc.central.systemadmin.api.SystemConfigurationView("dup.window", "30"));
        when(adminPort.getRetentionPolicy())
                .thenReturn(new vn.tmc.central.systemadmin.api.RetentionPolicyView(2, 5, 12, "BR-11"));

        var controller = new vn.tmc.central.systemadmin.internal.controller.PreviewSystemAdminController(
                adminPort, gatePort, auth);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();

        ActorContext admin = new ActorContext(UUID.randomUUID().toString(), null, List.of(Role.ADMIN),
                List.of("system.manage"), List.of(), List.of(), ActorContext.SCOPE_GLOBAL);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(admin, "tok", List.of()));
        try {
            // FE-ADMIN-05: version=1 khớp -> 200 + X-Resource-Version: 2
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .put("/api/v1/system/config/dup.window")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"value\":\"30\",\"version\":1}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Resource-Version", "2"));

            // FE-ADMIN-05: gửi lại version=1 cũ -> 409 VERSION_CONFLICT
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .put("/api/v1/system/config/dup.window")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"value\":\"30\",\"version\":1}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("VERSION_CONFLICT"));

            // FE-ADMIN-03: GET & PUT /api/v1/system/config đúng DTO CONTRACT-V1 §4 và khóa đủ 4 tham số cố định
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/system/config"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.version").value(1))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.duplicateScanWindowSeconds").value(30))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.qrTtlSeconds").value(60))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.revocationPollSeconds").value(30))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.offlineTargetHours").value(24));

            // Từ chối sửa bất kỳ tham số cố định nào (ví dụ offlineTargetHours=48)
            assertThrows(Exception.class, () -> mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .put("/api/v1/system/config")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"version\":1,\"duplicateScanWindowSeconds\":30,\"qrTtlSeconds\":60,\"revocationPollSeconds\":30,\"offlineTargetHours\":48}")));

            // PUT /api/v1/system/config hợp lệ -> 200 & tăng version lên 2, gửi lại version=1 -> 409 VERSION_CONFLICT
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .put("/api/v1/system/config")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"version\":1,\"duplicateScanWindowSeconds\":30,\"qrTtlSeconds\":60,\"revocationPollSeconds\":30,\"offlineTargetHours\":24}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.version").value(2));
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .put("/api/v1/system/config")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"version\":1,\"duplicateScanWindowSeconds\":30,\"qrTtlSeconds\":60,\"revocationPollSeconds\":30,\"offlineTargetHours\":24}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("VERSION_CONFLICT"));

            // FE-ADMIN-04: GET /api/v1/operations/edges đánh ONLINE (<90s) và OFFLINE (>90s)
            when(adminPort.getDeviceStatuses(null)).thenReturn(List.of(
                    new vn.tmc.central.systemadmin.api.DeviceStatusView(UUID.randomUUID().toString(), "EDGE", "Edge-1", "F1", "ONLINE", Instant.now().minusSeconds(10), "1.0", "10.0.0.1", null),
                    new vn.tmc.central.systemadmin.api.DeviceStatusView(UUID.randomUUID().toString(), "EDGE", "Edge-2", "F1", "ONLINE", Instant.now().minusSeconds(120), "1.0", "10.0.0.2", null)
            ));
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/operations/edges"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.totalElements").value(2))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].mode").value("ONLINE"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[1].mode").value("OFFLINE"));

            // FE-ADMIN-08: chưa re-auth -> 403 REAUTH_REQUIRED
            when(auth.isSessionReauthenticated("Bearer tokA")).thenReturn(false);
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .post("/api/v1/operations/jobs")
                            .header("Authorization", "Bearer tokA")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"type\":\"ARCHIVE\",\"reason\":\"Luu tru dinh ky du lieu cu\"}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("REAUTH_REQUIRED"));

            // FE-ADMIN-08: đã re-auth -> 202 Job DTO (QUEUED) và GET /operations/jobs/{id} trả đúng job; RESTORE thiếu backupId bị chặn
            when(auth.isSessionReauthenticated("Bearer tokA")).thenReturn(true);
            assertThrows(Exception.class, () -> mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .post("/api/v1/operations/jobs")
                            .header("Authorization", "Bearer tokA")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"type\":\"RESTORE\",\"reason\":\"Khoi phuc khong co backupId\"}")));

            String jobJson = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .post("/api/v1/operations/jobs")
                            .header("Authorization", "Bearer tokA")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"type\":\"ARCHIVE\",\"reason\":\"Luu tru dinh ky du lieu cu\"}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.state").value("QUEUED"))
                    .andReturn().getResponse().getContentAsString();
            String jobId = JSON.readTree(jobJson).get("id").asText();
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/operations/jobs/" + jobId))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.id").value(jobId));

            // FE-ADMIN-07: POST /privacy/requests → 201 SUBMITTED, PUT /privacy/requests/{id}/decision kiểm tra version (409)
            String dsrJson = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .post("/api/v1/privacy/requests")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"type\":\"ACCESS\",\"reason\":\"Yeu cau trich xuat ho so ca nhan\"}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.state").value("SUBMITTED"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.version").value(1))
                    .andReturn().getResponse().getContentAsString();
            String dsrId = JSON.readTree(dsrJson).get("id").asText();
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .put("/api/v1/privacy/requests/" + dsrId + "/decision")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"decision\":\"APPROVE\",\"reason\":\"Da xac minh danh tinh\",\"version\":99}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("VERSION_CONFLICT"));
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .put("/api/v1/privacy/requests/" + dsrId + "/decision")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"decision\":\"APPROVE\",\"reason\":\"Da xac minh danh tinh\",\"version\":1}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.state").value("APPROVED"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.version").value(2));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void scope09_and_grant01_legacyAttendanceScopeAndCrossTokenIsolation() {
        // SCOPE-09: legacy endpoints view/{employeeId} & summary chặn SELF đọc người khác
        InMemoryAttendanceRepository repo = new InMemoryAttendanceRepository();
        var adjService = new vn.tmc.central.attendanceprocessing.internal.service.AttendanceAdjustmentService(repo, repo);
        var processing = new AttendanceProcessingService(repo, repo, repo, adjService);
        var controller = new vn.tmc.central.attendanceprocessing.internal.controller.AttendanceController(processing, adjService);

        String selfEmpId = UUID.randomUUID().toString();
        String otherEmpId = UUID.randomUUID().toString();
        ActorContext selfActor = new ActorContext(UUID.randomUUID().toString(), selfEmpId,
                List.of(Role.EMPLOYEE), List.of("employee.self"), List.of(UUID.randomUUID().toString()), List.of(), ActorContext.SCOPE_SELF);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(selfActor, "tok", List.of()));
        try {
            assertThrows(SecurityException.class, () -> controller.findAttendance(otherEmpId));
            assertThrows(SecurityException.class, () -> controller.getMonthlySummary(otherEmpId, "2026-11", "F01"));
            assertNotNull(controller.findAttendance(selfEmpId).getBody());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }

        // GRANT-01: phân biệt opaque access token với Action Grant JWS
        UserAccountRepository accRepo = mock(UserAccountRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        UserAccount acc = new UserAccount("guard.iso", encoder.encode("StrongPass123!"), null,
                List.of(Role.GUARD), List.of(UUID.randomUUID().toString()), List.of(), ActorContext.SCOPE_FACTORY);
        when(accRepo.findByUsername("guard.iso")).thenReturn(Optional.of(acc));
        when(accRepo.findById(acc.getId())).thenReturn(Optional.of(acc));

        Es256ActionGrantIssuer issuer = new Es256ActionGrantIssuer("grant-k1", Clock.systemUTC(), JSON);
        AuthenticationService auth = new AuthenticationService(accRepo, encoder, issuer);
        Session session = auth.authenticate(new Authentication.LoginRequest("guard.iso", "StrongPass123!"));

        assertNotEquals(3, session.accessToken().split("\\.").length, "Access token phải là opaque, không phải JWS");
        ActionGrantResponse grant = auth.issueActionGrant(session.actor(), new Authentication.ActionGrantRequest(
                "StrongPass123!", "EMERGENCY", UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                null, "Kiem tra co lap token khan cap tai cong"));
        assertThrows(vn.tmc.central.usermgmt.internal.service.InvalidCredentialsException.class,
                () -> auth.getActorContext("Bearer " + grant.grant()));
    }

    @Test
    void auth04_allAccountAndEmployeeDeactivationPathsRevokeSessionsAndReauth() {
        var empRepo = mock(vn.tmc.central.usermgmt.internal.repository.EmployeeRepository.class);
        var policyRepo = mock(vn.tmc.central.usermgmt.internal.repository.PolicyAssignmentRepository.class);
        var deptRepo = mock(vn.tmc.central.usermgmt.internal.repository.DepartmentRepository.class);
        var accRepo = mock(vn.tmc.central.usermgmt.internal.repository.UserAccountRepository.class);
        var outboxRepo = mock(vn.tmc.central.usermgmt.internal.repository.EmployeeRevocationOutboxRepository.class);

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String factoryId = UUID.randomUUID().toString();
        var emp = new vn.tmc.central.usermgmt.internal.model.Employee("NV004", "Tran Van C", factoryId, null, true);
        var acc = new UserAccount("nv004", encoder.encode("StrongPass123!"), emp.getId(),
                List.of(Role.HR), List.of(factoryId), List.of(), ActorContext.SCOPE_FACTORY);
        when(empRepo.findById(emp.getId())).thenReturn(Optional.of(emp));
        when(accRepo.findById(acc.getId())).thenReturn(Optional.of(acc));
        when(accRepo.findByUsername("nv004")).thenReturn(Optional.of(acc));
        when(accRepo.findByEmployeeId(emp.getId())).thenReturn(Optional.of(acc));

        Es256ActionGrantIssuer issuer = new Es256ActionGrantIssuer("grant-k1", Clock.systemUTC(), JSON);
        AuthenticationService auth = new AuthenticationService(accRepo, encoder, issuer);
        var service = new vn.tmc.central.usermgmt.internal.service.EmployeeService(
                empRepo, policyRepo, deptRepo, accRepo, encoder, auth, outboxRepo);
        ActorContext admin = new ActorContext(UUID.randomUUID().toString(), null, List.of(Role.ADMIN),
                List.of("system.manage", "employee.manage"), List.of(), List.of(), ActorContext.SCOPE_GLOBAL);

        // Đường 1: thay đổi role tài khoản -> thu hồi phiên + re-auth
        Session s1 = auth.authenticate(new Authentication.LoginRequest("nv004", "StrongPass123!"));
        auth.reauth("Bearer " + s1.accessToken(), new Authentication.ReauthRequest("StrongPass123!"));
        assertTrue(auth.isSessionReauthenticated("Bearer " + s1.accessToken()));

        service.updateAccount(admin, acc.getId(), new EmployeeManagement.UpdateAccountCommand(
                List.of(Role.EMPLOYEE), List.of(factoryId), List.of(), ActorContext.SCOPE_SELF, true, acc.getVersion()));
        assertFalse(auth.isSessionReauthenticated("Bearer " + s1.accessToken()));
        assertThrows(vn.tmc.central.usermgmt.internal.service.InvalidCredentialsException.class,
                () -> auth.getActorContext("Bearer " + s1.accessToken()));

        // Đường 2: tắt nhân viên -> thu hồi phiên mới của tài khoản liên kết
        Session s2 = auth.authenticate(new Authentication.LoginRequest("nv004", "StrongPass123!"));
        service.updateEmployee(admin, emp.getId(), new EmployeeManagement.UpdateEmployeeCommand(
                "NV004", "Tran Van C", factoryId, null, false, emp.getVersion()));
        assertThrows(vn.tmc.central.usermgmt.internal.service.InvalidCredentialsException.class,
                () -> auth.getActorContext("Bearer " + s2.accessToken()));
    }

    @Test
    void idem09_createAccountSameKeyDifferentPasswordConflictsWithoutExposingRawPassword() throws Exception {
        var empMgmt = mock(EmployeeManagement.class);
        var auth = mock(Authentication.class);
        ActorContext admin = new ActorContext(UUID.randomUUID().toString(), null, List.of(Role.ADMIN),
                List.of("system.manage"), List.of(), List.of(), ActorContext.SCOPE_GLOBAL);
        when(auth.getActorContext("Bearer tok")).thenReturn(admin);
        var createdView = new AccountView(UUID.randomUUID().toString(), "acc.test", null,
                List.of(Role.EMPLOYEE), List.of(), List.of(), ActorContext.SCOPE_SELF, true, 1);
        when(empMgmt.createAccount(eq(admin), any())).thenReturn(createdView);

        var controller = new vn.tmc.central.usermgmt.internal.controller.EmployeeController(empMgmt, auth);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();

        String key = UUID.randomUUID().toString();
        String body1 = "{\"username\":\"acc.test\",\"employeeId\":null,\"roles\":[\"EMPLOYEE\"],\"factoryIds\":[],\"departmentIds\":[],\"scope\":\"SELF\",\"initialPassword\":\"SecretPass123456!\"}";
        String body2 = "{\"username\":\"acc.test\",\"employeeId\":null,\"roles\":[\"EMPLOYEE\"],\"factoryIds\":[],\"departmentIds\":[],\"scope\":\"SELF\",\"initialPassword\":\"DifferentPass9876!\"}";

        // Lần 1 -> 201 Created
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/accounts")
                        .header("Authorization", "Bearer tok")
                        .header("Idempotency-Key", key)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());

        // Cùng key + cùng mật khẩu -> replay 201 (không gọi createAccount lần 2)
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/accounts")
                        .header("Authorization", "Bearer tok")
                        .header("Idempotency-Key", key)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());
        verify(empMgmt, times(1)).createAccount(eq(admin), any());

        // Cùng key + mật khẩu khác -> 409 IDEMPOTENCY_CONFLICT
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/accounts")
                        .header("Authorization", "Bearer tok")
                        .header("Idempotency-Key", key)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }
}
