import React from 'react';

// PHỤ TRÁCH: Task 8. Các trang nghiệp vụ dùng chung; retry chỉ khi chủ luồng cho phép.
export default function RequestState({ loading, error, empty, children, onRetry }) {
  if (loading) return <p role="status" aria-live="polite">Đang tải…</p>;
  if (error) return <section role="alert" className="notice error">
    <p>{error.message || 'Không thể hoàn thành yêu cầu.'}</p>
    {error.requestId && <small>Mã tra cứu: {error.requestId}</small>}
    {error.retryable && onRetry && <button onClick={onRetry}>Thử lại</button>}
  </section>;
  if (empty) return <p role="status">Chưa có dữ liệu.</p>;
  return children;
}
