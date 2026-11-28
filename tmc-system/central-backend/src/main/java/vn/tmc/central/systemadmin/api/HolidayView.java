package vn.tmc.central.systemadmin.api;

import java.time.LocalDate;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Public View Record cho Holiday.
 * NGUỒN: main.pdf, FR-SA-02.
 */
public record HolidayView(
    String id,
    String factoryId,
    String name,
    LocalDate date,
    boolean paid
) {}
