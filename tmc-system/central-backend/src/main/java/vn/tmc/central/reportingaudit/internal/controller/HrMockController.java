package vn.tmc.central.reportingaudit.internal.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import vn.tmc.central.reportingaudit.internal.model.HrPayrollExchange.Receipt;
import vn.tmc.central.reportingaudit.internal.service.HrMockService;

@RestController
@Profile("hr-mock")
public final class HrMockController {
    private final HrMockService service;private final long delay;
    public HrMockController(HrMockService service,@Value("${tmc.hr-mock.delay-ms:20000}") long delay){this.service=service;this.delay=delay;}
    @PostMapping("/hr/v1/payroll/batches")
    public Receipt receive(@RequestHeader(value="Authorization",required=false) String authorization,
            @RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody byte[] body)throws InterruptedException {
        var receipt=service.receive(authorization,key,body);
        if(service.mode()==HrMockService.Mode.TIMEOUT)Thread.sleep(delay);
        return receipt;
    }
}
