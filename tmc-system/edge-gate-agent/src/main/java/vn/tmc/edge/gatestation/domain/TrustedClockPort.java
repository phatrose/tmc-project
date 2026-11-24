package vn.tmc.edge.gatestation.domain;

/**
 * PHỤ TRÁCH: Task 2. Nguồn thời gian cho scan. Thời điểm quét quyết định công và quyết định hạn token, nên khi Edge
 * không có thời gian tin cậy luồng quét phải fail closed với CLOCK_UNTRUSTED (CONTRACT-V1.md §4, §10).
 */
public interface TrustedClockPort {
    ClockReading read();
}
