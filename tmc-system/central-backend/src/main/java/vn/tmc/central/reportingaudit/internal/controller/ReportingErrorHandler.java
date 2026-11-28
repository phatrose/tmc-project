package vn.tmc.central.reportingaudit.internal.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.tmc.central.reportingaudit.internal.controller.ReportingHttp;
import vn.tmc.central.reportingaudit.internal.service.ReportingException;
import vn.tmc.contracts.ApiError;

@RestControllerAdvice(assignableTypes={ReportAuditController.class,HrMockController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class ReportingErrorHandler {
    @ExceptionHandler(ReportingException.class)
    ResponseEntity<ApiError> declared(ReportingException failure, HttpServletRequest request) {
        return error(request, failure.status(), failure.code(), failure.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, java.time.DateTimeException.class})
    ResponseEntity<ApiError> invalid(HttpServletRequest request) {
        return error(request, 400, "VALIDATION_ERROR", "Dữ liệu yêu cầu không hợp lệ");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> storage(HttpServletRequest request) {
        return error(request, 503, "STORAGE_UNAVAILABLE", "Kho dữ liệu báo cáo tạm thời không khả dụng");
    }

    @ExceptionHandler(java.util.NoSuchElementException.class)
    ResponseEntity<ApiError> missing(HttpServletRequest request) {
        return error(request,404,"NOT_FOUND","Không tìm thấy tài nguyên");
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> malformed(HttpServletRequest request) {
        return error(request,400,"MALFORMED_JSON","JSON yêu cầu không hợp lệ");
    }

    @ExceptionHandler({org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> binding(HttpServletRequest request) {
        return error(request,400,"VALIDATION_ERROR","Tham số yêu cầu không hợp lệ");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(HttpServletRequest request) {
        return error(request,500,"INTERNAL_ERROR","Không xử lý được yêu cầu báo cáo");
    }

    private ResponseEntity<ApiError> error(HttpServletRequest request, int status, String code, String message) {
        String requestId = ReportingHttp.requestId(request);
        return ResponseEntity.status(status).header("X-Request-Id", requestId).header("Cache-Control", "no-store")
                .body(new ApiError(code, message, requestId, status >= 500));
    }
}
