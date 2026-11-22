package vn.tmc.central.systemadmin.api;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Public View Record cho Factory.
 * NGUỒN: main.pdf, §1.2; DECISIONS.md §1.
 */
public record FactoryView(
    String id,
    String name,
    String zoneId,
    String address,
    String status
) {
    public FactoryView(String id, String name, String zoneId) {
        this(id, name, zoneId, "", "ACTIVE");
    }
}
