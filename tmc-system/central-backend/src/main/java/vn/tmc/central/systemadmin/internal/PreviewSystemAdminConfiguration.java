package vn.tmc.central.systemadmin.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import vn.tmc.central.systemadmin.api.Administration;
import vn.tmc.central.systemadmin.api.GateManagement;
import vn.tmc.central.systemadmin.internal.repository.*;
import vn.tmc.central.systemadmin.internal.service.AdministrationService;
import vn.tmc.central.systemadmin.internal.service.GateConfigurationService;

/**
 * PHỤ TRÁCH: Task 5 / Task 8. Wiring in-memory cho module system-admin trong profile integration-preview.
 */
@Configuration(proxyBeanMethods = false)
@Profile("integration-preview")
public class PreviewSystemAdminConfiguration {

    @Bean
    SystemConfigurationRepository previewSystemConfigRepo() {
        return new InMemorySystemConfigurationRepository();
    }

    @Bean
    FactoryRepository previewFactoryRepo() {
        return new InMemoryFactoryRepository();
    }

    @Bean
    GateRepository previewGateRepo() {
        return new InMemoryGateRepository();
    }

    @Bean
    DeviceStatusRepository previewDeviceStatusRepo() {
        return new InMemoryDeviceStatusRepository();
    }

    @Bean
    Administration previewAdministration(
            SystemConfigurationRepository configRepo,
            FactoryRepository factoryRepo,
            DeviceStatusRepository deviceRepo,
            GateRepository gateRepo
    ) {
        return new AdministrationService(
                configRepo,
                factoryRepo,
                new InMemoryShiftRepository(),
                new InMemoryHolidayRepository(),
                new InMemoryDataSubjectRequestRepository(),
                deviceRepo,
                gateRepo
        );
    }

    @Bean
    GateManagement previewGateManagement(
            GateRepository gateRepo,
            FactoryRepository factoryRepo,
            DeviceStatusRepository deviceRepo
    ) {
        return new GateConfigurationService(gateRepo, factoryRepo, deviceRepo);
    }
}
