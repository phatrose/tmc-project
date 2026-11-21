package vn.tmc.central.usermgmt.internal.model;

import jakarta.persistence.*;
import vn.tmc.central.usermgmt.api.EmployeeView;
import vn.tmc.central.usermgmt.internal.service.VersionConflictException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 1.
 * JPA Entity đại diện cho nhân viên theo CONTRACT-V1.md §4.
 * Phiên bản wire: { id, employeeCode, displayName, factoryId, departmentId, active, version, updatedAt }
 */
@Entity
@Table(name = "employees")
public class Employee {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "employee_code", nullable = false, unique = true, length = 64)
    private String employeeCode;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "factory_id", nullable = false, length = 36)
    private String factoryId;

    @Column(name = "department_id", length = 36)
    private String departmentId;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "anonymized_at")
    private Instant anonymizedAt;

    protected Employee() {}

    public Employee(String employeeCode, String displayName, String factoryId, String departmentId, boolean active) {
        this.id = UUID.randomUUID().toString();
        this.employeeCode = Objects.requireNonNull(employeeCode, "employeeCode required");
        this.displayName = Objects.requireNonNull(displayName, "displayName required");
        this.factoryId = Objects.requireNonNull(factoryId, "factoryId required");
        this.departmentId = departmentId;
        this.active = active;
        this.version = 1;
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getEmployeeCode() { return employeeCode; }
    public String getDisplayName() { return displayName; }
    public String getFactoryId() { return factoryId; }
    public String getDepartmentId() { return departmentId; }
    public boolean isActive() { return active; }
    public int getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getAnonymizedAt() { return anonymizedAt; }

    public void update(String employeeCode, String displayName, String factoryId, String departmentId, boolean active, int expectedVersion) {
        if (this.version != expectedVersion) {
            throw new VersionConflictException(expectedVersion, this.version);
        }
        if (employeeCode != null) this.employeeCode = employeeCode;
        if (displayName != null) this.displayName = displayName;
        if (factoryId != null) this.factoryId = factoryId;
        this.departmentId = departmentId;
        this.active = active;
        this.version++;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.version++;
        this.updatedAt = Instant.now();
    }

    public EmployeeView toView() {
        return new EmployeeView(
                id,
                employeeCode,
                displayName,
                factoryId,
                departmentId,
                active,
                version,
                updatedAt
        );
    }
}
