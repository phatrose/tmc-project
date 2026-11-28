package vn.tmc.central.attendanceprocessing.internal.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tmc.central.attendanceprocessing.api.AttendanceSync;
import vn.tmc.central.attendanceprocessing.internal.service.DeviceBindingException;
import vn.tmc.contracts.DeviceContext;
import vn.tmc.contracts.SyncBatchAck;
import vn.tmc.contracts.SyncBatchRequest;

import java.util.Objects;

/**
 * PHỤ TRÁCH: Task 3 & Task 8 (DEVICE-01, DEVICE-03).
 * VAI TRÒ: Controller tiếp nhận đồng bộ sự kiện từ Edge theo CONTRACT-V1.md §7.
 * Endpoint: POST /api/v1/attendance/sync — CHỈ đọc DeviceContext từ mTLS authenticated principal,
 * tuyệt đối KHÔNG đọc header X-Edge-Id/X-Factory-Id tự khai.
 */
@RestController
@org.springframework.context.annotation.Profile("!platform")
@RequestMapping("/api/v1/attendance")
public final class AttendanceSyncController {
    private final AttendanceSync service;

    public AttendanceSyncController(AttendanceSync service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    @PostMapping(value = {"/sync"})
    public ResponseEntity<SyncBatchAck> synchronize(@RequestBody SyncBatchRequest batch) {
        if (batch == null || batch.syncBatchId() == null || batch.events() == null) {
            throw new IllegalArgumentException("SyncBatchRequest không hợp lệ.");
        }

        DeviceContext deviceContext = resolveMtlsDeviceContext();
        if (deviceContext == null) {
            throw new DeviceBindingException("Yêu cầu chứng thư số mTLS hợp lệ cho thiết bị Edge.");
        }

        SyncBatchAck ack = service.synchronize(deviceContext, batch);
        return ResponseEntity.ok(ack);
    }

    private static DeviceContext resolveMtlsDeviceContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof DeviceContext dc) {
            return dc;
        }
        return null;
    }
}
