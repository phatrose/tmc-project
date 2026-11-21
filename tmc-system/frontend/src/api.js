// PHỤ TRÁCH: Task 8. Task 1–7 dùng client này, truyền cùng idempotencyKey khi retry mutation.
import { createHttpClient } from './httpClient.js';
import { getAccessToken, clearSession, setSession } from './session.js';
export { HttpError } from './httpClient.js';
export const request = createHttpClient({
  baseUrl: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  getToken: getAccessToken,
  onUnauthorized: clearSession,
});
// Adapter cho LoginPage của Task 1; không sinh session hoặc user demo.
export async function login(username, password, options = {}) {
  const session = await request('/auth/login', { ...options, method: 'POST', body: { username, password }, auth: false });
  setSession(session);
  return session.actor;
}
export async function logout(options = {}) {
  try { await request('/auth/logout', { idempotencyKey: crypto.randomUUID(), ...options, method: 'POST', body: {} }); }
  finally { clearSession(); }
}
