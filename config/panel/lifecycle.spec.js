import {test, expect} from '@playwright/test';
import {create, openList, mutate} from './fixtures.js';
import {startTarget, stopTarget, incident} from './target.js';

test.beforeAll(startTarget);
test.afterAll(stopTarget);

test('MON-10-AC03 pausa e retoma reais preservam DOWN e o mesmo incidente', async ({page, request}) => {
  const monitor = await incident(request);
  const current = await (await request.get(`/api/admin/monitors/${monitor.id}/summary`)).json();
  let card = await openList(page, request, monitor.id);
  await expect(card.getByTestId('state-label')).toHaveText('Indisponível');
  await card.getByRole('button', {name: 'Pausar', exact: true}).click();
  card = page.getByTestId(`monitor-${monitor.id}`);
  await expect(card.getByTestId('state-label')).toHaveText('Pausado');
  await expect(card).toContainText(current.currentIncident.id);
  await expect(card).toContainText('Estado confirmado: Indisponível');
  await card.getByRole('button', {name: 'Retomar', exact: true}).click();
  await expect(card.getByRole('button', {name: 'Pausar', exact: true})).toBeEnabled();
  const resumed = await (await request.get(`/api/admin/monitors/${monitor.id}/summary`)).json();
  expect(resumed.lifecycle).toBe('ACTIVE');
  expect(resumed.confirmedState).toBe('DOWN');
  expect(resumed.currentIncident.id).toBe(current.currentIncident.id);
  await expect(card).toContainText(current.currentIncident.id);
});

test('MON-10-AC03 aguarda confirmação e bloqueia envio duplicado', async ({page, request}) => {
  const monitor = await create(request);
  let release;
  const pending = new Promise(resolve => { release = resolve; });
  let calls = 0;
  await page.route(`**/monitors/${monitor.id}/pause`, async route => {
    calls++;
    await pending;
    await route.continue();
  });
  const card = await openList(page, request, monitor.id);
  await card.getByRole('button', {name: 'Pausar', exact: true}).click();
  await expect(card.getByRole('button', {name: 'Pausando…'})).toBeDisabled();
  await expect(card.getByRole('button', {name: 'Retomar', exact: true})).toHaveCount(0);
  expect((await (await request.get(`/api/admin/monitors/${monitor.id}`)).json()).lifecycle).toBe('ACTIVE');
  release();
  await expect(card.getByRole('button', {name: 'Retomar', exact: true})).toBeEnabled();
  expect(calls).toBe(1);
});

for (const status of [403, 503, 0]) {
  test(`MON-10-AC03/05 falha de pausa ${status} não simula mudança`, async ({page, request}) => {
    const monitor = await create(request);
    await page.route(`**/monitors/${monitor.id}/pause`, route => status
      ? route.fulfill({status, body: ''}) : route.abort());
    const card = await openList(page, request, monitor.id);
    await card.getByRole('button', {name: 'Pausar', exact: true}).click();
    await expect(card.getByRole('alert')).toContainText(status === 403 ? 'Acesso negado' : 'Resultado da operação não confirmado');
    await expect(card.getByRole('button', {name: 'Pausar', exact: true})).toBeEnabled();
    expect((await (await request.get(`/api/admin/monitors/${monitor.id}`)).json()).lifecycle).toBe('ACTIVE');
  });
  test(`MON-10-AC03/05 falha de retomada ${status} preserva pausa`, async ({page, request}) => {
    const monitor = await create(request);
    expect((await mutate(request, `/monitors/${monitor.id}/pause`, 'POST')).status()).toBe(200);
    await page.route(`**/monitors/${monitor.id}/resume`, route => status
      ? route.fulfill({status, body: ''}) : route.abort());
    const card = await openList(page, request, monitor.id);
    await card.getByRole('button', {name: 'Retomar', exact: true}).click();
    await expect(card.getByRole('alert')).toContainText(status === 403 ? 'Acesso negado' : 'Resultado da operação não confirmado');
    await expect(card.getByRole('button', {name: 'Retomar', exact: true})).toBeEnabled();
    expect((await (await request.get(`/api/admin/monitors/${monitor.id}`)).json()).lifecycle).toBe('PAUSED');
  });
}

test('MON-10-AC02/03 incidente real bloqueia edição de política sem perder valores', async ({page, request}) => {
  const monitor = await incident(request);
  await page.goto(`/#/edit/${monitor.id}`);
  await page.getByLabel('Nome', {exact: true}).fill('Nome deve permanecer no formulário');
  await page.getByLabel('Intervalo (segundos)', {exact: true}).fill('20');
  await page.getByRole('button', {name: 'Salvar monitor'}).click();
  await expect(page.getByRole('alert')).toContainText('Há um incidente aberto');
  await expect(page.getByLabel('Nome', {exact: true})).toHaveValue('Nome deve permanecer no formulário');
  await expect(page.getByLabel('Intervalo (segundos)', {exact: true})).toHaveValue('20');
  const saved = await (await request.get(`/api/admin/monitors/${monitor.id}`)).json();
  expect(saved.name).toBe(monitor.name);
  expect(saved.intervalSeconds).toBe(10);
  expect(saved.openIncidentId).not.toBeNull();
});
