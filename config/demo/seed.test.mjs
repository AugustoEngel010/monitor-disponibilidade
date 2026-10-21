import {test} from 'node:test';
import assert from 'node:assert/strict';
import {seed, configurations} from './seed.mjs';

test('MON-12-AC01 seed serial preserva IDs, rejeita divergência e duplicação', async () => {
  const items = [];
  const call = async (path, method, data) => {
    if (method === 'POST') {
      const monitor = {...data, id: `id-${items.length}`}; items.push(monitor); return monitor;
    }
    return {items};
  };
  const first = await seed(call);
  assert.equal(items.length, 2);
  assert.deepEqual(await seed(call), first);
  assert.equal(items.length, 2);
  assert.equal(items[0].alertRecipient, 'portfolio@example.test');
  items[0].timeoutSeconds = 3;
  await assert.rejects(seed(call), /incompatible/);
  items[0].timeoutSeconds = configurations[0].timeoutSeconds;
  items.push({...items[0], id: 'duplicate'});
  await assert.rejects(seed(call), /duplicate/);
});
