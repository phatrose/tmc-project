package vn.tmc.central.attendanceprocessing.internal;
import org.springframework.context.annotation.*;
import vn.tmc.central.attendanceprocessing.internal.repository.InMemoryAttendanceRepository;
import vn.tmc.central.attendanceprocessing.internal.service.*;
/** PHỤ TRÁCH: Task 8. Explicit volatile preview storage, not production persistence. */
@Configuration(proxyBeanMethods=false) @Profile("integration-preview")
public class PreviewAttendanceConfiguration {
 @Bean InMemoryAttendanceRepository previewAttendanceRepository(){return new InMemoryAttendanceRepository();}
 @Bean AttendanceAdjustmentService previewAdjustments(InMemoryAttendanceRepository repo){return new AttendanceAdjustmentService(repo,repo);}
 @Bean AttendanceProcessingService previewAttendance(InMemoryAttendanceRepository repo,AttendanceAdjustmentService adjustments){return new AttendanceProcessingService(repo,repo,repo,adjustments);}
}
