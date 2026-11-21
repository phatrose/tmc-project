package vn.tmc.central.attendanceprocessing.internal.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tmc.central.attendanceprocessing.api.AttendanceSync;
import vn.tmc.contracts.DeviceContext;
import vn.tmc.contracts.SyncBatchAck;
import vn.tmc.contracts.SyncBatchRequest;

import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: Controller tiếp nhận đồng bộ sự kiện từ Edge theo CONTRACT-V1.md §7.
 * Endpoint: POST /api/v1/attendance/sync (mTLS + edge/factory binding).
 */
@RestController
@RequestMapping("/api/v1/attendance")
public final class AttendanceSyncController {
    private final AttendanceSync service;

    public AttendanceSyncController(AttendanceSync service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    @PostMapping(value = {"/sync"})
    public ResponseEntity<SyncBatchAck> synchronize(
            @RequestBody SyncBatchRequest batch,
            @RequestHeader(value = "X-Edge-Id", required = false) String edgeIdHeader,
            @RequestHeader(value = "X-Factory-Id", required = false) String factoryIdHeader
    ) {
        if (batch == null || batch.syncBatchId() == null || batch.events() == null) {
            return ResponseEntity.badRequest().build();
        }

        DeviceContext deviceContext = null;
        if (edgeIdHeader != null && factoryIdHeader != null) {
            try {
                deviceContext = new DeviceContext(UUID.fromString(edgeIdHeader), UUID.fromString(factoryIdHeader));
            } catch (IllegalArgumentException ignored) {}
        }

        SyncBatchAck ack = service.synchronize(deviceContext, batch);
        return ResponseEntity.ok(ack);
    }
}
