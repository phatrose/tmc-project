import React from 'react';

// PHỤ TRÁCH: Task 8. Không hiển thị stack trace/props chứa thông tin phiên.
export default class AppErrorBoundary extends React.Component {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  render() {
    if (this.state.failed) return <main role="alert"><h1>Không thể hiển thị trang</h1>
      <p>Hãy tải lại ứng dụng. Bạn sẽ cần đăng nhập lại.</p>
      <button onClick={() => window.location.reload()}>Tải lại</button></main>;
    return this.props.children;
  }
}
