package vn.tmc.central;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** PHỤ TRÁCH: Task 8. Kiểm tra DAG và không truy cập internal của module khác. */
class ModuleBoundariesTest {
    @Test void moduleBoundariesRemainValid() {
        ApplicationModules.of(CentralApplication.class).verify();
    }
}
