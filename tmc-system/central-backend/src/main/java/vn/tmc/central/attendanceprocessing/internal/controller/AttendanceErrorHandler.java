package vn.tmc.central.attendanceprocessing.internal.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.tmc.central.attendanceprocessing.internal.service.DeviceBindingException;
import vn.tmc.central.attendanceprocessing.internal.service.IdempotencyConflictException;
import vn.tmc.contracts.ApiError;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * PHỤ TRÁCH: Task 3.
 * Ánh xạ ngoại lệ của module attendance-processing sang ApiError chuẩn theo CONTRACT-V1.md §2, §7, §9.
 */
@RestControllerAdvice(assignableTypes = {AttendanceController.class, AttendanceSyncController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class AttendanceErrorHandler {

    private static final String REQUEST_ID_ATTR = "vn.tmc.central.PlatformErrors.requestId";
    private static final Pattern UUID4 = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

    @ExceptionHandler(DeviceBindingException.class)
    ResponseEntity<ApiError> deviceBinding(DeviceBindingException ex, HttpServletRequest req, HttpServletResponse res) {
        return error(req, res, HttpStatus.FORBIDDEN, "DEVICE_BINDING_MISMATCH", ex.getMessage(), false);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ResponseEntity<ApiError> idempotencyConflict(IdempotencyConflictException ex, HttpServletRequest req, HttpServletResponse res) {
        return error(req, res, HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", ex.getMessage(), false);
    }

    @ExceptionHandler({SecurityException.class, AccessDeniedException.class})
    ResponseEntity<ApiError> forbidden(RuntimeException ex, HttpServletRequest req, HttpServletResponse res) {
        return error(req, res, HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage(), false);
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    ResponseEntity<ApiError> unauthenticated(AuthenticationCredentialsNotFoundException ex, HttpServletRequest req, HttpServletResponse res) {
        return error(req, res, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Cần đăng nhập để thực hiện thao tác.", false);
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiError> conflict(IllegalStateException ex, HttpServletRequest req, HttpServletResponse res) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "Trạng thái không hợp lệ.";
        String code = msg.contains("VERSION_CONFLICT") ? "VERSION_CONFLICT" : "INVALID_STATE";
        return error(req, res, HttpStatus.CONFLICT, code, msg, false);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> badRequest(IllegalArgumentException ex, HttpServletRequest req, HttpServletResponse res) {
        return error(req, res, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage(), false);
    }

    private static ResponseEntity<ApiError> error(HttpServletRequest req, HttpServletResponse res,
                                                  HttpStatus status, String code, String message, boolean retryable) {
        String requestId = resolveRequestId(req, res);
        return ResponseEntity.status(status)
                .header("X-Request-Id", requestId)
                .header("Cache-Control", "no-store")
                .body(new ApiError(code, message, requestId, retryable));
    }

    private static String resolveRequestId(HttpServletRequest req, HttpServletResponse res) {
        Object attr = req != null ? req.getAttribute(REQUEST_ID_ATTR) : null;
        if (attr != null) {
            return attr.toString();
        }
        String headerOut = res != null ? res.getHeader("X-Request-Id") : null;
        if (headerOut != null && UUID4.matcher(headerOut).matches()) {
            return headerOut;
        }
        String headerIn = req != null ? req.getHeader("X-Request-Id") : null;
        if (headerIn != null && UUID4.matcher(headerIn).matches()) {
            return headerIn;
        }
        return UUID.randomUUID().toString();
    }
}
