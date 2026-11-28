package vn.tmc.central.systemadmin.internal.model;

import java.time.ZoneId;
import java.util.Objects;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Domain Model cho Factory.
 * NGUỒN: main.pdf, §1.2; §5.1; DECISIONS.md §1.
 */
public record Factory(String id, String name, String zoneId, String address, String status) {
    public Factory {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        ZoneId.of(zoneId); // Kiểm tra zoneId hợp lệ theo chuẩn IANA
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
        }
    }

    public Factory(String id, String name, String zoneId) {
        this(id, name, zoneId, "", "ACTIVE");
    }
}
