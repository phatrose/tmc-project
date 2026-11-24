package vn.tmc.central.reportingaudit;
import com.tngtech.archunit.core.importer.*;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ReportingArchitectureTest {
    @Test void reportingPreservesModuleAndDomainBoundaries() {
        var classes=new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages("vn.tmc.central.reportingaudit");
        noClasses().should().dependOnClassesThat().resideInAnyPackage("vn.tmc.central.attendanceprocessing.internal..",
                "vn.tmc.central.usermgmt.internal..","vn.tmc.central.systemadmin.internal..").check(classes);
        noClasses().that().resideInAPackage("..reportingaudit.internal.model..").should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..","jakarta.persistence..","..reportingaudit.internal.adapter..","..reportingaudit.internal.controller..").check(classes);
        noClasses().that().resideInAPackage("..reportingaudit.internal.service..").should().dependOnClassesThat()
                .resideInAnyPackage("..reportingaudit.internal.adapter..","..reportingaudit.internal.controller..").check(classes);
    }
}
