package vn.tmc.central.qrsecurity.api;

import vn.tmc.contracts.QrKeySet;

/**
 * PHỤ TRÁCH: Task 6 / Task 8.
 * Public API port cung cấp tập khóa công khai ES256 đang tin cậy cho Edge bootstrap
 * và xác minh chữ ký QR nội bộ mà không phá vỡ ranh giới Spring Modulith.
 */
public interface QrKeySetQuery {
    QrKeySet keySet();
}
