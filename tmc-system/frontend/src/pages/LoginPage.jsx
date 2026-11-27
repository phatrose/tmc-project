// PHỤ TRÁCH: Task 1.
// VAI TRÒ: View — Form đăng nhập theo CONTRACT-V1.md §3.
// Gửi POST /api/v1/auth/login, nhận Session { accessToken, tokenType, expiresAt, actor }.
// Lưu token trong bộ nhớ (in-memory), không URL/localStorage per CONTRACT-V1.md §3.

import React, { useState } from 'react';
import { login as loginApi } from '../api.js';
import { getSession } from '../session.js';
export { getSession, setSession, clearSession } from '../session.js';

export default function LoginPage({ onLoginSuccess }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    setLoading(true);

    const pwd = password;
    setPassword(''); // Xóa mật khẩu khỏi state ngay lập tức
    setShowPassword(false);

    try {
      await loginApi(username.trim(), pwd);
      const session = getSession();
      if (!session || !session.accessToken) {
        throw new Error('Phản hồi phiên đăng nhập không hợp lệ.');
      }

      if (typeof onLoginSuccess === 'function') {
        onLoginSuccess(session);
      } else {
        window.location.hash = '/home';
      }
    } catch (err) {
      setError(err.message || 'Đăng nhập không thành công.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="login-shell">
      <div className="login-intro">
        <div>
          <span className="login-mark">TMC</span>
          <h2>Chào mừng<br />bạn trở lại.</h2>
          <p>
            Đăng nhập để quản lý nhân sự, tra cứu báo cáo chấm công và sử dụng mã QR động ES256 của bạn.
          </p>
        </div>
        <div className="login-detail">
          Bảo mật phiên trong bộ nhớ · Token QR tách biệt · Kiểm toán bất biến
        </div>
      </div>

      <div className="login-form-panel">
        <p className="eyebrow">Tài khoản nội bộ</p>
        <h2>Đăng nhập</h2>
        <p>Sử dụng thông tin tài khoản được quản trị viên cấp.</p>

        {error && <div className="notice error" role="alert" style={{ marginTop: 16 }}>{error}</div>}

        <form onSubmit={handleSubmit} aria-busy={loading}>
          <label htmlFor="username">Tên đăng nhập</label>
          <input
            id="username"
            name="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            placeholder="Nhập tên đăng nhập"
            required
            disabled={loading}
          />

          <label htmlFor="password">Mật khẩu</label>
          <div className="password-wrap">
            <input
              id="password"
              name="password"
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              placeholder="Nhập mật khẩu"
              required
              disabled={loading}
            />
            <button
              type="button"
              className="password-toggle"
              onClick={() => setShowPassword((v) => !v)}
              disabled={loading}
              aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
            >
              {showPassword ? 'Ẩn' : 'Hiện'}
            </button>
          </div>

          <button
            className="primary-button"
            type="submit"
            disabled={loading || !username.trim() || !password}
          >
            {loading ? 'Đang xác thực…' : 'Đăng nhập →'}
          </button>
        </form>

        <p className="login-note">
          Phiên làm việc có thời hạn cố định 15 phút kể từ lúc đăng nhập và chỉ lưu trong bộ nhớ trình duyệt. Tải lại trang sẽ yêu cầu đăng nhập lại.
        </p>
      </div>
    </section>
  );
}
