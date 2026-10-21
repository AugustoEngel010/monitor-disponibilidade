import {test} from 'node:test';
import assert from 'node:assert/strict';
import {startServer} from './server.mjs';

test('MON-12-AC02 modos, independência e entradas inválidas', async () => {
  const server = await startServer(0, 120);
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    assert.equal((await fetch(`${base}/targets/main`)).status, 200);
    assert.equal((await fetch(`${base}/control/main/error`, {method: 'POST'})).status, 200);
    assert.equal((await fetch(`${base}/targets/main`)).status, 503);
    assert.equal((await fetch(`${base}/targets/secondary`)).status, 200);
    assert.equal((await fetch(`${base}/control/main/slow`, {method: 'POST'})).status, 200);
    const start = performance.now();
    assert.equal((await fetch(`${base}/targets/main`)).status, 200);
    assert.ok(performance.now() - start >= 110, 'slow must delay headers');
    assert.equal((await fetch(`${base}/control/main/invalid`, {method: 'POST'})).status, 400);
    assert.equal((await fetch(`${base}/control/missing/error`, {method: 'POST'})).status, 404);
    assert.equal((await fetch(`${base}/missing`)).status, 404);
    assert.equal((await fetch(`${base}/control`)).status, 200);
    assert.equal((await (await fetch(`${base}/control`)).json()).main, 'slow');
    assert.equal((await fetch(`${base}/control/main/healthy`)).status, 405);
    await fetch(`${base}/control/main/healthy`, {method: 'POST'});
    assert.equal((await fetch(`${base}/targets/main`)).status, 200);
  } finally {
    server.closeAllConnections();
    await new Promise(resolve => server.close(resolve));
  }
});
