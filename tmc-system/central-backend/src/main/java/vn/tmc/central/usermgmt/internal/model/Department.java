package vn.tmc.central.usermgmt.internal.model;

import jakarta.persistence.*;
import vn.tmc.central.usermgmt.api.DepartmentView;

import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 1.
 * JPA Entity phòng ban theo CONTRACT-V1.md §4.
 */
@Entity
@Table(name = "departments")
public class Department {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "factory_id", nullable = false, length = 36)
    private String factoryId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "version", nullable = false)
    private int version;

    protected Department() {}

    public Department(String factoryId, String name) {
        this.id = UUID.randomUUID().toString();
        this.factoryId = Objects.requireNonNull(factoryId, "factoryId required");
        this.name = Objects.requireNonNull(name, "name required");
        this.version = 1;
    }

    public String getId() { return id; }
    public String getFactoryId() { return factoryId; }
    public String getName() { return name; }
    public int getVersion() { return version; }

    public void update(String name, int expectedVersion) {
        if (this.version != expectedVersion) {
            throw new vn.tmc.central.usermgmt.internal.service.VersionConflictException(expectedVersion, this.version);
        }
        this.name = Objects.requireNonNull(name);
        this.version++;
    }

    public DepartmentView toView() {
        return new DepartmentView(id, factoryId, name, version);
    }
}
