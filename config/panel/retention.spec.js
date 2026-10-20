import {test, expect} from '@playwright/test';
import {create} from './fixtures.js';

const cutoff = '2026-10-19T12:34:56Z';

for (const removed of [true, false]) {
  test(`MON-11-AC05 limite histórico e ausência no painel, removido=${removed}`, async ({page, request}) => {
    const monitor = await create(request);
    const periods = removed ? [{from: '2026-10-18T12:00:00Z', to: cutoff}] : [];
    await page.route(`**/monitors/${monitor.id}/summary*`, async route => {
      const actual = await (await route.fetch()).json();
      await route.fulfill({json: {...actual, historyAvailableFrom: cutoff, retainedOutPeriods: periods,
        statistics: {...actual.statistics, dataStatus: 'NO_DATA', totalChecks: 0, validSamples: 0,
          successSamples: 0, latencySamples: 0, successRatePercent: null, averageResponseTimeMs: null}}});
    });
    for (const resource of ['checks', 'incidents', 'notifications']) {
      await page.route(`**/monitors/${monitor.id}/${resource}?*`, async route => {
        const actual = await (await route.fetch()).json();
        await route.fulfill({json: {...actual, items: [], totalElements: 0, totalPages: 0,
          historyAvailableFrom: cutoff, retainedOutPeriods: periods}});
      });
    }
    await page.goto(`/#/monitors/${monitor.id}`);
    for (const name of ['Resumo', 'Verificações', 'Incidentes', 'Notificações']) {
      const region = page.getByRole('region', {name, exact: true});
      await expect(region.getByText('Histórico disponível desde', {exact: false})).toBeVisible();
      await expect(region.locator('time').filter({hasText: /19\/10\/2026/}).first())
        .toHaveAttribute('datetime', cutoff);
      const warning = region.getByText('Parte do período foi removida pela retenção. Ausência de histórico não indica disponibilidade.');
      await expect(warning).toHaveCount(removed ? 1 : 0);
    }
    await expect(page.getByTestId('success-rate')).toHaveText('Sem dados');
    await expect(page.getByTestId('average-latency')).toHaveText('Sem dados');
    await expect(page.getByRole('region', {name: 'Resumo', exact: true})).toContainText('0 amostras externas');
  });
}
