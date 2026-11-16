import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './App.jsx';
import './style.css';


/**
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Điểm mount React vào phần tử root trong index.html.
 * - Cần làm: Bổ sung provider/router/error boundary khi đã chốt cấu trúc chung, tránh gọi nghiệp vụ trong entry point.
 * - Lưu ý bàn giao: Giữ StrictMode và dọn cleanup trong effect của component để tránh request/timer lặp khi phát triển.
 */

// PHỤ TRÁCH: Task 8.
createRoot(document.getElementById('root')).render(<React.StrictMode><App /></React.StrictMode>);
