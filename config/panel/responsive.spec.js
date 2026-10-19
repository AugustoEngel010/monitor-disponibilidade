import {test, expect} from '@playwright/test';
import {config, openList} from './fixtures.js';
import fs from 'node:fs/promises';

async function noOverflow(page) {
  const dimensions = await page.evaluate(() => ({content: document.documentElement.scrollWidth, viewport: innerWidth}));
  expect(dimensions.content).toBeLessThanOrEqual(dimensions.viewport);
}

for (const [name, viewport] of [['desktop', {width: 1440, height: 900}], ['mobile', {width: 390, height: 844}]]) {
  test(`MON-10-AC06 funções principais acessíveis em ${name}`, async ({page, request}) => {
    await page.setViewportSize(viewport);
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto('/#/new');
    await page.getByLabel('Nome', {exact: true}).fill('Serviço de acompanhamento ' + name);
    await page.getByLabel('URL', {exact: true}).fill('https://example.org/' + 'a'.repeat(180));
    await page.getByLabel('Destinatário dos alertas', {exact: true}).fill(config.alertRecipient);
    await noOverflow(page);
    await page.getByLabel('Nome', {exact: true}).focus();
    await page.keyboard.press('Tab');
    await expect(page.getByLabel('URL', {exact: true})).toBeFocused();
    const outline = await page.getByLabel('URL', {exact: true}).evaluate(element => getComputedStyle(element).outlineStyle);
    expect(outline).not.toBe('none');
    await fs.mkdir('/reports/screenshots', {recursive: true});
    await page.screenshot({path: `/reports/screenshots/${name}-form.png`, fullPage: true});
    const createdResponse = page.waitForResponse(r => r.url().endsWith('/api/admin/monitors') && r.request().method() === 'POST');
    await page.getByRole('button', {name: 'Salvar monitor'}).click();
    const monitor = await (await createdResponse).json();
    const card = await openList(page, request, monitor.id);
    await expect(card.getByRole('button', {name: 'Pausar', exact: true})).toBeEnabled();
    await card.getByRole('button', {name: 'Pausar', exact: true}).click();
    await expect(card.getByTestId('state-label')).toHaveText('Pausado');
    await noOverflow(page);
    await card.scrollIntoViewIfNeeded();
    await page.screenshot({path: `/reports/screenshots/${name}-list.png`});
    await card.getByRole('button', {name: 'Retomar', exact: true}).click();
    await expect(card.getByRole('button', {name: 'Pausar', exact: true})).toBeEnabled();
    await card.getByRole('link', {name: 'Editar', exact: true}).click();
    await page.getByLabel('Nome', {exact: true}).fill('Monitor revisado ' + name);
    await page.getByRole('button', {name: 'Salvar monitor'}).click();
    await expect(page.getByRole('status').filter({hasText: 'Monitor atualizado.'})).toBeVisible();
    await page.goto(`/#/monitors/${monitor.id}`);
    await expect(page.getByRole('heading', {level: 1})).toHaveText('Monitor revisado ' + name);
    await expect(page.getByTestId('success-rate')).toBeVisible();
    await page.getByRole('region', {name: 'Incidentes', exact: true}).getByLabel('Estado do incidente').selectOption('OPEN');
    await page.getByRole('region', {name: 'Incidentes', exact: true}).getByRole('button', {name: 'Aplicar filtros'}).click();
    await expect(page.getByRole('region', {name: 'Incidentes', exact: true})).toContainText('Nenhum registro para os filtros selecionados.');
    await expect(page.getByRole('region', {name: 'Notificações', exact: true})).toContainText('Nenhum registro para os filtros selecionados.');
    await noOverflow(page);
    await page.screenshot({path: `/reports/screenshots/${name}-details.png`, fullPage: true});
    expect(errors).toEqual([]);
  });
}
