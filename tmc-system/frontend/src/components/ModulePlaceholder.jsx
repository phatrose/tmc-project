import React from 'react';


/**
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Component trình bày chung nhận title và description cho trang chưa triển khai.
 * - Cần làm: Chỉ hiển thị trạng thái chưa có chức năng; thay dần ở từng trang khi có form và API thật.
 * - Lưu ý bàn giao: Không gọi API, cấp quyền hoặc sinh dữ liệu demo trong component dùng chung này.
 */

// PHỤ TRÁCH: Task 8. Component chung cho các trang chưa triển khai.
export default function ModulePlaceholder({ title, description }) {
  return <section aria-labelledby="page-title">
    <p className="status">Chưa triển khai</p>
    <h1 id="page-title">{title}</h1>
    <p>{description}</p>
    <p>Chưa có dữ liệu. Các chức năng sẽ được bổ sung khi hoàn thành module.</p>
  </section>;
}
