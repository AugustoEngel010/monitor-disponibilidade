import {test, expect} from '@playwright/test';
import {config, create} from './fixtures.js';

const fields = {
  name: 'Nome', url: 'URL', intervalSeconds: 'Intervalo (segundos)', timeoutSeconds: 'Timeout (segundos)',
  expectedStatus: 'Código HTTP esperado', failureThreshold: 'Falhas para confirmar queda',
  recoveryThreshold: 'Sucessos para confirmar recuperação', alertRecipient: 'Destinatário dos alertas'
};

async function fill(page, values) {
  for (const [name, value] of Object.entries(values)) await page.getByLabel(fields[name], {exact: true}).fill(String(value));
}

test('MON-10-AC02 cadastra os oito campos pelo navegador e persiste no backend', async ({page, request}) => {
  await page.goto('/#/new');
  const values = {...config, name: `Cadastro ${crypto.randomUUID()}`, intervalSeconds: 120,
    timeoutSeconds: 8, expectedStatus: 204, failureThreshold: 4, recoveryThreshold: 3};
  await fill(page, values);
  const response = page.waitForResponse(r => r.url().endsWith('/api/admin/monitors') && r.request().method() === 'POST');
  await page.getByRole('button', {name: 'Salvar monitor'}).click();
  const created = await (await response).json();
  await expect(page.getByRole('status').filter({hasText: 'Monitor cadastrado.'})).toBeVisible();
  const persisted = await (await request.get(`/api/admin/monitors/${created.id}`)).json();
  for (const [key, value] of Object.entries(values)) expect(persisted[key]).toBe(value);
});

test('MON-10-AC02 edição carrega valores e salva configuração real', async ({page, request}) => {
  const monitor = await create(request);
  await page.goto(`/#/edit/${monitor.id}`);
  for (const key of Object.keys(fields)) await expect(page.getByLabel(fields[key], {exact: true})).toHaveValue(String(monitor[key]));
  await fill(page, {name: 'Nome editado', alertRecipient: 'novo@example.org', intervalSeconds: 180});
  await page.getByRole('button', {name: 'Salvar monitor'}).click();
  await expect(page.getByRole('status').filter({hasText: 'Monitor atualizado.'})).toBeVisible();
  const saved = await (await request.get(`/api/admin/monitors/${monitor.id}`)).json();
  expect(saved.name).toBe('Nome editado');
  expect(saved.alertRecipient).toBe('novo@example.org');
  expect(saved.intervalSeconds).toBe(180);
  expect(saved.configurationVersion).toBe(2);
});

test('MON-10-AC02 validação real associa erros e preserva todos os valores', async ({page}) => {
  await page.goto('/#/new');
  const values = {...config, name: ' ', url: 'https://example.org?q=secret',
    intervalSeconds: 9, timeoutSeconds: 10, expectedStatus: 199, failureThreshold: 0,
    recoveryThreshold: 11, alertRecipient: 'email-invalido'};
  await fill(page, values);
  await page.getByRole('button', {name: 'Salvar monitor'}).click();
  await expect(page.getByRole('alert')).toContainText('Revise os campos indicados.');
  for (const [key, value] of Object.entries(values)) {
    const input = page.getByLabel(fields[key], {exact: true});
    await expect(input).toHaveValue(String(value));
    await expect(input).toHaveAttribute('aria-invalid', 'true');
    const errorId = await input.getAttribute('aria-describedby');
    await expect(page.locator('#' + errorId)).not.toBeEmpty();
  }
  await expect(page.getByLabel('Nome', {exact: true})).toBeFocused();
});

for (const status of [403, 409, 503, 0]) {
  test(`MON-10-AC02/05 falha ${status} preserva formulário e permite nova tentativa consciente`, async ({page}) => {
    await page.goto('/#/new');
    await fill(page, config);
    let calls = 0;
    await page.route('**/api/admin/monitors', route => {
      calls++;
      return status ? route.fulfill({status, json: status === 409 ? {code: 'OPEN_INCIDENT', errors: {}} : {}})
        : route.abort();
    });
    await page.getByRole('button', {name: 'Salvar monitor'}).click();
    const expected = status === 403 ? 'Acesso negado' : status === 409 ? 'Há um incidente aberto'
      : 'Resultado da operação não confirmado';
    await expect(page.getByRole('alert')).toContainText(expected);
    for (const [key, value] of Object.entries(config)) await expect(page.getByLabel(fields[key], {exact: true})).toHaveValue(String(value));
    expect(calls).toBe(1);
    await expect(page.getByRole('button', {name: 'Salvar monitor'})).toBeEnabled();
  });
}

test('MON-10-AC02 limite e monitor inexistente têm mensagens claras', async ({page}) => {
  await page.goto('/#/new');
  await fill(page, config);
  await page.route('**/api/admin/monitors', route => route.fulfill({status: 409, json: {code: 'MONITOR_LIMIT', errors: {}}}));
  await page.getByRole('button', {name: 'Salvar monitor'}).click();
  await expect(page.getByRole('alert')).toHaveText('Limite de 100 monitores atingido.');
  await page.goto('/#/edit/00000000-0000-0000-0000-999999999999');
  await expect(page.getByRole('alert')).toHaveText('Monitor não encontrado.');
});
