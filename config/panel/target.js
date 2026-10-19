import http from 'node:http';
import {expect} from '@playwright/test';
import {create} from './fixtures.js';

const responses = new Map();
let server;
export async function startTarget() {
  server = http.createServer((req, res) => { res.writeHead(responses.get(req.url) ?? 503); res.end(); });
  await new Promise(resolve => server.listen(9080, '0.0.0.0', resolve));
}
export async function stopTarget() {
  server.closeAllConnections();
  await new Promise(resolve => server.close(resolve));
}
export function respond(monitor, status) { responses.set(new URL(monitor.url).pathname, status); }
export async function incident(request) {
  const monitor = await create(request, {url: `http://panel-tests:9080/${crypto.randomUUID()}`,
    intervalSeconds: 10, failureThreshold: 1, recoveryThreshold: 2});
  await expect.poll(async () => (await (await request.get(`/api/admin/monitors/${monitor.id}/summary`)).json()).confirmedState,
    {timeout: 20000}).toBe('DOWN');
  return monitor;
}
