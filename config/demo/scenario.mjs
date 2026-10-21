import assert from 'node:assert/strict';
import {readFile, writeFile, mkdir} from 'node:fs/promises';
import {chromium, expect} from '@playwright/test';
import {api, ready, until} from './client.mjs';
import {seed} from './seed.mjs';

const phase = process.argv[2];
const reports = '/reports';
await mkdir(reports, {recursive: true});
await ready();
const monitors = await seed();
const main = monitors.main.id;
const secondary = monitors.secondary.id;
const base = `/api/admin/monitors/${main}`;
const summary = id => api(`/api/admin/monitors/${id}/summary`);
const mode = async value => {
  const response = await fetch(`http://demo:8080/control/main/${value}`, {
    method: 'POST', signal: AbortSignal.timeout(3000)});
  assert.equal(response.status, 200);
};
const waitMain = (description, predicate, timeout) => until(description, () => summary(main), predicate, timeout);
const checks = id => api(`/api/admin/monitors/${id}/checks?size=100`);
const notifications = () => api(`${base}/notifications?size=100`);
const incidents = () => api(`${base}/incidents?size=100`);
const save = async (name, value) => {
  await writeFile(`${reports}/${name}.json`, JSON.stringify(value, null, 2) + '\n');
  console.log(`PASS ${name}`);
};
const load = async name => JSON.parse(await readFile(`${reports}/${name}.json`, 'utf8'));

async function screenshot(name, details = false) {
  const browser = await chromium.launch({headless: true});
  try {
    const context = await browser.newContext({
      httpCredentials: {username: process.env.ADMIN_USERNAME, password: process.env.ADMIN_PASSWORD},
      viewport: {width: 1440, height: 1000}, locale: 'pt-BR', timezoneId: 'America/Sao_Paulo'});
    const page = await context.newPage();
    await page.goto(`http://app:8080/#/${details ? 'monitors/' + main : ''}`);
    await page.getByRole('heading', {name: details ? 'Demo principal' : 'Monitores', exact: true}).waitFor();
    await expect(page.getByTestId('state-label')).toHaveCount(details ? 1 : 2);
    if (details) await expect(page.getByRole('region', {name: 'Notificações'})).toContainText('portfolio@example.test');
    await page.screenshot({path: `${reports}/${name}.png`, fullPage: true});
  } finally { await browser.close(); }
}

async function accepted(count) {
  return until('SMTP accepted', notifications,
    value => value.items.length === count && value.items.every(item => item.status === 'ACCEPTED'), 150000);
}

if (phase === 'normal') {
  const first = await seed();
  assert.equal(first.main.id, main);
  assert.equal(first.secondary.id, secondary);
  assert.equal((await api('/api/admin/monitors?size=100')).totalElements, 2);
  await waitMain('initial success', value => value.label === 'UP');
  assert.equal((await incidents()).totalElements, 0);
  assert.equal((await notifications()).totalElements, 0);
  await screenshot('01-normal');
  await mode('error');
  const confirming = await waitMain('confirming down', value => value.label === 'CONFIRMANDO_QUEDA');
  assert.equal(confirming.failureCount, 1);
  assert.equal(confirming.currentIncident, null);
  await save('confirming-down', confirming);
  const down = await waitMain('down', value => value.label === 'DOWN');
  assert.equal(down.failureCount, 3);
  const opened = down.currentIncident.id;
  await accepted(1);
  const previous = down.lastObservedAt;
  await waitMain('additional failed check', value => value.lastObservedAt > previous);
  assert.equal((await incidents()).totalElements, 1);
  assert.equal((await summary(main)).currentIncident.id, opened);
  assert.equal((await notifications()).totalElements, 1);
  await screenshot('02-down');
  await mode('healthy');
  const recovering = await waitMain('confirming recovery', value => value.label === 'CONFIRMANDO_RECUPERACAO');
  assert.equal(recovering.recoveryCount, 1);
  assert.equal(recovering.currentIncident.id, opened);
  await save('confirming-recovery', recovering);
  await screenshot('03-recovering');
  await waitMain('recovered', value => value.label === 'UP' && !value.currentIncident);
  const closed = (await incidents()).items;
  assert.equal(closed.length, 1);
  assert.equal(closed[0].id, opened);
  assert.equal(closed[0].status, 'CLOSED');
  assert.ok(closed[0].firstFailureAt < closed[0].downConfirmedAt);
  assert.ok(closed[0].recoveryStartedAt < closed[0].recoveredAt);
  const delivered = await accepted(2);
  assert.deepEqual(delivered.items.map(item => item.type).sort(), ['DOWN', 'RECOVERY']);
  const downNotice = delivered.items.find(item => item.type === 'DOWN');
  const recovery = delivered.items.find(item => item.type === 'RECOVERY');
  assert.ok(downNotice.completedAt <= recovery.attempts[0].startedAt);
  await save('normal', {main, secondary, down, closed, delivered, checks: await checks(main)});
  await screenshot('04-recovered');
} else if (phase === 'pending') {
  await mode('error');
  const down = await waitMain('second down', value => value.label === 'DOWN');
  const pending = await until('pending after SMTP failure', notifications, value =>
    value.items.some(item => item.incidentId === down.currentIncident.id
      && item.status === 'PENDING' && item.attemptCount >= 1));
  const notice = pending.items.find(item => item.incidentId === down.currentIncident.id);
  assert.equal(notice.type, 'DOWN');
  assert.equal(notice.attempts[0].outcome, 'FAILED');
  assert.equal(notice.attempts[0].errorCode, 'SMTP_FAILURE');
  assert.equal(notice.attemptCount, 1);
  await save('pending', {main, secondary, down, notice, checks: await checks(main), incidents: await incidents()});
  await screenshot('05-pending', true);
} else if (phase === 'restart') {
  const before = await load('pending');
  const down = await waitMain('same open incident after restart', value => value.confirmedState === 'DOWN');
  assert.equal(down.currentIncident.id, before.down.currentIncident.id);
  const after = (await notifications()).items.find(item => item.id === before.notice.id);
  assert.equal(after.status, 'PENDING');
  assert.equal(after.incidentId, before.notice.incidentId);
  assert.ok(after.attemptCount >= before.notice.attemptCount && after.attemptCount < 5);
  assert.equal((await incidents()).totalElements, before.incidents.totalElements);
  const persisted = await checks(main);
  for (const check of before.checks.items) assert.ok(persisted.items.some(row => row.executionId === check.executionId));
  await save('restart', {down, notice: after, checks: persisted});
} else if (phase === 'retry') {
  const before = await load('pending');
  const delivered = await accepted(3);
  const notice = delivered.items.find(item => item.id === before.notice.id);
  assert.equal(notice.incidentId, before.notice.incidentId);
  assert.ok(notice.attemptCount >= 2 && notice.attemptCount <= 5);
  assert.equal(notice.attempts.at(-1).outcome, 'ACCEPTED');
  assert.ok(Date.parse(notice.attempts[1].startedAt) - Date.parse(notice.attempts[0].finishedAt) >= 30000);
  await save('retry', {notice, down: await summary(main)});
} else if (phase === 'slow') {
  await api(`${base}/pause`, 'POST');
  await api(`/api/admin/monitors/${secondary}/pause`, 'POST');
  await mode('slow');
  const baseline = (await checks(secondary)).totalElements;
  await api(`${base}/resume`, 'POST');
  await until('slow request in flight', async () => (await (await fetch('http://demo:8080/control')).json()),
    value => value.active.main > 0, 10000);
  await api(`/api/admin/monitors/${secondary}/resume`, 'POST');
  const fast = await until('independent target success', () => checks(secondary), value => value.totalElements > baseline);
  assert.equal(fast.items[0].outcome, 'SUCCESS');
  const slow = await waitMain('slow timeout', value => value.lastObservation?.outcome === 'TIMEOUT');
  const fastCheck = fast.items[0];
  assert.ok(fastCheck.startedAt >= slow.lastObservation.startedAt);
  assert.ok(fastCheck.finishedAt < slow.lastObservation.finishedAt, 'fast completed during slow check');
  assert.equal(slow.confirmedState, 'DOWN');
  const incidentId = slow.currentIncident.id;
  await save('slow', {slow, fastCheck});
  await api(`${base}/pause`, 'POST');
  const paused = await summary(main);
  assert.equal(paused.label, 'PAUSED');
  assert.equal(paused.confirmedState, 'DOWN');
  assert.equal(paused.currentIncident.id, incidentId);
  const pausedCount = (await checks(main)).totalElements;
  await new Promise(resolve => setTimeout(resolve, 11000));
  assert.equal((await checks(main)).totalElements, pausedCount);
  await save('paused', paused);
  await screenshot('06-paused');
  await api(`${base}/resume`, 'POST');
  await waitMain('fresh down after resume', value => value.label === 'DOWN' && value.evidence === 'FRESH');
} else if (phase === 'stale') {
  const before = await load('paused');
  const stale = await waitMain('no recent data', value => value.label === 'SEM_DADOS_RECENTES', 45000);
  assert.equal(stale.lifecycle, 'ACTIVE');
  assert.equal(stale.confirmedState, 'DOWN');
  assert.equal(stale.evidence, 'STALE');
  assert.equal(stale.currentIncident.id, before.currentIncident.id);
  assert.ok(stale.noObservationPeriods.length > 0);
  await save('stale', stale);
  await screenshot('07-stale');
} else if (phase === 'finish') {
  await mode('healthy');
  await waitMain('final recovery', value => value.label === 'UP' && !value.currentIncident);
  const delivered = await accepted(4);
  const all = await incidents();
  assert.equal(all.totalElements, 2);
  assert.ok(all.items.every(item => item.status === 'CLOSED'));
  const messagesResponse = await fetch('http://smtp:8025/api/v1/messages');
  assert.equal(messagesResponse.status, 200);
  const messages = await messagesResponse.json();
  assert.equal(messages.total, 4);
  const emails = [];
  for (const message of messages.messages) {
    assert.equal(message.To[0].Address, 'portfolio@example.test');
    const response = await fetch(`http://smtp:8025/api/v1/message/${message.ID}`);
    assert.equal(response.status, 200);
    emails.push(await response.json());
  }
  const pending = await load('pending');
  assert.ok(delivered.items.some(item => item.id === pending.notice.id));
  for (const notice of delivered.items) {
    assert.equal(delivered.items.filter(item => item.incidentId === notice.incidentId && item.type === notice.type).length, 1);
    assert.ok(emails.some(email => JSON.stringify(email).includes(notice.incidentId)), 'email incident correlation');
  }
  await save('final', {main, secondary, summary: await summary(main), incidents: all, notifications: delivered,
    mailpit: messages, emails});
  await screenshot('08-final');
} else {
  throw new Error(`Unknown phase: ${phase}`);
}
