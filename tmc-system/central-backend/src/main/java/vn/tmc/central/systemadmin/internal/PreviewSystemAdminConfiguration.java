package vn.tmc.central.systemadmin.internal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.tmc.central.systemadmin.api.Administration;
import vn.tmc.central.systemadmin.api.GateManagement;
import vn.tmc.central.systemadmin.internal.repository.*;
import vn.tmc.central.systemadmin.internal.repository.JdbcSystemAdminRepositories.*;
import vn.tmc.central.systemadmin.internal.service.AdministrationService;
import vn.tmc.central.systemadmin.internal.service.GateConfigurationService;

/**
 * PHỤ TRÁCH: Task 5 / Task 8 (FE-ADMIN-02).
 * Wiring PostgreSQL bền vững (V013__systemadmin.sql) cho toàn bộ 7 repository của system-admin
 * khi có JdbcTemplate; tự động seed dữ liệu khởi tạo preview chỉ khi bảng trống (giữ nguyên dữ liệu sau restart).
 */
@Configuration(proxyBeanMethods = false)
@Profile("!platform")
public class PreviewSystemAdminConfiguration {

    @Bean
    SystemConfigurationRepository previewSystemConfigRepo(@Autowired(required = false) JdbcTemplate jdbc) {
        return jdbc != null ? new JdbcSystemConfigurationRepository(jdbc) : new InMemorySystemConfigurationRepository();
    }

    @Bean
    FactoryRepository previewFactoryRepo(@Autowired(required = false) JdbcTemplate jdbc) {
        return jdbc != null ? new JdbcFactoryRepository(jdbc) : new InMemoryFactoryRepository();
    }

    @Bean
    GateRepository previewGateRepo(@Autowired(required = false) JdbcTemplate jdbc) {
        return jdbc != null ? new JdbcGateRepository(jdbc) : new InMemoryGateRepository();
    }

    @Bean
    ShiftRepository previewShiftRepo(@Autowired(required = false) JdbcTemplate jdbc) {
        return jdbc != null ? new JdbcShiftRepository(jdbc) : new InMemoryShiftRepository();
    }

    @Bean
    HolidayRepository previewHolidayRepo(@Autowired(required = false) JdbcTemplate jdbc) {
        return jdbc != null ? new JdbcHolidayRepository(jdbc) : new InMemoryHolidayRepository();
    }

    @Bean
    DataSubjectRequestRepository previewDsrRepo(@Autowired(required = false) JdbcTemplate jdbc) {
        return jdbc != null ? new JdbcDataSubjectRequestRepository(jdbc) : new InMemoryDataSubjectRequestRepository();
    }

    @Bean
    DeviceStatusRepository previewDeviceStatusRepo(@Autowired(required = false) JdbcTemplate jdbc) {
        return jdbc != null ? new JdbcDeviceStatusRepository(jdbc) : new InMemoryDeviceStatusRepository();
    }

    @Bean
    @Profile("integration-preview")
    @Order(15)
    ApplicationRunner seedSystemAdminPreviewIfEmpty(
            FactoryRepository factoryRepo,
            GateRepository gateRepo,
            ShiftRepository shiftRepo,
            HolidayRepository holidayRepo,
            SystemConfigurationRepository configRepo,
            DeviceStatusRepository deviceRepo,
            DataSubjectRequestRepository dsrRepo
    ) {
        return args -> {
            if (factoryRepo.findAll().isEmpty()) {
                new InMemoryFactoryRepository().findAll().forEach(factoryRepo::save);
            }
            if (gateRepo.findAll().isEmpty()) {
                new InMemoryGateRepository().findAll().forEach(gateRepo::save);
            }
            if (shiftRepo.findAll().isEmpty()) {
                new InMemoryShiftRepository().findAll().forEach(shiftRepo::save);
            }
            if (holidayRepo.findAll().isEmpty()) {
                new InMemoryHolidayRepository().findAll().forEach(holidayRepo::save);
            }
            if (configRepo.findAll().isEmpty()) {
                new InMemorySystemConfigurationRepository().findAll().forEach(configRepo::save);
            }
            if (deviceRepo.findAll().isEmpty()) {
                new InMemoryDeviceStatusRepository().findAll().forEach(deviceRepo::save);
            }
            if (dsrRepo.findAll().isEmpty()) {
                new InMemoryDataSubjectRequestRepository().findAll().forEach(dsrRepo::save);
            }
        };
    }

    @Bean
    Administration previewAdministration(
            SystemConfigurationRepository configRepo,
            FactoryRepository factoryRepo,
            ShiftRepository shiftRepo,
            HolidayRepository holidayRepo,
            DataSubjectRequestRepository dsrRepo,
            DeviceStatusRepository deviceRepo,
            GateRepository gateRepo
    ) {
        return new AdministrationService(
                configRepo,
                factoryRepo,
                shiftRepo,
                holidayRepo,
                dsrRepo,
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
