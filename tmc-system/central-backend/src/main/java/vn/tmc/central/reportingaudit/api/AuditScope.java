package vn.tmc.central.reportingaudit.api;

import java.util.UUID;

public record AuditScope(UUID factoryId, UUID employeeId, UUID departmentId) {}
