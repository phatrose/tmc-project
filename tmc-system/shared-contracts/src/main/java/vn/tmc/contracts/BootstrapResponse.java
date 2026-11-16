package vn.tmc.contracts;

import java.util.List;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 7.
 * VAI TRÒ: DTO bootstrap.
 * NGUỒN: main.pdf, §4.2, §6.2.3.
 * TÀI LIỆU PDF: docs/tasks/04-api-contracts.md; docs/REQUIREMENTS.md.
 * TODO[Task 7]: afterRevision/delta thu hồi, revision mới, JWKS và policy snapshot trong factory scope.
 * TODO[Task 7]: Không chứa private key; chốt kiểu ID và contract delta đầy đủ trước khi dùng.
 * PHỐI HỢP: Task 2, Task 3, Task 6, Task 8.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: DTO bootstrap chứa revision, public JWKS, revocation và policy snapshot.
 * - Cần làm: Chốt định dạng snapshot/delta, phạm vi nhà máy và cách xử lý revision thiếu/lùi với issuer.
 * - Lưu ý bàn giao: String/list hiện là kiểu khung; không nhận private key và không cập nhật cursor trước khi commit toàn bộ cache.
 */
public record BootstrapResponse(long revision, String publicJwks, List<String> revokedSubjects, List<String> revokedJtis, List<String> revokedKids, String policySnapshot) {}
