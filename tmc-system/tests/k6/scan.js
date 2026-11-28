// PHỤ TRÁCH: Task 8. PERF-GS-01 thật; cần Task 1/2/6/7 và fixture được provision.
import http from 'k6/http';
import exec from 'k6/execution';
import { check, fail } from 'k6';
import { SharedArray } from 'k6/data';
import { Rate, Trend } from 'k6/metrics';

for (const name of ['CENTRAL_URL', 'EDGE_URL', 'GATE_ID', 'USERS_FILE', 'KIOSK_CERT', 'KIOSK_KEY', 'KIOSK_TLS_DOMAIN']) {
  if (!__ENV[name]) throw new Error(`Missing environment variable: ${name}`);
}
if (!__ENV.CENTRAL_URL.startsWith('https://') || !__ENV.EDGE_URL.startsWith('https://')) {
  throw new Error('Use trusted HTTPS test endpoints');
}
const users = new SharedArray('provisioned test users', () => JSON.parse(open(__ENV.USERS_FILE)));
if (users.length < 800) throw new Error('Provide 800 distinct authorized test employees for one factory');
const pipeline = new Rate('pipeline_success');
const scanAccepted = new Rate('scan_accepted');
const scanTime = new Trend('scan_duration', true);
export const options = {
  scenarios: {
    scans: { executor: 'constant-arrival-rate', rate: 800, timeUnit: '30m', duration: '30m',
      preAllocatedVUs: 5, maxVUs: 30, gracefulStop: '15s' },
  },
  thresholds: {
    scan_duration: ['p(95)<=2000'], scan_accepted: ['rate==1'],
    pipeline_success: ['rate==1'], dropped_iterations: ['count==0'],
    'http_req_failed{stage:scan}': ['rate==0'],
  },
  tlsAuth: [{ domains: [__ENV.KIOSK_TLS_DOMAIN], cert: open(__ENV.KIOSK_CERT), key: open(__ENV.KIOSK_KEY) }],
};
function uuid() {
  // UUID fixture correlation only; QR/token randomness remains the issuer's responsibility.
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = Math.floor(Math.random() * 16);
    return (c === 'x' ? r : (r & 3) | 8).toString(16);
  });
}
const params = (stage, token) => ({ timeout: '10s', redirects: 0, tags: { stage }, headers: {
  'Content-Type': 'application/json', 'X-Request-Id': uuid(), ...(token ? { Authorization: `Bearer ${token}` } : {}),
}});
function data(response) { try { return response.json(); } catch { return null; } }
export default function () {
  const user = users[exec.scenario.iterationInTest];
  if (!user) { pipeline.add(false); fail('Fixture exhausted; do not recycle employees'); }
  const login = http.post(`${__ENV.CENTRAL_URL}/api/v1/auth/login`,
    JSON.stringify({ username: user.username, password: user.password }), params('login'));
  const session = data(login);
  if (login.status !== 200 || !session?.accessToken) { pipeline.add(false); return; }
  try {
    const qr = http.get(`${__ENV.CENTRAL_URL}/api/v1/me/qr-token`, params('issue', session.accessToken));
    const credential = data(qr);
    if (qr.status !== 200 || !credential?.token) { pipeline.add(false); return; }
    const scanId = uuid();
    const scan = http.post(`${__ENV.EDGE_URL}/edge/v1/scans`,
      JSON.stringify({ scanId, gateId: __ENV.GATE_ID, token: credential.token }), params('scan'));
    const result = data(scan);
    scanTime.add(scan.timings.duration);
    const accepted = check(result, {
      'committed ALLOW with matching scanId': r => scan.status === 200 && r?.scanId === scanId &&
        r.decision === 'ALLOW' && Boolean(r.eventId) && Boolean(r.commandId),
      'online and command accepted': r => r?.mode === 'ONLINE' && r.commandStatus === 'UNLOCK_ACCEPTED',
    });
    scanAccepted.add(accepted); pipeline.add(accepted);
  } finally {
    const logoutParams = params('logout', session.accessToken);
    logoutParams.headers['Idempotency-Key'] = uuid();
    http.post(`${__ENV.CENTRAL_URL}/api/v1/auth/logout`, '{}', logoutParams);
  }
}
export function handleSummary(summary) {
  return { [__ENV.SUMMARY_FILE || 'tests/evidence/scan-summary.json']: JSON.stringify(summary, null, 2) };
}
