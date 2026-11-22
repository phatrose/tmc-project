package vn.tmc.central.reportingaudit.internal.service;

import vn.tmc.central.reportingaudit.internal.model.HrPayrollExchange.Receipt;

public interface HrPayrollPort {
    Receipt send(PayrollPayload.Prepared payload);
}
