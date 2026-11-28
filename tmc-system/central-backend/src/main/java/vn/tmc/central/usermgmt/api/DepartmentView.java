package vn.tmc.central.usermgmt.api;

/**
 * PHỤ TRÁCH: Task 1.
 * DTO đại diện cho phòng ban theo CONTRACT-V1.md §4.
 *
 * @param id mã UUID phòng ban
 * @param factoryId mã UUID nhà máy trực thuộc
 * @param name tên phòng ban
 * @param version số phiên bản phục vụ optimistic locking
 */
public record DepartmentView(
        String id,
        String factoryId,
        String name,
        int version
) {}
