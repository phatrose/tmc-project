package vn.tmc.central.reportingaudit.internal.service;

import vn.tmc.central.reportingaudit.api.ReportingSources.ActorContext;
import vn.tmc.central.reportingaudit.api.ReportingSources.EmployeeSource;
import vn.tmc.central.reportingaudit.api.ReportingSources.EmployeeInfo;
import vn.tmc.central.usermgmt.api.Role;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class ReportingAccess {
    private final Supplier<EmployeeSource> employees;

    public ReportingAccess(Supplier<EmployeeSource> employees) {
        this.employees = Objects.requireNonNull(employees);
    }

    public void require(ActorContext actor, String permission) {
        if (actor == null) throw new ReportingException("UNAUTHENTICATED", "Cần đăng nhập", 401);
        if (!actor.permissions().contains(permission))
            throw new ReportingException("FORBIDDEN", "Không có quyền thực hiện chức năng", 403);
    }

    public void factory(ActorContext actor, UUID factoryId) {
        if (actor == null) throw new ReportingException("UNAUTHENTICATED", "Cần đăng nhập", 401);
        if (factoryId == null) throw new IllegalArgumentException("factoryId bắt buộc");
        if ("GLOBAL".equals(actor.scope())) {
            if (!actor.roles().contains(Role.ADMIN)) denied();
        } else if (!actor.factoryIds().contains(factoryId.toString())) denied();
        if (!java.util.Set.of("SELF", "DEPARTMENT", "FACTORY", "GLOBAL").contains(actor.scope())) denied();
    }

    public boolean employee(ActorContext actor, UUID factoryId, UUID employeeId) {
        factory(actor, factoryId);
        if ("SELF".equals(actor.scope())) return employeeId.toString().equals(actor.employeeId());
        if (!"DEPARTMENT".equals(actor.scope())) return true;
        EmployeeSource source = employees.get();
        if (source == null) throw ReportingException.unavailable("EmployeeSource");
        EmployeeInfo employee;
        try { employee = source.getEmployee(actor, employeeId.toString()); }
        catch (RuntimeException failure) { throw ReportingException.unavailable("phạm vi nhân viên"); }
        if (employee == null || !employeeId.toString().equals(employee.id())
                || !factoryId.toString().equals(employee.factoryId()))
            throw ReportingException.unavailable("phạm vi nhân viên");
        return employee.departmentId() != null && actor.departmentIds().contains(employee.departmentId());
    }

    public void audit(ActorContext actor, UUID factoryId) {
        factory(actor, factoryId);
    }

    public void payroll(ActorContext actor, UUID factoryId) {
        require(actor, "payroll.export");
        factory(actor, factoryId);
        if (!actor.roles().contains(Role.HR) || actor.roles().contains(Role.AUDITOR))
            throw new ReportingException("FORBIDDEN", "Chốt kỳ yêu cầu HR và không có vai trò Auditor", 403);
        if (!"FACTORY".equals(actor.scope()) && !"GLOBAL".equals(actor.scope())) denied();
    }

    private static void denied() {
        throw new ReportingException("SCOPE_MISMATCH", "Dữ liệu nằm ngoài phạm vi được cấp", 403);
    }
}
