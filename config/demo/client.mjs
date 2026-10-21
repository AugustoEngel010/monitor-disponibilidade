import assert from 'node:assert/strict';

const origin = process.env.DEMO_APP_URL || 'http://app:8080';
const authorization = 'Basic ' + Buffer.from(
  `${process.env.ADMIN_USERNAME}:${process.env.ADMIN_PASSWORD}`).toString('base64');

export async function api(path, method = 'GET', data) {
  const headers = {Authorization: authorization};
  if (method !== 'GET') {
    const response = await fetch(`${origin}/api/admin/csrf`, {
      headers, signal: AbortSignal.timeout(10000)});
    assert.equal(response.status, 200, 'CSRF endpoint');
    const csrf = await response.json();
    headers.Cookie = response.headers.getSetCookie().map(value => value.split(';')[0]).join('; ');
    headers[csrf.headerName] = csrf.token;
    headers['Content-Type'] = 'application/json';
  }
  const response = await fetch(origin + path, {method, headers,
    body: data === undefined ? undefined : JSON.stringify(data), signal: AbortSignal.timeout(10000)});
  assert.ok(response.ok, `${method} ${path}: HTTP ${response.status}`);
  return response.json();
}

export async function until(description, probe, predicate, timeout = 90000) {
  const deadline = Date.now() + timeout;
  let result;
  while (Date.now() < deadline) {
    result = await probe();
    if (predicate(result)) return result;
    await new Promise(resolve => setTimeout(resolve, 250));
  }
  throw new Error(`Timeout: ${description}; last=${JSON.stringify(result)}`);
}

export async function ready() {
  await until('app readiness', async () => {
    try { return (await fetch(`${origin}/actuator/health/readiness`,
      {signal: AbortSignal.timeout(3000)})).status; } catch { return 0; }
  }, value => value === 200, 180000);
}
