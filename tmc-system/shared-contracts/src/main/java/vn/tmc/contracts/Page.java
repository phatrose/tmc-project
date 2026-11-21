package vn.tmc.contracts;

import java.util.Collections;
import java.util.List;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: DTO danh sách phân trang chuẩn theo CONTRACT-V1.md §2.
 * 
 * QUY ƯỚC:
 * - Page<T> = {items: T[], page: int, size: int, totalElements: int}
 * - page bắt đầu từ 0; size mặc định 20, tối đa 100.
 * - Sắp theo id tăng dần, riêng event/audit theo occurredAt giảm dần rồi id giảm dần.
 */
public record Page<T>(
        List<T> items,
        int page,
        int size,
        long totalElements
) {
    public Page {
        items = items != null ? Collections.unmodifiableList(items) : List.of();
    }

    public static <T> Page<T> of(List<T> items, int page, int size, long totalElements) {
        return new Page<>(items, page, size, totalElements);
    }

    public static <T> Page<T> empty(int page, int size) {
        return new Page<>(List.of(), page, size, 0L);
    }
}
