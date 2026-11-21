package vn.tmc.central.usermgmt.internal.model;

import jakarta.persistence.*;
import vn.tmc.central.usermgmt.api.EmployeePolicy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 1.
 * JPA Entity lưu trữ phân quyền khu vực / ca làm việc của nhân viên theo CONTRACT-V1.md §4.
 */
@Entity
@Table(name = "policy_assignments")
public class PolicyAssignment {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "employee_id", nullable = false, length = 36)
    private String employeeId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "policy_assignment_gates", joinColumns = @JoinColumn(name = "assignment_id"))
    @Column(name = "gate_id", nullable = false)
    private List<String> gateIds = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "policy_assignment_shifts", joinColumns = @JoinColumn(name = "assignment_id"))
    @Column(name = "shift_id", nullable = false)
    private List<String> shiftIds = new ArrayList<>();

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "version", nullable = false)
    private int version;

    protected PolicyAssignment() {}

    public PolicyAssignment(String employeeId, List<String> gateIds, List<String> shiftIds, Instant validFrom, Instant validUntil, int version) {
        this.id = UUID.randomUUID().toString();
        this.employeeId = employeeId;
        this.gateIds = gateIds != null ? new ArrayList<>(gateIds) : new ArrayList<>();
        this.shiftIds = shiftIds != null ? new ArrayList<>(shiftIds) : new ArrayList<>();
        this.validFrom = validFrom != null ? validFrom : Instant.now();
        this.validUntil = validUntil;
        this.version = version;
    }

    public String getId() { return id; }
    public String getEmployeeId() { return employeeId; }
    public List<String> getGateIds() { return Collections.unmodifiableList(gateIds); }
    public List<String> getShiftIds() { return Collections.unmodifiableList(shiftIds); }
    public Instant getValidFrom() { return validFrom; }
    public Instant getValidUntil() { return validUntil; }
    public int getVersion() { return version; }

    public EmployeePolicy.Assignment toAssignment() {
        return new EmployeePolicy.Assignment(
                Collections.unmodifiableList(gateIds),
                Collections.unmodifiableList(shiftIds),
                validFrom,
                validUntil
        );
    }
}
