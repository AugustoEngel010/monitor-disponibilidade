import {test, expect} from '@playwright/test';

const configuration = {
  name: 'CSRF browser test', url: 'https://example.org/health', intervalSeconds: 60,
  timeoutSeconds: 5, expectedStatus: 200, failureThreshold: 3, recoveryThreshold: 2,
  alertRecipient: 'ops@example.org'
};

test('MON-10-AC06 página e assets exigem Basic e challenge', async ({playwright, baseURL, request}) => {
  const anonymous = await playwright.request.newContext({baseURL, httpCredentials: undefined});
  for (const path of ['/', '/index.html', '/panel/api.js']) {
    const denied = await anonymous.get(path);
    expect(denied.status()).toBe(401);
    expect(await denied.text()).toBe('');
    expect(denied.headers()['www-authenticate']).toContain('Basic');
    expect((await request.get(path)).status()).toBe(200);
  }
  expect((await anonymous.post('/api/admin/monitors', {data: configuration})).status()).toBe(401);
  await anonymous.dispose();
});

test('MON-10-AC06 cliente envia CSRF e cookie; escrita sem token é recusada', async ({page, request}) => {
  expect((await request.post('/api/admin/monitors', {data: configuration})).status()).toBe(403);
  await page.goto('/');
  const created = await page.evaluate(async config => {
    const api = await import('/panel/api.js');
    return api.write('/monitors', 'POST', config);
  }, configuration);
  expect(created.name).toBe(configuration.name);
  expect(created.lifecycle).toBe('ACTIVE');
  expect((await request.get(`/api/admin/monitors/${created.id}`)).status()).toBe(200);
  expect(await page.evaluate(() => Object.keys(localStorage))).toEqual([]);
});

for (const [status, message] of [[401, 'Autenticação administrativa necessária'],
  [403, 'Acesso negado ou proteção CSRF expirada'], [503, 'Não foi possível consultar o backend']]) {
  test(`MON-10-AC05/06 cliente trata ${status} sem corpo`, async ({page}) => {
    await page.goto('/');
    await page.route('**/api/admin/probe', route => route.fulfill({status, body: ''}));
    const result = await page.evaluate(async () => {
      try { await (await import('/panel/api.js')).get('/probe'); }
      catch (error) { return {status: error.status, message: error.message}; }
    });
    expect(result.status).toBe(status);
    expect(result.message).toContain(message);
  });
}

test('MON-10-AC05 escrita interrompida não é repetida e não promete sucesso', async ({page}) => {
  await page.goto('/');
  let calls = 0;
  await page.route('**/api/admin/probe', route => { calls++; return route.abort(); });
  const message = await page.evaluate(async () => {
    try { await (await import('/panel/api.js')).write('/probe', 'POST', {}); }
    catch (error) { return error.message; }
  });
  expect(message).toContain('Resultado da operação não confirmado');
  expect(calls).toBe(1);
});
