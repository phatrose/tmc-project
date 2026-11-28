package vn.tmc.central.usermgmt.internal.model;

import jakarta.persistence.*;
import vn.tmc.central.usermgmt.api.AccountView;
import vn.tmc.central.usermgmt.api.Role;
import vn.tmc.central.usermgmt.internal.service.VersionConflictException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 1.
 * JPA Entity tài khoản đăng nhập theo CONTRACT-V1.md §3, §4.
 * Tách biệt khỏi hồ sơ nhân viên; quản lý roles, factoryIds, departmentIds, scope.
 */
@Entity
@Table(name = "user_accounts")
public class UserAccount {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "username", nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "employee_id", length = 36)
    private String employeeId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_account_roles", joinColumns = @JoinColumn(name = "account_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private List<Role> roles = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_account_factories", joinColumns = @JoinColumn(name = "account_id"))
    @Column(name = "factory_id", nullable = false)
    private List<String> factoryIds = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_account_departments", joinColumns = @JoinColumn(name = "account_id"))
    @Column(name = "department_id", nullable = false)
    private List<String> departmentIds = new ArrayList<>();

    @Column(name = "scope", nullable = false, length = 50)
    private String scope; // "SELF" | "DEPARTMENT" | "FACTORY" | "GLOBAL"

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserAccount() {}

    public UserAccount(String username, String passwordHash, String employeeId,
                       List<Role> roles, List<String> factoryIds, List<String> departmentIds,
                       String scope) {
        this.id = UUID.randomUUID().toString();
        this.username = Objects.requireNonNull(username, "username required");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash required");
        this.employeeId = employeeId;
        this.roles = roles != null ? new ArrayList<>(roles) : new ArrayList<>();
        this.factoryIds = factoryIds != null ? new ArrayList<>(factoryIds) : new ArrayList<>();
        this.departmentIds = departmentIds != null ? new ArrayList<>(departmentIds) : new ArrayList<>();
        this.scope = scope != null ? scope : "SELF";
        this.active = true;
        this.version = 1;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getEmployeeId() { return employeeId; }
    public List<Role> getRoles() { return Collections.unmodifiableList(roles); }
    public List<String> getFactoryIds() { return Collections.unmodifiableList(factoryIds); }
    public List<String> getDepartmentIds() { return Collections.unmodifiableList(departmentIds); }
    public String getScope() { return scope; }
    public boolean isActive() { return active; }
    // PHỤ TRÁCH: Task 1. Contract V1 §4 dùng active cho trạng thái tài khoản; không có khóa tạm riêng.
    public boolean isLocked() { return !active; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }

    public void update(List<Role> roles, List<String> factoryIds, List<String> departmentIds,
                       String scope, boolean active, int expectedVersion) {
        if (this.version != expectedVersion) {
            throw new VersionConflictException(expectedVersion, this.version);
        }
        if (roles != null) this.roles = new ArrayList<>(roles);
        if (factoryIds != null) this.factoryIds = new ArrayList<>(factoryIds);
        if (departmentIds != null) this.departmentIds = new ArrayList<>(departmentIds);
        if (scope != null) this.scope = scope;
        this.active = active;
        this.version++;
    }

    public void updatePassword(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash);
    }

    public AccountView toView() {
        return new AccountView(
                id,
                username,
                employeeId,
                Collections.unmodifiableList(roles),
                Collections.unmodifiableList(factoryIds),
                Collections.unmodifiableList(departmentIds),
                scope,
                active,
                version
        );
    }
}
