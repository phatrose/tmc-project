package vn.tmc.edge.gatestation.domain;

import vn.tmc.contracts.QrClaims;

/**
 * PHỤ TRÁCH: Task 6.
 * Khung triển khai theo docs/DECISIONS.md; chưa có nghiệp vụ hoạt động.
 * Kết quả xác minh chữ ký/claims, chưa đồng nghĩa đã được phép mở cổng.
 * TODO: kiểm tra policy và reserve replay nguyên tử trước khi command.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Mang claims/kid từ verifier sang use case mà không cần truyền lại token thô.
 * - Cần làm: Dùng sub để tra policy, jti để reserve replay; chỉ verifier tin cậy mới nên tạo kết quả này.
 * - Lưu ý bàn giao: Record không tự kiểm tra chữ ký hoặc replay; tồn tại instance không đồng nghĩa có quyền mở cổng.
 */
public record VerifiedQr(QrClaims claims, String kid) {}
