// PHỤ TRÁCH: Task 8. Session memory theo §3; Task 1 gọi setSession sau login thật.
let session = null;
let expiryTimer;
const listeners = new Set();
const notify = () => listeners.forEach(listener => listener());
export function clearSession() {
  clearTimeout(expiryTimer);
  expiryTimer = undefined;
  session = null;
  notify();
}
export function setSession(value) {
  const remaining = Date.parse(value?.expiresAt) - Date.now();
  if (value?.tokenType !== 'Bearer' || !value.accessToken || !value.actor ||
      !Array.isArray(value.actor.roles) || !Array.isArray(value.actor.permissions) ||
      !Number.isFinite(remaining) || remaining <= 0 || remaining > 15 * 60 * 1000 + 5000) {
    throw new TypeError('Invalid session response');
  }
  clearTimeout(expiryTimer);
  session = structuredClone(value);
  // Consumers must not mutate trusted session metadata via the returned snapshot.
  Object.freeze(session.actor.roles);
  Object.freeze(session.actor.permissions);
  Object.freeze(session.actor);
  Object.freeze(session);
  expiryTimer = setTimeout(clearSession, remaining);
  expiryTimer.unref?.();
  notify();
}
export function setCookieSession(actor, expiresAt) {
  const remaining = Date.parse(expiresAt) - Date.now();
  if (!actor || !Array.isArray(actor.roles) || !Array.isArray(actor.permissions) ||
      !Number.isFinite(remaining) || remaining <= 0) {
    throw new TypeError('Invalid cookie session');
  }
  clearTimeout(expiryTimer);
  session = structuredClone({ tokenType: 'Cookie', accessToken: null, expiresAt, actor });
  Object.freeze(session.actor.roles);
  Object.freeze(session.actor.permissions);
  Object.freeze(session.actor);
  Object.freeze(session);
  expiryTimer = setTimeout(clearSession, remaining);
  expiryTimer.unref?.();
  notify();
}
export function getSession() { return session; }
export function getAccessToken() {
  if (session && Date.parse(session.expiresAt) <= Date.now()) clearSession();
  return session?.accessToken ?? null;
}
export function subscribeSession(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
