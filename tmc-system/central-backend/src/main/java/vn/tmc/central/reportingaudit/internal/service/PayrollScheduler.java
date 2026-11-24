package vn.tmc.central.reportingaudit.internal.service;

import vn.tmc.central.reportingaudit.internal.model.PeriodClosingSchedule;
import vn.tmc.central.reportingaudit.api.ReportingSources.ConfigurationQuery;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;

public final class PayrollScheduler {
    private final java.util.function.Supplier<ConfigurationQuery> configuration;
    private final PayrollExportService payroll;
    private final Clock clock;
    public PayrollScheduler(ConfigurationQuery configuration,PayrollExportService payroll,Clock clock) {
        this(()->configuration,payroll,clock);
    }
    public PayrollScheduler(java.util.function.Supplier<ConfigurationQuery> configuration,PayrollExportService payroll,Clock clock) {
        this.configuration=configuration;this.payroll=payroll;this.clock=clock;
    }
    @org.springframework.scheduling.annotation.Scheduled(cron="${tmc.reporting.closing-cron:0,30 * * * * *}",zone="UTC")
    public void tick() {
        var source=configuration.get();if(source==null)return;
        for(var factory:source.factories()) {
            var zone=ZoneId.of(factory.zoneId());var last=YearMonth.from(clock.instant().atZone(zone)).minusMonths(1);
            for(var month=payroll.firstPeriod(factory.id(),last);!month.isAfter(last);month=month.plusMonths(1)) {
                if(clock.instant().isBefore(PeriodClosingSchedule.closeAt(month,zone)))continue;
                try{payroll.scheduledClose(factory.id(),month);}
                catch(RuntimeException failure){System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"Payroll close pending for factory {0} / {1}",factory.id(),month);}
                try{payroll.warnDeadline(factory.id(),month);}
                catch(RuntimeException failure){System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"Payroll deadline check unavailable for factory {0}",factory.id());}
            }
        }
    }
}
