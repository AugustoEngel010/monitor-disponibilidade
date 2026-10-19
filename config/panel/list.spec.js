import {test, expect} from '@playwright/test';
import {create, openList} from './fixtures.js';

test('MON-10-AC01 lista usa backend real e paginação da API', async ({page, request}) => {
  const monitor = await create(request);
  const card = await openList(page, request, monitor.id);
  await expect(card.getByRole('heading')).toHaveText(monitor.name);
  await expect(card).toContainText('Última observação');
  await expect(card).toContainText('Incidente aberto: não');
  await expect(card.getByRole('link', {name: 'Detalhes'})).toHaveAttribute('href', `#/monitors/${monitor.id}`);
  await expect(page.getByRole('navigation', {name: 'Paginação de monitores'})).toContainText('registros');
});

const cases = [
  ['PAUSED', 'Pausado'], ['SEM_DADOS', 'Sem observação corrente'],
  ['SEM_DADOS_RECENTES', 'Dados antigos'], ['CONFIRMANDO_QUEDA', 'Confirmando queda'],
  ['CONFIRMANDO_RECUPERACAO', 'Confirmando recuperação'], ['UP', 'Disponível'], ['DOWN', 'Indisponível']
];
for (const [label, text] of cases) {
  test(`MON-10-AC01/05 label oficial ${label} e incidente independente`, async ({page, request}) => {
    const monitor = await create(request);
    await page.route(`**/monitors/${monitor.id}/summary`, async route => {
      const body = await (await route.fetch()).json();
      await route.fulfill({json: {...body, label, confirmedState: 'DOWN',
        lastObservedAt: '2026-10-18T12:00:00Z', currentIncident: {id: 'incidente-preservado'}}});
    });
    const card = await openList(page, request, monitor.id);
    await expect(card.getByTestId('state-label')).toHaveText(text);
    await expect(card).toContainText('Estado confirmado: Indisponível');
    await expect(card).toContainText('incidente-preservado');
    await expect(card.locator('time')).toHaveAttribute('datetime', '2026-10-18T12:00:00Z');
  });
}

test('MON-10-AC05 vazio e página excedente oferecem mensagem e retorno', async ({page}) => {
  await page.route(/\/api\/admin\/monitors\?/, route => route.fulfill({json: {
    items: [], page: 1, size: 20, totalElements: 1, totalPages: 1
  }}));
  await page.goto('/#/?page=1');
  await expect(page.getByText('Nenhum monitor nesta página.')).toBeVisible();
  await expect(page.getByRole('button', {name: 'Anterior', exact: true})).toBeEnabled();
  await page.unroute(/\/api\/admin\/monitors\?/);
  await page.route(/\/api\/admin\/monitors\?/, route => route.fulfill({json: {
    items: [], page: 0, size: 20, totalElements: 0, totalPages: 0
  }}));
  await page.getByRole('button', {name: 'Anterior', exact: true}).click();
  await expect(page.getByText('Nenhum monitor cadastrado. Comece em Novo monitor.')).toBeVisible();
});

test('MON-10-AC05 carregamento, erro geral e nova consulta', async ({page}) => {
  let release;
  const pending = new Promise(resolve => { release = resolve; });
  await page.route(/\/api\/admin\/monitors\?/, async route => {
    await pending;
    await route.fulfill({status: 503, body: 'internal details must not appear'});
  });
  await page.goto('/');
  await expect(page.getByText('Carregando monitores…')).toBeVisible();
  release();
  await expect(page.getByRole('alert')).toContainText('Não foi possível consultar o backend');
  await expect(page.locator('main')).not.toContainText('internal details');
  await page.unroute(/\/api\/admin\/monitors\?/);
  await page.getByRole('button', {name: 'Atualizar', exact: true}).click();
  await expect(page.getByRole('navigation', {name: 'Paginação de monitores'})).toBeVisible();
});

test('MON-10-AC05 falha de summary é isolada e nomes são texto seguro', async ({page, request}) => {
  const monitor = await create(request, {name: '<img src=x onerror=alert(1)>'});
  await page.route(`**/monitors/${monitor.id}/summary`, route => route.abort());
  const card = await openList(page, request, monitor.id);
  await expect(card.getByRole('alert')).toContainText('Estado não carregado');
  await expect(card.getByRole('heading')).toHaveText('<img src=x onerror=alert(1)>');
  await expect(card.locator('img')).toHaveCount(0);
  await expect(card.getByTestId('state-label')).toHaveCount(0);
});

test('MON-10-AC01 paginação consulta a próxima página real', async ({page, request}) => {
  let data = await (await request.get('/api/admin/monitors?size=100')).json();
  while (data.totalElements < 21) {
    await create(request);
    data = await (await request.get('/api/admin/monitors?size=100')).json();
  }
  await page.goto('/');
  const first = data.items[0];
  const next = data.items[20];
  await expect(page.getByTestId(`monitor-${first.id}`)).toBeVisible();
  await page.getByRole('button', {name: 'Próxima', exact: true}).click();
  await expect(page.getByTestId(`monitor-${next.id}`)).toBeVisible();
  await expect(page.getByTestId(`monitor-${first.id}`)).toHaveCount(0);
});
