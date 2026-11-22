package vn.tmc.central.systemadmin.internal.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Domain Model cho Holiday (Ngày nghỉ lễ).
 * NGUỒN: main.pdf, FR-SA-02.
 */
public record Holiday(String id, String factoryId, String name, LocalDate date, boolean paid) {
    public Holiday {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(date, "date must not be null");
    }
}
