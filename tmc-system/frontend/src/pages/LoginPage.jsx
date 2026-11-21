// PHỤ TRÁCH: Task 1.
// VAI TRÒ: View — Form đăng nhập theo CONTRACT-V1.md §3.
// Gửi POST /api/v1/auth/login, nhận Session { accessToken, tokenType, expiresAt, actor }.
// Lưu token trong bộ nhớ (in-memory), không URL/localStorage per CONTRACT-V1.md §3 dòng 62.

import React, { useState } from 'react';

const BASE = '/api/v1';

// Quản lý session trong bộ nhớ JavaScript (in-memory)
let inMemorySession = null;

export function getSession() {
  return inMemorySession;
}

export function setSession(session) {
  inMemorySession = session;
}

export function clearSession() {
  inMemorySession = null;
}

async function loginApi(username, password) {
  const res = await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data.message || data.error || `Lỗi máy chủ (${res.status})`);
  }
  return data;
}

export default function LoginPage({ onLoginSuccess }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    setLoading(true);

    const pwd = password;
    setPassword(''); // Xóa mật khẩu khỏi state ngay lập tức

    try {
      const session = await loginApi(username, pwd);
      if (!session || !session.accessToken) {
        throw new Error('Phản hồi phiên đăng nhập không hợp lệ.');
      }

      // Lưu vào bộ nhớ phiên
      setSession(session);

      if (typeof onLoginSuccess === 'function') {
        onLoginSuccess(session);
      } else {
        window.location.href = '/';
      }
    } catch (err) {
      setError(err.message || 'Đăng nhập không thành công.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div style={styles.container}>
      <div style={styles.card}>
        <div style={styles.header}>
          <h2 style={styles.title}>Hệ thống Chấm công TMC</h2>
          <p style={styles.subtitle}>Đăng nhập để truy cập tài nguyên hệ thống</p>
        </div>

        {error && <div style={styles.errorAlert}>{error}</div>}

        <form onSubmit={handleSubmit} style={styles.form}>
          <div style={styles.fieldGroup}>
            <label style={styles.label}>Tên đăng nhập</label>
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="Nhập tên đăng nhập"
              required
              disabled={loading}
              style={styles.input}
              autoComplete="username"
            />
          </div>

          <div style={styles.fieldGroup}>
            <label style={styles.label}>Mật khẩu</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Nhập mật khẩu"
              required
              disabled={loading}
              style={styles.input}
              autoComplete="current-password"
            />
          </div>

          <button
            type="submit"
            disabled={loading || !username.trim()}
            style={{
              ...styles.submitBtn,
              opacity: loading || !username.trim() ? 0.6 : 1,
              cursor: loading || !username.trim() ? 'not-allowed' : 'pointer',
            }}
          >
            {loading ? 'Đang xác thực...' : 'Đăng nhập'}
          </button>
        </form>

        <div style={styles.footerNote}>
          <small>Phiên làm việc tự động hết hạn sau 15 phút không hoạt động.</small>
        </div>
      </div>
    </div>
  );
}

const styles = {
  container: {
    minHeight: '80vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '24px',
  },
  card: {
    background: '#ffffff',
    borderRadius: '8px',
    boxShadow: '0 4px 16px rgba(0, 0, 0, 0.1)',
    padding: '36px',
    width: '100%',
    maxWidth: '420px',
  },
  header: { marginBottom: '24px', textAlign: 'center' },
  title: { margin: '0 0 8px 0', fontSize: '22px', color: '#1a1a2e' },
  subtitle: { margin: 0, fontSize: '14px', color: '#666' },
  errorAlert: {
    background: '#fff2f0',
    border: '1px solid #ffccc7',
    borderRadius: '4px',
    padding: '10px 14px',
    marginBottom: '20px',
    color: '#cf1322',
    fontSize: '13px',
  },
  form: { display: 'flex', flexDirection: 'column', gap: '18px' },
  fieldGroup: { display: 'flex', flexDirection: 'column', gap: '6px' },
  label: { fontSize: '13px', fontWeight: 600, color: '#333' },
  input: {
    padding: '10px 12px',
    borderRadius: '4px',
    border: '1px solid #d9d9d9',
    fontSize: '14px',
    outline: 'none',
  },
  submitBtn: {
    marginTop: '6px',
    padding: '12px',
    background: '#1890ff',
    color: '#ffffff',
    border: 'none',
    borderRadius: '4px',
    fontSize: '15px',
    fontWeight: 600,
  },
  footerNote: {
    marginTop: '20px',
    textAlign: 'center',
    color: '#888',
  },
};
