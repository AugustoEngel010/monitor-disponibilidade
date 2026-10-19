import {test, expect} from '@playwright/test';
import {create} from './fixtures.js';
import {startTarget, stopTarget, incident, respond} from './target.js';

test.beforeAll(startTarget);
test.afterAll(stopTarget);

test('MON-10-AC04 consulta checks, incidente e notificações reais até recuperação', async ({page, request}) => {
  const monitor = await incident(request);
  await page.goto(`/#/monitors/${monitor.id}`);
  const checks = page.getByRole('region', {name: 'Verificações', exact: true});
  const incidents = page.getByRole('region', {name: 'Incidentes', exact: true});
  const notifications = page.getByRole('region', {name: 'Notificações', exact: true});
  await expect(checks).toContainText('Código HTTP inesperado');
  await expect(checks).toContainText('503');
  await expect(incidents).toContainText('Primeira falha');
  await expect(incidents).toContainText('Queda confirmada');
  await expect(incidents).toContainText('Idade do incidente aberto');
  await expect(notifications).toContainText('Pendente');
  await expect(notifications).toContainText('ops@example.org');
  await expect(notifications).toContainText('Tentativas: 0');
  respond(monitor, 200);
  await expect.poll(async () => (await (await request.get(`/api/admin/monitors/${monitor.id}/summary`)).json()).confirmedState,
    {timeout: 30000}).toBe('UP');
  await page.getByRole('button', {name: 'Atualizar', exact: true}).click();
  await expect(incidents).toContainText('Encerrado');
  await expect(incidents).toContainText('Início da recuperação');
  await expect(incidents).toContainText('Recuperação confirmada');
  await expect(incidents).toContainText('Duração final estimada');
  await expect(notifications).toContainText('Recuperação');
  const actual = await (await request.get(`/api/admin/monitors/${monitor.id}/incidents`)).json();
  await expect(incidents).toContainText(actual.items[0].estimatedDurationSeconds.toLocaleString('pt-BR', {maximumFractionDigits: 9}) + ' s');
});

test('MON-10-AC04/05 exibe métricas e duração fracionária recebidas sem recalcular', async ({page, request}) => {
  const monitor = await create(request);
  await page.route(`**/monitors/${monitor.id}/summary*`, async route => {
    const actual = await (await route.fetch()).json();
    await route.fulfill({json: {...actual, label: 'SEM_DADOS_RECENTES', confirmedState: 'DOWN',
      statistics: {metricType: 'CHECK_SUCCESS_SAMPLING', dataStatus: 'HAS_DATA', totalChecks: 13,
        validSamples: 10, successSamples: 8, latencySamples: 8, successRatePercent: 80, averageResponseTimeMs: 100},
      currentIncident: {id: 'old-open', status: 'OPEN', firstFailureAt: '2026-10-18T12:00:00Z',
        downConfirmedAt: '2026-10-18T12:02:00Z', recoveryStartedAt: null, recoveredAt: null, ageSeconds: 100.9},
      noObservationPeriods: [{from: '2026-10-18T12:02:05Z', to: '2026-10-18T12:03:20Z'}]}});
  });
  await page.route(`**/monitors/${monitor.id}/incidents?*`, route => route.fulfill({json: {
    items: [{id: 'fractional', status: 'CLOSED', firstFailureAt: '2026-10-18T12:00:00.100Z',
      downConfirmedAt: '2026-10-18T12:02:00Z', recoveryStartedAt: '2026-10-18T12:03:01Z',
      recoveredAt: '2026-10-18T12:04:00Z', estimatedDurationSeconds: 180.9}],
    page: 0, size: 20, totalElements: 1, totalPages: 1
  }}));
  await page.goto(`/#/monitors/${monitor.id}`);
  await expect(page.getByTestId('success-rate')).toHaveText('80%');
  await expect(page.getByTestId('average-latency')).toHaveText('100 ms');
  await expect(page.getByRole('region', {name: 'Resumo', exact: true})).toContainText('10 amostras externas');
  await expect(page.getByRole('region', {name: 'Resumo', exact: true})).toContainText('13 verificações');
  await expect(page.getByRole('region', {name: 'Resumo', exact: true})).toContainText('8 amostras de latência');
  await expect(page.getByTestId('state-label')).toHaveText('Dados antigos');
  await expect(page.getByRole('region', {name: 'Incidente atual'})).toContainText('100,9 s');
  await expect(page.getByRole('region', {name: 'Incidentes', exact: true})).toContainText('180,9 s');
  const history = page.getByRole('region', {name: 'Incidentes', exact: true});
  for (const [caption, timestamp] of [
    ['Primeira falha', '2026-10-18T12:00:00.100Z'],
    ['Queda confirmada', '2026-10-18T12:02:00Z'],
    ['Início da recuperação', '2026-10-18T12:03:01Z'],
    ['Recuperação confirmada', '2026-10-18T12:04:00Z']
  ]) {
    await expect(history.locator('p').filter({hasText: caption + ':'}).locator('time'))
      .toHaveAttribute('datetime', timestamp);
  }
  const current = page.getByRole('region', {name: 'Incidente atual'});
  await expect(current.locator('p').filter({hasText: 'Primeira falha:'}).locator('time'))
    .toHaveAttribute('datetime', '2026-10-18T12:00:00Z');
  await expect(current.locator('p').filter({hasText: 'Queda confirmada:'}).locator('time'))
    .toHaveAttribute('datetime', '2026-10-18T12:02:00Z');
  for (const caption of ['Início da recuperação', 'Recuperação confirmada']) {
    const field = current.locator('p').filter({hasText: caption + ':'});
    await expect(field).toHaveText(caption + ': Sem registro');
    await expect(field.locator('time')).toHaveCount(0);
  }
  await expect(page.getByText('Taxa de sucesso por amostragem')).toBeVisible();
  await page.getByText('Períodos sem observação (1)').click();
  await expect(page.locator('time[datetime="2026-10-18T12:02:05Z"]')).toBeVisible();
  await expect(page.getByText('Cobertura conhecida desde', {exact: false})).toBeVisible();
});

for (const value of [null, 0]) {
  test(`MON-10-AC05 indicador ${value} preserva distinção entre ausência e zero`, async ({page, request}) => {
    const monitor = await create(request);
    await page.route(`**/monitors/${monitor.id}/summary*`, async route => {
      const actual = await (await route.fetch()).json();
      await route.fulfill({json: {...actual, statistics: {...actual.statistics,
        successRatePercent: value, averageResponseTimeMs: value}}});
    });
    await page.goto(`/#/monitors/${monitor.id}`);
    await expect(page.getByTestId('success-rate')).toHaveText(value === null ? 'Sem dados' : '0%');
    await expect(page.getByTestId('average-latency')).toHaveText(value === null ? 'Sem dados' : '0 ms');
  });
}

test('MON-10-AC04 filtros reais e janela antiga mantêm incidente atual separado', async ({page, request}) => {
  const monitor = await incident(request);
  await page.goto(`/#/monitors/${monitor.id}`);
  const incidents = page.getByRole('region', {name: 'Incidentes', exact: true});
  await expect(incidents).toContainText('Aberto');
  await incidents.getByLabel('Estado do incidente').selectOption('CLOSED');
  await incidents.getByRole('button', {name: 'Aplicar filtros'}).click();
  await expect(incidents).toContainText('Nenhum registro para os filtros selecionados.');
  const notifications = page.getByRole('region', {name: 'Notificações', exact: true});
  await notifications.getByLabel('Estado da notificação').selectOption('PENDING');
  await notifications.getByLabel('Tipo da notificação').selectOption('RECOVERY');
  const filtered = page.waitForRequest(r => r.url().includes('/notifications?') && r.url().includes('type=RECOVERY'));
  await notifications.getByRole('button', {name: 'Aplicar filtros'}).click();
  const url = new URL((await filtered).url());
  expect(url.searchParams.get('status')).toBe('PENDING');
  expect(url.searchParams.get('type')).toBe('RECOVERY');
  await expect(notifications).toContainText('Nenhum registro para os filtros selecionados.');
  await page.getByLabel('Início (ISO-8601 com fuso)').fill('2020-01-01T00:00:00+03:00');
  await page.getByLabel('Fim (ISO-8601 com fuso)').fill('2020-01-02T00:00:00+03:00');
  const summaryRequest = page.waitForRequest(r => r.url().includes('/summary?') && r.url().includes('2020-01-01'));
  await page.getByRole('button', {name: 'Aplicar período'}).click();
  const summaryUrl = new URL((await summaryRequest).url());
  expect(summaryUrl.searchParams.get('from')).toBe('2020-01-01T00:00:00+03:00');
  expect([...summaryUrl.searchParams.keys()].sort()).toEqual(['from', 'to']);
  await expect(page.getByTestId('success-rate')).toHaveText('Sem dados');
  await expect(page.getByRole('region', {name: 'Incidente atual'})).toContainText('Aberto');
});

test('MON-10-AC04 paginação independente e outcome enviados ao backend', async ({page, request}) => {
  const monitor = await create(request);
  await page.route(`**/monitors/${monitor.id}/checks?*`, route => {
    const params = new URL(route.request().url()).searchParams;
    const p = Number(params.get('page'));
    return route.fulfill({json: {items: [{executionId: 'check-page-' + p,
      startedAt: '2026-10-18T12:00:00Z', finishedAt: '2026-10-18T12:00:01Z',
      outcome: 'TIMEOUT', durationNanos: 1000000000, configurationVersion: 1, executionGeneration: 1}],
    page: p, size: 1, totalElements: 2, totalPages: 2}});
  });
  await page.goto(`/#/monitors/${monitor.id}`);
  const checks = page.getByRole('region', {name: 'Verificações', exact: true});
  await expect(checks).toContainText('check-page-0');
  await checks.getByRole('button', {name: 'Próxima', exact: true}).click();
  await expect(checks).toContainText('check-page-1');
  const changed = page.waitForRequest(r => r.url().includes('/checks?') && r.url().includes('outcome=TIMEOUT'));
  await checks.getByLabel('Resultado da verificação').selectOption('TIMEOUT');
  await checks.getByLabel('Registros por página de Verificações').selectOption('1');
  await checks.getByRole('button', {name: 'Aplicar filtros'}).click();
  const params = new URL((await changed).url()).searchParams;
  expect(params.get('page')).toBe('0');
  expect(params.get('size')).toBe('1');
  expect(params.get('outcome')).toBe('TIMEOUT');
  await expect(checks).toContainText('check-page-0');
  await expect(page.getByRole('region', {name: 'Incidentes', exact: true})).toContainText('Página 0 de 0');
});

test('MON-10-AC04 notificações expõem tentativas sanitizadas e aceitação SMTP', async ({page, request}) => {
  const monitor = await create(request);
  await page.route(`**/monitors/${monitor.id}/notifications?*`, route => route.fulfill({json: {
    items: [{id: 'notice-1', incidentId: 'incident-1', transitionId: 'transition-1', type: 'DOWN',
      confirmedAt: '2026-10-18T12:00:00Z', recipient: 'frozen@example.org', status: 'ACCEPTED', attemptCount: 2,
      nextAttemptAt: null, completedAt: '2026-10-18T12:01:00Z', attempts: [
        {attemptNumber: 1, startedAt: '2026-10-18T12:00:01Z', finishedAt: '2026-10-18T12:00:02Z',
          outcome: 'UNCERTAIN', errorCode: 'SMTP_UNCERTAIN'},
        {attemptNumber: 2, startedAt: '2026-10-18T12:00:32Z', finishedAt: '2026-10-18T12:00:33Z',
          outcome: 'ACCEPTED', errorCode: null}]}], page: 0, size: 20, totalElements: 1, totalPages: 1
  }}));
  await page.goto(`/#/monitors/${monitor.id}`);
  const notifications = page.getByRole('region', {name: 'Notificações', exact: true});
  await expect(notifications).toContainText('Aceita pelo SMTP');
  await expect(notifications).toContainText('frozen@example.org');
  await expect(notifications).toContainText('transition-1');
  await expect(notifications).toContainText('Tentativas: 2');
  await expect(notifications).toContainText('SMTP_UNCERTAIN');
  await expect(notifications.locator('time[datetime="2026-10-18T12:00:33Z"]')).toBeVisible();
  await expect(notifications).toContainText('Aceitação SMTP não confirma entrega na caixa postal.');
});

for (const [resource, title] of [['incidents', 'Incidentes'], ['notifications', 'Notificações']]) {
  test(`MON-10-AC04 pagina ${resource} sem mudar outros históricos`, async ({page, request}) => {
    const monitor = await create(request);
    await page.route(`**/monitors/${monitor.id}/${resource}?*`, route => {
      const p = Number(new URL(route.request().url()).searchParams.get('page'));
      const item = resource === 'incidents' ? {id: 'incident-page-' + p, status: 'OPEN', ageSeconds: 12.5}
        : {id: 'notification-page-' + p, type: 'DOWN', status: 'EXHAUSTED', attemptCount: 5, attempts: []};
      return route.fulfill({json: {items: [item], page: p, size: 1, totalElements: 2, totalPages: 2}});
    });
    await page.goto(`/#/monitors/${monitor.id}`);
    const section = page.getByRole('region', {name: title, exact: true});
    await expect(section).toContainText(resource === 'incidents' ? 'incident-page-0' : 'notification-page-0');
    await section.getByRole('button', {name: 'Próxima', exact: true}).click();
    await expect(section).toContainText(resource === 'incidents' ? 'incident-page-1' : 'notification-page-1');
    await expect(section.getByRole('button', {name: 'Próxima', exact: true})).toBeDisabled();
    const other = page.getByRole('region', {name: resource === 'incidents' ? 'Notificações' : 'Incidentes', exact: true});
    await expect(other).toContainText('Página 0 de 0');
  });
}

test('MON-10-AC05/06 falhas independentes, 401 e validação do período', async ({page, request}) => {
  const monitor = await create(request);
  await page.route(`**/monitors/${monitor.id}/summary*`, route => route.fulfill({status: 401, body: ''}));
  await page.route(`**/monitors/${monitor.id}/incidents?*`, route => route.fulfill({status: 503, body: ''}));
  await page.goto(`/#/monitors/${monitor.id}`);
  await expect(page.getByRole('region', {name: 'Resumo', exact: true}).getByRole('alert')).toContainText('Autenticação administrativa necessária');
  await expect(page.getByRole('region', {name: 'Incidentes', exact: true}).getByRole('alert')).toContainText('Não foi possível consultar o backend');
  await expect(page.getByRole('region', {name: 'Notificações', exact: true})).toContainText('Nenhum registro para os filtros selecionados.');
  await page.unroute(`**/monitors/${monitor.id}/summary*`);
  await page.getByLabel('Início (ISO-8601 com fuso)').fill('invalid');
  await page.getByRole('button', {name: 'Aplicar período'}).click();
  await expect(page.locator('#from-error')).not.toBeEmpty();
  await expect(page.getByLabel('Início (ISO-8601 com fuso)')).toHaveValue('invalid');
  await expect(page.getByLabel('Início (ISO-8601 com fuso)')).toHaveAttribute('aria-invalid', 'true');
});

test('MON-10-AC05 resposta atrasada não substitui formulário após navegação', async ({page, request}) => {
  const monitor = await create(request);
  let release;
  const pending = new Promise(resolve => { release = resolve; });
  await page.route(`**/monitors/${monitor.id}/summary*`, async route => {
    const response = await route.fetch();
    await pending;
    await route.fulfill({response});
  });
  await page.goto(`/#/monitors/${monitor.id}`);
  await expect(page.getByText('Carregando resumo…')).toBeVisible();
  await page.getByRole('link', {name: 'Editar', exact: true}).click();
  await expect(page.getByLabel('Nome', {exact: true})).toHaveValue(monitor.name);
  await page.getByLabel('Nome', {exact: true}).fill('Rascunho preservado');
  release();
  await expect(page.getByLabel('Nome', {exact: true})).toHaveValue('Rascunho preservado');
  await expect(page.getByRole('heading', {name: 'Resumo', exact: true})).toHaveCount(0);
});
