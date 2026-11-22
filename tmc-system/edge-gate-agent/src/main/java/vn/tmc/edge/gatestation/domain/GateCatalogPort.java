package vn.tmc.edge.gatestation.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 2. Tra cứu cấu hình cổng từ cache cục bộ. Hướng vào/ra do Edge lấy từ cấu hình cổng
 * (CONTRACT-V1.md §5), không tin dữ liệu từ scanner. Trả empty khi cache không có cổng đó hoặc đã hết tin cậy.
 */
public interface GateCatalogPort {
    Optional<GateInfo> find(UUID gateId, Instant now);
}
