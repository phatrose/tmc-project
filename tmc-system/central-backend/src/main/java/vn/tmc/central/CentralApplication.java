package vn.tmc.central;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PHỤ TRÁCH: Task 8. Khởi động nền tảng Central, cấu hình qua môi trường.
 * Task 1–7 đăng ký bean thật sau khi hoàn thiện public port/contract; không wiring stub TODO.
 * Profile platform chỉ kiểm tra HTTP không DB; mặc định dùng PostgreSQL/Flyway.
 */
@SpringBootApplication
public class CentralApplication {
    public static void main(String[] args) {
        SpringApplication.run(CentralApplication.class, args);
    }
}
