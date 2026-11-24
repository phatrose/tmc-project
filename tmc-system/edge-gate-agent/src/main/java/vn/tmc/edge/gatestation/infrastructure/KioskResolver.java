package vn.tmc.edge.gatestation.infrastructure;

import com.sun.net.httpserver.HttpExchange;
import vn.tmc.edge.gatestation.domain.KioskContext;

import java.util.Optional;

/**
 * PHỤ TRÁCH: Task 2. Xác định kiosk gọi API từ kết nối đã xác thực (CONTRACT-V1.md §3: scanner → Edge dùng mTLS,
 * chứng chỉ gắn gateId). Trả empty nếu không nhận ra thiết bị. Danh tính KHÔNG bao giờ lấy từ header hay body do client tự khai.
 */
public interface KioskResolver {
    Optional<KioskContext> resolve(HttpExchange exchange);
}
