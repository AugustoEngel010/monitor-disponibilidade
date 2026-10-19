import {expect} from '@playwright/test';

export const config = {
  name: 'Monitor de teste', url: 'https://example.org/health', intervalSeconds: 3600,
  timeoutSeconds: 5, expectedStatus: 200, failureThreshold: 3, recoveryThreshold: 2,
  alertRecipient: 'ops@example.org'
};

export async function mutate(request, path, method, data) {
  const csrf = await (await request.get('/api/admin/csrf')).json();
  return request.fetch('/api/admin' + path, {
    method, data, headers: {[csrf.headerName]: csrf.token}
  });
}

export async function create(request, values = {}) {
  const response = await mutate(request, '/monitors', 'POST', {
    ...config, name: `Monitor ${crypto.randomUUID().slice(0, 8)}`, ...values
  });
  expect(response.status()).toBe(201);
  return response.json();
}

export async function openList(page, request, id) {
  const list = await (await request.get('/api/admin/monitors?size=100')).json();
  const index = list.items.findIndex(item => item.id === id);
  expect(index).toBeGreaterThanOrEqual(0);
  await page.goto(`/#/?page=${Math.floor(index / 20)}`);
  return page.getByTestId(`monitor-${id}`);
}
