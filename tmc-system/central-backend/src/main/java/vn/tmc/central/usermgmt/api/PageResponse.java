package vn.tmc.central.usermgmt.api;

import java.util.List;

/**
 * PHỤ TRÁCH: Task 1.
 * Cấu trúc phân trang danh sách chuẩn theo CONTRACT-V1.md §2.
 * Shape wire: { items: T[], page: int, size: int, totalElements: int }
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements
) {}
