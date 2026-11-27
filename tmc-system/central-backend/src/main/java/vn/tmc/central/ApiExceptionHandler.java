package vn.tmc.central;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import vn.tmc.contracts.ApiError;

/** PHỤ TRÁCH: Task 8. Lỗi transport chung; DENY/ACK nghiệp vụ vẫn là HTTP 200. */
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(HttpServletRequest req) {
        return error(req, 403, "FORBIDDEN", "Access is denied.", false);
    }
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> unauthenticated(HttpServletRequest req) {
        return error(req, 401, "UNAUTHENTICATED", "Authentication is required.", false);
    }
    @ExceptionHandler(vn.tmc.central.qrsecurity.api.QrSecurityException.class)
    ResponseEntity<ApiError> qr(vn.tmc.central.qrsecurity.api.QrSecurityException ex,HttpServletRequest req){
        return error(req,ex.httpStatus(),ex.code(),ex.getMessage(),ex.retryable());
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> declared(ApiException ex, HttpServletRequest req) {
        return error(req, ex.status(), ex.code(), ex.getMessage(), ex.retryable());
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> malformed(HttpServletRequest req) {
        return error(req, 400, "MALFORMED_JSON", "Invalid JSON request.", false);
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> invalid(HttpServletRequest req) {
        return error(req, 400, "VALIDATION_ERROR", "Invalid request values.", false);
    }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> missing(HttpServletRequest req) {
        return error(req, 404, "NOT_FOUND", "Resource not found.", false);
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(HttpServletRequest req) {
        return error(req, 500, "INTERNAL_ERROR", "Request could not be completed.", true);
    }
    private ResponseEntity<ApiError> error(HttpServletRequest req, int status, String code,
                                            String message, boolean retryable) {
        return ResponseEntity.status(status).header("X-Request-Id", PlatformErrors.requestId(req))
                .header("Cache-Control", "no-store")
                .body(new ApiError(code, message, PlatformErrors.requestId(req), retryable));
    }
}
